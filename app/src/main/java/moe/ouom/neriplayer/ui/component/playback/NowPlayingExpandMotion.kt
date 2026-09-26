package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * MiniPlayer ↔ NowPlaying 「连贯反馈」展开/收起动效。
 *
 * 开关关闭时走原有抽屉滑入滑出；开启时叠加封面共享元素 + 弹簧协同。
 * 只新增、不替换原 tween 路径（见 resolveNowPlayingExpandTransitions）。
 */

object NowPlayingExpandMotion {
    /** 封面共享元素 key，需同时挂在 MiniPlayer 封面与 NowPlaying 封面 */
    const val COVER_SHARED_KEY = "np_expand_cover"

    val CoverBoundsSpring = spring<androidx.compose.ui.geometry.Rect>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val CoverBoundsSpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 播放页入场：略快于原 300ms 抽屉，弹簧更有重量 */
    val ExpandEnterSlideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val ExpandExitSlideSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    const val ExpandEnterFadeMs = 220
    const val ExpandExitFadeMs = 180
    const val MiniPlayerExitFadeMs = 160
    const val MiniPlayerExitScale = 0.92f

    // —— 方案 B：跟手 ——
    /** 松手关闭：位移超过高度比例 或 甩动速度超过该值 (px/s) */
    const val DismissDistanceRatio = 0.28f
    const val DismissVelocityPxPerSec = 1200f
    /** MiniPlayer 上滑展开阈值 (dp) */
    val ExpandSwipeUpThresholdDp = 48f

    // —— 方案 C：内容错落 ——
    const val StaggerTitleDelayMs = 80
    const val StaggerControlsDelayMs = 160
    const val StaggerDurationMs = 280
}

/**
 * SharedTransition 作用域：由 NeriApp 包裹 MiniPlayer 与 NowPlaying 后提供。
 * 为空则退化为无共享元素的原动画。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalNowPlayingExpandSharedScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * @param coherentFeedbackEnabled 设置 → 动效 → 连贯反馈
 * @return true 时使用共享元素 + 弹簧；false 时返回 null，由调用方走原 tween 动画
 */
fun shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled: Boolean): Boolean =
    coherentFeedbackEnabled

@OptIn(ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
fun SharedTransitionScope.coverSharedModifier(
    enabled: Boolean,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope
): androidx.compose.ui.Modifier {
    return if (!enabled) {
        androidx.compose.ui.Modifier
    } else {
        androidx.compose.ui.Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = NowPlayingExpandMotion.COVER_SHARED_KEY
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = { _, _ -> NowPlayingExpandMotion.CoverBoundsSpring }
        )
    }
}

/**
 * 连贯反馈开启时的播放页转场；关闭时返回 null 表示使用调用处原有 enter/exit。
 */
fun nowPlayingExpandEnterTransition(coherentFeedbackEnabled: Boolean): EnterTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return slideInVertically(
        animationSpec = NowPlayingExpandMotion.ExpandEnterSlideSpec,
        initialOffsetY = { fullHeight -> fullHeight }
    ) + fadeIn(
        animationSpec = tween(durationMillis = NowPlayingExpandMotion.ExpandEnterFadeMs)
    ) + scaleIn(
        initialScale = 0.98f,
        animationSpec = NowPlayingExpandMotion.CoverBoundsSpringFloat
    )
}

fun nowPlayingExpandExitTransition(coherentFeedbackEnabled: Boolean): ExitTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return slideOutVertically(
        animationSpec = NowPlayingExpandMotion.ExpandExitSlideSpec,
        targetOffsetY = { fullHeight -> fullHeight }
    ) + fadeOut(
        animationSpec = tween(durationMillis = NowPlayingExpandMotion.ExpandExitFadeMs)
    )
}

/**
 * 连贯反馈开启时 MiniPlayer 退场：淡出 + 微缩，避免与播放页「各落各的」。
 */
fun miniPlayerExpandExitTransition(coherentFeedbackEnabled: Boolean): ExitTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return fadeOut(
        animationSpec = tween(durationMillis = NowPlayingExpandMotion.MiniPlayerExitFadeMs)
    ) + scaleOut(
        targetScale = NowPlayingExpandMotion.MiniPlayerExitScale,
        animationSpec = NowPlayingExpandMotion.CoverBoundsSpringFloat
    )
}

fun miniPlayerExpandEnterTransition(coherentFeedbackEnabled: Boolean): EnterTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return fadeIn(
        animationSpec = tween(durationMillis = 180)
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = NowPlayingExpandMotion.CoverBoundsSpringFloat
    )
}

