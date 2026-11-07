package moe.ouom.neriplayer.ui.component.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlassMenuScrollbarTest {
    @Test
    fun `no scrollbar is drawn when the menu does not overflow`() {
        assertNull(
            resolveGlassMenuScrollbarMetrics(
                viewportHeightPx = 360,
                scrollRangePx = 0,
                scrollValuePx = 0,
            )
        )
    }

    @Test
    fun `scrollbar thumb reaches the bottom at the end of a long menu`() {
        val metrics = requireNotNull(
            resolveGlassMenuScrollbarMetrics(
                viewportHeightPx = 300,
                scrollRangePx = 900,
                scrollValuePx = 900,
            )
        )
        assertEquals(0.25f, metrics.thumbFraction, 0.0001f)
        assertEquals(0.75f, metrics.offsetFraction, 0.0001f)
    }
}
