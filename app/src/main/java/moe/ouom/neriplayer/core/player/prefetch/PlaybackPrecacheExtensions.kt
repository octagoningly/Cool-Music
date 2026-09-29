package moe.ouom.neriplayer.core.player.prefetch

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import moe.ouom.neriplayer.data.model.displayCoverUrl

private const val LIST_MEDIA_MIN_BYTES = 384L * 1024L

enum class PlaybackPrecacheScenario {
    APP_LAUNCH,
    NEXT_TRACK,
    RECENT_LIST,
    PLAYLIST_OPEN,
    HOME_RECOMMEND,
    SEARCH
}

data class PlaybackPrecacheConfig(
    val masterEnabled: Boolean = true,
    val appLaunchEnabled: Boolean = true,
    val nextTrackEnabled: Boolean = true,
    val recentListEnabled: Boolean = false,
    val playlistOpenEnabled: Boolean = false,
    val homeRecommendEnabled: Boolean = false,
    val searchEnabled: Boolean = false
) {
    fun isEnabled(scenario: PlaybackPrecacheScenario): Boolean {
        val scenarioEnabled = when (scenario) {
            PlaybackPrecacheScenario.APP_LAUNCH -> appLaunchEnabled
            PlaybackPrecacheScenario.NEXT_TRACK -> nextTrackEnabled
            PlaybackPrecacheScenario.RECENT_LIST -> recentListEnabled
            PlaybackPrecacheScenario.PLAYLIST_OPEN -> playlistOpenEnabled
            PlaybackPrecacheScenario.HOME_RECOMMEND -> homeRecommendEnabled
            PlaybackPrecacheScenario.SEARCH -> searchEnabled
        }
        return PlaybackPrecachePolicy.isScenarioEnabled(masterEnabled, scenarioEnabled)
    }
}

private const val PRECACHE_JOB_LABEL = "playback-precache-list"

/**
 * 列表类场景的入口：按场景开关预取若干歌曲的音频前缀。
 * 同一时间只保留一个列表预取任务，后触发的会覆盖先前的。
 */
