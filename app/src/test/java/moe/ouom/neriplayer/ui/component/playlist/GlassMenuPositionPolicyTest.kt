package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMenuPositionPolicyTest {
    private val window = IntSize(1080, 2400)
    private val menu = IntSize(400, 600)
    private val reservedBottom = 200

    @Test
    fun prefersOpeningUpwardAboveAnchor() {
        val anchor = IntRect(left = 200, top = 1200, right = 280, bottom = 1280)
        val pos = resolveGlassMenuPosition(
            anchor = anchor,
            windowSize = window,
            popupSize = menu,
            offsetX = 0,
            offsetY = 8,
            reservedBottomPx = reservedBottom,
        )
        // 向上：anchor.top - height - offset
        assertEquals(1200 - 600 - 8, pos.y)
        assertEquals(200, pos.x)
    }

    @Test
    fun downwardWhenNoRoomAboveStillAvoidsMiniPlayer() {
        // 锚点很靠上，向上放不下
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
}
