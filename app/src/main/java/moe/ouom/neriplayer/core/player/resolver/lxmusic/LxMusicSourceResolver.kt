package moe.ouom.neriplayer.core.player.resolver.lxmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.model.PlaybackAudioInfo
import moe.ouom.neriplayer.core.player.model.PlaybackAudioSource
import moe.ouom.neriplayer.core.player.model.PlaybackQualityOption
import moe.ouom.neriplayer.core.player.model.SongUrlResult
import moe.ouom.neriplayer.core.player.quality.effectiveNeteaseQuality
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.LxSearchItem
import moe.ouom.neriplayer.data.source.lxmusic.LxSongUrlResult
import moe.ouom.neriplayer.data.source.lxmusic.inferLxMimeType
import moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceRuntime
import moe.ouom.neriplayer.data.source.lxmusic.mapNeteaseQualityToLxOrder
import moe.ouom.neriplayer.data.source.lxmusic.normalizeLxQualityLabel
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.absoluteValue

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
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicSourceResolver
 * Updated: 2026/3/23
 */

private const val TAG = "NERI-LxMusicSource"
private const val LX_SEARCH_LIMIT = 6
private const val LX_MIN_ACCEPT_SCORE = 68
private const val LX_RESOLVE_TOTAL_TIMEOUT_MS = 12_000L
private const val LX_MAX_SEARCH_QUERIES = 2
private const val LX_MAX_QUALITY_ATTEMPTS = 2
private const val LX_JS_MAX_PLATFORMS = 3
private val lxCacheKeyUnsafeRegex = Regex("[^A-Za-z0-9_.-]+")
private val lxNonTextRegex = Regex("[^\\p{L}\\p{N}]+")
private val lxWhitespaceRegex = Regex("\\s+")

/**
 * 优先通过已导入的 LX Music 在线音源解析播放地址。
 * 成功返回 [SongUrlResult.Success]；失败或未启用时返回 null，回落到原有音源链路。
 *
 * 热路径约束：
 * - 无启用音源时零 I/O 立即返回
 * - 仅在首次解析尝试时介入（重试交给原有平台链路）
 * - 整体限时，避免音源站超时拖慢播放
 */
