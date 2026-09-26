package moe.ouom.neriplayer.ui.effect.glass

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex

internal const val ADVANCED_GLASS_NAVIGATION_DAMPING_RATIO =
    Spring.DampingRatioNoBouncy
internal const val ADVANCED_GLASS_NAVIGATION_STIFFNESS =
    Spring.StiffnessMediumLow
// 主 Tab 切换：更短、更跟手，降低双页合成时的掉帧感
internal const val ADVANCED_GLASS_MAIN_TAB_EXIT_DURATION_MS = 45
internal const val ADVANCED_GLASS_MAIN_TAB_ENTER_DURATION_MS = 200
internal const val ADVANCED_GLASS_MAIN_TAB_TRANSITION_DURATION_MS =
    ADVANCED_GLASS_MAIN_TAB_EXIT_DURATION_MS +
        ADVANCED_GLASS_MAIN_TAB_ENTER_DURATION_MS
internal val ADVANCED_GLASS_MAIN_TAB_EXIT_EASING = FastOutSlowInEasing
internal val ADVANCED_GLASS_MAIN_TAB_ENTER_EASING = FastOutSlowInEasing

internal fun advancedGlassNavigationSpringSpec(): FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = ADVANCED_GLASS_NAVIGATION_DAMPING_RATIO,
    stiffness = ADVANCED_GLASS_NAVIGATION_STIFFNESS
)

internal fun <T> advancedGlassMainTabExitSpec(
    durationMillis: Int = ADVANCED_GLASS_MAIN_TAB_EXIT_DURATION_MS
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis,
    easing = ADVANCED_GLASS_MAIN_TAB_EXIT_EASING
)

internal fun <T> advancedGlassMainTabEnterSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = ADVANCED_GLASS_MAIN_TAB_ENTER_DURATION_MS,
    easing = ADVANCED_GLASS_MAIN_TAB_ENTER_EASING
)

internal fun <T> advancedGlassMainTabTransitionSpec(
    durationMillis: Int = ADVANCED_GLASS_MAIN_TAB_TRANSITION_DURATION_MS
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis,
    easing = ADVANCED_GLASS_MAIN_TAB_ENTER_EASING
)

internal fun isolatedAdvancedGlassHorizontalTransition(
    forward: Boolean
): ContentTransform {
    val direction = if (forward) 1 else -1
    val animationSpec = advancedGlassNavigationSpringSpec()
    return slideInHorizontally(animationSpec) { fullWidth -> direction * fullWidth } togetherWith
        slideOutHorizontally(animationSpec) { fullWidth -> -direction * fullWidth }
}

internal fun isolatedAdvancedGlassVerticalTransition(
    forward: Boolean
): ContentTransform {
    val direction = if (forward) 1 else -1
    // 旧页退场更快：媒体库顶栏等 chrome 不再在进详情后多挂约 0.5s
    val exitSpec = tween<IntOffset>(durationMillis = 160, easing = FastOutLinearInEasing)
    return slideInVertically(advancedGlassNavigationSpringSpec()) { fullHeight -> direction * fullHeight } togetherWith
        slideOutVertically(exitSpec) { fullHeight -> -direction * fullHeight }
}

internal fun buildAdvancedGlassDrawerTransition(
    forward: Boolean,
    retainedExit: ExitTransition = ExitTransition.None,
    targetContentZIndex: Float = if (forward) 1f else -1f
): ContentTransform {
    val durationMillis = if (forward) {
        DRAWER_NAVIGATION_OPEN_DURATION_MS
    } else {
        DRAWER_NAVIGATION_CLOSE_DURATION_MS
    }
    val animationSpec = tween<Float>(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    )
    return ContentTransform(
        targetContentEnter = fadeIn(
            initialAlpha = DRAWER_ROOT_RETAIN_ALPHA,
            animationSpec = animationSpec
        ),
        initialContentExit = retainedExit,
        targetContentZIndex = targetContentZIndex
    )
}

