package moe.ouom.neriplayer.core.player.resolver.lxmusic

import android.os.SystemClock
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
import moe.ouom.neriplayer.core.player.quality.effectiveLxQuality
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.data.source.lxmusic.LxImportedSource
import moe.ouom.neriplayer.data.source.lxmusic.LxSearchItem
import moe.ouom.neriplayer.data.source.lxmusic.LxSongUrlResult
import moe.ouom.neriplayer.data.source.lxmusic.inferLxMimeType
import moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceEngine
import moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceRuntime
import moe.ouom.neriplayer.data.source.lxmusic.mapNeteaseQualityToLxOrder
import moe.ouom.neriplayer.data.source.lxmusic.mapLxQualityToNeteaseKey
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
private const val LX_RESOLVE_TOTAL_TIMEOUT_MS = 11_000L
private const val LX_FALLBACK_TOTAL_TIMEOUT_MS = 7_000L
private const val LX_JS_CALL_TIMEOUT_MS = 4_500L
private const val LX_MAX_SEARCH_QUERIES = 2
private const val LX_MAX_QUALITY_ATTEMPTS = 2
private const val LX_FALLBACK_QUALITY_ATTEMPTS = 1
private const val LX_ATTEMPT_DEDUPE_WINDOW_MS = 15_000L

/** 落雪 user-api 的标准音质集合；脚本未上报时按它兜底 */
internal val DEFAULT_LX_QUALITIES = listOf("128k", "320k", "flac", "flac24bit")

/** NeriPlayer 里在线曲目默认来自网易云，落雪协议中对应 source=wy */
internal const val LX_NETEASE_PLATFORM_ID = "wy"

/**
 * 同一次解析链路里避免重复等待在线音源：
 * 顶部优先解析失败后，网易云无版权回落链路不再无谓地重试一遍。
 */
private object LxAttemptGuard {
    private var lastSongKey: String? = null
    private var lastAttemptAtMs: Long = 0L

    @Synchronized
    fun shouldSkip(songKey: String, nowMs: Long): Boolean {
        return lastSongKey == songKey && nowMs - lastAttemptAtMs < LX_ATTEMPT_DEDUPE_WINDOW_MS
    }

    @Synchronized
    fun record(songKey: String, nowMs: Long) {
        lastSongKey = songKey
        lastAttemptAtMs = nowMs
    }

    @Synchronized
    fun reset() {
        lastSongKey = null
        lastAttemptAtMs = 0L
    }
}

private val lxCacheKeyUnsafeRegex = Regex("[^A-Za-z0-9_.-]+")
private val lxNonTextRegex = Regex("[^\\p{L}\\p{N}]+")
private val lxWhitespaceRegex = Regex("\\s+")

/**
 * 优先通过已导入的 LX Music 在线音源解析播放地址。
 * 成功返回 [SongUrlResult.Success]；失败或未启用时返回 null，回落到原有音源链路。
 *
 * @param isFallbackAttempt 为 true 时表示「平台音源已确认不可播（无版权/仅试听）」后的回落尝试，
 *   此时收紧预算：同一次链路里刚试过就跳过，且只请求最高一档音质。
 *
 * 热路径约束：
 * - 无启用音源时零 I/O 立即返回
 * - 整体限时，避免音源站超时拖慢播放
 */
