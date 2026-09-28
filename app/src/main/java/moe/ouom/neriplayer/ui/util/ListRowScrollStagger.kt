package moe.ouom.neriplayer.ui.util

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.util.ListRowScrollStagger
 * Updated: 2026/3/23
 */

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp

/**
 * 竖向列表/网格「行与行」滚动交错：滚动/惯性过程中下行相对上行有微小滞后。
 * 静止时 translationY = 0，与布局完全对齐；只改绘制，不改测量与热区。
 *
 * 关键手感（对齐 RecyclerView 视差常见做法）：
 * - **相位用条目在视口里的像素 offset**，不用 `index - firstVisible` 查表
 *   （带 header / 行高不一致 / 回收重组时 index 数学会跳，表现为某行突然瞬移）
 * - 按 **item key** 在 `visibleItemsInfo` 里定位自己，与 items() 下标无关
 * - 滚动位置用 `index + offset/size` 连续化算速度，避免跨条目时速度跳变
 * - 速度按帧间隔归一，60/120Hz 手感一致；松手后按时间常数收束到 0
 */
object ListRowScrollStagger {
    /** 基准滞后时间（秒）。translationY ≈ 速度 × 该时间 × 视口相位 */
    const val LagTimeSeconds = 0.028f

    /** 速度低通时间常数（秒），越大越钝、越稳 */
    const val VelocitySmoothTauSeconds = 0.055f

    /** 静止收束时间常数（秒），松手后大约 3τ 内贴回布局 */
    const val SettleTauSeconds = 0.070f

    /** 低于该滞后（px）直接归零，避免亚像素抖动 */
    const val EpsilonPx = 0.12f

    /** 视口相位上限（0 = 顶栏跟手，1 = 底行最粘），保留常量便于调参/测试 */
    const val MaxPhaseNorm = 1f

    /** 运动距离整体缩放（含上限），调「动感强弱」只改这一处 */
    const val DistanceScale = 2.5f

    /** 默认最大错位（dp），调用处可覆盖 */
    val DefaultMaxLag: Dp = 7.dp

    /**
     * 单帧位移超过该值视为布局跳变（顶栏收展补偿等），不是用户滚动。
     * 普通甩动很少一帧超过 ~60px；80dp 顶栏补偿在 2.75x 屏约 200px。
     */
    const val LayoutJumpThresholdPx = 120f

    /** 相位跨度（条目数）：仅作无 layoutInfo 时的回退，正常走像素相位 */
    const val PhaseSpanItems = 5.5f

    /**
     * 像素相位在视口高度的多少比例内从 0 走到 1。
     * ≈ 旧参数 5.5 行 / 约 8 可见行。
     */
    const val PhaseSpanViewportFraction = 0.72f
}

/** 行级滚动交错的滞后状态（整列/整网格共用一份） */
@Stable
class ListScrollLagState internal constructor() {
    /**
     * 全相位基准滞后（px）。行内再乘视口相位 (0..1)。
     * 只在 graphicsLayer 里读取，避免每帧重组。
     */
    var lagPx: Float
        get() = _lagPx.floatValue
        internal set(value) {
            _lagPx.floatValue = value
        }

    private val _lagPx = mutableFloatStateOf(0f)

    /** 顶栏收展等布局跳变时衰减，避免把补偿滚动当成用户甩动 */
    fun cancelLag() {
        _lagPx.floatValue = 0f
    }
}

/**
 * 预绑定 listState / lag / 开关后，按条目身份生成 Modifier。
 * 相位从 `visibleItemsInfo` 的像素 offset 取，与 items() 下标、header 数量无关。
 */
@Stable
class ListRowStaggerScope internal constructor(
    private val lagState: ListScrollLagState,
    private val maxLagPx: Float,
    private val enabled: Boolean,
    private val phaseLookup: (itemKey: Any?, itemIndex: Int) -> Float,
) {
    /** 优先用 item key（稳定，不怕 header/插入）；无 key 时用 Lazy 下标 */
    fun modifier(itemKey: Any? = null, itemIndex: Int = -1): Modifier {
        if (!enabled) return Modifier
        return Modifier.graphicsLayer {
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = phaseLookup(itemKey, itemIndex),
                maxLagPx = maxLagPx,
            )
        }
    }

    /** 兼容旧调用：传 Lazy 列表下标 */
    fun modifier(rowIndex: Int): Modifier = modifier(itemKey = null, itemIndex = rowIndex)

    fun cancelLag() {
        lagState.cancelLag()
    }
}