internal data class AdvancedGlassSceneMotion(
    val revealTopFraction: Float,
    val contentTranslationYFraction: Float,
    val contentScale: Float
) {
    companion object {
        val None = AdvancedGlassSceneMotion(
            revealTopFraction = 0f,
            contentTranslationYFraction = 0f,
            contentScale = 1f
        )
    }
}

internal fun resolveAdvancedGlassSceneZIndex(navigationDepth: Int): Float =
    navigationDepth.coerceAtLeast(0).toFloat()

internal fun Modifier.advancedGlassSceneZIndex(navigationDepth: Int): Modifier =
    zIndex(resolveAdvancedGlassSceneZIndex(navigationDepth))

@Composable
internal fun <S> Transition<S>.animateAdvancedGlassSceneMotion(
    sceneState: S,
    coherentFeedbackEnabled: Boolean,
    navigationDepth: (S) -> Int,
    label: String
): AdvancedGlassSceneMotion {
    if (coherentFeedbackEnabled) {
        return AdvancedGlassSceneMotion.None
    }
    val forward = navigationDepth(targetState) > navigationDepth(currentState)
    val durationMillis = if (forward) {
        DRAWER_NAVIGATION_OPEN_DURATION_MS
    } else {
        DRAWER_NAVIGATION_CLOSE_DURATION_MS
    }
    val revealTopFraction by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_reveal"
    ) { transitionState ->
        resolveAdvancedGlassDrawerSceneMotion(
            sceneState = sceneState,
            activeState = transitionState,
            navigationDepth = navigationDepth,
            forward = forward
        ).revealTopFraction
    }
    val contentTranslationYFraction by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_content_translation"
    ) { transitionState ->
        resolveAdvancedGlassDrawerSceneMotion(
            sceneState = sceneState,
            activeState = transitionState,
            navigationDepth = navigationDepth,
            forward = forward
        ).contentTranslationYFraction
    }
    val contentScale by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_content_scale"
    ) { transitionState ->
        resolveAdvancedGlassDrawerSceneMotion(
            sceneState = sceneState,
            activeState = transitionState,
            navigationDepth = navigationDepth,
            forward = forward
        ).contentScale
    }
    return AdvancedGlassSceneMotion(
        revealTopFraction = revealTopFraction,
        contentTranslationYFraction = contentTranslationYFraction,
        contentScale = contentScale
    )
}

internal fun <S> resolveAdvancedGlassDrawerSceneMotion(
    sceneState: S,
    activeState: S,
    navigationDepth: (S) -> Int,
    forward: Boolean
): AdvancedGlassSceneMotion {
    if (sceneState == activeState) {
        return AdvancedGlassSceneMotion.None
    }
    val sceneDepth = navigationDepth(sceneState)
    val activeDepth = navigationDepth(activeState)
    return when {
        // 被更深页面盖住（如媒体库 → 歌单详情）
        sceneDepth < activeDepth && forward -> AdvancedGlassSceneMotion(
            // 从顶部裁掉：旧「媒体库 / 分类」顶栏立刻让位，不再多挂 0.5s
            revealTopFraction = 1f,
            contentTranslationYFraction = DRAWER_BACKGROUND_SINK_FRACTION,
            contentScale = DRAWER_RECESSED_CONTENT_SCALE
        )
        // 关闭详情时底层列表已在下方完整待命
        sceneDepth < activeDepth -> AdvancedGlassSceneMotion.None
        // 进入更深页：直接整页覆盖，避免从下往上揭开时露出旧顶栏
        forward -> AdvancedGlassSceneMotion.None
        // 退出更深页：从顶部收起
        else -> AdvancedGlassSceneMotion(
            revealTopFraction = 1f,
            contentTranslationYFraction = 1f,
            contentScale = 1f
        )
    }
}

