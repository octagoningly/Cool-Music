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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * 竖向列表「行与行」滚动交错：滚动/惯性过程中下行相对上行有微小滞后。
 * 静止时 translationY = 0，与布局完全对齐；只改绘制，不改测量与热区。
 */
object ListRowScrollStagger {
    /** 相位封顶：可见窗口内最多叠到该档，长列表不会无限拖尾 */
    const val MaxPhase = 6

    /** 每档相位对滚动滞后的放大系数（无量纲，乘在 px/frame 速度上） */
    const val PhaseScale = 0.16f

    /** 滚动进行中：速度低通（旧值权重） */
    const val VelocitySmoothing = 0.72f

    /** 空闲时滞后衰减（每帧乘），用于松手后收束回 0 */
    const val IdleDecay = 0.84f

    /** 速度低于该值视为静止（px/帧） */
    const val EpsilonPx = 0.12f
}

/** 行级滚动交错的滞后状态（整列共用一份，由 [rememberListScrollLagState] 驱动） */
@Stable
class ListScrollLagState internal constructor() {
    /** 当前滚动滞后（px/帧 量级，已平滑）。只在 graphicsLayer 里读取，避免每帧重组 */
    var lagPx: Float
        get() = _lagPx.floatValue
        internal set(value) {
            _lagPx.floatValue = value
        }

    private val _lagPx = mutableFloatStateOf(0f)
}

/**
 * 行间交错位移（px）。
 *
 * @param scrollLagPx 列表滚动滞后（见 [ListScrollLagState.lagPx]）
 * @param rowPhase 本行相位（0 = 跟手；越大越粘），会先与 [maxPhase] 取小
 * @param maxLagPx 错位上限，防止快甩时「散架」
 */
fun listRowStaggerTranslationY(
    scrollLagPx: Float,
    rowPhase: Int,
    maxLagPx: Float,
    maxPhase: Int = ListRowScrollStagger.MaxPhase,
    phaseScale: Float = ListRowScrollStagger.PhaseScale,
): Float {
    if (scrollLagPx == 0f || rowPhase <= 0 || maxLagPx <= 0f) return 0f
    val phase = rowPhase.coerceAtMost(maxPhase).toFloat()
    val raw = scrollLagPx * phase * phaseScale
    return raw.coerceIn(-maxLagPx, maxLagPx)
}

/**
 * 跟踪 [LazyListState] 的滚动速度并平滑成 [ListScrollLagState]。
 * 整列调用一次，行内通过 [Modifier.listRowScrollStagger] 消费。
 */
@Composable
fun rememberListScrollLagState(listState: LazyListState): ListScrollLagState {
    val state = remember(listState) { ListScrollLagState() }
    LaunchedEffect(listState) {
        var prev = listState.firstVisibleItemIndex * 100_000 +
            listState.firstVisibleItemScrollOffset
        while (true) {
            withFrameNanos {
                val current = listState.firstVisibleItemIndex * 100_000 +
                    listState.firstVisibleItemScrollOffset
                val delta = (current - prev).toFloat()
                prev = current
                state.lagPx = if (listState.isScrollInProgress) {
                    val smoothed = state.lagPx * ListRowScrollStagger.VelocitySmoothing +
                        delta * (1f - ListRowScrollStagger.VelocitySmoothing)
                    if (abs(smoothed) < ListRowScrollStagger.EpsilonPx) 0f else smoothed
                } else {
                    val decayed = state.lagPx * ListRowScrollStagger.IdleDecay
                    if (abs(decayed) < ListRowScrollStagger.EpsilonPx) 0f else decayed
                }
            }
        }
    }
    return state
}

/**
 * 行级滚动交错：下行更粘（视口内相位），快滚有拖尾，静止归位。
 *
 * @param lagState [rememberListScrollLagState] 的返回值
 * @param rowIndex 列表中的绝对下标（[androidx.compose.foundation.lazy.itemsIndexed]）
 * @param listState 同一个 LazyListState，用于算视口内相位
 * @param maxLagPx 最大错位（px）
 */
fun Modifier.listRowScrollStagger(
    lagState: ListScrollLagState,
    rowIndex: Int,
    listState: LazyListState,
    maxLagPx: Float,
    enabled: Boolean = true,
    maxPhase: Int = ListRowScrollStagger.MaxPhase,
    phaseScale: Float = ListRowScrollStagger.PhaseScale,
): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.graphicsLayer {
            val phase = (rowIndex - listState.firstVisibleItemIndex).coerceIn(0, maxPhase)
            translationY = listRowStaggerTranslationY(
                scrollLagPx = lagState.lagPx,
                rowPhase = phase,
                maxLagPx = maxLagPx,
                maxPhase = maxPhase,
                phaseScale = phaseScale,
            )
        }
    )
}
