package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun appleStyleSlideIsOnePageNotScreenWide() {
        assertEquals(1.0f, TrackChangeCoverMotion.PageSlideFraction, 1e-4f)
        assertTrue(TrackChangeCoverMotion.DurationMs in 420..650)
    }

    @Test
    fun sideScaleIsGentleLikeCoverFlow() {
        assertTrue(TrackChangeCoverMotion.SideScale in 0.80f..0.92f)
    }

    @Test
    fun outgoingSlidesLeftAndShrinks() {
        val w = 800f
        assertEquals(0f, TrackChangeCoverMotion.outgoingTranslationX(0f, w), 1e-4f)
        assertEquals(-w, TrackChangeCoverMotion.outgoingTranslationX(1f, w), 1e-4f)
        assertEquals(1f, TrackChangeCoverMotion.outgoingScale(0f), 1e-4f)
        assertEquals(
            TrackChangeCoverMotion.SideScale,
            TrackChangeCoverMotion.outgoingScale(1f),
            1e-4f,
        )
    }

    @Test
    fun incomingSlidesFromRightAndGrows() {
        val w = 800f
        assertEquals(w, TrackChangeCoverMotion.incomingTranslationX(0f, w), 1e-4f)
        assertEquals(0f, TrackChangeCoverMotion.incomingTranslationX(1f, w), 1e-4f)
        assertEquals(
            TrackChangeCoverMotion.SideScale,
            TrackChangeCoverMotion.incomingScale(0f),
            1e-4f,
        )
        assertEquals(1f, TrackChangeCoverMotion.incomingScale(1f), 1e-4f)
    }

    @Test
    fun bothCoversRemainVisibleMidway() {
        val p = 0.5f
        assertTrue(TrackChangeCoverMotion.outgoingAlpha(p) > 0.3f)
        assertTrue(TrackChangeCoverMotion.incomingAlpha(p) > 0.5f)
    }
}
