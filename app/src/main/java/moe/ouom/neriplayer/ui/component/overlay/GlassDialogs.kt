package moe.ouom.neriplayer.ui.component.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassBackdropRegistrationEnabled
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassDepth
import moe.ouom.neriplayer.ui.effect.glass.LocalGlassOverlayElevated

/** 对话框 / 面板统一圆角（开发规则：对话框 28.dp） */
internal val GlassDialogShape = RoundedCornerShape(28.dp)

/** 下拉菜单统一圆角（开发规则：菜单 20.dp） */
internal val GlassMenuShape = RoundedCornerShape(20.dp)

/** 底部面板：仅顶部 28.dp 圆角（开发规则：对话框/面板 28.dp） */
internal val GlassSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

internal enum class GlassPanelPosition {
    Centered,
    Bottom
}

/**
 * 设置弹窗「强 Q 弹」：中心爆开（行程大、过冲明显、震荡 2～3 次）。
 *
 * 比三点菜单更夸张一档，但仍是缩放+淡入，不旋转。
 * 底部面板改为自下弹入并过冲落位。设置 → 动效 → 连贯反馈 关闭时保持瞬时开合。
 */
internal object GlassDialogMotion {
    // 与三点菜单 GlassMenuMotion 同一套手感（除 ⋮ 菜单外所有弹窗统一）
    const val EnterFadeMs = 234
    const val ExitFadeMs = 156

    const val EnterScaleFrom = 0.76f
    const val ExitScaleTo = 0.92f

    /** 微位移：居中/底部面板都从下方轻推，幅度与菜单一致 */
    const val CenterSlideFraction = 0.08f
    const val SheetSlideFraction = 0.10f

    /** 与菜单相同：damping 0.48 / stiffness 888，Q 弹但不过分晃 */
    val EnterTransformSpring = spring<Float>(
        dampingRatio = 0.48f,
        stiffness = 888f,
    )

    val ExitTransformSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 888f,
    )

    /** progress: 0=收起, 1=展开；弹簧可 >1（过冲）或 <1（回弹不足） */
    fun appearScale(expanding: Boolean, progress: Float): Float =
        if (expanding) {
            lerp(EnterScaleFrom, 1f, progress)
        } else {
            lerp(ExitScaleTo, 1f, progress)
        }

    /**
     * 相对高度的 Y 位移；progress>1 时过冲到另一侧。
     */
    fun appearSlideYFraction(isBottomSheet: Boolean, progress: Float): Float {
        val from = if (isBottomSheet) SheetSlideFraction else CenterSlideFraction
        return from * (1f - progress)
    }
}

/**
 * 弹窗内控件关闭时先播退场再卸载（参考 RN `exiting={FadeOut}`）。
 * 为 null 表示不在弹窗内，直接走原 onClick。
 */
internal val LocalGlassDialogAnimateExit =
    androidx.compose.runtime.staticCompositionLocalOf<(suspend () -> Unit)?> { null }

/**
 * 主窗口坐标系定位。`calculatePosition()` 返回值即弹窗在父窗口（主窗口）的坐标，
 * 直接作为 `regionBoundsOverride`，与 `GlassDropdownMenu` 同一套对齐公式。
 */
private class GlassPanelPositionProvider(
    private val position: GlassPanelPosition,
    private val yOffsetPx: Int = 0,
    private val onBoundsInMainWindow: (Rect) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0)
        val y = when (position) {
            GlassPanelPosition.Centered ->
                ((windowSize.height - popupContentSize.height) / 2 + yOffsetPx).coerceAtLeast(0)
            GlassPanelPosition.Bottom ->
                (windowSize.height - popupContentSize.height + yOffsetPx).coerceAtLeast(0)
        }
        onBoundsInMainWindow(
            Rect(
                left = x.toFloat(),
                top = y.toFloat(),
                right = (x + popupContentSize.width).toFloat(),
                bottom = (y + popupContentSize.height).toFloat(),
            )
        )
        return IntOffset(x, y)
    }
}