/**
 * 顶栏/搜索框收起展开时补偿列表滚动，避免 contentPadding 变化把列表顶跳。
 *
 * @param deltaTopPx 新 top padding − 旧 top padding
 */
suspend fun compensateListContentTopScroll(
    listState: LazyListState,
    deltaTopPx: Float,
    stagger: ListRowStaggerScope? = null,
) {
    if (deltaTopPx == 0f) return
    stagger?.cancelLag()
    listState.scrollBy(deltaTopPx)
}

suspend fun compensateGridContentTopScroll(
    gridState: LazyGridState,
    deltaTopPx: Float,
    stagger: ListRowStaggerScope? = null,
) {
    if (deltaTopPx == 0f) return
    stagger?.cancelLag()
    gridState.scrollBy(deltaTopPx)
}

/**
 * 连续滚动位置（单位：条目）。跨条目时与 `offset/size` 对齐，不会跳。
 * 只用于速度估计；相位请用 [listRowPhaseFromViewportOffset]。
 */
fun continuousListScrollPosition(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
): Float {
    if (firstVisibleItemSize <= 0) return firstVisibleItemIndex.toFloat()
    return firstVisibleItemIndex + firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize
}

/**
 * 行间交错位移（px）。
 *
 * @param scrollLagPx 全相位基准滞后（见 [ListScrollLagState.lagPx]）
 * @param phaseNorm 视口相位 0..1（0 = 跟手，1 = 最粘）
 * @param maxLagPx 错位上限（缩放前），防止快甩时「散架」
 */
fun listRowStaggerTranslationY(
    scrollLagPx: Float,
    phaseNorm: Float,
    maxLagPx: Float,
): Float {
    if (scrollLagPx == 0f || phaseNorm <= 0f || maxLagPx <= 0f) return 0f
    val phase = phaseNorm.coerceIn(0f, ListRowScrollStagger.MaxPhaseNorm)
    val scale = ListRowScrollStagger.DistanceScale
    val raw = scrollLagPx * phase * scale
    val limit = maxLagPx * scale
    return raw.coerceIn(-limit, limit)
}

/**
 * 像素相位：条目顶边在视口内的位置 / 跨度。
 * 条目每移动 1px，相位只变 1px/span —— 连续，不会因回收/换行高瞬移。
 */
fun listRowPhaseFromViewportOffset(
    itemOffsetPx: Float,
    spanPx: Float,
): Float {
    if (spanPx <= 0f) return 0f
    return (itemOffsetPx / spanPx).coerceIn(0f, ListRowScrollStagger.MaxPhaseNorm)
}

/**
 * 按条目身份从 layoutInfo 取视口相位。
 * 找不到（未可见）返回 0；该行此时也不参与绘制，translation 无意义。
 */
