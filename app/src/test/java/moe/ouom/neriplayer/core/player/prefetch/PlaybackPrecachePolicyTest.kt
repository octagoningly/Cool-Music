package moe.ouom.neriplayer.core.player.prefetch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPrecachePolicyTest {
    @Test
    fun scenarioRequiresMasterSwitch() {
        assertTrue(
            PlaybackPrecachePolicy.isScenarioEnabled(
                masterEnabled = true,
                scenarioEnabled = true
            )
        )
        assertFalse(
            PlaybackPrecachePolicy.isScenarioEnabled(
                masterEnabled = false,
                scenarioEnabled = true
            )
        )
        assertFalse(
            PlaybackPrecachePolicy.isScenarioEnabled(
                masterEnabled = true,
                scenarioEnabled = false
            )
        )
    }

    @Test
    fun prefixBytesUsesContentLengthRatioWhenAvailable() {
        val bytes = PlaybackPrecachePolicy.prefixBytes(
            prefixMs = 3_000L,
            contentLength = 3_000_000L,
            durationMs = 300_000L
        )
        assertEquals(30_000L, bytes)
    }

    @Test
    fun prefixBytesFallsBackToDefaultBitrate() {
        // 320kbps * 1.3s ≈ 52KB
        val bytes = PlaybackPrecachePolicy.prefixBytes(
            prefixMs = 1_300L,
            contentLength = null,
            durationMs = 0L
        )
        assertTrue(bytes in (40_000L..60_000L))
    }

    @Test
    fun prefixBytesNeverExceedsContentLength() {
        val bytes = PlaybackPrecachePolicy.prefixBytes(
            prefixMs = 100L,
            contentLength = 1_000L,
            durationMs = 300_000L
        )
        assertTrue(bytes <= 1_000L)
        assertTrue(bytes > 0L)
    }

    @Test
    fun prefixBytesHasFloorForTypicalContent() {
        val bytes = PlaybackPrecachePolicy.prefixBytes(
            prefixMs = 100L,
            contentLength = 3_000_000L,
            durationMs = 300_000L
        )
        assertTrue(bytes >= 24L * 1024L)
    }

    @Test
    fun rapidSkipDetectedWithinWindow() {
        val stamps = mutableListOf<Long>()
        PlaybackPrecachePolicy.noteSkipClick(stamps, nowMs = 1_000L)
        assertFalse(PlaybackPrecachePolicy.isRapidSkipping(stamps, nowMs = 1_000L))
        PlaybackPrecachePolicy.noteSkipClick(stamps, nowMs = 1_500L)
        assertTrue(PlaybackPrecachePolicy.isRapidSkipping(stamps, nowMs = 1_500L))
    }

    @Test
    fun rapidSkipExpiresAfterWindow() {
        val stamps = mutableListOf(1_000L, 1_200L)
        assertTrue(PlaybackPrecachePolicy.isRapidSkipping(stamps, nowMs = 1_300L))
        assertFalse(
            PlaybackPrecachePolicy.isRapidSkipping(stamps, nowMs = 1_300L + 2_500L)
        )
    }

    @Test
    fun rapidSkipWindowSelectsMoreTracks() {
        val normal = PlaybackPrecachePolicy.selectPrefetchWindow(
            queueSize = 10,
            startIndex = 2,
            rapidSkip = false,
            repeatAll = false
        )
        val rapid = PlaybackPrecachePolicy.selectPrefetchWindow(
            queueSize = 10,
            startIndex = 2,
            rapidSkip = true,
            repeatAll = false
        )
        assertTrue(normal.size <= 2)
        assertTrue(rapid.size >= normal.size)
        assertEquals(PlaybackPrecachePolicy.RAPID_SKIP_MAX_SONGS, rapid.size)
    }

    @Test
    fun queueWindowWrapsWithRepeatAll() {
        val window = PlaybackPrecachePolicy.selectPrefetchWindow(
            queueSize = 3,
            startIndex = 2,
            rapidSkip = true,
            repeatAll = true
        )
        assertEquals(listOf(2, 0, 1), window.take(3))
    }

    @Test
    fun nextPrefixIsLongerThanRapidPrefix() {
        assertTrue(
            PlaybackPrecachePolicy.prefixMsForQueueIndex(0) >
                PlaybackPrecachePolicy.prefixMsForQueueIndex(1)
        )
        assertEquals(
            PlaybackPrecachePolicy.RAPID_SKIP_PREFIX_MS,
            PlaybackPrecachePolicy.prefixMsForQueueIndex(2)
        )
    }

    @Test
    fun defaultConfigEnablesNextAndAppLaunchOnly() {
        val config = PlaybackPrecacheConfig()
        assertTrue(config.isEnabled(PlaybackPrecacheScenario.NEXT_TRACK))
        assertTrue(config.isEnabled(PlaybackPrecacheScenario.APP_LAUNCH))
        assertFalse(config.isEnabled(PlaybackPrecacheScenario.RECENT_LIST))
        assertFalse(config.isEnabled(PlaybackPrecacheScenario.PLAYLIST_OPEN))
        assertFalse(config.isEnabled(PlaybackPrecacheScenario.HOME_RECOMMEND))
        assertFalse(config.isEnabled(PlaybackPrecacheScenario.SEARCH))
    }

    @Test
    fun masterSwitchDisablesAllScenarios() {
        val config = PlaybackPrecacheConfig(
            masterEnabled = false,
            appLaunchEnabled = true,
            nextTrackEnabled = true,
            recentListEnabled = true,
            playlistOpenEnabled = true,
            homeRecommendEnabled = true,
            searchEnabled = true
        )
        PlaybackPrecacheScenario.entries.forEach { scenario ->
            assertFalse(config.isEnabled(scenario))
        }
    }
}
