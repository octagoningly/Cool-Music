package moe.ouom.neriplayer.data.source.lxmusic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.ouom.neriplayer.core.logging.NPLogger
import java.io.IOException
import java.util.UUID

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.data.source.lxmusic/LxMusicSourceRepository
 * Updated: 2026/3/23
 */

private val Context.lxMusicSourceDataStore by preferencesDataStore("lx_music_sources")

private object LxMusicSourcePreferenceKeys {
    val SOURCES_JSON = stringPreferencesKey("sources_json")
    val PREFER_CUSTOM_SOURCE = booleanPreferencesKey("prefer_custom_source")
}

/** 某首歌曲的在线音源偏好：成功音源优先，失败音源降权（不排除）。 */
@Serializable
internal data class LxSongSourceAffinity(
    val preferredSourceId: String? = null,
    val failedSourceIds: List<String> = emptyList(),
    /** Sources whose URL reached Media3 but then failed to start playback. */
    val playbackFailedSourceIds: List<String> = emptyList(),
    /** Wall-clock time of the most recent player-level source failure. */
    val playbackFailureAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

@Serializable
private data class LxSongSourceAffinityStore(
    val entries: Map<String, LxSongSourceAffinity> = emptyMap()
)

internal const val LX_SONG_AFFINITY_MAX_ENTRIES = 300
internal const val LX_PLAYBACK_FAILED_SOURCE_SUPPRESSION_MS = 15 * 60 * 1000L

/**
 * A resolver returning an HTTP URL is not proof that the endpoint can actually play.
 * Keep player-level failures out of the retry set briefly so a refresh tries another
 * imported source instead of returning to the same dead endpoint.
 */
internal fun recentLxPlaybackFailedSourceIds(
    affinity: LxSongSourceAffinity?,
    nowMs: Long
): Set<String> {
    if (affinity == null || affinity.playbackFailureAtMs <= 0L) return emptySet()
    if (nowMs - affinity.playbackFailureAtMs !in 0..LX_PLAYBACK_FAILED_SOURCE_SUPPRESSION_MS) {
        return emptySet()
    }
    return affinity.playbackFailedSourceIds.filter { it.isNotBlank() }.toSet()
}

/**
 * 按「成功音源 → 其他音源 → 本曲失败过的音源」排序，保持原有配置顺序作为次序。
 */
internal fun orderLxSourcesForSong(
    sources: List<LxImportedSource>,
    affinity: LxSongSourceAffinity?
): List<LxImportedSource> {
    if (affinity == null || sources.size <= 1) return sources
    val failedIds = affinity.failedSourceIds.toSet()
    val preferredId = affinity.preferredSourceId
    return sources.sortedWith(
        compareBy { source ->
            when {
                source.id == preferredId -> 0
                source.id in failedIds -> 2
                else -> 1
            }
        }
    )
}

class LxMusicSourceRepository(
    private val context: Context,
    private val client: LxMusicSourceClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** 播放期解析结果：用于在设置页显示在线音源当前是否可用 */
    data class RuntimeStatus(
        val lastSuccessAtMs: Long = 0L,
        val lastFailureAtMs: Long = 0L,
        val lastFailureReason: String? = null
    ) {
        val hasActivity: Boolean get() = lastSuccessAtMs > 0L || lastFailureAtMs > 0L
        val lastAttemptFailed: Boolean get() = lastFailureAtMs > lastSuccessAtMs
    }

    private val _runtimeStatus = MutableStateFlow(RuntimeStatus())
    val runtimeStatusFlow: StateFlow<RuntimeStatus> = _runtimeStatus.asStateFlow()

    fun recordResolveSuccess() {
        _runtimeStatus.value = RuntimeStatus(
            lastSuccessAtMs = System.currentTimeMillis(),
            lastFailureAtMs = _runtimeStatus.value.lastFailureAtMs,
            lastFailureReason = null
        )
    }

    fun recordResolveFailure(reason: String) {
        val now = System.currentTimeMillis()
        _runtimeStatus.update { current ->
            // 同一轮解析会按音质/平台多次失败，节流避免刷屏
            if (current.lastAttemptFailed && now - current.lastFailureAtMs < STATUS_THROTTLE_MS) {
                current
            } else {
                current.copy(lastFailureAtMs = now, lastFailureReason = reason)
            }
        }
    }

    /** 播放热路径用内存缓存，避免每次 resolve 都读 DataStore */
    @Volatile
    private var cachedSources: List<LxImportedSource> = emptyList()

    @Volatile
    private var cachedPreferCustomSource: Boolean = true

    @Volatile
    private var cacheWarmed: Boolean = false

    /** 歌曲 → 在线音源记忆，独立于 DataStore，播放热路径同步读取 */
    private val songAffinityPreferences by lazy {
        context.getSharedPreferences(SONG_AFFINITY_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val songAffinityLock = Any()
    private var songAffinityLoaded = false
    private var songAffinityCache: MutableMap<String, LxSongSourceAffinity> = mutableMapOf()

    val sourcesFlow: Flow<List<LxImportedSource>> =
        context.lxMusicSourceDataStore.data.map { prefs ->
            val sources = decodeSources(prefs[LxMusicSourcePreferenceKeys.SOURCES_JSON])
            cachedSources = sources
            cacheWarmed = true
            sources
        }

    val preferCustomSourceFlow: Flow<Boolean> =
        context.lxMusicSourceDataStore.data.map { prefs ->
            val enabled = prefs[LxMusicSourcePreferenceKeys.PREFER_CUSTOM_SOURCE] ?: true
            cachedPreferCustomSource = enabled
            cacheWarmed = true
            enabled
        }

    /**
     * 同步读取已启用音源（优先内存缓存）。
     * 未预热时返回空列表，避免阻塞播放路径。
     */
    fun peekEnabledSources(): List<LxImportedSource> {
        if (!cacheWarmed) return emptyList()
        if (!cachedPreferCustomSource) return emptyList()
        return cachedSources.filter { source ->
            if (!source.enabled) return@filter false
            if (source.isJsSource) {
                source.scriptPath.isNotBlank()
            } else {
                source.searchApiUrl.isNotBlank() && source.songUrlApiUrl.isNotBlank()
            }
        }
    }

    fun peekHasActiveSources(): Boolean = peekEnabledSources().isNotEmpty()

    /** 读取某首歌记住的在线音源顺序（无记录时返回 null）。 */
    internal fun peekSongSourceAffinity(songKey: String): LxSongSourceAffinity? {
        if (songKey.isBlank()) return null
        return synchronized(songAffinityLock) {
            loadSongAffinityLocked()[songKey]
        }
    }

    /**
     * 记录一次「某音源失败、另一音源成功」的解析结果。
     * 只降权失败音源，不排除；成功后该音源成为该曲的优先音源。
     */
    internal fun recordSongSourceOutcome(
        songKey: String,
        successSourceId: String,
        failedSourceIds: Collection<String>
    ) {
        if (songKey.isBlank() || successSourceId.isBlank()) return
        synchronized(songAffinityLock) {
            val entries = loadSongAffinityLocked()
            val previous = entries[songKey] ?: LxSongSourceAffinity()
            val failed = LinkedHashSet(previous.failedSourceIds)
            failed.addAll(failedSourceIds.filter { it.isNotBlank() && it != successSourceId })
            entries[songKey] = previous.copy(
                preferredSourceId = successSourceId,
                failedSourceIds = failed.toList(),
                updatedAtMs = System.currentTimeMillis()
            )
            trimSongAffinityLocked(entries)
            persistSongAffinityLocked(entries)
        }
    }

    /**
     * The player rejected or could not start a URL returned by this source. This is
     * deliberately separate from resolver failures: a syntactically valid URL may
     * still be expired, blocked, or serve an unsupported payload.
     */
    internal fun recordSongSourcePlaybackFailure(songKey: String, sourceId: String) {
        if (songKey.isBlank() || sourceId.isBlank()) return
        synchronized(songAffinityLock) {
            val entries = loadSongAffinityLocked()
            val previous = entries[songKey] ?: LxSongSourceAffinity()
            val failed = LinkedHashSet(previous.playbackFailedSourceIds)
            failed += sourceId
            val nowMs = System.currentTimeMillis()
            entries[songKey] = previous.copy(
                preferredSourceId = previous.preferredSourceId.takeUnless { it == sourceId },
                playbackFailedSourceIds = failed.toList(),
                playbackFailureAtMs = nowMs,
                updatedAtMs = nowMs
            )
            trimSongAffinityLocked(entries)
            persistSongAffinityLocked(entries)
        }
    }

    /** A source is promoted only after Media3 has actually started it. */
    internal fun recordSongSourcePlaybackSuccess(songKey: String, sourceId: String) {
        if (songKey.isBlank() || sourceId.isBlank()) return
        synchronized(songAffinityLock) {
            val entries = loadSongAffinityLocked()
            val previous = entries[songKey] ?: LxSongSourceAffinity()
            val remainingFailures = previous.playbackFailedSourceIds.filterNot { it == sourceId }
            entries[songKey] = previous.copy(
                preferredSourceId = sourceId,
                playbackFailedSourceIds = remainingFailures,
                playbackFailureAtMs = if (remainingFailures.isEmpty()) 0L else previous.playbackFailureAtMs,
                updatedAtMs = System.currentTimeMillis()
            )
            trimSongAffinityLocked(entries)
            persistSongAffinityLocked(entries)
        }
    }

    private fun loadSongAffinityLocked(): MutableMap<String, LxSongSourceAffinity> {
        if (songAffinityLoaded) return songAffinityCache
        val decoded = runCatching {
            val raw = songAffinityPreferences.getString(SONG_AFFINITY_JSON_KEY, null)
            if (raw.isNullOrBlank()) {
                emptyMap()
            } else {
                json.decodeFromString(LxSongSourceAffinityStore.serializer(), raw).entries
            }
        }.getOrElse {
            NPLogger.w(TAG, "Failed to decode song source affinity: ${it.message}")
            emptyMap()
        }
        songAffinityCache = decoded.toMutableMap()
        songAffinityLoaded = true
        return songAffinityCache
    }

    private fun trimSongAffinityLocked(entries: MutableMap<String, LxSongSourceAffinity>) {
        if (entries.size <= LX_SONG_AFFINITY_MAX_ENTRIES) return
        val keepKeys = entries.entries
            .sortedByDescending { it.value.updatedAtMs }
            .take(LX_SONG_AFFINITY_MAX_ENTRIES)
            .map { it.key }
            .toSet()
        entries.keys.retainAll(keepKeys)
    }

    private fun persistSongAffinityLocked(entries: Map<String, LxSongSourceAffinity>) {
        runCatching {
            val encoded = json.encodeToString(
                LxSongSourceAffinityStore.serializer(),
                LxSongSourceAffinityStore(entries)
            )
            songAffinityPreferences.edit().putString(SONG_AFFINITY_JSON_KEY, encoded).apply()
        }.onFailure {
            NPLogger.w(TAG, "Failed to persist song source affinity: ${it.message}")
        }
    }

    suspend fun getEnabledSources(): List<LxImportedSource> {
        return sourcesFlow.first().filter { it.enabled }
    }

    suspend fun isPreferCustomSourceEnabled(): Boolean {
        return preferCustomSourceFlow.first()
    }

    suspend fun fetchRemoteSourceRegistry(): Result<LxRemoteSourceRegistry> {
        return client.fetchRemoteSourceRegistry()
    }

    suspend fun setPreferCustomSource(enabled: Boolean) {
        context.lxMusicSourceDataStore.edit { prefs ->
            prefs[LxMusicSourcePreferenceKeys.PREFER_CUSTOM_SOURCE] = enabled
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        val current = sourcesFlow.first()
        if (current.none { it.id == id }) return
        saveSources(current.map { source ->
            if (source.id == id) source.copy(enabled = enabled) else source
        })
    }

    suspend fun removeSource(id: String) {
        moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceEngine.destroy(id)
        saveSources(sourcesFlow.first().filterNot { it.id == id })
    }

    /**
     * 从 URL 导入/更新 LX 音源（JSON API 型或 JS 脚本型）。
     * 相同 URL 会覆盖旧条目。
     */
    suspend fun importFromUrl(rawUrl: String): Result<LxImportedSource> {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("empty url"))
        }
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return Result.failure(IllegalArgumentException("invalid url scheme"))
        }

        return client.fetchRawSourceBody(url).mapCatching { body ->
            val now = System.currentTimeMillis()
            val existing = sourcesFlow.first().firstOrNull { it.url.equals(url, ignoreCase = true) }
            val id = existing?.id ?: UUID.randomUUID().toString()

            if (LxMusicSourceParser.looksLikeLxMusicJsSource(body) ||
                (url.endsWith(".js", ignoreCase = true) && LxMusicSourceParser.parseSourceDefinition(body) == null)
            ) {
                val scriptPath = saveJsScript(id, body)
                val meta = LxMusicSourceParser.parseJsScriptMetadata(body)
                val imported = LxImportedSource(
                    id = id,
                    url = url,
                    name = meta.name,
                    kind = "js",
                    description = meta.description,
                    author = meta.author,
                    version = meta.version,
                    srcId = id,
                    enabled = existing?.enabled ?: true,
                    supportedQualities = listOf("128k", "320k", "flac", "flac24bit"),
                    scriptPath = scriptPath,
                    jsSourceIds = meta.sourceIds,
                    importedAt = existing?.importedAt ?: now,
                    lastValidatedAt = now,
                    lastError = null
                )
                upsertSource(imported)
                return@mapCatching imported
            }

            val definition = LxMusicSourceParser.parseSourceDefinition(body)
                ?: throw IOException("Invalid LX source")
            val imported = LxImportedSource(
                id = id,
                url = url,
                name = definition.name,
                kind = "json",
                description = definition.description,
                author = definition.author,
                version = definition.version,
                srcId = definition.srcId.ifBlank { id },
                enabled = existing?.enabled ?: true,
                supportedQualities = definition.supportedQualities,
                searchApiUrl = definition.searchApiUrl,
                songUrlApiUrl = definition.songUrlApiUrl,
                lyricApiUrl = definition.lyricApiUrl,
                picApiUrl = definition.picApiUrl,
                scriptPath = "",
                jsSourceIds = emptyList(),
                importedAt = existing?.importedAt ?: now,
                lastValidatedAt = now,
                lastError = null
            )
            upsertSource(imported)
            imported
        }.onFailure { error ->
            NPLogger.w(TAG, "Import LX source failed: url=$url, error=${error.message}")
        }
    }

    private suspend fun upsertSource(imported: LxImportedSource) {
        // 脚本内容可能已更新：丢弃旧运行时，下次播放按新脚本重新加载
        moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceEngine.destroy(imported.id)
        val current = sourcesFlow.first()
        val next = if (current.any { it.id == imported.id }) {
            current.map { if (it.id == imported.id) imported else it }
        } else {
            current + imported
        }
        saveSources(next)
    }

    private fun saveJsScript(id: String, script: String): String {
        val dir = java.io.File(context.filesDir, "lx_music_sources")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, "$id.js")
        file.writeText(script)
        return file.absolutePath
    }

    fun readJsScript(source: LxImportedSource): String? {
        if (source.scriptPath.isBlank()) return null
        val file = java.io.File(source.scriptPath)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    suspend fun refreshSource(id: String): Result<LxImportedSource> {
        val source = sourcesFlow.first().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("source not found"))
        return importFromUrl(source.url).onFailure { error ->
            saveSources(
                sourcesFlow.first().map {
                    if (it.id == id) {
                        it.copy(
                            lastValidatedAt = System.currentTimeMillis(),
                            lastError = error.message ?: "refresh failed"
                        )
                    } else {
                        it
                    }
                }
            )
        }
    }

    private suspend fun saveSources(sources: List<LxImportedSource>) {
        val encoded = json.encodeToString(LxImportedSourceList(sources))
        context.lxMusicSourceDataStore.edit { prefs ->
            if (sources.isEmpty()) {
                prefs.remove(LxMusicSourcePreferenceKeys.SOURCES_JSON)
            } else {
                prefs[LxMusicSourcePreferenceKeys.SOURCES_JSON] = encoded
            }
        }
    }

    private fun decodeSources(raw: String?): List<LxImportedSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(LxImportedSourceList.serializer(), raw).sources
        }.getOrElse {
            NPLogger.e(TAG, "Failed to decode LX sources", it)
            emptyList()
        }
    }

    private companion object {
        private const val TAG = "LxMusicSourceRepository"
        private const val STATUS_THROTTLE_MS = 5_000L
        private const val SONG_AFFINITY_PREFS_NAME = "lx_song_source_affinity"
        private const val SONG_AFFINITY_JSON_KEY = "entries_json"
    }
}
