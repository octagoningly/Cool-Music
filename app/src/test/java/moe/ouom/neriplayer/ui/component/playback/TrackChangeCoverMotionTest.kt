package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun slideIsMuchFasterThanSettleScale() {
        assertTrue(TrackChangeCoverMotion.SlideSpec.durationMillis <= 280)
        assertTrue(TrackChangeCoverMotion.ScaleSpec.durationMillis >= 480)
        assertTrue(
            TrackChangeCoverMotion.SlideSpec.durationMillis <
                TrackChangeCoverMotion.ScaleSpec.durationMillis - 150
        )
    }

    @Test
    fun shrinkIsClearlyVisible() {
        // 缩小幅度要一眼能看出来，放大才有「缓速长回」的感觉
        assertTrue(TrackChangeCoverMotion.OldScaleTo <= 0.72f)
        assertTrue(TrackChangeCoverMotion.NewScaleFrom <= 0.72f)
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
    }

    @Test
    fun easingCurvesAreStronglyFastThenSlow() {
        // 前段应远快于线性（>0.55 at 0.25）
        val slideEarly = TrackChangeCoverMotion.SlideEasing.transform(0.25f)
        val settleEarly = TrackChangeCoverMotion.SettleEasing.transform(0.25f)
        val rippleEarly = TrackChangeCoverMotion.RippleEasing.transform(0.25f)
        assertTrue("slide=$slideEarly", slideEarly > 0.55f)
        assertTrue("settle=$settleEarly", settleEarly > 0.55f)
        assertTrue("ripple=$rippleEarly", rippleEarly > 0.55f)
        // 尾段几乎贴住 1
        assertTrue(TrackChangeCoverMotion.SettleEasing.transform(0.75f) > 0.92f)
    }
}