fun listRowPhaseForItem(
    visibleItems: List<LazyItemOffsetInfo>,
    itemKey: Any?,
    itemIndex: Int,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Float {
    val info = visibleItems.firstOrNull { info ->
        when {
            itemKey != null -> info.key == itemKey
            itemIndex >= 0 -> info.index == itemIndex
            else -> false
        }
    } ?: return 0f
    val spanPx = (viewportEndOffset - viewportStartOffset).toFloat() *
        ListRowScrollStagger.PhaseSpanViewportFraction
    return listRowPhaseFromViewportOffset(
        itemOffsetPx = (info.offset - viewportStartOffset).toFloat(),
        spanPx = spanPx,
    )
}

/** 抽象 Lazy item 位置，便于单测，也兼容 list / grid */
data class LazyItemOffsetInfo(
    val index: Int,
    val key: Any?,
    val offset: Int,
)

fun listRowPhaseForItem(
    listState: LazyListState,
    itemKey: Any? = null,
    itemIndex: Int = -1,
): Float {
    val layout = listState.layoutInfo
    return listRowPhaseForItem(
        visibleItems = layout.visibleItemsInfo.map {
            LazyItemOffsetInfo(index = it.index, key = it.key, offset = it.offset)
        },
        itemKey = itemKey,
        itemIndex = itemIndex,
        viewportStartOffset = layout.viewportStartOffset,
        viewportEndOffset = layout.viewportEndOffset,
    )
}

fun listRowPhaseForItem(
    gridState: LazyGridState,
    itemKey: Any? = null,
    itemIndex: Int = -1,
): Float {
    val layout = gridState.layoutInfo
    return listRowPhaseForItem(
        visibleItems = layout.visibleItemsInfo.map {
            LazyItemOffsetInfo(index = it.index, key = it.key, offset = it.offset.y)
        },
        itemKey = itemKey,
        itemIndex = itemIndex,
        viewportStartOffset = layout.viewportStartOffset,
        viewportEndOffset = layout.viewportEndOffset,
    )
}

/**
 * 旧「下标相位」：仅作无 layoutInfo 时的回退。带头部/变高列表会跳，勿作主路径。
 */
fun listRowViewportPhase(
    rowIndex: Int,
    continuousScrollItems: Float,
    visibleSpanItems: Float = ListRowScrollStagger.PhaseSpanItems,
): Float {
    if (visibleSpanItems <= 0f) return 0f
    val relative = rowIndex - continuousScrollItems
    return (relative / visibleSpanItems).coerceIn(0f, ListRowScrollStagger.MaxPhaseNorm)
}

private fun updateLagForFrame(
    state: ListScrollLagState,
    listStateScrolling: Boolean,
    deltaItems: Float,
    refSizePx: Float,
    dt: Float,
) {
    val deltaPx = deltaItems * refSizePx
    if (abs(deltaPx) >= ListRowScrollStagger.LayoutJumpThresholdPx) {
        // 顶栏收展/补偿滚动：不要喂进速度，也不要硬清零（硬清零会让行突然弹回）
        state.lagPx *= 0.35f
        if (abs(state.lagPx) < ListRowScrollStagger.EpsilonPx) state.lagPx = 0f
        return
    }
    val rawVelocityPx = deltaItems / dt * refSizePx
    if (listStateScrolling) {
        val targetLag = rawVelocityPx * ListRowScrollStagger.LagTimeSeconds
        val alpha = 1f - exp(-dt / ListRowScrollStagger.VelocitySmoothTauSeconds)
        val lag = state.lagPx + (targetLag - state.lagPx) * alpha
        state.lagPx = if (abs(lag) < ListRowScrollStagger.EpsilonPx) 0f else lag
    } else {
        val decay = exp(-dt / ListRowScrollStagger.SettleTauSeconds)
        val settled = state.lagPx * decay
        state.lagPx = if (abs(settled) < ListRowScrollStagger.EpsilonPx) 0f else settled
    }
}

@Composable
fun rememberListScrollLagState(listState: LazyListState): ListScrollLagState {
    val state = remember(listState) { ListScrollLagState() }
    LaunchedEffect(listState) {
        var lastNanos = 0L
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        val firstSize = first?.size?.takeIf { it > 0 }
        var lastPosition = if (firstSize != null) {
            continuousListScrollPosition(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = firstSize,
            )
        } else {
            listState.firstVisibleItemIndex.toFloat()
        }
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.0005f, 0.05f)
                lastNanos = nanos
                val visibleFirst = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val sizePx = visibleFirst?.size?.takeIf { it > 0 }
                if (sizePx == null) {
                    // 空 layout / 尺寸未就绪：不要把 offset/1 当位移（会让相位整列跳一下）
                    return@withFrameNanos
                }
                val position = continuousListScrollPosition(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = sizePx,
                )
                val deltaItems = position - lastPosition
                lastPosition = position
                updateLagForFrame(
                    state = state,
                    listStateScrolling = listState.isScrollInProgress,
                    deltaItems = deltaItems,
                    refSizePx = sizePx.toFloat(),
                    dt = dt,
                )
            }
        }
    }
    return state
}