/** 供测试：连贯反馈关闭时必须完全走原路径 */
fun nowPlayingExpandUsesOriginalAnimation(coherentFeedbackEnabled: Boolean): Boolean =
    !shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)

// ---------------------------------------------------------------------------
// 方案 B：NowPlaying 下滑跟手关闭
// ---------------------------------------------------------------------------

@Stable
class NowPlayingDismissDragState internal constructor() {
    internal val offset = Animatable(0f)
    val offsetY: Float get() = offset.value
    var heightPx: Float = 0f
        internal set

    internal fun onDragDelta(deltaY: Float, scope: CoroutineScope) {
        val next = (offset.value + deltaY).coerceAtLeast(0f)
        scope.launch { offset.snapTo(next) }
    }

    internal fun onDragStopped(
        velocityY: Float,
        scope: CoroutineScope,
        onDismiss: () -> Unit
    ) {
        val threshold = heightPx * NowPlayingExpandMotion.DismissDistanceRatio
        val shouldDismiss =
            offset.value >= threshold || velocityY >= NowPlayingExpandMotion.DismissVelocityPxPerSec
        scope.launch {
            if (shouldDismiss) {
                offset.animateTo(
                    targetValue = heightPx.coerceAtLeast(offset.value + 1f),
                    animationSpec = tween(durationMillis = 220)
                )
                onDismiss()
            } else {
                offset.animateTo(
                    targetValue = 0f,
                    animationSpec = NowPlayingExpandMotion.CoverBoundsSpringFloat
                )
            }
        }
    }
}

@Composable
fun rememberNowPlayingDismissDragState(): NowPlayingDismissDragState {
    return remember { NowPlayingDismissDragState() }
}

/**
 * 连贯反馈开启：内容随手下滑，松手按距离/速度决定关闭或回弹。
 * 关闭：返回空 Modifier，由调用方使用原 `detectVerticalDragGestures` 硬切。
 */
@Composable
fun Modifier.nowPlayingDismissDrag(
    enabled: Boolean,
    state: NowPlayingDismissDragState,
    onDismiss: () -> Unit
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val draggableState = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
        state.onDragDelta(delta, scope)
    }
    return this
        .onSizeChanged { size -> state.heightPx = size.height.toFloat() }
        .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                state.onDragStopped(velocity, scope, currentOnDismiss)
            }
        )
}

// ---------------------------------------------------------------------------
// 方案 B：MiniPlayer 上滑跟手展开
// ---------------------------------------------------------------------------

/**
 * 连贯反馈开启：MiniPlayer 向上拖超过阈值或快速上甩则展开。
 * 关闭：不拦截，仍由点击展开。
 */
@Composable
fun Modifier.miniPlayerExpandSwipe(
    enabled: Boolean,
    onExpand: () -> Unit
): Modifier {
    if (!enabled) return this
    val currentOnExpand by rememberUpdatedState(onExpand)
    val thresholdPx = with(LocalDensity.current) {
        NowPlayingExpandMotion.ExpandSwipeUpThresholdDp.dp.toPx()
    }
    var dragUpPx by remember { mutableFloatStateOf(0f) }
    return this.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { dragUpPx = 0f },
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount < 0f) {
                    change.consume()
                    dragUpPx -= dragAmount
                }
            },
            onDragEnd = {
                val shouldExpand = dragUpPx >= thresholdPx
                val quickFling = dragUpPx >= thresholdPx * 0.35f && dragUpPx > 12f
                if (shouldExpand || quickFling) {
                    currentOnExpand()
                }
                dragUpPx = 0f
            },
            onDragCancel = { dragUpPx = 0f }
        )
    }
}

// ---------------------------------------------------------------------------
// 方案 C：内容错落进场
// ---------------------------------------------------------------------------

/**
 * 连贯反馈开启时：标题/控件延迟淡入。
 * 关闭时：直接显示，保持原行为（标题原有 contentVisible 动画仍由调用方决定）。
 */
@Composable
fun ExpandStaggerContainer(
    enabled: Boolean,
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = NowPlayingExpandMotion.StaggerDurationMs,
                delayMillis = delayMillis
            )
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = NowPlayingExpandMotion.StaggerDurationMs,
                delayMillis = delayMillis
            ),
            initialOffsetY = { it / 8 }
        ),
        exit = ExitTransition.None
    ) {
        content()
    }
}
