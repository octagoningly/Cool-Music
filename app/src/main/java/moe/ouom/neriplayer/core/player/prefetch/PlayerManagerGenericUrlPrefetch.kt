package moe.ouom.neriplayer.core.player.prefetch

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.cache.writeCachedSong
import moe.ouom.neriplayer.core.player.model.SongUrlResult
import moe.ouom.neriplayer.core.player.policy.refresh.RefreshResolverSideEffects
import moe.ouom.neriplayer.core.player.policy.refresh.RefreshSideEffectGate
import moe.ouom.neriplayer.core.player.url.CachePrefetchReadiness
import moe.ouom.neriplayer.core.player.url.OFFLINE_CACHE_URL_PREFIX
import moe.ouom.neriplayer.core.player.url.allowsCustomCacheKey
import moe.ouom.neriplayer.core.player.url.prepareExoPlayerCacheForPrefetch
import moe.ouom.neriplayer.core.player.url.resolveSongUrl
import moe.ouom.neriplayer.core.player.url.synchronizeCachedPlaybackDescriptor
import moe.ouom.neriplayer.data.local.media.LocalSongSupport
import moe.ouom.neriplayer.data.model.SongItem

internal fun resolveGenericUrlPrefetchTtlMs(
    currentTrackDurationMs: Long,
    defaultTtlMs: Long = GENERIC_URL_PREFETCH_TTL_MS,
    maxTtlMs: Long = GENERIC_URL_PREFETCH_MAX_TTL_MS
): Long {
    val durationBasedTtl = currentTrackDurationMs
        .takeIf { it > 0L }
        ?.plus(30_000L)
    return (durationBasedTtl ?: defaultTtlMs).coerceIn(1L, maxTtlMs)
}

internal const val GENERIC_MEDIA_PREFETCH_BYTES = 1_536L * 1024L
private const val GENERIC_MEDIA_PREFETCH_MIN_BYTES = 256L * 1024L
internal const val NEXT_TRACK_MEDIA_MIN_BYTES = 512L * 1024L
private const val PREFETCH_PLAYBACK_AWAIT_MS = 350L

internal fun resolveGenericMediaPrefetchBytes(expectedContentLength: Long?): Long {
    return expectedContentLength
        ?.takeIf { it > 0L }
        ?.coerceAtMost(GENERIC_MEDIA_PREFETCH_BYTES)
        ?.coerceAtLeast(GENERIC_MEDIA_PREFETCH_MIN_BYTES)
        ?: GENERIC_MEDIA_PREFETCH_BYTES
}

internal fun resolveGenericMediaPrefetchCacheKey(
    genericCacheKey: String,
    result: SongUrlResult.Success
): String {
    return result.cacheKeyOverride
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: genericCacheKey
}

internal fun PlayerManager.prefetchNextGenericTrackUrl() {
    if (!isApplicationInitialized()) return
    if (!precacheEnabledForScenario(PlaybackPrecacheScenario.NEXT_TRACK)) {
        cancelGenericUrlPrefetch(reason = "next_track_precache_disabled")
        return
    }

    if (player.shuffleModeEnabled || repeatModeSetting == Player.REPEAT_MODE_ONE) {
        cancelGenericUrlPrefetch(reason = "non_sequential_playback_mode")
        return
    }

    val upcoming = collectUpcomingSequentialSongs(maxCount = 2)
    if (upcoming.isEmpty()) {
        cancelGenericUrlPrefetch(reason = "no_supported_next_track")
        return
    }

    if (upcoming.any { isYouTubeMusicTrack(it) }) {
        val firstYt = upcoming.first { isYouTubeMusicTrack(it) }
        val startIndex = currentPlaylist.indexOf(firstYt).coerceAtLeast(currentIndex + 1)
        prefetchYouTubePlayableUrlWindow(
            playlist = currentPlaylist,
            startIndex = startIndex,
            source = "next_track_youtube"
        )
    }

    val genericTargets = upcoming.filterNot {
        isLocalSong(it) || isYouTubeMusicTrack(it)
    }
    if (genericTargets.isEmpty()) return

    val targetKeys = genericTargets.map { computeCacheKey(it) }.toSet()
    if (currentGenericUrlPrefetchJob?.isActive == true &&
        currentGenericUrlPrefetchKey in targetKeys &&
        currentGenericUrlPrefetchTargets == targetKeys
    ) {
        return
    }

    cancelGenericUrlPrefetch(reason = "replace_target")
    currentGenericUrlPrefetchTargets = targetKeys
    currentGenericUrlPrefetchKey = genericTargets.first().let { computeCacheKey(it) }
    NPLogger.d(
        "NERI-PlayerManager",
        "next-track prefetch start: count=" + genericTargets.size +
            ", songs=" + genericTargets.joinToString { it.name }
    )
    val launchedJob = ioScope.launch {
        genericTargets.forEach { song ->
            prefetchOneGenericSongForPlayback(song)
        }
    }
    currentGenericUrlPrefetchJob = launchedJob
    launchedJob.invokeOnCompletion {
        if (currentGenericUrlPrefetchJob === launchedJob) {
            currentGenericUrlPrefetchJob = null
            currentGenericUrlPrefetchKey = null
            currentGenericUrlPrefetchTargets = emptySet()
        }
    }
}