@Composable
fun rememberListScrollLagState(gridState: LazyGridState): ListScrollLagState {
    val state = remember(gridState) { ListScrollLagState() }
    LaunchedEffect(gridState) {
        var lastNanos = 0L
        val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
        val firstSize = first?.size?.height?.takeIf { it > 0 }
        var lastPosition = if (firstSize != null) {
            continuousListScrollPosition(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = firstSize,
            )
        } else {
            gridState.firstVisibleItemIndex.toFloat()
        }
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.0005f, 0.05f)
                lastNanos = nanos
                val visibleFirst = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                val sizePx = visibleFirst?.size?.height?.takeIf { it > 0 }
                if (sizePx == null) return@withFrameNanos
                val position = continuousListScrollPosition(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = sizePx,
                )
                val deltaItems = position - lastPosition
                lastPosition = position
                updateLagForFrame(
                    state = state,
                    listStateScrolling = gridState.isScrollInProgress,
                    deltaItems = deltaItems,
                    refSizePx = sizePx.toFloat(),
                    dt = dt,
                )
            }
        }
    }
    return state
}

@Composable
fun rememberListRowStagger(
    listState: LazyListState,
    enabled: Boolean,
    maxLag: Dp = ListRowScrollStagger.DefaultMaxLag,
    rowStride: Int = 1,
): ListRowStaggerScope {
    val lagState = rememberListScrollLagState(listState)
    val maxLagPx = with(LocalDensity.current) { maxLag.toPx() }
    // 相位走像素 offset，同一网格行的 offset.y 相同，stride 不再参与相位
    return remember(lagState, maxLagPx, enabled, listState) {
        ListRowStaggerScope(
            lagState = lagState,
            maxLagPx = maxLagPx,
            enabled = enabled,
            phaseLookup = { itemKey, itemIndex ->
                listRowPhaseForItem(listState, itemKey = itemKey, itemIndex = itemIndex)
            },
        )
    }
}

@Composable
fun rememberListRowStagger(
    gridState: LazyGridState,
    enabled: Boolean,
    maxLag: Dp = ListRowScrollStagger.DefaultMaxLag,
    rowStride: Int = 1,
): ListRowStaggerScope {
    val lagState = rememberListScrollLagState(gridState)
    val maxLagPx = with(LocalDensity.current) { maxLag.toPx() }
    return remember(lagState, maxLagPx, enabled, gridState) {
        ListRowStaggerScope(
            lagState = lagState,
            maxLagPx = maxLagPx,
            enabled = enabled,
            phaseLookup = { itemKey, itemIndex ->
                listRowPhaseForItem(gridState, itemKey = itemKey, itemIndex = itemIndex)
            },
        )
    }
}

/**
 * 行级滚动交错：下行更粘（视口像素相位），快滚有拖尾，静止归位。
 */
fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    rowIndex: Int,
    listState: LazyListState,
    maxLagPx: Float,
    enabled: Boolean = true,
    rowStride: Int = 1,
): Modifier = listRowScrollStagger(
    lagState = lagState,
    itemKey = null,
    itemIndex = rowIndex,
    listState = listState,
    maxLagPx = maxLagPx,
    enabled = enabled,
)

fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    itemKey: Any?,
    itemIndex: Int = -1,
    listState: LazyListState,
    maxLagPx: Float,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.graphicsLayer {
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = listRowPhaseForItem(
                    listState = listState,
                    itemKey = itemKey,
                    itemIndex = itemIndex,
                ),
                maxLagPx = maxLagPx,
            )
        }
    )
}

fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    rowIndex: Int,
    gridState: LazyGridState,
    maxLagPx: Float,
    enabled: Boolean = true,
    rowStride: Int = 1,
): Modifier = listRowScrollStagger(
    lagState = lagState,
    itemKey = null,
    itemIndex = rowIndex,
    gridState = gridState,
    maxLagPx = maxLagPx,
    enabled = enabled,
)

fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    itemKey: Any?,
    itemIndex: Int = -1,
    gridState: LazyGridState,
    maxLagPx: Float,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.graphicsLayer {
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = listRowPhaseForItem(
                    gridState = gridState,
                    itemKey = itemKey,
                    itemIndex = itemIndex,
                ),
                maxLagPx = maxLagPx,
            )
        }
    )
}
