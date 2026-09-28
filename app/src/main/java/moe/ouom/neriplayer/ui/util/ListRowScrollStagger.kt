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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp

/**
 * 竖向列表/网格「行与行」滚动交错：下行相对上行滞后一点，静止完全归位。
 *
 * 实现要点（对齐 RecyclerView 视差 / UIScrollView 减速跟手的做法）：
 * 1. **速度来自 NestedScrollConnection 的真实像素增量**（`consumed.y`），
 *    不再用 `firstVisibleItemIndex + offset/size` 猜速度（跨条目/换行高会抖）。
 * 2. **相位来自条目在视口里的像素 offset**（`LazyListItemInfo.offset`），按 key 定位。
 * 3. **平移用 `drawWithContent` 画布 translate**，不开每行 graphicsLayer，
 *    避免与 ReorderableItem / animateItem 抢图层导致「抽一下」。
 * 4. **滞后用一阶指数收敛**追目标（不过冲）：滚动时下行拖尾，停稳单向贴回，
 *    不会出现「先窜过头再弹回来」；布局跳变时冻结而不是硬砍。
 */
object ListRowScrollStagger {
    /** 基准滞后时间（秒）。translationY ≈ 速度 × 该时间 × 视口相位 */
    const val LagTimeSeconds = 0.028f

    /** 静止收束时间常数（秒）。略长一点，松手归位更柔 */
    const val SettleTauSeconds = 0.085f

    /** 低于该滞后（px）直接归零，避免亚像素抖动 */
    const val EpsilonPx = 0.12f

    /** 视口相位上限 */
    const val MaxPhaseNorm = 1f

    /** 运动距离整体缩放（含上限）。2.5 × 1.5（用户要求幅度加大 1.5 倍） */
    const val DistanceScale = 3.75f

    /** 默认最大错位（dp） */
    val DefaultMaxLag: Dp = 7.dp

    /**
     * 单帧位移超过该值视为布局跳变/程序滚动（顶栏收展补偿）。
     * 只冻结滞后，不喂进速度，也不把滞后砍半（砍半会让行突然弹一下）。
     */
    const val LayoutJumpThresholdPx = 120f

    /** 无 layoutInfo 时的旧回退相位跨度（条目） */
    const val PhaseSpanItems = 5.5f

    /** 像素相位在视口高度的多少比例内 0→1 */
    const val PhaseSpanViewportFraction = 0.72f

    /** 速度平滑时间常数（仅 nested-scroll 帧内聚合用） */
    const val VelocitySmoothTauSeconds = 0.055f
}

/** 行级滚动交错的滞后状态（整列/整网格共用一份） */
@Stable
class ListScrollLagState internal constructor() {
    /** 全相位基准滞后（px）。行内再乘视口相位；只在 draw 读取 */
    var lagPx: Float
        get() = _lagPx.floatValue
        internal set(value) {
            _lagPx.floatValue = value
        }

    private val _lagPx = mutableFloatStateOf(0f)

    /** 本帧用户滚动像素（NestedScroll 累计，正=向列表下方滚） */
    @Volatile
    internal var pendingScrollPx: Float = 0f

    /**
     * 挂到 LazyColumn / LazyVerticalGrid 上，采集真实滚动像素。
     * 注意：`listState.scrollBy()` 程序滚动不会走这里，顶栏补偿不会污染速度。
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // consumed.y = 本次列表真正吃掉的像素，方向与 scrollBy 一致
            pendingScrollPx += consumed.y
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            Velocity.Zero
    }

    /** 顶栏收展等布局跳变时收束，不要把补偿滚动当成甩动 */
    fun cancelLag() {
        _lagPx.floatValue = 0f
        pendingScrollPx = 0f
    }

    internal fun stepLag(
        deltaPx: Float,
        dt: Float,
        scrolling: Boolean,
    ) {
        // 布局跳变：冻结滞后（绝不 ×0.35 硬砍，那会表现为整列瞬移）
        if (abs(deltaPx) >= ListRowScrollStagger.LayoutJumpThresholdPx) {
            return
        }

        val targetLag = if (scrolling && abs(deltaPx) > 0.01f) {
            val velocityPx = deltaPx / dt
            velocityPx * ListRowScrollStagger.LagTimeSeconds
        } else {
            0f
        }

        // 一阶指数收敛：始终位于 current 与 target 之间，**不过冲**
        // （弹簧数值积分容易在松手时窜过 0，表现为「先上移再下移」）
        val tau = if (abs(targetLag) > abs(lagPx)) {
            // 追速度：稍快跟手
            ListRowScrollStagger.VelocitySmoothTauSeconds
        } else {
            // 收回 0：更柔，单向贴回布局
            ListRowScrollStagger.SettleTauSeconds
        }
        val alpha = 1f - exp(-dt / tau)
        val next = lagPx + (targetLag - lagPx) * alpha
        lagPx = if (abs(next) < ListRowScrollStagger.EpsilonPx) 0f else next
    }
}

/**
 * 预绑定 listState / lag / 开关。给列表挂 [ListRowStaggerScope.listModifier]，
 * 给每一行挂 [ListRowStaggerScope.modifier]。
 */