private fun PlayerManager.collectUpcomingSequentialSongs(maxCount: Int): List<SongItem> {
    if (currentPlaylist.isEmpty() || currentIndex !in currentPlaylist.indices) return emptyList()
    val result = ArrayList<SongItem>(maxCount)
    var cursor = currentIndex + 1
    val repeatAll = repeatModeSetting == Player.REPEAT_MODE_ALL
    while (result.size < maxCount) {
        if (cursor in currentPlaylist.indices) {
            result += currentPlaylist[cursor]
            cursor++
        } else if (repeatAll && currentPlaylist.size > 1) {
            cursor = 0
            // avoid re-adding current track forever
            if (result.size >= currentPlaylist.size - 1) break
        } else {
            break
        }
    }
    return result
}

private suspend fun PlayerManager.prefetchOneGenericSongForPlayback(song: SongItem) {
    try {
        val cacheKey = computeCacheKey(song)
        if (cacheKey.isBlank()) return
        val freshUrlResult = genericUrlPrefetchCache.peekFresh(cacheKey, SystemClock.elapsedRealtime())
        if (freshUrlResult != null && isDirectStreamUrl(freshUrlResult.url)) {
            prefetchGenericTrackMedia(
                result = freshUrlResult,
                cacheKey = cacheKey,
                song = song
            )
            return
        }
        val result = resolveSongUrl(
            song = song,
            allowGenericPrefetchCache = false,
            sideEffects = RefreshResolverSideEffects(RefreshSideEffectGate { false }),
            shouldApplyCacheMutation = { false }
        )
        if (result is SongUrlResult.Success &&
            !result.url.startsWith(OFFLINE_CACHE_URL_PREFIX) &&
            (isDirectStreamUrl(result.url) || LocalSongSupport.isLocalMediaUri(result.url))
        ) {
            genericUrlPrefetchCache.put(
                key = cacheKey,
                result = result,
                nowMs = SystemClock.elapsedRealtime(),
                ttlMsOverride = resolveGenericUrlPrefetchTtlMs(
                    currentTrackDurationMs = maxOf(
                        playbackDurationFlow.value,
                        currentSongFlow.value?.durationMs ?: 0L
                    )
                )
            )
            if (isDirectStreamUrl(result.url)) {
                prefetchGenericTrackMedia(
                    result = result,
                    cacheKey = cacheKey,
                    song = song
                )
            }
            NPLogger.d(
                "NERI-PlayerManager",
                "generic URL prefetch completed: song=" + song.name + ", key=" + cacheKey
            )
        } else {
            NPLogger.w(
                "NERI-PlayerManager",
                "generic URL prefetch got unusable result: song=" + song.name +
                    ", key=" + cacheKey + ", result=" + result
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        NPLogger.w(
            "NERI-PlayerManager",
            "generic URL prefetch failed: song=" + song.name,
            error
        )
    }
}

private suspend fun PlayerManager.prefetchGenericTrackMedia(
    result: SongUrlResult.Success,
    cacheKey: String,
    song: SongItem
) {
    val mediaCacheKey = resolveGenericMediaPrefetchCacheKey(cacheKey, result)
    if (playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey)) {
        NPLogger.d(
            "NERI-PlayerManager",
            "skip media prefetch for playback demand: song=" + song.name + ", key=" + mediaCacheKey
        )
        return
    }
    if (result.audioInfo != null) {
        val descriptorResult = synchronizeCachedPlaybackDescriptor(
            cacheKey = mediaCacheKey,
            audioInfo = result.audioInfo,
            expectedContentLength = result.expectedContentLength,
            representationIdentity = result.representationIdentity,
            song = song,
            shouldApplyMutation = { !playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey) }
        )
        NPLogger.d(
            "NERI-PlayerManager",
            "media prefetch descriptor: song=" + song.name + ", key=" + mediaCacheKey +
                ", result=" + descriptorResult
        )
        // Even if descriptor sync is skipped, still try to warm media bytes + cache metadata.
    } else {
        runCatching { cache?.writeCachedSong(mediaCacheKey, song) }
    }
    when (
        prepareExoPlayerCacheForPrefetch(
            cacheKey = mediaCacheKey,
            shouldApplyMutation = { !playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey) }
        )
    ) {
        CachePrefetchReadiness.COMPLETE -> {
            runCatching { cache?.writeCachedSong(mediaCacheKey, song) }
            return
        }
        CachePrefetchReadiness.UNAVAILABLE -> {
            NPLogger.w(
                "NERI-PlayerManager",
                "media prefetch cache unavailable: song=" + song.name + ", key=" + mediaCacheKey
            )
            return
        }
        CachePrefetchReadiness.READY_FOR_PREFETCH -> Unit
    }
    val targetBytes = maxOf(
        PlaybackPrecachePolicy.prefixBytes(
            prefixMs = PlaybackPrecachePolicy.NEXT_TRACK_PREFIX_MS,
            contentLength = result.expectedContentLength,
            durationMs = result.durationMs ?: song.durationMs,
            bitrateKbps = result.audioInfo?.bitrateKbps
        ),
        NEXT_TRACK_MEDIA_MIN_BYTES
    )
    val prefetchedBytes = runCatching {
        prefetchIntoPlayerCache(
            url = result.url,
            cacheKey = mediaCacheKey,
            targetBytes = targetBytes
        )
    }.getOrElse { error ->
        NPLogger.w(
            "NERI-PlayerManager",
            "generic media prefetch failed: song=" + song.name + ", key=" + mediaCacheKey +
                ", error=" + error.message
        )
        return
    }
    if (prefetchedBytes > 0L) {
        runCatching { cache?.writeCachedSong(mediaCacheKey, song) }
    }
    NPLogger.d(
        "NERI-PlayerManager",
        "generic media prefetch finished: song=" + song.name + ", key=" + mediaCacheKey +
            ", prefetchedBytes=" + prefetchedBytes + ", targetBytes=" + targetBytes
    )
}
internal suspend fun PlayerManager.awaitInFlightGenericUrlPrefetch(song: SongItem) {
    val key = song
        .takeUnless { isLocalSong(it) || isYouTubeMusicTrack(it) }
        ?.let { song.cachedPlaybackKey ?: computeCacheKey(it) }
        ?: return
    val activeJob = currentGenericUrlPrefetchJob?.takeIf {
        it.isActive && currentGenericUrlPrefetchKey == key
    } ?: return
    // Only wait briefly: URL is usually cached first; do not block playback on a slow media download.
    val finished = kotlinx.coroutines.withTimeoutOrNull(PREFETCH_PLAYBACK_AWAIT_MS) {
        activeJob.join()
    } != null
    NPLogger.d(
        "NERI-PlayerManager",
        "await in-flight prefetch before playback: song=" + song.name + ", key=" + key +
            ", finished=" + finished + ", timeoutMs=" + PREFETCH_PLAYBACK_AWAIT_MS
    )
}
internal fun PlayerManager.cancelGenericUrlPrefetch(reason: String) {
    val activeJob = currentGenericUrlPrefetchJob
    if (activeJob?.isActive == true) {
        NPLogger.d(
            "NERI-PlayerManager",
            "cancel generic URL prefetch: reason=$reason, key=$currentGenericUrlPrefetchKey"
        )
    }
    activeJob?.cancel()
    currentGenericUrlPrefetchJob = null
    currentGenericUrlPrefetchKey = null
    currentGenericUrlPrefetchTargets = emptySet()
}

