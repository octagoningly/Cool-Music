package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.ui.component.common.SceneDepthMotion

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
        // 参考 SwiftAnimPlayground Hero：轻微回弹更有“落位”感，比 NoBouncy 更灵动
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * 电影感缓动（skydoves Shared Bounds Expansion 风格）。
     * 展开偏慢有分量；收回更快，避免「页面都没了封面还在飞」。
     */
    val CoverBoundsTween = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = 480,
        easing = FastOutSlowInEasing
    )

    /** 封面收回（变小）：明显快于展开，跟抽屉回收同步收束 */
    val CoverBoundsCollapseTween = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = 280,
        easing = FastOutSlowInEasing
    )

    /** 飞行时的抬升阴影（dp），参考 SwiftUI-experiments drag transform 的 dragging shadow */
    val CoverFlyShadowDp = 6.dp
    val CoverMiniCornerRadiusDp = 8.dp
    val CoverLargeCornerRadiusDp = 24.dp

    /** 落位：展开结束后轻微 1.04→1.0 收束 */
    val CoverSettleFromScale = 1.04f
    val CoverSettleDelayMs = 70
    val CoverSettleSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    val CoverBoundsSpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 播放页抽屉：入场弹簧有分量；回收用稍长的缓动，动画走完整 */
    val ExpandEnterSlideSpec = spring<IntOffset>(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessMediumLow
    )

    val ExpandExitSlideSpec = tween<IntOffset>(
        durationMillis = 420,
        easing = FastOutSlowInEasing
    )

    // —— 强化抽屉 ——
    const val HeroEnterFadeMs = 320
    /** 回收淡出略长于原先，避免页面瞬间消失 */
    const val HeroExitFadeMs = 280
    const val HeroEnterFromScale = 0.96f
    const val HeroExitToScale = 0.98f

    val HeroEnterSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    const val ExpandEnterFadeMs = 280
    const val ExpandExitFadeMs = 280
    const val MiniPlayerExitFadeMs = 160
    const val MiniPlayerExitScale = 0.92f

    /** 与歌单卡片展开共用 1.20 倍背景推进；全程不透明，避免浅色底透出形成白蒙层。 */
    const val BackgroundRecedeScale = SceneDepthMotion.ExpandedScale
    const val BackgroundRecedeAlpha = 1.0f
    const val BackgroundRecedeDurationMs = SceneDepthMotion.OpenDurationMillis

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

    // —— 爱心收藏 pop（放大回弹） ——
    /** 收藏时冲高到的缩放 */
    const val HeartPopPeakScale = 1.28f
    /** 取消收藏时轻压 */
    const val HeartUnlikeDipScale = 0.90f
    /** 连贯反馈关闭时的轻量峰值 */
    const val HeartPopPeakScaleLight = 1.10f
    const val HeartUnlikeDipScaleLight = 0.95f

    val HeartPopSpring = spring<Float>(
        dampingRatio = 0.48f,
        stiffness = Spring.StiffnessMediumLow
    )
    val HeartPopSettleSpring = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )
    val HeartUnlikeSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

internal data class NowPlayingBackgroundFrame(
    val scale: Float,
    val alpha: Float
)

internal fun resolveNowPlayingBackgroundFrame(progress: Float): NowPlayingBackgroundFrame {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    return NowPlayingBackgroundFrame(
        scale = 1f -
            (1f - NowPlayingExpandMotion.BackgroundRecedeScale) * normalizedProgress,
        alpha = 1f -
            (1f - NowPlayingExpandMotion.BackgroundRecedeAlpha) * normalizedProgress
    )
}

/**
 * 封面 SharedContentState 桥：供跟手关闭时把甩动速度交给共享元素动画
 * （Compose `prepareTransitionWithInitialVelocity`）。
 */
@Stable
class ExpandCoverSharedBridge {
    @OptIn(ExperimentalSharedTransitionApi::class)
    var sharedContentState: SharedTransitionScope.SharedContentState? = null
}