@Stable
class ListRowStaggerScope internal constructor(
    private val lagState: ListScrollLagState,
    private val maxLagPx: Float,
    private val enabled: Boolean,
    private val phaseLookup: (itemKey: Any?, itemIndex: Int) -> Float,
) {
    /** 挂到 LazyColumn / LazyVerticalGrid 的 modifier（采集滚动增量） */
    val listModifier: Modifier =
        if (enabled) Modifier.nestedScroll(lagState.nestedScrollConnection) else Modifier

    /** 优先 item key（不怕 header）；无 key 用 Lazy 下标 */
    fun modifier(itemKey: Any? = null, itemIndex: Int = -1): Modifier {
        if (!enabled) return Modifier
        val state = lagState
        val max = maxLagPx
        // drawWithContent：画布 translate，不抢 graphicsLayer
        return Modifier.drawWithContent {
            val phase = phaseLookup(itemKey, itemIndex)
            val ty = listRowStaggerTranslationY(
                scrollLagPx = state.lagPx,
                phaseNorm = phase,
                maxLagPx = max,
            )
            if (abs(ty) < 0.05f) {
                drawContent()
            } else {
                translate(0f, ty) {
                    this@drawWithContent.drawContent()
                }
            }
        }
    }

    fun modifier(rowIndex: Int): Modifier = modifier(itemKey = null, itemIndex = rowIndex)

    fun cancelLag() {
        lagState.cancelLag()
    }
}

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

fun continuousListScrollPosition(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    firstVisibleItemSize: Int,
): Float {
    if (firstVisibleItemSize <= 0) return firstVisibleItemIndex.toFloat()
    return firstVisibleItemIndex + firstVisibleItemScrollOffset.toFloat() / firstVisibleItemSize
}

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

/** 像素相位：条目顶边在视口内的位置 / 跨度，连续、不跳 */
fun listRowPhaseFromViewportOffset(
    itemOffsetPx: Float,
    spanPx: Float,
): Float {
    if (spanPx <= 0f) return 0f
    return (itemOffsetPx / spanPx).coerceIn(0f, ListRowScrollStagger.MaxPhaseNorm)
}

data class LazyItemOffsetInfo(
    val index: Int,
    val key: Any?,
    val offset: Int,
)

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

/** 旧下标相位：仅单测/回退 */
fun listRowViewportPhase(
    rowIndex: Int,
    continuousScrollItems: Float,
    visibleSpanItems: Float = ListRowScrollStagger.PhaseSpanItems,
): Float {
    if (visibleSpanItems <= 0f) return 0f
    val relative = rowIndex - continuousScrollItems
    return (relative / visibleSpanItems).coerceIn(0f, ListRowScrollStagger.MaxPhaseNorm)
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

                val nestedDelta = state.pendingScrollPx
                state.pendingScrollPx = 0f

                val deltaPx = if (abs(nestedDelta) > 0.01f) {
                    nestedDelta
                } else {
                    // 回退：列表未接 nestedScroll 时用连续位置估像素
                    val visibleFirst = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val sizePx = visibleFirst?.size?.takeIf { it > 0 }
                    if (sizePx == null) {
                        state.stepLag(deltaPx = 0f, dt = dt, scrolling = listState.isScrollInProgress)
                        return@withFrameNanos
                    }
                    val position = continuousListScrollPosition(
                        firstVisibleItemIndex = listState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                        firstVisibleItemSize = sizePx,
                    )
                    val deltaItems = position - lastPosition
                    lastPosition = position
                    deltaItems * sizePx
                }
                if (abs(nestedDelta) > 0.01f) {
                    // nested 路径：同步 lastPosition，避免之后回退路径算出大 delta
                    val visibleFirst = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val sizePx = visibleFirst?.size?.takeIf { it > 0 }
                    if (sizePx != null) {
                        lastPosition = continuousListScrollPosition(
                            firstVisibleItemIndex = listState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                            firstVisibleItemSize = sizePx,
                        )
                    }
                }

                state.stepLag(
                    deltaPx = deltaPx,
                    dt = dt,
                    scrolling = listState.isScrollInProgress || abs(nestedDelta) > 0.01f,
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

                val nestedDelta = state.pendingScrollPx
                state.pendingScrollPx = 0f

                val deltaPx = if (abs(nestedDelta) > 0.01f) {
                    val visibleFirst = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val sizePx = visibleFirst?.size?.height?.takeIf { it > 0 }
                    if (sizePx != null) {
                        lastPosition = continuousListScrollPosition(
                            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                            firstVisibleItemSize = sizePx,
                        )
                    }
                    nestedDelta
                } else {
                    val visibleFirst = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val sizePx = visibleFirst?.size?.height?.takeIf { it > 0 }
                    if (sizePx == null) {
                        state.stepLag(0f, dt, gridState.isScrollInProgress)
                        return@withFrameNanos
                    }
                    val position = continuousListScrollPosition(
                        firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                        firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                        firstVisibleItemSize = sizePx,
                    )
                    val deltaItems = position - lastPosition
                    lastPosition = position
                    deltaItems * sizePx
                }

                state.stepLag(
                    deltaPx = deltaPx,
                    dt = dt,
                    scrolling = gridState.isScrollInProgress || abs(nestedDelta) > 0.01f,
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
    val state = lagState
    val max = maxLagPx
    return this.drawWithContent {
        val ty = listRowStaggerTranslationY(
            scrollLagPx = state.lagPx,
            phaseNorm = listRowPhaseForItem(listState, itemKey = itemKey, itemIndex = itemIndex),
            maxLagPx = max,
        )
        if (abs(ty) < 0.05f) drawContent()
        else translate(0f, ty) { this@drawWithContent.drawContent() }
    }
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
    val state = lagState
    val max = maxLagPx
    return this.drawWithContent {
        val ty = listRowStaggerTranslationY(
            scrollLagPx = state.lagPx,
            phaseNorm = listRowPhaseForItem(gridState, itemKey = itemKey, itemIndex = itemIndex),
            maxLagPx = max,
        )
        if (abs(ty) < 0.05f) drawContent()
        else translate(0f, ty) { this@drawWithContent.drawContent() }
    }
}
