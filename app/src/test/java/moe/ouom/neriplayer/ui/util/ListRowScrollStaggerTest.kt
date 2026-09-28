package moe.ouom.neriplayer.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRowScrollStaggerTest {
    @Test
    fun `idle or top row stays at layout position`() {
        assertEquals(0f, listRowStaggerTranslationY(0f, rowPhase = 3, maxLagPx = 8f), 0f)
        assertEquals(0f, listRowStaggerTranslationY(20f, rowPhase = 0, maxLagPx = 8f), 0f)
        assertEquals(0f, listRowStaggerTranslationY(20f, rowPhase = -1, maxLagPx = 8f), 0f)
    }

    @Test
    fun `lower rows lag more than upper rows while scrolling`() {
        val upper = listRowStaggerTranslationY(12f, rowPhase = 1, maxLagPx = 32f)
        val lower = listRowStaggerTranslationY(12f, rowPhase = 4, maxLagPx = 32f)
        assertTrue(lower > upper)
        assertTrue(upper > 0f)
    }

    @Test
    fun `phase is capped so long lists do not trail forever`() {
        val capped = listRowStaggerTranslationY(
            scrollLagPx = 12f,
            rowPhase = 99,
            maxLagPx = 32f,
            maxPhase = ListRowScrollStagger.MaxPhase,
        )
        val atCap = listRowStaggerTranslationY(
            scrollLagPx = 12f,
            rowPhase = ListRowScrollStagger.MaxPhase,
            maxLagPx = 32f,
            maxPhase = ListRowScrollStagger.MaxPhase,
        )
        assertEquals(atCap, capped, 0f)
    }

    @Test
    fun `translation is clamped to max lag`() {
        val huge = listRowStaggerTranslationY(
            scrollLagPx = 400f,
            rowPhase = 6,
            maxLagPx = 8f,
        )
        assertEquals(8f, huge, 0f)
        val hugeDown = listRowStaggerTranslationY(
            scrollLagPx = -400f,
            rowPhase = 6,
            maxLagPx = 8f,
        )
        assertEquals(-8f, hugeDown, 0f)
    }

    @Test
    fun `sign follows scroll direction so rows trail correctly`() {
        assertTrue(listRowStaggerTranslationY(15f, 2, 32f) > 0f)
        assertTrue(listRowStaggerTranslationY(-15f, 2, 32f) < 0f)
    }

    @Test
    fun `motion tokens stay in a subtle range`() {
        assertTrue(ListRowScrollStagger.MaxPhase in 3..8)
        assertTrue(ListRowScrollStagger.PhaseScale in 0.05f..0.35f)
        assertTrue(ListRowScrollStagger.VelocitySmoothing in 0.5f..0.9f)
        assertTrue(ListRowScrollStagger.IdleDecay in 0.7f..0.95f)
    }
}
