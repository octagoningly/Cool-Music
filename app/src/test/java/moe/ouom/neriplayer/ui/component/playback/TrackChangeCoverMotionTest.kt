package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun scaleIsClearlyVisibleAndSettleIsLongerThanSlide() {
        assertTrue(TrackChangeCoverMotion.SideScale <= 0.78f)
        assertTrue(
            TrackChangeCoverMotion.ScaleSettleSpec.durationMillis >
                TrackChangeCoverMotion.SlideSpec.durationMillis
        )
    }

    @Test
    fun outgoingShrinksSlidesAndFades() {
        val w = 800f
        assertEquals(-w, TrackChangeCoverMotion.outgoingTranslationX(1f, w), 1e-4f)
        assertEquals(TrackChangeCoverMotion.SideScale, TrackChangeCoverMotion.outgoingScale(1f), 1e-4f)
        assertEquals(0f, TrackChangeCoverMotion.outgoingAlpha(1f), 1e-4f)
        assertEquals(1f, TrackChangeCoverMotion.outgoingAlpha(0f), 1e-4f)
    }

    @Test
    fun incomingGrowsFromSideScaleToFull() {
        assertEquals(
            TrackChangeCoverMotion.SideScale,
            TrackChangeCoverMotion.incomingScale(0f),
            1e-4f,
        )
        assertEquals(1f, TrackChangeCoverMotion.incomingScale(1f), 1e-4f)
        assertEquals(0f, TrackChangeCoverMotion.incomingTranslationX(1f, 800f), 1e-4f)
    }

    @Test
    fun settleEasingSlowsNearFullSize() {
        val ease = androidx.compose.animation.core.CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)
        // 前段快：0.3 时已超过线性
        assertTrue(ease.transform(0.3f) > 0.35f)
        // 尾段贴近 1（缓速收束）
        assertTrue(ease.transform(0.9f) > 0.95f)
    }
}
