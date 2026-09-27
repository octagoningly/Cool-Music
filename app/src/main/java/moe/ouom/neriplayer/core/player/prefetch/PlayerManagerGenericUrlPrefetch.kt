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

    val nextIndex = when {
        currentIndex + 1 in currentPlaylist.indices -> currentIndex + 1
        repeatModeSetting == Player.REPEAT_MODE_ALL && currentPlaylist.size > 1 -> 0
        else -> -1
    }
    val nextSong = currentPlaylist.getOrNull(nextIndex)
    if (nextSong == null || isLocalSong(nextSong)) {
        cancelGenericUrlPrefetch(reason = "no_supported_next_track")
        return
    }
    if (isYouTubeMusicTrack(nextSong)) {
        cancelGenericUrlPrefetch(reason = "no_supported_next_track")
        val windowIds = buildList {
            var cursor = nextIndex
            while (size < 3 && cursor in currentPlaylist.indices) {
                val candidate = currentPlaylist[cursor]
                if (isYouTubeMusicTrack(candidate)) {
                    add(candidate)
                }
                cursor++
            }
        }
        prefetchYouTubePlayableUrlWindow(
            playlist = currentPlaylist,
            startIndex = nextIndex,
            source = "next_track_youtube"
        )
        return
    }

    val cacheKey = computeCacheKey(nextSong)
    assert(cacheKey.isNotBlank()) { "generic URL prefetch cache key must not be blank" }
    val freshUrlResult = genericUrlPrefetchCache.peekFresh(cacheKey, SystemClock.elapsedRealtime())
    if (freshUrlResult != null) {
        if (isDirectStreamUrl(freshUrlResult.url) &&
            currentGenericUrlPrefetchJob?.isActive != true
        ) {
            currentGenericUrlPrefetchKey = cacheKey
            val mediaWarmJob = ioScope.launch {
                try {
                    prefetchGenericTrackMedia(
                        result = freshUrlResult,
                        cacheKey = cacheKey,
                        song = nextSong
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    NPLogger.w(
                        "NERI-PlayerManager",
                        "generic media warm from cached URL failed: song=" + nextSong.name,
                        error
                    )
                }
            }
            currentGenericUrlPrefetchJob = mediaWarmJob
            mediaWarmJob.invokeOnCompletion {
                if (currentGenericUrlPrefetchJob === mediaWarmJob) {
                    currentGenericUrlPrefetchJob = null
                    currentGenericUrlPrefetchKey = null
                }
            }
        }
        return
    }
    if (currentGenericUrlPrefetchJob?.isActive == true && currentGenericUrlPrefetchKey == cacheKey) {
        return
    }

    cancelGenericUrlPrefetch(reason = "replace_target")
    currentGenericUrlPrefetchKey = cacheKey
    val launchedJob = ioScope.launch {
        try {
            val result = resolveSongUrl(
                song = nextSong,
                allowGenericPrefetchCache = false,
                sideEffects = RefreshResolverSideEffects(RefreshSideEffectGate { false }),
                shouldApplyCacheMutation = { false }
            )
            // 本地兜底命中的受限歌曲同样值得预取, 否则消费方会白等一个不落盘的任务
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
                        song = nextSong
                    )
                }
                NPLogger.d(
                    "NERI-PlayerManager",
                    "generic URL prefetch completed: song=${nextSong.name}, key=$cacheKey"
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NPLogger.w(
                "NERI-PlayerManager",
                "generic URL prefetch failed: song=${nextSong.name}, key=$cacheKey",
                error
            )
        }
    }
    currentGenericUrlPrefetchJob = launchedJob
    launchedJob.invokeOnCompletion {
        if (currentGenericUrlPrefetchJob === launchedJob) {
            currentGenericUrlPrefetchJob = null
            currentGenericUrlPrefetchKey = null
        }
    }
}

private suspend fun PlayerManager.prefetchGenericTrackMedia(
    result: SongUrlResult.Success,
    cacheKey: String,
    song: SongItem
) {
    val mediaCacheKey = resolveGenericMediaPrefetchCacheKey(cacheKey, result)
    if (playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey)) return
    if (result.audioInfo != null) {
        val descriptorResult = synchronizeCachedPlaybackDescriptor(
            cacheKey = mediaCacheKey,
            audioInfo = result.audioInfo,
            expectedContentLength = result.expectedContentLength,
            representationIdentity = result.representationIdentity,
            song = song,
            shouldApplyMutation = { !playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey) }
        )
        if (!descriptorResult.allowsCustomCacheKey()) {
            NPLogger.w(
                "NERI-PlayerManager",
                "skip generic media prefetch because cache descriptor was not synchronized: " +
                    "song=" + song.name + ", key=" + mediaCacheKey + ", result=" + descriptorResult
            )
            return
        }
    }
    when (
        prepareExoPlayerCacheForPrefetch(
            cacheKey = mediaCacheKey,
            shouldApplyMutation = { !playbackDemandArbiter.shouldYieldPrefetch(mediaCacheKey) }
        )
    ) {
        CachePrefetchReadiness.COMPLETE -> return
        CachePrefetchReadiness.UNAVAILABLE -> return
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
            "generic media prefetch failed: song=" + song.name + ", key=" + mediaCacheKey + ", error=" + error.message
        )
        return
    }
    if (prefetchedBytes > 0L) {
        runCatching {
            cache?.writeCachedSong(mediaCacheKey, song)
        }
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
