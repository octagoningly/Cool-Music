package moe.ouom.neriplayer.ui.component.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassDialogMotionTest {
    @Test
    fun dialogMotionMatchesThreeDotMenuFeel() {
        // 与三点菜单同一套：damping 0.48 / stiffness 888
        assertEquals(0.48f, GlassDialogMotion.EnterTransformSpring.dampingRatio, 1e-3f)
        assertTrue(GlassDialogMotion.EnterTransformSpring.stiffness in 800f..980f)
        assertEquals(0.72f, GlassDialogMotion.ExitTransformSpring.dampingRatio, 1e-3f)
        assertTrue(
            GlassDialogMotion.ExitTransformSpring.dampingRatio >
                GlassDialogMotion.EnterTransformSpring.dampingRatio
        )
    }

    @Test
    fun scaleTravelIsModerateLikeMenu() {
        // 统一为菜单 0.76→1，不再用 0.55 的猛弹
        assertEquals(0.76f, GlassDialogMotion.EnterScaleFrom, 1e-4f)
        assertEquals(0.92f, GlassDialogMotion.ExitScaleTo, 1e-4f)
        val travel = 1f - GlassDialogMotion.EnterScaleFrom
        assertTrue(travel in 0.18f..0.32f)
    }

    @Test
    fun appearScaleOvershootsWhenSpringPassesOne() {
        assertEquals(
            GlassDialogMotion.EnterScaleFrom,
            GlassDialogMotion.appearScale(true, 0f),
            1e-4f
        )
        assertEquals(1f, GlassDialogMotion.appearScale(true, 1f), 1e-4f)
        assertTrue(GlassDialogMotion.appearScale(true, 1.15f) > 1f)
        assertTrue(GlassDialogMotion.appearScale(true, 0.9f) < 1f)
        assertEquals(1f, GlassDialogMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassDialogMotion.ExitScaleTo, GlassDialogMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun slideIsSubtleLikeMenu() {
        val center = GlassDialogMotion.appearSlideYFraction(isBottomSheet = false, progress = 0f)
        val sheet = GlassDialogMotion.appearSlideYFraction(isBottomSheet = true, progress = 0f)
        assertTrue(center in 0.04f..0.12f)
        assertTrue(sheet in 0.04f..0.14f)
        assertEquals(
            0f,
            GlassDialogMotion.appearSlideYFraction(isBottomSheet = false, progress = 1f),
            1e-4f
        )
    }

    @Test
    fun fadeIsIndependentAndFasterToClose() {
        assertTrue(GlassDialogMotion.EnterFadeMs in 180..280)
        assertTrue(GlassDialogMotion.ExitFadeMs < GlassDialogMotion.EnterFadeMs)
        assertFalse(GlassDialogMotion.EnterScaleFrom >= 1f)
    }
}