@Composable
private fun glassDialogFallbackColor(glassActive: Boolean) =
    if (glassActive) {
        // 与 GlassDropdownMenu 同档半透明底，避免二级弹窗发黑
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

/**
 * 通用玻璃面板：圆角 + 半透明底 + 细边，模糊开时走 [AdvancedGlassSurface] 真模糊，
 * 关时退 [fallbackColor] 实底。开关/模糊度：设置 → 动效 → 高级模糊 / 模糊度。
 *
 * 开合动效：设置 → 动效 → 连贯反馈（见 [GlassDialogMotion]）。
 */
@Composable
internal fun GlassPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassDialogShape,
    role: AdvancedGlassRole = AdvancedGlassRole.DialogPanel,
    position: GlassPanelPosition = GlassPanelPosition.Centered,
    maxWidth: Dp = 300.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 16.dp
        ),
    maxHeight: Dp = 420.dp,
    yOffset: Dp = 0.dp,
    /** 强制实底、不走模糊（嵌套叠层模糊异常时的兜底） */
    forceSolid: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled && !forceSolid
    val coherentFeedbackEnabled by AppContainer.settingsRepo
        .coherentFeedbackEnabledFlow
        .collectAsState(initial = false)
    var boundsInMainWindow by remember { mutableStateOf<Rect?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val yOffsetPx = with(density) { yOffset.roundToPx() }
    val positionProvider = remember(position, yOffsetPx) {
        GlassPanelPositionProvider(position, yOffsetPx) { boundsInMainWindow = it }
    }

    val scope = rememberCoroutineScope()
    // 先保持 0，等连贯反馈真实值到达后再决定「立刻显示」还是「弹出」
    val transformProgress = remember { Animatable(0f) }
    val opacityProgress = remember { Animatable(0f) }
    var contentAlive by remember { mutableStateOf(true) }
    var dismissing by remember { mutableStateOf(false) }
    var enterStarted by remember { mutableStateOf(false) }

    // 不能只依赖初始 false：DataStore 是异步的，第一帧几乎总是 initial=false，
    // 若 LaunchedEffect(Unit) 在此时短路，开关就算打开也永远不播动画。
    LaunchedEffect(coherentFeedbackEnabled) {
        if (!coherentFeedbackEnabled) {
            transformProgress.snapTo(1f)
            opacityProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (enterStarted || dismissing) return@LaunchedEffect
        enterStarted = true
        transformProgress.snapTo(0f)
        opacityProgress.snapTo(0f)
        coroutineScope {
            launch {
                transformProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = GlassDialogMotion.EnterTransformSpring,
                )
            }
            launch {
                opacityProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = GlassDialogMotion.EnterFadeMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    // 先播退场，再让调用方卸载/业务关闭（对应 RN exiting={FadeOut}）
    val animateExit: suspend () -> Unit = {
        if (!dismissing) {
            dismissing = true
            if (coherentFeedbackEnabled) {
                coroutineScope {
                    launch {
                        transformProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = GlassDialogMotion.ExitTransformSpring,
                        )
                    }
                    launch {
                        opacityProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = GlassDialogMotion.ExitFadeMs,
                                easing = FastOutLinearInEasing,
                            ),
                        )
                    }
                }
            } else {
                transformProgress.snapTo(0f)
                opacityProgress.snapTo(0f)
            }
            contentAlive = false
        }
    }

    val animatedDismiss: () -> Unit = {
        scope.launch {
            animateExit()
            onDismissRequest()
        }
    }

    if (!contentAlive) return

    CompositionLocalProvider(LocalGlassDialogAnimateExit provides animateExit) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = animatedDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            // 弹窗可能从 SettingsGroup/Section 等玻璃面内弹出，depth>0 会禁止采样。
        // Popup 是独立前景层，必须按 depth=0 注册，才能真正模糊背后内容。
        // 退场时立刻停注册：模糊挂在主窗口区域上，不随面板 alpha 消失，否则内容没了还留一块糊。
        CompositionLocalProvider(
            LocalGlassOverlayElevated provides true,
            LocalAdvancedGlassDepth provides 0,
            LocalAdvancedGlassBackdropRegistrationEnabled provides !dismissing,
        ) {
            // 内容自适应宽度/高度，禁止 fillMaxWidth 撑满（二级弹窗过大根因）
            Box(
                Modifier
                    .widthIn(min = 160.dp, max = maxWidth)
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        val t = transformProgress.value
                        // 居中弹窗：中心爆开；底部面板：贴底边（y=1）长出
                        transformOrigin = when (position) {
                            GlassPanelPosition.Centered -> TransformOrigin(0.5f, 0.5f)
                            GlassPanelPosition.Bottom -> TransformOrigin(0.5f, 1f)
                        }
                        val scale = GlassDialogMotion.appearScale(expanding = !dismissing, t)
                        scaleX = scale
                        scaleY = scale
                        alpha = opacityProgress.value
                        translationY = GlassDialogMotion.appearSlideYFraction(
                            isBottomSheet = position == GlassPanelPosition.Bottom,
                            progress = t,
                        ) * size.height
                    }
            ) {
                AdvancedGlassSurface(
                    role = role,
                    shape = shape,
                    fallbackColor = glassDialogFallbackColor(glassActive),
                    tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    enabled = glassActive,
                    // 退场时传空矩形，触发 region.remove，避免主窗口残留下模糊块
                    regionBoundsOverride = if (dismissing) Rect.Zero else boundsInMainWindow,
                    modifier = modifier
                        .widthIn(min = 160.dp, max = maxWidth)
                        .heightIn(max = maxHeight)
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 160.dp, max = maxWidth)
                            .heightIn(max = maxHeight)
                            .padding(contentPadding),
                        content = content,
                    )
                }
            }
        }
        }
    }
}

