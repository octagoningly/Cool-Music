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

    @Test
    fun opensDownwardWhenRoomBelow() {
        val anchor = IntRect(left = 200, top = 1200, right = 280, bottom = 1280)
        val pos = resolveGlassMenuPosition(
            anchor = anchor,
            windowSize = window,
            popupSize = menu,
            offsetX = 0,
            offsetY = 8,
            reservedBottomPx = reservedBottom,
        )
        // 优先向下：贴锚点底 + offset
        assertEquals(1280 + 8, pos.y)
        assertEquals(200, pos.x)
        assertFalse(glassMenuOpensUpward(pos, anchor, menu))
    }

    @Test
    fun flipsUpwardWhenRoomBelowIsTight() {
        // 下方几乎放不下：roomBelow < min(h/4, 96)
        val anchor = IntRect(left = 100, top = 2020, right = 180, bottom = 2100)
        val pos = resolveGlassMenuPosition(
            anchor = anchor,
            windowSize = window,
            popupSize = menu,
            offsetX = 0,
            offsetY = 8,
            reservedBottomPx = reservedBottom,
        )
        // 上弹：anchor.top - height - offset - 72（避让 Dock）
        assertEquals(2020 - 600 - 8 - 72, pos.y)
        assertTrue(glassMenuOpensUpward(pos, anchor, menu))
    }

    @Test
    fun downwardWhenNoRoomAboveStillAvoidsMiniPlayer() {
        // 锚点很靠上，空间充足优先向下
        val anchor = IntRect(left = 100, top = 40, right = 180, bottom = 120)
        val pos = resolveGlassMenuPosition(
            anchor = anchor,
            windowSize = window,
            popupSize = menu,
            offsetX = 0,
            offsetY = 8,
            reservedBottomPx = reservedBottom,
        )
        val maxBottom = window.height - reservedBottom
        assertTrue(pos.y + menu.height <= maxBottom)
        assertTrue(pos.y >= 8)
        // 向下时贴着锚点底
        assertEquals(120 + 8, pos.y)
    }

    @Test
    fun neverOverlapsReservedBottomEvenWhenAnchorIsLow() {
        val anchor = IntRect(left = 100, top = 2000, right = 180, bottom = 2080)
        val pos = resolveGlassMenuPosition(
            anchor = anchor,
            windowSize = window,
            popupSize = menu,
            offsetX = 0,
            offsetY = 8,
            reservedBottomPx = reservedBottom,
        )
        val maxBottom = window.height - reservedBottom
        assertTrue(
            "menu bottom ${pos.y + menu.height} must be <= $maxBottom",
            pos.y + menu.height <= maxBottom
        )
    }

    @Test
    fun menuBoundsMatchPosition() {
        val pos = IntOffset(30, 40)
        val bounds = glassMenuBoundsInMainWindow(pos, menu)
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