internal fun PlayerManager.precacheSongList(
    songs: List<SongItem>,
    scenario: PlaybackPrecacheScenario,
    maxSongs: Int = PlaybackPrecachePolicy.LIST_MAX_SONGS,
    prefixMs: Long = PlaybackPrecachePolicy.LIST_PREFIX_MS
) {
    if (!isApplicationInitialized()) return
    if (!playbackPrecacheConfig.isEnabled(scenario)) {
        NPLogger.d(
            "NERI-PlayerManager",
            "skip playback precache: scenario=$scenario disabled"
        )
        return
    }
    val targets = songs
        .asSequence()
        .filter { !isLocalSong(it) }
        .filter { !isYouTubeMusicTrack(it) }
        .take(maxSongs)
        .toList()
    if (targets.isEmpty()) return

    val previous = currentPlaybackPrecacheJob
    if (previous?.isActive == true &&
        currentPlaybackPrecacheScenario == scenario &&
        currentPlaybackPrecacheKeys == targets.map { computeCacheKey(it) }.toSet()
    ) {
        return
    }
    previous?.cancel()
    currentPlaybackPrecacheScenario = scenario
    currentPlaybackPrecacheKeys = targets.map { computeCacheKey(it) }.toSet()
    val launched = ioScope.launch {
        targets.forEachIndexed { index, song ->
            try {
                precacheSongPrefix(
                    song = song,
                    prefixMs = prefixMs,
                    scenario = scenario,
                    label = "$PRECACHE_JOB_LABEL/${scenario.name}#$index"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                NPLogger.w(
                    "NERI-PlayerManager",
                    "playback precache item failed: scenario=$scenario, song=${song.name}",
                    error
                )
            }
        }
    }
    currentPlaybackPrecacheJob = launched
    launched.invokeOnCompletion {
        if (currentPlaybackPrecacheJob === launched) {
            currentPlaybackPrecacheJob = null
            currentPlaybackPrecacheScenario = null
            currentPlaybackPrecacheKeys = emptySet()
        }
    }
}

/**
 * 开屏预加载：针对迷你栏上的上次歌曲（当前曲）预取前缀。
 */
internal fun PlayerManager.precacheAppLaunchCurrentSong() {
    if (!isApplicationInitialized()) return
    if (!playbackPrecacheConfig.isEnabled(PlaybackPrecacheScenario.APP_LAUNCH)) return
    val song = currentSongFlow.value ?: return
    if (isPlayingFlow.value || player.playWhenReady) return
    ioScope.launch {
        try {
            precacheSongPrefix(
                song = song,
                prefixMs = PlaybackPrecachePolicy.APP_LAUNCH_PREFIX_MS,
                scenario = PlaybackPrecacheScenario.APP_LAUNCH,
                label = "playback-precache/app-launch"
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NPLogger.w(
                "NERI-PlayerManager",
                "app launch precache failed: song=${song.name}",
                error
            )
        }
    }
}

/**
 * 最近列表预加载：恢复队列后预取当前及后续少量曲目。
 */
internal fun PlayerManager.precacheRecentQueue() {
    if (!isApplicationInitialized()) return
    if (!playbackPrecacheConfig.isEnabled(PlaybackPrecacheScenario.RECENT_LIST)) return
    val playlist = currentPlaylist
    if (playlist.isEmpty()) return
    val startIndex = currentIndex.coerceIn(0, playlist.lastIndex)
    val window = PlaybackPrecachePolicy.selectPrefetchWindow(
        queueSize = playlist.size,
        startIndex = startIndex,
        rapidSkip = false,
        repeatAll = false
    ).take(PlaybackPrecachePolicy.RECENT_LIST_MAX_SONGS)
    precacheSongList(
        songs = window.mapNotNull { playlist.getOrNull(it) },
        scenario = PlaybackPrecacheScenario.RECENT_LIST,
        maxSongs = PlaybackPrecachePolicy.RECENT_LIST_MAX_SONGS,
        prefixMs = PlaybackPrecachePolicy.LIST_PREFIX_MS
    )
}

/**
 * 为单曲预取音频前缀。字节数按码率/时长估算到目标秒数。
 */
internal suspend fun PlayerManager.precacheSongPrefix(
    song: SongItem,
    prefixMs: Long,
    scenario: PlaybackPrecacheScenario,
    label: String
) {
    if (isLocalSong(song)) return
    if (isYouTubeMusicTrack(song)) return
    if (!isApplicationInitialized()) return

    val cacheKey = computeCacheKey(song)
    if (cacheKey.isBlank()) return
    if (playbackDemandArbiter.shouldYieldPrefetch(cacheKey)) {
        NPLogger.d(
            "NERI-PlayerManager",
            "skip playback precache for active playback demand: key=$cacheKey, label=$label"
        )
        return
    }

    val result = resolveSongUrl(
        song = song,
        allowGenericPrefetchCache = false,
        sideEffects = RefreshResolverSideEffects(RefreshSideEffectGate { false }),
        shouldApplyCacheMutation = { false }
    )
    if (result !is SongUrlResult.Success) return
    if (result.url.startsWith(OFFLINE_CACHE_URL_PREFIX)) return
    if (!isDirectStreamUrl(result.url) && !LocalSongSupport.isLocalMediaUri(result.url)) return
    if (LocalSongSupport.isLocalMediaUri(result.url)) return

    // Store the resolved URL so playback can skip a network resolve (this is what removes the spinner).
    genericUrlPrefetchCache.put(
        key = cacheKey,
        result = result,
        nowMs = android.os.SystemClock.elapsedRealtime(),
        ttlMsOverride = resolveGenericUrlPrefetchTtlMs(
            currentTrackDurationMs = maxOf(
                playbackDurationFlow.value,
                currentSongFlow.value?.durationMs ?: 0L
            )
        )
    )
    NPLogger.d(
        "NERI-PlayerManager",
        "precached URL ready: label=$label, song=${song.name}, key=$cacheKey"
    )
    warmLyricsAndCoverForPrecache(song)

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
            NPLogger.d(
                "NERI-PlayerManager",
                "descriptor not ready, still warm media: label=$label, key=$mediaCacheKey, result=$descriptorResult"
            )
        }
    } else {
        runCatching { cache?.writeCachedSong(mediaCacheKey, song) }
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
            prefixMs = prefixMs,
            contentLength = result.expectedContentLength,
            durationMs = result.durationMs ?: song.durationMs,
            bitrateKbps = result.audioInfo?.bitrateKbps
        ),
        LIST_MEDIA_MIN_BYTES
    )
    val prefetched = runCatching {
        prefetchIntoPlayerCache(
            url = result.url,
            cacheKey = mediaCacheKey,
            targetBytes = targetBytes
        )
    }.getOrElse { error ->
        NPLogger.w(
            "NERI-PlayerManager",
            "playback precache media failed: label=$label, song=${song.name}, key=$mediaCacheKey, " +
                "error=${error.message}"
        )
        return
    }
    if (prefetched > 0L) {
        runCatching {
            cache?.writeCachedSong(mediaCacheKey, song)
        }
    }
    NPLogger.d(
        "NERI-PlayerManager",
        "playback precache finished: label=$label, song=${song.name}, key=$mediaCacheKey, " +
            "prefixMs=$prefixMs, targetBytes=$targetBytes, prefetchedBytes=$prefetched"
    )
}

internal fun PlayerManager.noteNextSkipClick() {
    val now = SystemClock.elapsedRealtime()
    val count = PlaybackPrecachePolicy.noteSkipClick(
        timestamps = recentNextSkipTimestamps,
        nowMs = now
    )
    NPLogger.d(
        "NERI-PlayerManager",
        "next skip click noted: countInWindow=$count, rapid=${
            PlaybackPrecachePolicy.isRapidSkipping(recentNextSkipTimestamps, now)
        }"
    )
}

internal fun PlayerManager.isRapidNextSkipping(): Boolean {
    return PlaybackPrecachePolicy.isRapidSkipping(
        timestamps = recentNextSkipTimestamps,
        nowMs = SystemClock.elapsedRealtime()
    )
}

internal fun PlayerManager.precacheEnabledForScenario(
    scenario: PlaybackPrecacheScenario
): Boolean {
    return playbackPrecacheConfig.isEnabled(scenario)
}


private suspend fun PlayerManager.warmLyricsAndCoverForPrecache(song: SongItem) {
    runCatching {
        val lyrics = getLyrics(song)
        NPLogger.d(
            "NERI-PlayerManager",
            "precached lyrics: song=${song.name}, entries=${lyrics.size}"
        )
    }.onFailure { error ->
        NPLogger.w(
            "NERI-PlayerManager",
            "precached lyrics failed: song=${song.name}, error=${error.message}"
        )
    }
    runCatching {
        val coverUrl = song.displayCoverUrl()
        if (!coverUrl.isNullOrBlank()) {
            val request = coil.request.ImageRequest.Builder(application)
                .data(coverUrl)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
            coil.Coil.imageLoader(application).execute(request)
            NPLogger.d(
                "NERI-PlayerManager",
                "precached cover: song=${song.name}, url=$coverUrl"
            )
        }
    }.onFailure { error ->
        NPLogger.w(
            "NERI-PlayerManager",
            "precached cover failed: song=${song.name}, error=${error.message}"
        )
    }
}