/**
 * 真模糊对话框：圆角 28.dp + 高级透明模糊。
 * API 对齐 Material AlertDialog，供 [DensityScaledAlertDialog] 复用。
 */
@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = GlassDialogShape,
    iconContentColor: androidx.compose.ui.graphics.Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: androidx.compose.ui.graphics.Color = AlertDialogDefaults.titleContentColor,
    textContentColor: androidx.compose.ui.graphics.Color = AlertDialogDefaults.textContentColor,
    maxWidth: Dp = 220.dp,
    maxHeight: Dp = 300.dp,
    yOffset: Dp = 0.dp,
    position: GlassPanelPosition = GlassPanelPosition.Centered,
    forceSolid: Boolean = false,
) {
    GlassPanel(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        role = AdvancedGlassRole.DialogPanel,
        position = position,
        // 默认紧凑宽；内容多的弹窗可传更大 maxWidth/maxHeight
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        yOffset = yOffset,
        forceSolid = forceSolid,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        icon()
                    }
                }
            }
            if (title != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        title()
                    }
                }
            }
            if (text != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        text()
                    }
                }
            }
            if (dismissButton == null) {
                // 无次要按钮时：关闭居中置底，加圆角描边更突出
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    confirmButton()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * 真模糊底部面板：默认顶部圆角 28.dp + 高级透明模糊。
 * role 用 [AdvancedGlassRole.DialogPanel]，只依赖基础高级模糊开关即可采样。
 * 居中浮层可传 [GlassPanelPosition.Centered] + [GlassDialogShape]（四角统一 28.dp）。
 */
@Composable
internal fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassSheetShape,
    position: GlassPanelPosition = GlassPanelPosition.Bottom,
    maxWidth: Dp = 300.dp,
    maxHeight: Dp = 420.dp,
    yOffset: Dp = 0.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 20.dp
        ),
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        role = AdvancedGlassRole.DialogPanel,
        position = position,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        yOffset = yOffset,
        contentPadding = contentPadding,
        content = content
    )
}
