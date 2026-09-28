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
 * File: moe.ouom.neriplayer.ui.util/ListRowScrollStagger
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
 * 关键手感：
 * - 滚动位置用 `index + offset/size` 连续化，避免跨条目时速度跳变（一抽一抽）
 * - 速度按帧间隔归一，60/120Hz 手感一致
 * - 松手后按时间常数收束到 0，不残留错位
 * - 相位取自条目在视口内的连续 Y，不用 `index - firstVisible`（会跳）
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

    /** 相位跨度（条目数）：固定值，避免 visibleItemsInfo.size 波动导致相位跳 */
    const val PhaseSpanItems = 5.5f
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

    /** 顶栏收展等布局跳变时清零，避免把补偿滚动当成用户甩动 */
    fun cancelLag() {
        _lagPx.floatValue = 0f
    }
}

/** 预绑定 listState / lag / 开关后，按行号生成 Modifier，减少调用处样板 */
@Stable
class ListRowStaggerScope internal constructor(
    private val lagState: ListScrollLagState,
    private val maxLagPx: Float,
    private val enabled: Boolean,
    private val phaseLookup: (rowIndex: Int) -> Float,
) {
    fun modifier(rowIndex: Int): Modifier {
        if (!enabled) return Modifier
        return Modifier.graphicsLayer {
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = phaseLookup(rowIndex),
                maxLagPx = maxLagPx,
            )
        }
    }

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
 * 视口内连续相位：用「行号 − 连续滚动位置」计算，不查 visibleItemsInfo。
 * 查表在条目回收/贴边时会瞬间变 0 再跳回，表现为某些行突然闪烁。
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
        var lastPosition = continuousListScrollPosition(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            firstVisibleItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1,
        )
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.0005f, 0.05f)
                lastNanos = nanos
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val position = continuousListScrollPosition(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = first?.size ?: 1,
                )
                val deltaItems = position - lastPosition
                lastPosition = position
                updateLagForFrame(
                    state = state,
                    listStateScrolling = listState.isScrollInProgress,
                    deltaItems = deltaItems,
                    refSizePx = first?.size?.toFloat() ?: 1f,
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
        var lastPosition = continuousListScrollPosition(
            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            firstVisibleItemSize = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 1,
        )
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.0005f, 0.05f)
                lastNanos = nanos
                val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                val position = continuousListScrollPosition(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = first?.size?.height ?: 1,
                )
                val deltaItems = position - lastPosition
                lastPosition = position
                updateLagForFrame(
                    state = state,
                    listStateScrolling = gridState.isScrollInProgress,
                    deltaItems = deltaItems,
                    refSizePx = first?.size?.height?.toFloat() ?: 1f,
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
    val stride = rowStride.coerceAtLeast(1)
    return remember(lagState, maxLagPx, enabled, listState, stride) {
        ListRowStaggerScope(
            lagState = lagState,
            maxLagPx = maxLagPx,
            enabled = enabled,
            phaseLookup = { rowIndex ->
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val scrollItems = continuousListScrollPosition(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = first?.size ?: 1,
                )
                listRowViewportPhase(
                    rowIndex = rowIndex / stride,
                    continuousScrollItems = scrollItems / stride,
                )
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
    val stride = rowStride.coerceAtLeast(1)
    return remember(lagState, maxLagPx, enabled, gridState, stride) {
        ListRowStaggerScope(
            lagState = lagState,
            maxLagPx = maxLagPx,
            enabled = enabled,
            phaseLookup = { rowIndex ->
                val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
                val scrollItems = continuousListScrollPosition(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = first?.size?.height ?: 1,
                )
                listRowViewportPhase(
                    rowIndex = rowIndex / stride,
                    continuousScrollItems = scrollItems / stride,
                )
            },
        )
    }
}

/**
 * 行级滚动交错：下行更粘（视口内连续相位），快滚有拖尾，静止归位。
 */
fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    rowIndex: Int,
    listState: LazyListState,
    maxLagPx: Float,
    enabled: Boolean = true,
    rowStride: Int = 1,
): Modifier {
    if (!enabled) return this
    val stride = rowStride.coerceAtLeast(1)
    return this.then(
        Modifier.graphicsLayer {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            val scrollItems = continuousListScrollPosition(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = first?.size ?: 1,
            )
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = listRowViewportPhase(
                    rowIndex = rowIndex / stride,
                    continuousScrollItems = scrollItems / stride,
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
): Modifier {
    if (!enabled) return this
    val stride = rowStride.coerceAtLeast(1)
    return this.then(
        Modifier.graphicsLayer {
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            val scrollItems = continuousListScrollPosition(
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                firstVisibleItemSize = first?.size?.height ?: 1,
            )
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                phaseNorm = listRowViewportPhase(
                    rowIndex = rowIndex / stride,
                    continuousScrollItems = scrollItems / stride,
                ),
                maxLagPx = maxLagPx,
            )
        }
    )
}
