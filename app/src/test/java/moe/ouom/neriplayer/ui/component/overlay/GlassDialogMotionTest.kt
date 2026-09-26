package moe.ouom.neriplayer.ui.component.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDialogMotionTest {
    @Test
    fun enterSpringIsStrongerBounceThanMenu() {
        // 强 Q 弹：damping 0.40，过冲比 ⋮ 菜单（0.48）更明显
        assertTrue(GlassDialogMotion.EnterTransformSpring.dampingRatio < 0.45f)
        assertTrue(GlassDialogMotion.EnterTransformSpring.dampingRatio >= 0.35f)
        assertTrue(
            GlassDialogMotion.ExitTransformSpring.dampingRatio >
                GlassDialogMotion.EnterTransformSpring.dampingRatio
        )
    }

    @Test
    fun scaleTravelIsLargeForObviousPop() {
        val travel = 1f - GlassDialogMotion.EnterScaleFrom
        assertTrue("travel=$travel", travel >= 0.28f)
        assertTrue(GlassDialogMotion.ExitScaleTo in 0.80f..0.95f)
    }

    @Test
    fun appearScaleOvershootsWhenSpringPassesOne() {
        assertEquals(
            GlassDialogMotion.EnterScaleFrom,
            GlassDialogMotion.appearScale(true, 0f),
            1e-4f
        )
        assertEquals(1f, GlassDialogMotion.appearScale(true, 1f), 1e-4f)
        // progress 过冲 1.2 → scale 约 1.06，肉眼能看出冲过位
        assertTrue(GlassDialogMotion.appearScale(true, 1.2f) > 1.05f)
        assertTrue(GlassDialogMotion.appearScale(true, 1.12f) > 1.02f)
        assertTrue(GlassDialogMotion.appearScale(true, 0.9f) < 1f)
        // 收回：progress 1→0
        assertEquals(1f, GlassDialogMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassDialogMotion.ExitScaleTo, GlassDialogMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun sheetSlidesFromBelowAndCanOvershoot() {
        val enter = GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 0f)
        assertTrue("enter slide=$enter", enter > 0.2f)
        assertEquals(
            0f,
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 1f),
            1e-4f
        )
        // 过冲到上方
        assertTrue(
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 1.15f) < 0f
        )
        // 居中弹窗不位移
        assertEquals(
            0f,
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = false, progress = 0f),
            1e-4f
        )
    }

    @Test
    fun fadeIsIndependentAndFasterToClose() {
        assertTrue(GlassDialogMotion.EnterFadeMs in 140..240)
        assertTrue(GlassDialogMotion.ExitFadeMs < GlassDialogMotion.EnterFadeMs)
        assertFalse(GlassDialogMotion.EnterScaleFrom >= 1f)
    }
}