internal suspend fun PlayerManager.tryResolveLxMusicCustomSource(
    song: SongItem,
    isFallbackAttempt: Boolean = false
): SongUrlResult? {
    val repository = runCatching { AppContainer.lxMusicSourceRepository }.getOrNull()
        ?: return null

    // 同步内存快照，未配置时直接放行
    val enabledSources = repository.peekEnabledSources()
    if (enabledSources.isEmpty()) return null

    val songKey = song.stableKey()
    val nowMs = SystemClock.elapsedRealtime()
    if (isFallbackAttempt && LxAttemptGuard.shouldSkip(songKey, nowMs)) {
        NPLogger.d(
            TAG,
            "Skip fallback LX attempt, already tried recently: song=${song.name}"
        )
        return null
    }
    LxAttemptGuard.record(songKey, nowMs)

    val preferredNeteaseQuality = effectiveLxQuality()
        .let { mapLxQualityToNeteaseKey(it) ?: effectiveNeteaseQuality() }
    LxJsSourceEngine.clearFailureReasons()
    NPLogger.d(
        TAG,
        "Try LX custom source: song=${song.name}, id=${song.id}, fallback=$isFallbackAttempt, " +
            "sources=${enabledSources.map { "${it.name}(${it.kind})" }}, quality=$preferredNeteaseQuality"
    )
    val totalTimeoutMs = if (isFallbackAttempt) {
        LX_FALLBACK_TOTAL_TIMEOUT_MS
    } else {
        LX_RESOLVE_TOTAL_TIMEOUT_MS
    }
    val qualityAttempts = if (isFallbackAttempt) {
        LX_FALLBACK_QUALITY_ATTEMPTS
    } else {
        LX_MAX_QUALITY_ATTEMPTS
    }
    val resolved = withTimeoutOrNull(totalTimeoutMs) {
        for (source in enabledSources) {
            try {
                val result = if (source.isJsSource) {
                    resolveFromLxJsSource(
                        song = song,
                        source = source,
                        preferredNeteaseQuality = preferredNeteaseQuality,
                        maxQualityAttempts = qualityAttempts
                    )
                } else {
                    resolveFromLxSource(
                        song = song,
                        source = source,
                        preferredNeteaseQuality = preferredNeteaseQuality,
                        maxQualityAttempts = qualityAttempts
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
    if (resolved != null) {
        repository.recordResolveSuccess()
    } else {
        repository.recordResolveFailure(resolveFailureReason())
    }
    return resolved
}

/** 汇总本次失败原因（优先取 JS 运行时记录的 HTTP 状态码/脚本报错） */
private fun resolveFailureReason(): String {
    val jsReason = LxJsSourceEngine.activeFailureReason()
    return jsReason ?: "no playable url"
}

private suspend fun PlayerManager.resolveFromLxJsSource(
    song: SongItem,
    source: LxImportedSource,
    preferredNeteaseQuality: String,
    maxQualityAttempts: Int
): SongUrlResult? {
    val repository = AppContainer.lxMusicSourceRepository
    val script = repository.readJsScript(source) ?: return null
    val engine = moe.ouom.neriplayer.data.source.lxmusic.js.LxJsSourceEngine
    if (!engine.ensureLoaded(
            context = application,
            okHttpClient = AppContainer.sharedOkHttpClient,
            sourceId = source.id,
            sourceName = source.name,
            script = script,
            description = source.description,
            version = source.version,
            author = source.author
        )
    ) {
        return null
    }
    val runtime = engine.runtime(source.id) ?: return null

    val supportedSources = runtime.supportedSources
    val target = resolveLxJsPlatformTarget(
        song = song,
        isNeteaseTrack = isNeteaseMusicTrack(song),
        supportedSourceIds = supportedSources
    )
    if (target == null) {
        NPLogger.d(
            TAG,
            "LX JS source skipped, no platform id for song: song=${song.name}, " +
                "bili=${isBiliTrack(song)}, youtube=${isYouTubeMusicTrack(song)}, " +
                "sources=$supportedSources"
        )
        return null
    }

    val qualities = selectLxQualityOrder(
        preferredNeteaseQuality = preferredNeteaseQuality,
        supportedQualities = runtime.supportedQualities,
        maxAttempts = maxQualityAttempts
    )
    val musicInfoJson = buildLxOldMusicInfoJson(
        song = song,
        platformSourceId = target.sourceId,
        songMid = target.songMid
    )

    for (quality in qualities) {
        val url = runtime.getMusicUrl(
            sourceId = target.sourceId,
            quality = quality,
            musicInfoJson = musicInfoJson,
            timeoutMs = LX_JS_CALL_TIMEOUT_MS
        ) ?: continue
        if (!url.startsWith("http", ignoreCase = true)) continue
        val mimeType = inferLxMimeType(quality, url)
        NPLogger.w(
            TAG,
            "LX JS source selected: source=${source.name}, platform=${target.sourceId}, " +
                "songmid=${target.songMid}, song=${song.name}, quality=$quality"
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
            cacheKeyOverride = "lxjs-${source.id}-${target.sourceId}-${target.songMid}-$quality"
        )
    }
    NPLogger.w(
        TAG,
        "LX JS source returned no url: source=${source.name}, platform=${target.sourceId}, " +
            "songmid=${target.songMid}, song=${song.name}, qualities=$qualities"
    )
    // 网易云通道不通时，退一步用其他平台的曲目 ID 取流（与落雪换源行为一致）
    return resolveFromLxJsCrossPlatform(
        song = song,
        source = source,
        runtime = runtime,
        preferredNeteaseQuality = preferredNeteaseQuality,
        maxQualityAttempts = maxQualityAttempts
    )
}

/**
 * 跨平台取流：NeriPlayer 没有其他平台的曲目 ID，就只能按「歌名 + 歌手」去搜，
 * 命中的候选再用时长复核，然后交给在线音源对应平台的通道取流。
 * 这样即使音源服务端的某个平台通道故障，在线音源仍然可用。
 */
private suspend fun PlayerManager.resolveFromLxJsCrossPlatform(
    song: SongItem,
    source: LxImportedSource,
    runtime: LxJsSourceRuntime,
    preferredNeteaseQuality: String,
    maxQualityAttempts: Int
): SongUrlResult? {
    val supportedSources = runtime.supportedSources
    val platforms = LX_CROSS_PLATFORM_ORDER.filter { platform ->
        supportedSources.isEmpty() || platform in supportedSources
    }
    if (platforms.isEmpty()) return null

    val qualities = selectLxQualityOrder(
        preferredNeteaseQuality = preferredNeteaseQuality,
        supportedQualities = runtime.supportedQualities,
        maxAttempts = maxQualityAttempts
    )
    if (qualities.isEmpty()) return null

    val songKey = song.stableKey()
    for (platform in platforms) {
        val cacheKey = "$songKey|$platform"
        val hit = LxCrossPlatformHitCache.get(cacheKey) ?: run {
            val found = searchLxCrossPlatformHit(song, platform)
            if (found != null) LxCrossPlatformHitCache.put(cacheKey, found)
            found
        } ?: continue

        for (quality in qualities) {
            val url = runtime.getMusicUrl(
                sourceId = hit.sourceId,
                quality = quality,
                musicInfoJson = buildLxCrossPlatformMusicInfoJson(song, hit, quality),
                timeoutMs = LX_JS_CALL_TIMEOUT_MS
            ) ?: continue
            if (!url.startsWith("http", ignoreCase = true)) continue
            val mimeType = inferLxMimeType(quality, url)
            NPLogger.w(
                TAG,
                "LX cross-platform source selected: source=${source.name}, platform=${hit.sourceId}, " +
                    "songmid=${hit.songMid}, song=${song.name}, quality=$quality"
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
                cacheKeyOverride = "lxjs-${source.id}-${hit.sourceId}-${hit.songMid}-$quality"
            )
        }
        NPLogger.w(
            TAG,
            "LX cross-platform channel returned no url: platform=${hit.sourceId}, " +
                "songmid=${hit.songMid}, song=${song.name}, qualities=$qualities"
        )
    }
    return null
}

/** 在单个平台上搜索并挑出最佳候选（取流与歌词同步共用） */
internal suspend fun searchLxCrossPlatformHit(
    song: SongItem,
    platform: String
): LxCrossPlatformHit? {
    for (query in buildLxSearchQueries(song)) {
        val hits = fetchLxCrossPlatformHits(
            client = AppContainer.sharedOkHttpClient,
            sourceId = platform,
            keyword = query
        )
        if (hits.isEmpty()) continue
        val best = selectLxCrossPlatformHit(song, hits) ?: continue
        NPLogger.w(
            TAG,
            "LX cross-platform match: song=${song.name}, platform=$platform, " +
                "hit=${best.describe()}, score=${scoreLxCrossPlatformHit(song, best)}"
        )
        return best
    }
    NPLogger.w(TAG, "LX cross-platform search found no match on $platform: song=${song.name}")
    return null
}

/**
 * NeriPlayer 没有独立的平台字段：本地/B 站/YouTube 都有各自的标记，
 * 其余在线曲目一律按网易云处理（[SongItem.id] 即网易云 songId）。
 */
private fun PlayerManager.isNeteaseMusicTrack(song: SongItem): Boolean {
    return !isLocalSong(song) && !isBiliTrack(song) && !isYouTubeMusicTrack(song)
}

internal data class LxJsPlatformTarget(
    val sourceId: String,
    val songMid: String
)

/**
 * 决定用哪个平台的曲目 ID 去请求在线音源。
 *
 * 落雪协议里 `musicInfo.songmid` 必须是**该平台自己的曲目 ID**。
 * NeriPlayer 只有网易云曲目带有对应的平台 ID（[SongItem.id] 即网易云 songId），
 * B 站 / YouTube 曲目没有对应平台的 ID。
 * 把网易云 ID 填给 kw/kg/tx/mg 会让音源站按「别人家的另一首歌」取流，
 * 这正是之前「显示在线音源但放的不是同一首」的根因，因此这里不再跨平台试探。
 */
internal fun resolveLxJsPlatformTarget(
    song: SongItem,
    isNeteaseTrack: Boolean,
    supportedSourceIds: Set<String>
): LxJsPlatformTarget? {
    if (!isNeteaseTrack) return null
    val songId = song.id.takeIf { it > 0L } ?: return null
    if (supportedSourceIds.isNotEmpty() && LX_NETEASE_PLATFORM_ID !in supportedSourceIds) return null
    return LxJsPlatformTarget(sourceId = LX_NETEASE_PLATFORM_ID, songMid = songId.toString())
}

/** 只请求音源脚本真正声明的音质档位（落雪协议里没有 hq/sq/aac 这些档位） */
internal fun selectLxQualityOrder(
    preferredNeteaseQuality: String,
    supportedQualities: Set<String>,
    maxAttempts: Int
): List<String> {
    if (maxAttempts <= 0) return emptyList()
    val order = mapNeteaseQualityToLxOrder(preferredNeteaseQuality)
    val allowed = supportedQualities.ifEmpty { DEFAULT_LX_QUALITIES.toSet() }
    return order.filter { it in allowed }.distinct().take(maxAttempts)
}

/** 落雪 JS 音源期望的旧版 musicInfo 形状；source/songmid 必须同属一个平台 */
internal fun buildLxOldMusicInfoJson(
    song: SongItem,
    platformSourceId: String,
    songMid: String
): String {
    val name = (song.originalName ?: song.name).trim()
    val singer = (song.originalArtist ?: song.artist).trim()
    val intervalSec = if (song.durationMs > 0) song.durationMs / 1000L else 0L
    val interval = String.format(
        java.util.Locale.US,
        "%02d:%02d",
        intervalSec / 60,
        intervalSec % 60
    )
    // 不要把网易云 songId 塞进其他平台的 songmid：那是各平台自己的曲目 ID，
    // 错误 ID 会导致源端按错误曲目取流，出现「显示在线音源但不是同一首」。
    return JSONObject().apply {
        put("name", name)
        put("singer", singer)
        put("source", platformSourceId)
        put("songmid", songMid)
        put("interval", interval)
        put("albumName", song.album.orEmpty())
        put("img", song.coverUrl.orEmpty())
        put("albumId", song.albumId.takeIf { it > 0L }?.toString().orEmpty())
        put("types", JSONArray())
        put("_types", JSONObject())
        put("typeUrl", JSONObject())
    }.toString()
}

private suspend fun PlayerManager.resolveFromLxSource(
    song: SongItem,
    source: LxImportedSource,
    preferredNeteaseQuality: String,
    maxQualityAttempts: Int
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
            .take(maxQualityAttempts)

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

internal fun buildLxSearchQueries(song: SongItem): List<String> {
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

internal fun normalizeLxText(value: String): String {
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
internal fun textSimilarityScore(left: String, right: String): Double {
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
