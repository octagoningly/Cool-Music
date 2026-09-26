package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuPositionPolicyTest {
    private val window = IntSize(1080, 2400)
    private val menu = IntSize(400, 600)
    private val reservedBottom = 200

    private fun pos(
        anchor: IntRect,
        menuSize: IntSize = menu,
        reserved: Int = reservedBottom,
        offsetY: Int = 8,
    ): IntOffset = resolveGlassMenuPosition(
        anchor = anchor,
        windowSize = window,
        popupSize = menuSize,
        offsetX = 0,
        offsetY = offsetY,
        reservedBottomPx = reserved,
    )

    @Test
    fun songMoreVertPrefersLeftOfButtonSoDotsStayVisible() {
        // 歌曲行右侧 ⋮：约 x=980..1030
        val anchor = IntRect(left = 980, top = 1200, right = 1030, bottom = 1250)
        val p = pos(anchor)
        // 弹窗整体在按钮左侧，不挡住 ⋮
        assertTrue("menu right=${p.x + menu.width} should be <= anchor.left", p.x + menu.width <= anchor.left)
        assertFalse(glassMenuLeftOfAnchor(p, menu, anchor).not())
        assertTrue(glassMenuLeftOfAnchor(p, menu, anchor))
        // 也不与按钮相交
        assertFalse(
            p.x < anchor.right && p.x + menu.width > anchor.left &&
                p.y < anchor.bottom && p.y + menu.height > anchor.top
        )
    }

    @Test
    fun midScreenFallsBackToRightAlignBelowWhenLeftFits() {
        // 居中偏左的锚点：左侧放 400 宽仍可
        val anchor = IntRect(left = 200, top = 1200, right = 280, bottom = 1280)
        val p = pos(anchor)
        // 优先左侧：x = 200 - 400 - 12 = -212 放不下 → 试右侧 x=280+12=292
        assertEquals(280 + 12, p.x)
        assertEquals(1280 + 12, p.y)
        assertFalse(glassMenuLeftOfAnchor(p, menu, anchor))
    }

    @Test
    fun flipsUpwardWhenRoomBelowIsTight() {
        val anchor = IntRect(left = 980, top = 2020, right = 1030, bottom = 2100)
        val p = pos(anchor)
        // 上弹时也在按钮左侧
        assertTrue(p.x + menu.width <= anchor.left)
        assertTrue(glassMenuOpensUpward(p, anchor, menu))
    }

    @Test
    fun neverOverlapsAnchorEvenWhenClamped() {
        val anchor = IntRect(left = 500, top = 1100, right = 560, bottom = 1160)
        val p = pos(anchor)
        val overlaps =
            p.x < anchor.right && p.x + menu.width > anchor.left &&
                p.y < anchor.bottom && p.y + menu.height > anchor.top
        assertFalse("menu $p overlaps anchor $anchor", overlaps)
    }

    @Test
    fun neverOverlapsReservedBottomEvenWhenAnchorIsLow() {
        val anchor = IntRect(left = 980, top = 2000, right = 1030, bottom = 2080)
        val p = pos(anchor)
        val maxBottom = window.height - reservedBottom
        assertTrue(
            "menu bottom ${p.y + menu.height} must be <= $maxBottom",
            p.y + menu.height <= maxBottom
        )
    }

    @Test
    fun menuBoundsMatchPosition() {
        val p = IntOffset(30, 40)
        val bounds = glassMenuBoundsInMainWindow(p, menu)
        assertEquals(30f, bounds.left)
        assertEquals(40f, bounds.top)
        assertEquals(430f, bounds.right)
        assertEquals(640f, bounds.bottom)
    }

    @Test
    fun opensUpwardWhenMenuSitsAboveAnchor() {
        val anchor = IntRect(left = 200, top = 1200, right = 280, bottom = 1280)
        val above = IntOffset(200, 592)
        assertTrue(glassMenuOpensUpward(above, anchor, menu))
        val below = IntOffset(200, 1288)
        assertFalse(glassMenuOpensUpward(below, anchor, menu))
    }
}
