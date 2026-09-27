package moe.ouom.neriplayer.ui.component.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuMotionTest {
    @Test
    fun enterSpringIsUnderdampedForVisibleBounce() {
        assertTrue(GlassMenuMotion.EnterTransformSpring.dampingRatio < 1f)
        assertTrue(GlassMenuMotion.EnterTransformSpring.dampingRatio in 0.35f..0.65f)
        assertTrue(GlassMenuMotion.ExitTransformSpring.dampingRatio < 1f)
        assertTrue(
            GlassMenuMotion.ExitTransformSpring.dampingRatio >
                GlassMenuMotion.EnterTransformSpring.dampingRatio
        )
        // 抖动时长约 1.3 倍：stiffness ≈ 1500 / 1.3²
        assertTrue(GlassMenuMotion.EnterTransformSpring.stiffness in 800f..980f)
    }

    @Test
    fun fadeStaysIndependentOfSpringOvershoot() {
        assertTrue(GlassMenuMotion.EnterFadeMs in 180..280)
        assertTrue(GlassMenuMotion.ExitFadeMs < GlassMenuMotion.EnterFadeMs)
    }

    @Test
    fun blurBoundsShrinksWithMenuSoTheyVanishTogether() {
        val base = androidx.compose.ui.geometry.Rect(100f, 200f, 500f, 700f)
        val full = GlassMenuMotion.blurBounds(
            base = base,
            progress = 1f,
            expanding = true,
            opensUpward = false,
            menuLeftOfAnchor = true,
        )
        assertEquals(100f, full.left, 1f)
        assertEquals(200f, full.top, 1f)
        assertEquals(500f, full.right, 1f)
        assertEquals(700f, full.bottom, 1f)

        val closing = GlassMenuMotion.blurBounds(
            base = base,
            progress = 0.5f,
            expanding = false,
            opensUpward = false,
            menuLeftOfAnchor = true,
        )
        // 绕右上角收缩，且比原区域小
        assertTrue(closing.width < base.width)
        assertTrue(closing.right == 500f || kotlin.math.abs(closing.right - 500f) < 1f)

        val gone = GlassMenuMotion.blurBounds(
            base = base,
            progress = 0f,
            expanding = false,
            opensUpward = false,
            menuLeftOfAnchor = true,
        )
        // progress=0 时模糊区域必须为空，与内容同时消失
        assertTrue(gone.width <= 0f)
    }

    @Test
    fun appearScaleEndpointsMatchRecipe() {
        assertEquals(GlassMenuMotion.EnterScaleFrom, GlassMenuMotion.appearScale(true, 0f), 1e-4f)
        assertEquals(1f, GlassMenuMotion.appearScale(true, 1f), 1e-4f)
        assertEquals(1f, GlassMenuMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassMenuMotion.ExitScaleTo, GlassMenuMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun appearScaleOvershootsPastTargetWhenSpringPassesOne() {
        val overshoot = GlassMenuMotion.appearScale(true, 1.15f)
        assertTrue("overshoot scale=$overshoot", overshoot > 1f)
        val undershoot = GlassMenuMotion.appearScale(true, 0.88f)
        assertTrue("undershoot scale=$undershoot", undershoot < 1f)
    }

    @Test
    fun slideComesFromSideAwayFromMoreButton() {
        // 下弹 + 菜单在按钮左侧：从更左、更下靠拢（不经过 ⋮）
        val enter = GlassMenuMotion.appearSlideFractions(
            opensUpward = false,
            menuLeftOfAnchor = true,
            progress = 0f,
        )
        assertTrue("dx=${enter.x} should be < 0", enter.x < 0f)
        assertTrue("dy=${enter.y} should be > 0", enter.y > 0f)

        // 上弹 + 左侧：从更左、更上靠拢
        val up = GlassMenuMotion.appearSlideFractions(
            opensUpward = true,
            menuLeftOfAnchor = true,
            progress = 0f,
        )
        assertTrue(up.x < 0f)
        assertTrue(up.y < 0f)

        // 落位后位移为 0
        val settled = GlassMenuMotion.appearSlideFractions(false, true, 1f)
        assertEquals(0f, settled.x, 1e-4f)
        assertEquals(0f, settled.y, 1e-4f)
    }

    @Test
    fun transformOriginFacesTheMoreButton() {
        // 菜单在按钮左侧 → 右缘朝向按钮
        val left = GlassMenuMotion.transformOrigin(opensUpward = false, menuLeftOfAnchor = true)
        assertEquals(1f, left.pivotFractionX, 1e-4f)
        assertEquals(0f, left.pivotFractionY, 1e-4f)
        // 菜单在按钮右侧 → 左缘朝向按钮
        val right = GlassMenuMotion.transformOrigin(opensUpward = true, menuLeftOfAnchor = false)
        assertEquals(0f, right.pivotFractionX, 1e-4f)
        assertEquals(1f, right.pivotFractionY, 1e-4f)
    }

    @Test
    fun scaleTravelIsLargeEnoughToShowBounce() {
        val travel = 1f - GlassMenuMotion.EnterScaleFrom
        assertTrue("travel=$travel", travel >= 0.18f)
        assertTrue(GlassMenuMotion.ExitScaleTo in 0.85f..0.98f)
        assertTrue(GlassMenuMotion.SlideFraction in 0.02f..0.15f)
        assertFalse(GlassMenuMotion.EnterScaleFrom >= 1f)
    }
}
