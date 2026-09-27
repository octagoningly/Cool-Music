package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun coverSpecIsBouncyButReadable() {
        assertTrue(TrackChangeCoverMotion.DurationMs in 320..520)
        assertTrue(TrackChangeCoverMotion.OldScaleTo in 0.7f..0.9f)
        assertTrue(TrackChangeCoverMotion.NewScaleFrom in 0.75f..0.95f)
        assertTrue(TrackChangeCoverMotion.SlideXFraction >= 1f)
    }

    @Test
    fun outgoingCoverSinksAndSlidesLeft() {
        val w = 1000f
        assertEquals(1f, TrackChangeCoverMotion.outgoingScale(0f), 1e-4f)
        assertEquals(
            TrackChangeCoverMotion.OldScaleTo,
            TrackChangeCoverMotion.outgoingScale(1f),
            1e-4f,
        )
        assertEquals(0f, TrackChangeCoverMotion.outgoingTranslationX(0f, w), 1e-4f)
        assertTrue(TrackChangeCoverMotion.outgoingTranslationX(1f, w) < -w * 0.9f)
    }

    @Test
    fun incomingCoverFliesFromRightAndGrows() {
        val w = 1000f
        assertEquals(
            TrackChangeCoverMotion.NewScaleFrom,
            TrackChangeCoverMotion.incomingScale(0f),
            1e-4f,
        )
        assertEquals(1f, TrackChangeCoverMotion.incomingScale(1f), 1e-4f)
        assertTrue(TrackChangeCoverMotion.incomingTranslationX(0f, w) > w * 0.9f)
        assertEquals(0f, TrackChangeCoverMotion.incomingTranslationX(1f, w), 1e-4f)
    }

    @Test
    fun simultaneousInOutShareSameProgressAxis() {
        // 同一 progress：旧的向左、新的从右靠拢
        val w = 800f
        val p = 0.5f
        assertTrue(TrackChangeCoverMotion.outgoingTranslationX(p, w) < 0f)
        assertTrue(TrackChangeCoverMotion.incomingTranslationX(p, w) > 0f)
    }
}
