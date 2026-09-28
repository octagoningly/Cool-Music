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
        assertTrue(ListRowScrollStagger.DistanceScale in 3f..4.5f)
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
    fun `viewport phase is continuous and does not flicker at edges`() {
        // 贴边/回收时不能再出现 0↔1 跳变；用连续滚动位置算相位
        val phaseTop = listRowViewportPhase(rowIndex = 10, continuousScrollItems = 10f, visibleSpanItems = 6f)
        val phaseJustBelow = listRowViewportPhase(rowIndex = 11, continuousScrollItems = 10.2f, visibleSpanItems = 6f)
        assertTrue(phaseTop < 0.2f)
        assertTrue(phaseJustBelow < 0.5f)
        assertTrue(phaseJustBelow > phaseTop)

        val phaseFar = listRowViewportPhase(rowIndex = 16, continuousScrollItems = 10f, visibleSpanItems = 6f)
        assertEquals(1f, phaseFar, 0.01f)

        // 滚出上方的行相位应为 0，而不是突然跳到中间
        val phaseAbove = listRowViewportPhase(rowIndex = 8, continuousScrollItems = 10f, visibleSpanItems = 6f)
        assertEquals(0f, phaseAbove, 0f)
    }

    @Test
    fun `pixel phase tracks item offset continuously without teleport`() {
        assertEquals(0f, listRowPhaseFromViewportOffset(0f, 100f), 0f)
        assertEquals(0.5f, listRowPhaseFromViewportOffset(50f, 100f), 0.001f)
        assertEquals(1f, listRowPhaseFromViewportOffset(150f, 100f), 0.001f)
        assertEquals(0f, listRowPhaseFromViewportOffset(-20f, 100f), 0f)
        assertEquals(0f, listRowPhaseFromViewportOffset(50f, 0f), 0f)

        // 每 1px 只应有小步变化（旧 index 相位在换行高时会整段跳）
        var prev = listRowPhaseFromViewportOffset(0f, 200f)
        for (px in 1..200) {
            val next = listRowPhaseFromViewportOffset(px.toFloat(), 200f)
            assertTrue(next - prev < 0.01f)
            prev = next
        }
    }

    @Test
    fun `phase lookup prefers item key so headers do not shift rows`() {
        val items = listOf(
            LazyItemOffsetInfo(index = 0, key = "header", offset = 0),
            LazyItemOffsetInfo(index = 1, key = "pad", offset = 80),
            LazyItemOffsetInfo(index = 2, key = "row-a", offset = 200),
            LazyItemOffsetInfo(index = 3, key = "row-b", offset = 280),
        )
        // 视口高 400，相位跨度 = 400 * 0.72 = 288
        val phaseA = listRowPhaseForItem(
            visibleItems = items,
            itemKey = "row-a",
            itemIndex = -1,
            viewportStartOffset = 0,
            viewportEndOffset = 400,
        )
        val phaseByIndex = listRowPhaseForItem(
            visibleItems = items,
            itemKey = null,
            itemIndex = 2,
            viewportStartOffset = 0,
            viewportEndOffset = 400,
        )
        assertEquals(phaseA, phaseByIndex, 0.001f)
        assertTrue(phaseA > 0.5f)

        // 找不到 key 时返回 0，而不是用错误下标顶替
        assertEquals(
            0f,
            listRowPhaseForItem(
                visibleItems = items,
                itemKey = "missing",
                itemIndex = -1,
                viewportStartOffset = 0,
                viewportEndOffset = 400,
            ),
            0f,
        )
    }

    @Test
    fun `motion tokens stay in a subtle smooth range`() {
        assertTrue(ListRowScrollStagger.LagTimeSeconds in 0.01f..0.08f)
        assertTrue(ListRowScrollStagger.VelocitySmoothTauSeconds in 0.02f..0.12f)
        assertTrue(ListRowScrollStagger.SettleTauSeconds in 0.03f..0.15f)
        assertTrue(ListRowScrollStagger.MaxPhaseNorm == 1f)
        assertTrue(ListRowScrollStagger.LayoutJumpThresholdPx in 48f..200f)
        assertTrue(ListRowScrollStagger.PhaseSpanItems in 3f..10f)
        assertTrue(ListRowScrollStagger.PhaseSpanViewportFraction in 0.4f..1f)
    }

    @Test
    fun `settle time constant returns to zero in reasonable frames`() {
        // 约 3τ 应衰减到 5% 以下：e^(-3) ≈ 0.05
        val frames60Hz = (3f * ListRowScrollStagger.SettleTauSeconds * 60f).toInt()
        assertTrue(frames60Hz in 4..40)
    }
}
