package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackChangeCoverMotionTest {
    @Test
    fun scaleIsClearlyVisibleAndSettleIsLongerThanSlide() {
        // 约当前 0.8 倍（0.72→0.58），缩小要看得出来
        assertTrue(TrackChangeCoverMotion.SideScale in 0.52f..0.62f)
        assertTrue(
            TrackChangeCoverMotion.ScaleSettleSpec.durationMillis >
                TrackChangeCoverMotion.SlideSpec.durationMillis
        )
        assertTrue(TrackChangeCoverMotion.ScaleStartDelayMs in 120L..260L)
    }

    @Test
    fun outgoingShrinksSlidesAndFades() {
        val w = 800f
        assertEquals(-w, TrackChangeCoverMotion.outgoingTranslationX(1f, w, true), 1e-4f)
        // 上一首：镜像，向右飞出
        assertEquals(w, TrackChangeCoverMotion.outgoingTranslationX(1f, w, false), 1e-4f)
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
        assertEquals(0f, TrackChangeCoverMotion.incomingTranslationX(1f, 800f, true), 1e-4f)
        // 上一首：新封面从左侧进入
        assertEquals(-800f, TrackChangeCoverMotion.incomingTranslationX(0f, 800f, false), 1e-4f)
    }

    @Test
    fun backgroundRevealIsSlowerTailedAndFadesIn() {
        assertTrue(TrackChangeCoverMotion.BackgroundRevealSpec.durationMillis >= 650)
        assertTrue(TrackChangeCoverMotion.BackgroundRevealSpec.durationMillis > TrackChangeCoverMotion.ScaleDurationMs)
        assertEquals(
            TrackChangeCoverMotion.RevealFadeFromAlpha,
            TrackChangeCoverMotion.revealAlpha(0f),
            1e-4f,
        )
        assertEquals(1f, TrackChangeCoverMotion.revealAlpha(1f), 1e-4f)
        assertTrue(TrackChangeCoverMotion.RevealFadeFromAlpha < 1f)
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
