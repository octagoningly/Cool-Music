package moe.ouom.neriplayer.ui.component.playback

import moe.ouom.neriplayer.ui.component.common.SceneDepthMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingExpandMotionTest {
    @Test
    fun `coherent feedback off keeps original drawer animation`() {
        assertTrue(nowPlayingExpandUsesOriginalAnimation(coherentFeedbackEnabled = false))
        assertFalse(shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled = false))
    }

    @Test
    fun `coherent feedback on enables shared cover expand motion`() {
        assertFalse(nowPlayingExpandUsesOriginalAnimation(coherentFeedbackEnabled = true))
        assertTrue(shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled = true))
    }

    @Test
    fun `transition helpers return null for original path`() {
        assertTrue(nowPlayingExpandEnterTransition(coherentFeedbackEnabled = false) == null)
        assertTrue(nowPlayingExpandExitTransition(coherentFeedbackEnabled = false) == null)
        assertTrue(miniPlayerExpandExitTransition(coherentFeedbackEnabled = false) == null)
        assertTrue(miniPlayerExpandEnterTransition(coherentFeedbackEnabled = false) == null)
    }

    @Test
    fun `transition helpers return specs when coherent feedback is on`() {
        assertTrue(nowPlayingExpandEnterTransition(coherentFeedbackEnabled = true) != null)
        assertTrue(nowPlayingExpandExitTransition(coherentFeedbackEnabled = true) != null)
        assertTrue(miniPlayerExpandExitTransition(coherentFeedbackEnabled = true) != null)
        assertTrue(miniPlayerExpandEnterTransition(coherentFeedbackEnabled = true) != null)
    }

    @Test
    fun `dismiss thresholds stay in usable range`() {
        assertTrue(NowPlayingExpandMotion.DismissDistanceRatio in 0.15f..0.45f)
        assertTrue(NowPlayingExpandMotion.DismissVelocityPxPerSec >= 800f)
        assertTrue(NowPlayingExpandMotion.ExpandSwipeUpThresholdDp in 24f..96f)
    }

    @Test
    fun `stagger delays are ordered cover title then controls`() {
        assertTrue(NowPlayingExpandMotion.StaggerTitleDelayMs >= 0)
        assertTrue(
            NowPlayingExpandMotion.StaggerControlsDelayMs >
                NowPlayingExpandMotion.StaggerTitleDelayMs
        )
        assertTrue(NowPlayingExpandMotion.StaggerDurationMs in 150..500)
    }

    @Test
    fun `background remains opaque while it recedes and returns`() {
        val resting = resolveNowPlayingBackgroundFrame(0f)
        val receded = resolveNowPlayingBackgroundFrame(1f)

        assertEquals(1f, resting.scale, 0.0001f)
        assertEquals(NowPlayingExpandMotion.BackgroundRecedeScale, receded.scale, 0.0001f)
        assertEquals(SceneDepthMotion.ExpandedScale, receded.scale, 0.0001f)
        assertEquals(1f, resting.alpha, 0.0001f)
        assertEquals(1f, receded.alpha, 0.0001f)
    }

    @Test
    fun `background progress is clamped to valid range`() {
        assertEquals(
            resolveNowPlayingBackgroundFrame(0f),
            resolveNowPlayingBackgroundFrame(-1f)
        )
        assertEquals(
            resolveNowPlayingBackgroundFrame(1f),
            resolveNowPlayingBackgroundFrame(2f)
        )
    }
}
