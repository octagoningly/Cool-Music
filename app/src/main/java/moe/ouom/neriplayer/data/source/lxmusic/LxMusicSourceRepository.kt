package moe.ouom.neriplayer.data.source.lxmusic

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

class LxMusicSourceRepository(
    private val context: Context,
    private val client: LxMusicSourceClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** 播放热路径用内存缓存，避免每次 resolve 都读 DataStore */
    @Volatile
    private var cachedSources: List<LxImportedSource> = emptyList()

    @Volatile
    private var cachedPreferCustomSource: Boolean = true

    @Volatile
    private var cacheWarmed: Boolean = false

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

    suspend fun getEnabledSources(): List<LxImportedSource> {
        return sourcesFlow.first().filter { it.enabled }
    }

    suspend fun isPreferCustomSourceEnabled(): Boolean {
        return preferCustomSourceFlow.first()
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
    }
}