val LocalExpandCoverSharedBridge =
    staticCompositionLocalOf { ExpandCoverSharedBridge() }

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
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    cornerRadius: Dp = NowPlayingExpandMotion.CoverMiniCornerRadiusDp,
    shadowElevation: Dp = NowPlayingExpandMotion.CoverFlyShadowDp,
    clipToShape: Boolean = true
): Modifier {
    if (!enabled) return Modifier
    val shape = RoundedCornerShape(cornerRadius)
    val overlayClip = remember(cornerRadius) { OverlayClip(shape) }
    val sharedContentState = rememberSharedContentState(
        key = NowPlayingExpandMotion.COVER_SHARED_KEY
    )
    val bridge = LocalExpandCoverSharedBridge.current
    remember(sharedContentState) { bridge.sharedContentState = sharedContentState }
    return Modifier
        // 专辑封面是「同一张图」的缩放/位移，必须用 sharedElement：
        // sharedBounds 会对进出内容做 fadeIn/fadeOut，看起来像两张图交叉溶解，不够连贯。
        .sharedElement(
            sharedContentState = sharedContentState,
            animatedVisibilityScope = animatedVisibilityScope,
            // 展开慢、收回快：目标比初始更小 → 回收，避免封面孤零零拖在页面后面
            boundsTransform = { initialBounds, targetBounds ->
                if (targetBounds.width < initialBounds.width) {
                    NowPlayingExpandMotion.CoverBoundsCollapseTween
                } else {
                    NowPlayingExpandMotion.CoverBoundsTween
                }
            },
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 8f,
            clipInOverlayDuringTransition = overlayClip
        )
        // 切歌飞出/飞入要离开封面框：外层不能 clip（圆角画在封面层内）
        .then(if (clipToShape) Modifier.clip(shape) else Modifier)
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false
        )
}

/**
 * 封面落位：展开后 1.04→1.0 轻微收束（挂在封面内容上，避免和 sharedElement 抢变换）。
 */
@androidx.compose.runtime.Composable
fun Modifier.coverSettleScale(enabled: Boolean): Modifier {
    if (!enabled) return this
    var settled by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (settled) 1f else NowPlayingExpandMotion.CoverSettleFromScale,
        animationSpec = NowPlayingExpandMotion.CoverSettleSpring,
        label = "cover_settle"
    )
    LaunchedEffect(Unit) {
        delay(NowPlayingExpandMotion.CoverSettleDelayMs.toLong())
        settled = true
    }
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 主界面背景轻微后退（抽屉打开时的层次感），由连贯反馈开关控制。
 */
@androidx.compose.runtime.Composable
fun Modifier.nowPlayingBackgroundRecede(
    enabled: Boolean,
    nowPlayingVisible: Boolean
): Modifier {
    if (!enabled) return this
    val progress by animateFloatAsState(
        targetValue = if (nowPlayingVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (nowPlayingVisible) {
                NowPlayingExpandMotion.BackgroundRecedeDurationMs
            } else {
                SceneDepthMotion.CloseDurationMillis
            },
            easing = if (nowPlayingVisible) {
                SceneDepthMotion.OpenEasing
            } else {
                SceneDepthMotion.CloseEasing
            }
        ),
        label = "np_bg_recede"
    )
    return this.graphicsLayer {
        val frame = resolveNowPlayingBackgroundFrame(progress)
        scaleX = frame.scale
        scaleY = frame.scale
        alpha = frame.alpha
    }
}

/**
 * 爱心收藏点击：放大回弹（无扩散环）。
 * - 收藏：冲到 [NowPlayingExpandMotion.HeartPopPeakScale] 再弹簧落回 1
 * - 取消：轻压到 dip 再回弹
 * - [coherentFeedbackEnabled] 关闭时用轻量峰值
 *
 * @param pulse 递增触发器（点一次 +1）；0 表示尚未交互
 */
