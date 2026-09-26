package moe.ouom.neriplayer.ui.component.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDialogMotionTest {
    @Test
    fun enterSpringIsStrongObviousBounce() {
        // 强 Q 弹：damping 很低，过冲大、震荡久
        assertTrue(GlassDialogMotion.EnterTransformSpring.dampingRatio < 0.38f)
        assertTrue(GlassDialogMotion.EnterTransformSpring.dampingRatio >= 0.28f)
        assertTrue(
            GlassDialogMotion.ExitTransformSpring.dampingRatio >
                GlassDialogMotion.EnterTransformSpring.dampingRatio
        )
    }

    @Test
    fun scaleTravelIsLargeForObviousPop() {
        val travel = 1f - GlassDialogMotion.EnterScaleFrom
        assertTrue("travel=$travel", travel >= 0.40f)
        assertTrue(GlassDialogMotion.ExitScaleTo in 0.75f..0.92f)
    }

    @Test
    fun appearScaleOvershootsWhenSpringPassesOne() {
        assertEquals(
            GlassDialogMotion.EnterScaleFrom,
            GlassDialogMotion.appearScale(true, 0f),
            1e-4f
        )
        assertEquals(1f, GlassDialogMotion.appearScale(true, 1f), 1e-4f)
        // progress 过冲 1.25 → scale 约 1.11
        assertTrue(GlassDialogMotion.appearScale(true, 1.25f) > 1.08f)
        assertTrue(GlassDialogMotion.appearScale(true, 0.9f) < 1f)
        assertEquals(1f, GlassDialogMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassDialogMotion.ExitScaleTo, GlassDialogMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun bothDialogKindsSlideFromBelowAndCanOvershoot() {
        val center = GlassDialogMotion.appearSlideYFraction(isBottomSheet = false, progress = 0f)
        val sheet = GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 0f)
        assertTrue("center=$center", center > 0.12f)
        assertTrue("sheet=$sheet", sheet > center)
        assertEquals(
            0f,
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = false, progress = 1f),
            1e-4f
        )
        assertTrue(
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 1.15f) < 0f
        )
    }

    @Test
    fun fadeIsIndependentAndFasterToClose() {
        assertTrue(GlassDialogMotion.EnterFadeMs in 160..280)
        assertTrue(GlassDialogMotion.ExitFadeMs < GlassDialogMotion.EnterFadeMs)
        assertFalse(GlassDialogMotion.EnterScaleFrom >= 1f)
    }
}

