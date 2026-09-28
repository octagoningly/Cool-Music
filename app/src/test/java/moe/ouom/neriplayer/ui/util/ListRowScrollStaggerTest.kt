package moe.ouom.neriplayer.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListRowScrollStaggerTest {
    @Test
    fun `idle or top row stays at layout position`() {
        assertEquals(0f, listRowStaggerTranslationY(0f, phaseNorm = 0.6f, maxLagPx = 8f), 0f)
        assertEquals(0f, listRowStaggerTranslationY(20f, phaseNorm = 0f, maxLagPx = 8f), 0f)
        assertEquals(0f, listRowStaggerTranslationY(20f, phaseNorm = -1f, maxLagPx = 8f), 0f)
    }

    @Test
    fun `lower rows lag more than upper rows while scrolling`() {
        val upper = listRowStaggerTranslationY(12f, phaseNorm = 0.25f, maxLagPx = 32f)
        val lower = listRowStaggerTranslationY(12f, phaseNorm = 0.9f, maxLagPx = 32f)
        assertTrue(lower > upper)
        assertTrue(upper > 0f)
    }

    @Test
    fun `translation is clamped to scaled max lag`() {
        val limit = 8f * ListRowScrollStagger.DistanceScale
        assertEquals(
            limit,
            listRowStaggerTranslationY(400f, phaseNorm = 1f, maxLagPx = 8f),
            0.01f,
        )
        assertEquals(
            -limit,
            listRowStaggerTranslationY(-400f, phaseNorm = 1f, maxLagPx = 8f),
            0.01f,
        )
    }

    @Test
    fun `distance scale boosts motion beyond base`() {
        assertTrue(ListRowScrollStagger.DistanceScale in 1.4f..3f)
        val base = listRowStaggerTranslationY(12f, phaseNorm = 0.5f, maxLagPx = 100f)
        val raw = 12f * 0.5f
        assertEquals(raw * ListRowScrollStagger.DistanceScale, base, 0.01f)
    }

    @Test
    fun `sign follows scroll direction so rows trail correctly`() {
        assertTrue(listRowStaggerTranslationY(15f, 0.5f, 32f) > 0f)
        assertTrue(listRowStaggerTranslationY(-15f, 0.5f, 32f) < 0f)
    }

    @Test
    fun `continuous scroll position is seamless across item boundary`() {
        // 条目高 100：滚到边界时 (index=2, offset=100) 应等于 (index=3, offset=0)
        val atBoundary = continuousListScrollPosition(2, 100, 100)
        val afterWrap = continuousListScrollPosition(3, 0, 100)
        assertEquals(atBoundary, afterWrap, 0.001f)

        // 半程：offset 走 40/100
        val mid = continuousListScrollPosition(2, 40, 100)
        assertEquals(2.4f, mid, 0.001f)
    }

    @Test
    fun `continuous scroll position handles missing size`() {
        assertEquals(3f, continuousListScrollPosition(3, 50, 0), 0f)
        assertEquals(3f, continuousListScrollPosition(3, 50, -1), 0f)
    }

    @Test
    fun `motion tokens stay in a subtle smooth range`() {
        assertTrue(ListRowScrollStagger.LagTimeSeconds in 0.01f..0.08f)
        assertTrue(ListRowScrollStagger.VelocitySmoothTauSeconds in 0.02f..0.1f)
        assertTrue(ListRowScrollStagger.SettleTauSeconds in 0.03f..0.12f)
        assertTrue(ListRowScrollStagger.MaxPhaseNorm == 1f)
        assertTrue(ListRowScrollStagger.LayoutJumpThresholdPx in 48f..160f)
    }

    @Test
    fun `settle time constant returns to zero in reasonable frames`() {
        // 约 3τ 应衰减到 5% 以下：e^(-3) ≈ 0.05
        val frames60Hz = (3f * ListRowScrollStagger.SettleTauSeconds * 60f).toInt()
        assertTrue(frames60Hz in 4..40)
    }
}
