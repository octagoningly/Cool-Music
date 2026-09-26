package moe.ouom.neriplayer.ui.component.playback

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
}