internal fun PlayerManager.cancelGenericUrlPrefetchUnlessReusableForSong(
    song: SongItem,
    reason: String
) {
    val activeJob = currentGenericUrlPrefetchJob?.takeIf { it.isActive } ?: return
    val reusableKey = song
        .takeUnless { isLocalSong(it) || isYouTubeMusicTrack(it) || isDirectStreamUrl(it.streamUrl) }
        ?.let(::computeCacheKey)
    if (reusableKey != null && reusableKey == currentGenericUrlPrefetchKey) {
        NPLogger.d(
            "NERI-PlayerManager",
            "keep reusable generic URL prefetch: reason=$reason, key=$reusableKey"
        )
        return
    }
    activeJob.cancel()
    currentGenericUrlPrefetchJob = null
    currentGenericUrlPrefetchKey = null
}

internal suspend fun PlayerManager.consumeGenericUrlPrefetch(
    cacheKey: String
): SongUrlResult.Success? {
    consumeValidGenericUrlPrefetch(cacheKey)?.let { return it }
    val activeJob = currentGenericUrlPrefetchJob
        ?.takeIf { it.isActive && currentGenericUrlPrefetchKey == cacheKey }
        ?: return null
    activeJob.join()
    return consumeValidGenericUrlPrefetch(cacheKey)
}

private fun PlayerManager.consumeValidGenericUrlPrefetch(cacheKey: String): SongUrlResult.Success? {
    val result = genericUrlPrefetchCache.consume(cacheKey, SystemClock.elapsedRealtime()) ?: return null
    // 预取到消费之间开关可能被关掉或文件失效, 复验不过就丢弃走全新解析
    if (result.isNeteaseLocalFallback &&
        LocalSongSupport.isLocalMediaUri(result.url) &&
        (!neteaseLocalSourceFallbackEnabled || !isReadableLocalMediaUri(result.url))
    ) {
        NPLogger.d(
            "NERI-PlayerManager",
            "drop stale local prefetch result: key=$cacheKey"
        )
        return null
    }
    NPLogger.d("NERI-PlayerManager", "generic URL prefetch cache hit: key=$cacheKey")
    return result
}
