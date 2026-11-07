package moe.ouom.neriplayer.ui.screen.tab

import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreScreenLayoutPolicyTest {
    @Test
    fun filterLayerVisibleUsesSmallestTopPadding() {
        assertEquals(
            12,
            resolveExploreListContentTopDp(
                showTypeFilterLayer = true,
                showExploreSearchField = true
            )
        )
    }

    @Test
    fun searchFieldVisibleWithoutFilterLayerUsesFullChromePadding() {
        assertEquals(
            116,
            resolveExploreListContentTopDp(
                showTypeFilterLayer = false,
                showExploreSearchField = true
            )
        )
    }

    @Test
    fun collapsedChromeUsesReducedPadding() {
        assertEquals(
            56,
            resolveExploreListContentTopDp(
                showTypeFilterLayer = false,
                showExploreSearchField = false
            )
        )
    }

    @Test
    fun filterLayerWinsOverSearchFieldState() {
        // 两个状态都为 true 时必须以筛选行为准，否则列表留白与筛选行高度会叠加、网格被推挤。
        assertEquals(
            resolveExploreListContentTopDp(true, true),
            resolveExploreListContentTopDp(true, false)
        )
    }
}