@Composable
internal fun Transition<EnterExitState>.animateAdvancedGlassVisibilitySceneMotion(
    coherentFeedbackEnabled: Boolean,
    enteringFromDeeperScene: Boolean,
    exitingToDeeperScene: Boolean,
    label: String
): AdvancedGlassSceneMotion {
    if (coherentFeedbackEnabled) {
        return AdvancedGlassSceneMotion.None
    }
    val opening = when {
        targetState == EnterExitState.Visible -> !enteringFromDeeperScene
        targetState == EnterExitState.PostExit -> exitingToDeeperScene
        else -> true
    }
    val durationMillis = if (opening) {
        DRAWER_NAVIGATION_OPEN_DURATION_MS
    } else {
        DRAWER_NAVIGATION_CLOSE_DURATION_MS
    }
    val revealTopFraction by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_reveal"
    ) { state ->
        resolveAdvancedGlassVisibilitySceneMotion(
            state = state,
            enteringFromDeeperScene = enteringFromDeeperScene,
            exitingToDeeperScene = exitingToDeeperScene
        ).revealTopFraction
    }
    val contentTranslationYFraction by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_content_translation"
    ) { state ->
        resolveAdvancedGlassVisibilitySceneMotion(
            state = state,
            enteringFromDeeperScene = enteringFromDeeperScene,
            exitingToDeeperScene = exitingToDeeperScene
        ).contentTranslationYFraction
    }
    val contentScale by animateFloat(
        transitionSpec = {
            tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        },
        label = "${label}_content_scale"
    ) { state ->
        resolveAdvancedGlassVisibilitySceneMotion(
            state = state,
            enteringFromDeeperScene = enteringFromDeeperScene,
            exitingToDeeperScene = exitingToDeeperScene
        ).contentScale
    }
    return AdvancedGlassSceneMotion(
        revealTopFraction = revealTopFraction,
        contentTranslationYFraction = contentTranslationYFraction,
        contentScale = contentScale
    )
}

internal fun resolveAdvancedGlassVisibilitySceneMotion(
    state: EnterExitState,
    enteringFromDeeperScene: Boolean,
    exitingToDeeperScene: Boolean
): AdvancedGlassSceneMotion = when (state) {
    EnterExitState.Visible -> AdvancedGlassSceneMotion.None
    EnterExitState.PreEnter -> if (enteringFromDeeperScene) {
        AdvancedGlassSceneMotion(
            revealTopFraction = 0f,
            contentTranslationYFraction = DRAWER_BACKGROUND_SINK_FRACTION,
            contentScale = DRAWER_RECESSED_CONTENT_SCALE
        )
    } else {
        AdvancedGlassSceneMotion(
            revealTopFraction = 1f,
            contentTranslationYFraction = 1f,
            contentScale = 1f
        )
    }
    EnterExitState.PostExit -> if (exitingToDeeperScene) {
        AdvancedGlassSceneMotion(
            revealTopFraction = 0f,
            contentTranslationYFraction = DRAWER_BACKGROUND_SINK_FRACTION,
            contentScale = DRAWER_RECESSED_CONTENT_SCALE
        )
    } else {
        AdvancedGlassSceneMotion(
            revealTopFraction = 1f,
            contentTranslationYFraction = 1f,
            contentScale = 1f
        )
    }
}

internal fun <S> AnimatedContentTransitionScope<S>.advancedGlassHostNavigationTransition(
    forward: Boolean,
    coherentFeedbackEnabled: Boolean,
    targetContentZIndex: Float = if (forward) 1f else -1f
): ContentTransform = if (coherentFeedbackEnabled) {
    isolatedAdvancedGlassVerticalTransition(forward)
} else {
    buildAdvancedGlassDrawerTransition(
        forward = forward,
        retainedExit = ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = targetContentZIndex
    )
}

internal const val DRAWER_NAVIGATION_OPEN_DURATION_MS = 300
internal const val DRAWER_NAVIGATION_CLOSE_DURATION_MS = 280
internal const val DRAWER_BACKGROUND_SINK_FRACTION = 0f
internal const val DRAWER_RECESSED_CONTENT_SCALE = 0.98f
private const val DRAWER_ROOT_RETAIN_ALPHA = 0.999f
