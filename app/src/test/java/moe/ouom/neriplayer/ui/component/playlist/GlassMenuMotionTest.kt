package moe.ouom.neriplayer.ui.component.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuMotionTest {
    @Test
    fun enterIsSnappierThanDrawerButStillReadable() {
        assertTrue(GlassMenuMotion.EnterDurationMs in 120..240)
        assertTrue(GlassMenuMotion.ExitDurationMs < GlassMenuMotion.EnterDurationMs)
        assertTrue(GlassMenuMotion.ExitDurationMs in 80..180)
    }

    @Test
    fun appearScaleEndpointsMatchRecipe() {
        // 展开：0.86 → 1
        assertEquals(GlassMenuMotion.EnterScaleFrom, GlassMenuMotion.appearScale(true, 0f), 1e-4f)
        assertEquals(1f, GlassMenuMotion.appearScale(true, 1f), 1e-4f)
        // 收回：1 → 0.92（progress 从 1 到 0）
        assertEquals(1f, GlassMenuMotion.appearScale(false, 1f), 1e-4f)
        assertEquals(GlassMenuMotion.ExitScaleTo, GlassMenuMotion.appearScale(false, 0f), 1e-4f)
    }

    @Test
    fun slideComesFromButtonSide() {
        // 下弹：从上方（负 Y）长出
        assertTrue(GlassMenuMotion.appearSlideYFraction(opensUpward = false, progress = 0f) < 0f)
        assertEquals(0f, GlassMenuMotion.appearSlideYFraction(false, 1f), 1e-4f)
        // 上弹：从下方（正 Y）长出
        assertTrue(GlassMenuMotion.appearSlideYFraction(opensUpward = true, progress = 0f) > 0f)
        assertEquals(0f, GlassMenuMotion.appearSlideYFraction(true, 1f), 1e-4f)
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
    fun scaleFractionStaysBounded() {
        assertTrue(GlassMenuMotion.EnterScaleFrom in 0.7f..0.95f)
        assertTrue(GlassMenuMotion.ExitScaleTo in 0.85f..0.98f)
        assertTrue(GlassMenuMotion.SlideFraction in 0.02f..0.15f)
        assertFalse(GlassMenuMotion.EnterScaleFrom >= 1f)
    }
}
