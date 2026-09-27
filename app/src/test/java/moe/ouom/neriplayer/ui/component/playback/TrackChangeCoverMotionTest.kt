package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun slideIsFasterThanSettleScale() {
        assertTrue(TrackChangeCoverMotion.SlideSpec.durationMillis <= 320)
        assertTrue(TrackChangeCoverMotion.ScaleSpec.durationMillis >= 400)
        assertTrue(
            TrackChangeCoverMotion.SlideSpec.durationMillis <
                TrackChangeCoverMotion.ScaleSpec.durationMillis
        )
    }

    @Test
    fun outgoingCoverSinksAndFliesOffScreenLeft() {
        val fly = 1200f
        assertEquals(1f, TrackChangeCoverMotion.outgoingScale(0f), 1e-4f)
        assertEquals(
            TrackChangeCoverMotion.OldScaleTo,
            TrackChangeCoverMotion.outgoingScale(1f),
            1e-4f,
        )
        assertEquals(0f, TrackChangeCoverMotion.outgoingTranslationX(0f, fly), 1e-4f)
        // 终点在屏幕左缘外
        assertEquals(-fly, TrackChangeCoverMotion.outgoingTranslationX(1f, fly), 1e-4f)
    }

    @Test
    fun incomingCoverFliesFromRightAndSettlesScale() {
        val fly = 1200f
        assertEquals(
            TrackChangeCoverMotion.NewScaleFrom,
            TrackChangeCoverMotion.incomingScale(0f),
            1e-4f,
        )
        assertEquals(1f, TrackChangeCoverMotion.incomingScale(1f), 1e-4f)
        assertEquals(fly, TrackChangeCoverMotion.incomingTranslationX(0f, fly), 1e-4f)
        assertEquals(0f, TrackChangeCoverMotion.incomingTranslationX(1f, fly), 1e-4f)
    }

    @Test
    fun easingCurvesPreferFastThenSlow() {
        // 先快后慢：前段应明显快于线性
        val slideEarly = TrackChangeCoverMotion.SlideEasing.transform(0.3f)
        val rippleEarly = TrackChangeCoverMotion.RippleEasing.transform(0.3f)
        val settleEarly = TrackChangeCoverMotion.SettleEasing.transform(0.3f)
        assertTrue("slide early=$slideEarly", slideEarly > 0.3f)
        assertTrue("ripple early=$rippleEarly", rippleEarly > 0.3f)
        assertTrue("settle early=$settleEarly", settleEarly > 0.3f)
        // 尾段趋缓：0.8 时应已接近 1
        assertTrue(TrackChangeCoverMotion.SettleEasing.transform(0.8f) > 0.85f)
    }
}