@androidx.compose.runtime.Composable
fun rememberFavoriteHeartPopScale(
    pulse: Int,
    willFavorite: Boolean,
    coherentFeedbackEnabled: Boolean
): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse <= 0) return@LaunchedEffect
        if (willFavorite) {
            val peak = if (coherentFeedbackEnabled) {
                NowPlayingExpandMotion.HeartPopPeakScale
            } else {
                NowPlayingExpandMotion.HeartPopPeakScaleLight
            }
            scale.animateTo(peak, animationSpec = NowPlayingExpandMotion.HeartPopSpring)
            scale.animateTo(1f, animationSpec = NowPlayingExpandMotion.HeartPopSettleSpring)
        } else {
            val dip = if (coherentFeedbackEnabled) {
                NowPlayingExpandMotion.HeartUnlikeDipScale
            } else {
                NowPlayingExpandMotion.HeartUnlikeDipScaleLight
            }
            scale.animateTo(dip, animationSpec = tween(durationMillis = 90))
            scale.animateTo(1f, animationSpec = NowPlayingExpandMotion.HeartUnlikeSpring)
        }
    }
    return scale.value
}

/**
 * 连贯反馈开启时的播放页转场；关闭时返回 null 表示使用调用处原有 enter/exit。
 *
 * 强化抽屉：整页从底部弹簧上推 + 淡入 + 轻微放大，封面仍走 sharedElement。
 */
fun nowPlayingExpandEnterTransition(coherentFeedbackEnabled: Boolean): EnterTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    // 不做整页 scaleIn：全屏 graphicsLayer 缩放 + 玻璃/歌词/封面同帧最掉帧
    return slideInVertically(
        animationSpec = NowPlayingExpandMotion.ExpandEnterSlideSpec,
        initialOffsetY = { fullHeight -> fullHeight }
    ) + fadeIn(
        animationSpec = tween(durationMillis = NowPlayingExpandMotion.HeroEnterFadeMs)
    )
}

fun nowPlayingExpandExitTransition(coherentFeedbackEnabled: Boolean): ExitTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return slideOutVertically(
        animationSpec = NowPlayingExpandMotion.ExpandExitSlideSpec,
        targetOffsetY = { fullHeight -> fullHeight }
    ) + fadeOut(
        animationSpec = tween(durationMillis = NowPlayingExpandMotion.HeroExitFadeMs)
    )
}

/**
 * 连贯反馈开启时 MiniPlayer 退场：只微缩，**不要淡出**。
 * 淡出会让迷你栏瞬间透明透底；盖住它的播放页自己会滑入。
 */
fun miniPlayerExpandExitTransition(coherentFeedbackEnabled: Boolean): ExitTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return scaleOut(
        targetScale = NowPlayingExpandMotion.MiniPlayerExitScale,
        animationSpec = NowPlayingExpandMotion.CoverBoundsSpringFloat
    )
}

/**
 * 连贯反馈开启时 MiniPlayer 入场：只微弹，**不要淡入**。
 * 保持模糊面板不透明，由播放页滑开露出。
 */
fun miniPlayerExpandEnterTransition(coherentFeedbackEnabled: Boolean): EnterTransition? {
    if (!shouldUseNowPlayingExpandSharedMotion(coherentFeedbackEnabled)) return null
    return scaleIn(
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
        onDismiss: () -> Unit,
        onDismissWithVelocity: ((Float) -> Unit)? = null
    ) {
        val threshold = heightPx * NowPlayingExpandMotion.DismissDistanceRatio
        val shouldDismiss =
            offset.value >= threshold || velocityY >= NowPlayingExpandMotion.DismissVelocityPxPerSec
        scope.launch {
            if (shouldDismiss) {
                // 先把甩动速度交给封面共享元素，再收起页面
                // 立刻 onDismiss：不要先 offset 动画 200ms 再退场，会和 AnimatedVisibility 叠成两次下滑/闪屏
                onDismissWithVelocity?.invoke(velocityY)
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
    val bridge = LocalExpandCoverSharedBridge.current
    val draggableState = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
        state.onDragDelta(delta, scope)
    }
    return this
        .onSizeChanged { size -> state.heightPx = size.height.toFloat() }
        .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                state.onDragStopped(
                    velocityY = velocity,
                    scope = scope,
                    onDismiss = currentOnDismiss,
                    onDismissWithVelocity = { v ->
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        bridge.sharedContentState?.prepareTransitionWithInitialVelocity(
                            androidx.compose.ui.unit.Velocity(0f, v)
                        )
                    }
                )
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
