package moe.ouom.neriplayer.ui.screen.host

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos

internal data class HostScrollPosition(
    val index: Int,
    val offset: Int,
    val key: String? = null
)

internal fun LazyGridState.captureHostScrollPosition(): HostScrollPosition {
    val index = firstVisibleItemIndex
    return HostScrollPosition(
        index = index,
        offset = firstVisibleItemScrollOffset,
        key = layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.key as? String
    )
}

internal fun LazyListState.captureHostScrollPosition(): HostScrollPosition {
    val index = firstVisibleItemIndex
    return HostScrollPosition(
        index = index,
        offset = firstVisibleItemScrollOffset,
        key = layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index }
            ?.key as? String
    )
}

internal suspend fun LazyListState.restoreHostScrollPosition(
    position: HostScrollPosition,
    resolvedIndex: Int? = null
) {
    val requestedIndex = resolvedIndex ?: position.index
    // 等列表先有内容且数量稳定，避免过早 scrollToItem 后又被顶回顶部
    val itemCount = awaitStableHostItemCount(requestedIndex) { layoutInfo.totalItemsCount }
    if (itemCount <= 0) return
    val safeIndex = requestedIndex.coerceAtMost(itemCount - 1)
    scrollToItem(
        index = safeIndex,
        scrollOffset = if (safeIndex == requestedIndex) position.offset else 0
    )
    // 若能对上 key，再校正一次，避免头部项增减导致偏 1～2 项
    val targetKey = position.key
    if (targetKey != null && safeIndex == requestedIndex) {
        withFrameNanos { }
        val visible = layoutInfo.visibleItemsInfo
        val match = visible.firstOrNull { it.key == targetKey }
        if (match != null && match.index != firstVisibleItemIndex) {
            val delta = match.index - firstVisibleItemIndex
            scrollToItem(
                index = firstVisibleItemIndex + delta,
                scrollOffset = firstVisibleItemScrollOffset
            )
        } else if (match == null && visible.isNotEmpty()) {
            // 当前可见里没有目标 key：若目标 key 在上方则上移一格补偿
            val firstKey = visible.first().key
            if (firstKey != targetKey && requestedIndex > 0) {
                // 保持已恢复位置，不强行猜
            }
        }
    }
}

internal suspend fun LazyGridState.restoreHostScrollPosition(
    position: HostScrollPosition,
    resolvedIndex: Int? = null
) {
    val requestedIndex = resolvedIndex ?: position.index
    val itemCount = awaitStableHostItemCount(requestedIndex) { layoutInfo.totalItemsCount }
    if (itemCount <= 0) return
    val safeIndex = requestedIndex.coerceAtMost(itemCount - 1)
    scrollToItem(
        index = safeIndex,
        scrollOffset = if (safeIndex == requestedIndex) position.offset else 0
    )
}

private suspend fun awaitStableHostItemCount(
    requestedIndex: Int,
    itemCount: () -> Int
): Int {
    var count = itemCount()
    var attempts = 0
    var stableFrames = 0
    var last = count
    while (attempts < HOST_SCROLL_RESTORE_MAX_FRAMES) {
        if (count > requestedIndex && stableFrames >= 2) break
        if (count == last && count > 0) {
            stableFrames++
        } else {
            stableFrames = 0
        }
        last = count
        withFrameNanos { }
        count = itemCount()
        attempts++
    }
    return count
}

private const val HOST_SCROLL_RESTORE_MAX_FRAMES = 60
