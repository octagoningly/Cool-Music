package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

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
