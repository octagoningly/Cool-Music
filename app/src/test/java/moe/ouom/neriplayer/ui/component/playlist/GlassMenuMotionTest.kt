package moe.ouom.neriplayer.ui.component.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuMotionTest {
    @Test
    fun enterSpringIsUnderdampedForVisibleBounce() {
        // Q 弹：欠阻尼，会过冲并震荡
        assertTrue(GlassMenuMotion.EnterTransformSpring.dampingRatio < 1f)
        assertTrue(GlassMenuMotion.EnterTransformSpring.dampingRatio in 0.35f..0.65f)
        // 收回更稳，但仍略带弹性
        assertTrue(GlassMenuMotion.ExitTransformSpring.dampingRatio < 1f)
        assertTrue(
            GlassMenuMotion.ExitTransformSpring.dampingRatio >
                GlassMenuMotion.EnterTransformSpring.dampingRatio
        )
    }

    @Test
    fun fadeStaysIndependentOfSpringOvershoot() {
        assertTrue(GlassMenuMotion.EnterFadeMs in 120..240)
        assertTrue(GlassMenuMotion.ExitFadeMs < GlassMenuMotion.EnterFadeMs)
    }

    @Test
    fun appearScaleEndpointsMatchRecipe() {
        // 展开起点
        assertEquals(GlassMenuMotion.EnterScaleFrom, GlassMenuMotion.appearScale(true, 0f), 1e-4f)
        assertEquals(1f, GlassMenuMotion.appearScale(true, 1f), 1e-4f)
        // 收回：progress 1→0
        assertEquals(1f, GlassMenuMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassMenuMotion.ExitScaleTo, GlassMenuMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun appearScaleOvershootsPastTargetWhenSpringPassesOne() {
        // 弹簧 progress>1 → scale>1（冲过预定位置）
        val overshoot = GlassMenuMotion.appearScale(true, 1.15f)
        assertTrue("overshoot scale=$overshoot", overshoot > 1f)
        // 回弹不足 progress<1 → scale<1
        val undershoot = GlassMenuMotion.appearScale(true, 0.88f)
        assertTrue("undershoot scale=$undershoot", undershoot < 1f)
    }

    @Test
    fun slideComesFromButtonSide() {
        // 下弹：从上方（负 Y）长出
        assertTrue(GlassMenuMotion.appearSlideYFraction(opensUpward = false, progress = 0f) < 0f)
        assertEquals(0f, GlassMenuMotion.appearSlideYFraction(false, 1f), 1e-4f)
        // 上弹：从下方（正 Y）长出
        assertTrue(GlassMenuMotion.appearSlideYFraction(opensUpward = true, progress = 0f) > 0f)
        assertEquals(0f, GlassMenuMotion.appearSlideYFraction(true, 1f), 1e-4f)
        // 过冲时位移越过 0 到另一侧
        assertTrue(GlassMenuMotion.appearSlideYFraction(false, 1.1f) > 0f)
    }

    @Test
    fun transformOriginFollowsOpenDirection() {
        // 下弹 TopEnd：贴右上角（三点按钮侧）
        assertEquals(1f, GlassMenuMotion.transformOrigin(false).pivotFractionX, 1e-4f)
        assertEquals(0f, GlassMenuMotion.transformOrigin(false).pivotFractionY, 1e-4f)
        // 上弹 BottomEnd：贴右下角
        assertEquals(1f, GlassMenuMotion.transformOrigin(true).pivotFractionX, 1e-4f)
        assertEquals(1f, GlassMenuMotion.transformOrigin(true).pivotFractionY, 1e-4f)
    }

    @Test
    fun scaleTravelIsLargeEnoughToShowBounce() {
        // 行程太短的话弹簧过冲会看不出来
        val travel = 1f - GlassMenuMotion.EnterScaleFrom
        assertTrue("travel=$travel", travel >= 0.18f)
        assertTrue(GlassMenuMotion.ExitScaleTo in 0.85f..0.98f)
        assertTrue(GlassMenuMotion.SlideFraction in 0.05f..0.18f)
        assertFalse(GlassMenuMotion.EnterScaleFrom >= 1f)
    }
}