internal suspend fun PlayerManager.tryResolveLxMusicCustomSource(
    song: SongItem,
    onlyOnFirstAttempt: Boolean = true,
    attempt: Int = 0
): SongUrlResult? {
    if (onlyOnFirstAttempt && attempt > 0) return null

    val repository = runCatching { AppContainer.lxMusicSourceRepository }.getOrNull()
        ?: return null

    // 同步内存快照，未配置时直接放行
    val enabledSources = repository.peekEnabledSources()
    if (enabledSources.isEmpty()) return null

    val preferredNeteaseQuality = effectiveNeteaseQuality()
    return withTimeoutOrNull(LX_RESOLVE_TOTAL_TIMEOUT_MS) {
        for (source in enabledSources) {
            try {
                val result = if (source.isJsSource) {
                    resolveFromLxJsSource(
                        song = song,
                        source = source,
                        preferredNeteaseQuality = preferredNeteaseQuality
                    )
                } else {
                    resolveFromLxSource(
                        song = song,
                        source = source,
                        preferredNeteaseQuality = preferredNeteaseQuality
                    )
                }
                if (result != null) {
                    return@withTimeoutOrNull result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NPLogger.w(TAG, "LX source resolve error: source=${source.name}, error=${e.message}")
            }
        }
        null
    }
}

private val lxJsRuntimeCache = java.util.concurrent.ConcurrentHashMap<String, LxJsSourceRuntime>()

private suspend fun PlayerManager.resolveFromLxJsSource(
    song: SongItem,
    source: LxImportedSource,
    preferredNeteaseQuality: String
): SongUrlResult? {
    val repository = AppContainer.lxMusicSourceRepository
    val script = repository.readJsScript(source) ?: return null
    val runtime = lxJsRuntimeCache.getOrPut(source.id) {
        LxJsSourceRuntime(
            context = application,
            okHttpClient = AppContainer.sharedOkHttpClient,
            scriptId = source.id,
            scriptName = source.name,
            script = script
        )
    }
    if (runtime.supportedSources.isEmpty() && !runtime.load()) {
        lxJsRuntimeCache.remove(source.id)
        return null
    }

    val sourceIds = (source.jsSourceIds.ifEmpty { listOf("kw", "kg", "tx", "wy", "mg") })
        .filter { runtime.supportedSources.isEmpty() || it in runtime.supportedSources }
        .ifEmpty { listOf("kw", "kg", "tx", "wy", "mg") }
        .take(LX_JS_MAX_PLATFORMS)
    val musicInfoBySource = sourceIds.associateWith { buildLxOldMusicInfoJson(song, it) }
    val qualities = mapNeteaseQualityToLxOrder(preferredNeteaseQuality).take(LX_MAX_QUALITY_ATTEMPTS)

    for (sourceId in sourceIds) {
        for (quality in qualities) {
            val url = runtime.getMusicUrl(
                sourceId = sourceId,
                quality = quality,
                musicInfoJson = musicInfoBySource[sourceId].orEmpty(),
                timeoutMs = 5_000L
            ) ?: continue
            if (!url.startsWith("http", ignoreCase = true)) continue
            val mimeType = inferLxMimeType(quality, url)
            NPLogger.w(
                TAG,
                "LX JS source selected: source=${source.name}, platform=$sourceId, " +
                    "song=${song.name}, quality=$quality"
            )
            return SongUrlResult.Success(
                url = url,
                durationMs = song.durationMs.takeIf { it > 0L },
                mimeType = mimeType,
                audioInfo = buildLxPlaybackAudioInfo(
                    source = source,
                    qualityKey = quality,
                    mimeType = mimeType
                ),
                cacheKeyOverride = "lxjs-${source.id}-$sourceId-${url.hashCode()}"
            )
        }
    }
    return null
}

/** 落雪 JS 音源期望的旧版 musicInfo 形状；source 需与请求的平台 id 一致 */
private fun buildLxOldMusicInfoJson(song: SongItem, platformSourceId: String): String {
    val name = (song.originalName ?: song.name).trim()
    val singer = (song.originalArtist ?: song.artist).trim()
    val intervalSec = if (song.durationMs > 0) song.durationMs / 1000L else 0L
    val interval = String.format(
        java.util.Locale.US,
        "%02d:%02d",
        intervalSec / 60,
        intervalSec % 60
    )
    return JSONObject().apply {
        put("name", name)
        put("singer", singer)
        put("source", platformSourceId)
        put("songmid", song.id.toString())
        put("interval", interval)
        put("albumName", song.album.orEmpty())
        put("img", "")
        put("albumId", "")
        put("types", JSONArray())
        put("_types", JSONObject())
        put("typeUrl", JSONObject())
    }.toString()
}

private suspend fun PlayerManager.resolveFromLxSource(
    song: SongItem,
    source: LxImportedSource,
    preferredNeteaseQuality: String
): SongUrlResult? {
    val client = AppContainer.lxMusicSourceClient
    val queries = buildLxSearchQueries(song).take(LX_MAX_SEARCH_QUERIES)
    if (queries.isEmpty()) return null

    NPLogger.d(
        TAG,
        "Trying LX source: source=${source.name}, song=${song.name}, queries=$queries"
    )

    for (query in queries) {
        val items = client.search(
            searchApiUrl = source.searchApiUrl,
            keyword = query,
            limit = LX_SEARCH_LIMIT
        )
        if (items.isEmpty()) continue

        val best = items
            .map { it to scoreLxSearchItem(song, it) }
            .sortedByDescending { it.second }
            .firstOrNull { it.second >= LX_MIN_ACCEPT_SCORE }
            ?.first
            ?: continue

        val qualityOrder = mapNeteaseQualityToLxOrder(preferredNeteaseQuality)
            .filter { quality ->
                source.supportedQualities.isEmpty() || quality in source.supportedQualities
            }
            .ifEmpty { mapNeteaseQualityToLxOrder(preferredNeteaseQuality) }
            .take(LX_MAX_QUALITY_ATTEMPTS)

        for (quality in qualityOrder) {
            when (val urlResult = client.resolveSongUrl(
                songUrlApiUrl = source.songUrlApiUrl,
                musicId = best.musicId,
                quality = quality
            )) {
                is LxSongUrlResult.Success -> {
                    val mimeType = inferLxMimeType(quality, urlResult.url)
                    val resolvedQuality = urlResult.quality?.takeIf { it.isNotBlank() } ?: quality
                    NPLogger.w(
                        TAG,
                        "LX source selected: source=${source.name}, song=${song.name}, " +
                            "id=${best.musicId}, quality=$resolvedQuality, score=${scoreLxSearchItem(song, best)}"
                    )
                    return SongUrlResult.Success(
                        url = urlResult.url,
                        durationMs = song.durationMs.takeIf { it > 0L },
                        mimeType = mimeType,
                        audioInfo = buildLxPlaybackAudioInfo(
                            source = source,
                            qualityKey = resolvedQuality,
                            mimeType = mimeType
                        ),
                        cacheKeyOverride = buildLxCacheKey(
                            source = source,
                            musicId = best.musicId,
                            quality = resolvedQuality
                        )
                    )
                }
                LxSongUrlResult.Failure -> Unit
            }
        }
    }
    return null
}

private fun buildLxPlaybackAudioInfo(
    source: LxImportedSource,
    qualityKey: String,
    mimeType: String
): PlaybackAudioInfo {
    val qualityLabel = normalizeLxQualityLabel(qualityKey)
    val options = buildList {
        add(PlaybackQualityOption(qualityKey, qualityLabel))
        source.supportedQualities
            .filter { it.isNotBlank() && it != qualityKey }
            .forEach { add(PlaybackQualityOption(it, normalizeLxQualityLabel(it))) }
    }
    return PlaybackAudioInfo(
        source = PlaybackAudioSource.CUSTOM_LX,
        qualityKey = qualityKey,
        qualityLabel = qualityLabel,
        qualityOptions = options,
        codecLabel = normalizeLxQualityLabel(qualityKey),
        mimeType = mimeType
    )
}

private fun buildLxCacheKey(
    source: LxImportedSource,
    musicId: String,
    quality: String
): String {
    val sourcePart = source.srcId.ifBlank { source.id }
        .replace(lxCacheKeyUnsafeRegex, "_")
        .trim('_')
        .ifBlank { "lx" }
    val idPart = musicId.replace(lxCacheKeyUnsafeRegex, "_")
        .trim('_')
        .ifBlank { "unknown" }
    val qualityPart = quality.replace(lxCacheKeyUnsafeRegex, "_")
        .trim('_')
        .ifBlank { "auto" }
    return "lx-$sourcePart-$idPart-$qualityPart"
}

private fun buildLxSearchQueries(song: SongItem): List<String> {
    val title = (song.originalName ?: song.name).trim()
    val artist = (song.originalArtist ?: song.artist).trim()
    return listOf(
        "$title $artist",
        "$artist $title",
        title
    ).map(::normalizeLxText)
        .filter { it.isNotBlank() }
        .distinct()
}

private fun normalizeLxText(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(lxNonTextRegex, " ")
        .replace(lxWhitespaceRegex, " ")
        .trim()
}

private fun scoreLxSearchItem(song: SongItem, item: LxSearchItem): Int {
    val targetTitle = normalizeLxText(song.originalName ?: song.name)
    val targetArtist = normalizeLxText(song.originalArtist ?: song.artist)
    val candidateTitle = normalizeLxText(item.name)
    val candidateArtist = normalizeLxText(item.singer)

    if (targetTitle.isBlank() || candidateTitle.isBlank()) return 0

    var score = 0
    val titleScore = textSimilarityScore(targetTitle, candidateTitle)
    score += (titleScore * 60).toInt()

    if (targetArtist.isNotBlank() && candidateArtist.isNotBlank()) {
        val artistScore = textSimilarityScore(targetArtist, candidateArtist)
        score += (artistScore * 30).toInt()
    } else {
        score += 10
    }

    val candidateDurationMs = parseLxIntervalToMs(item.intervalText)
    if (song.durationMs > 0L && candidateDurationMs > 0L) {
        val deltaMs = (song.durationMs - candidateDurationMs).absoluteValue
        val durationScore = when {
            deltaMs <= 3_000L -> 1.0
            deltaMs <= 8_000L -> 0.7
            deltaMs <= 15_000L -> 0.35
            deltaMs <= 30_000L -> 0.1
            else -> 0.0
        }
        score += (durationScore * 10).toInt()
    } else {
        score += 5
    }
    return score
}

private fun parseLxIntervalToMs(interval: String): Long {
    val trimmed = interval.trim()
    if (trimmed.isEmpty()) return 0L
    if (trimmed.all { it.isDigit() }) {
        return trimmed.toLongOrNull()?.let { seconds -> seconds * 1000L } ?: 0L
    }
    val parts = trimmed.split(':').map { it.trim() }
    if (parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return 0L
    return when (parts.size) {
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return 0L
            val seconds = parts[1].toLongOrNull() ?: return 0L
            (minutes * 60L + seconds) * 1000L
        }
        3 -> {
            val hours = parts[0].toLongOrNull() ?: return 0L
            val minutes = parts[1].toLongOrNull() ?: return 0L
            val seconds = parts[2].toLongOrNull() ?: return 0L
            (hours * 3600L + minutes * 60L + seconds) * 1000L
        }
        else -> 0L
    }
}

/** 简易相似度：前缀/包含 + token 重叠，范围 0..1 */
private fun textSimilarityScore(left: String, right: String): Double {
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left.equals(right, ignoreCase = true)) return 1.0
    if (left.contains(right, ignoreCase = true) || right.contains(left, ignoreCase = true)) {
        val shorter = minOf(left.length, right.length).toDouble()
        val longer = maxOf(left.length, right.length).toDouble().coerceAtLeast(1.0)
        return 0.75 + 0.25 * (shorter / longer)
    }
    val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
    val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
    if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
    val intersection = leftTokens.intersect(rightTokens).size
    val union = leftTokens.union(rightTokens).size
    return intersection.toDouble() / union.toDouble()
}
