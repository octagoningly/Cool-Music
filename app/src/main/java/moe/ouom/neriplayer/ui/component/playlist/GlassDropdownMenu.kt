package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassBackdropRegistrationEnabled
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassDepth
import moe.ouom.neriplayer.ui.effect.glass.LocalGlassOverlayElevated
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight

/**
 * 「三点」溢出菜单的锚点缩放淡入（连贯反馈开启时）。
 *
 * 参考 compose-animations Example2 的 scale+fade+TransformOrigin，
 * 以及系统 Menu「从按钮角落长出」的语义：原点贴右上角（下弹）或右下角（上弹），
 * 叠加约 8% 高度的微位移。关闭更快，与封面收回快于展开一致。
 *
 * 设置 → 动效 → 连贯反馈（`coherent_feedback_enabled`）关闭时保持原瞬时开合。
 */
object GlassMenuMotion {
    /** 淡入淡出时长（透明度不跟弹簧过冲，避免闪一下）；约 1.3 倍手感 */
    const val EnterFadeMs = 234
    const val ExitFadeMs = 156

    const val EnterScaleFrom = 0.76f
    const val ExitScaleTo = 0.92f

    /** 微位移比例：从远离 ⋮ 的一侧靠拢，避免弹出过程中盖住按钮 */
    const val SlideFraction = 0.08f

    /**
     * Q 弹：欠阻尼弹簧，冲过 1.0 再回弹不足，震荡几次收住。
     * 时长约 1.3 倍：stiffness = Medium(1500) / 1.3² ≈ 888。
     */
    val EnterTransformSpring = spring<Float>(
        dampingRatio = 0.48f,
        stiffness = 888f,
    )

    /** 收回：仍带一点弹性，但不明显过冲 */
    val ExitTransformSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 888f,
    )

    /**
     * 缩放原点：贴在靠近 ⋮ 的那一角。
     * 菜单在按钮左侧 → 右缘靠按钮（pivotX=1）；右侧 → 左缘（pivotX=0）。
     */
    fun transformOrigin(opensUpward: Boolean, menuLeftOfAnchor: Boolean): TransformOrigin {
        val pivotX = if (menuLeftOfAnchor) 1f else 0f
        return if (opensUpward) TransformOrigin(pivotX, 1f) else TransformOrigin(pivotX, 0f)
    }

    /**
     * progress: 0=收起, 1=展开；弹簧可 >1（过冲）或 <1（回弹不足）。
     * 展开从 EnterScaleFrom 到 1，收回从 1 到 ExitScaleTo。
     */
    fun appearScale(expanded: Boolean, progress: Float): Float =
        if (expanded) {
            lerp(EnterScaleFrom, 1f, progress)
        } else {
            lerp(ExitScaleTo, 1f, progress)
        }

    /**
     * progress: 0=收起, 1=展开；返回相对宽/高的位移比例。
     * 从**远离锚点按钮**的一侧靠拢，弹出过程不经过 ⋮。
     */
    fun appearSlideFractions(
        opensUpward: Boolean,
        menuLeftOfAnchor: Boolean,
        progress: Float,
    ): androidx.compose.ui.geometry.Offset {
        val t = 1f - progress
        val dx = if (menuLeftOfAnchor) -SlideFraction * t else SlideFraction * t
        val dy = if (opensUpward) -SlideFraction * t else SlideFraction * t
        return androidx.compose.ui.geometry.Offset(dx, dy)
    }

    /**
     * 玻璃模糊区域随菜单缩放同步收缩/放大，关闭时与内容同时消失。
     * 绕与 [transformOrigin] 相同的角点缩放，避免糊块和面板脱节。
     */
    fun blurBounds(
        base: Rect,
        progress: Float,
        expanding: Boolean,
        opensUpward: Boolean,
        menuLeftOfAnchor: Boolean,
    ): Rect {
        // 退场时 appearScale 只收到 0.92，必须再乘 progress，模糊才能跟内容一起收到 0
        val scale = if (expanding) {
            appearScale(true, progress)
        } else {
            appearScale(false, progress) * progress.coerceIn(0f, 1f)
        }.coerceIn(0f, 1.5f)
        if (scale <= 0.02f) return Rect.Zero
        val pivotX = if (menuLeftOfAnchor) base.right else base.left
        val pivotY = if (opensUpward) base.bottom else base.top
        val width = base.width * scale
        val height = base.height * scale
        val left = if (menuLeftOfAnchor) pivotX - width else pivotX
        val top = if (opensUpward) pivotY - height else pivotY
        return Rect(left, top, left + width, top + height)
    }
}

/**
 * 菜单是否向上弹出（内容在锚点上方）。用于选 TransformOrigin / 微位移方向。
 */
internal fun glassMenuOpensUpward(
    position: IntOffset,
    anchor: IntRect,
    popupSize: IntSize,
): Boolean = position.y + popupSize.height <= anchor.top + 1

/** 菜单是否在锚点按钮左侧（右侧 ⋮ 常用，弹窗避开按钮本身）。 */
internal fun glassMenuLeftOfAnchor(
    position: IntOffset,
    popupSize: IntSize,
    anchor: IntRect,
): Boolean {
    val menuCenterX = position.x + popupSize.width / 2
    val anchorCenterX = (anchor.left + anchor.right) / 2
    return menuCenterX <= anchorCenterX
}

/**
 * 菜单定位：**贴近锚点按钮**（重命名/删除等 ⋮ 菜单），同时不盖住按钮本身。
 *
 * 默认在按钮下方右对齐（经典下拉，离按钮近）；空间不够才上弹。
 * 仍与按钮保持最小空隙，兜底保证不与 ⋮ 相交。
 * 返回主窗口坐标，供玻璃区域注册。
 */
internal fun resolveGlassMenuPosition(
    anchor: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    offsetX: Int,
    offsetY: Int,
    reservedBottomPx: Int,
    preferDownward: Boolean = false,
): IntOffset {
    // 与 ⋮ 的空隙：够看清按钮即可，不要拉太远
    val gapX = maxOf(offsetX, 8)
    val gapY = maxOf(offsetY, 8)
    val edgeMargin = 8

    val maxBottom = (windowSize.height - reservedBottomPx).coerceAtLeast(0)
    val maxTop = (maxBottom - popupSize.height).coerceAtLeast(0)

    // —— 横向：与按钮右对齐（贴近 ⋮，经典下拉） ——
    var x = anchor.right - popupSize.width
    if (x < edgeMargin) {
        x = anchor.left + gapX
    }
    if (x < edgeMargin) x = edgeMargin
    if (x + popupSize.width > windowSize.width - edgeMargin) {
        x = (windowSize.width - edgeMargin - popupSize.width).coerceAtLeast(edgeMargin)
    }

    // —— 纵向：默认优先向下贴着按钮；preferDownward 时顶边不得高于按钮 ——
    val minDownY = anchor.bottom + gapY
    var y = minDownY
    val roomBelow = maxBottom - y
    val flipUpThreshold = if (preferDownward) 0 else minOf(popupSize.height / 4, 96)
    if (!preferDownward && roomBelow < flipUpThreshold) {
        // 上弹时额外上移，避免与底部 Dock/工具栏重叠
        y = anchor.top - popupSize.height - gapY - 72
    }
    if (y + popupSize.height > maxBottom) {
        y = if (preferDownward) {
            // 顶边贴着按钮下沿，宁可底部被裁也不把菜单顶到按钮上方
            minDownY
        } else {
            maxTop
        }
    }
    if (preferDownward) {
        y = y.coerceAtLeast(minDownY)
    }
    if (y < edgeMargin) y = edgeMargin

    // —— 兜底：仍与按钮相交则推开，保证 ⋮ 不被挡 ——
    val overlapsAnchor =
        x < anchor.right && x + popupSize.width > anchor.left &&
            y < anchor.bottom && y + popupSize.height > anchor.top
    if (overlapsAnchor) {
        val below = anchor.bottom + gapY
        val above = anchor.top - popupSize.height - gapY
        y = when {
            below + popupSize.height <= maxBottom -> below
            !preferDownward && above >= edgeMargin -> above
            else -> y
        }
    }

    return IntOffset(x, y)
}

internal fun glassMenuBoundsInMainWindow(
    position: IntOffset,
    popupSize: IntSize,
): Rect = Rect(
    left = position.x.toFloat(),
    top = position.y.toFloat(),
    right = (position.x + popupSize.width).toFloat(),
    bottom = (position.y + popupSize.height).toFloat(),
)

/**
 * 与 Material DropdownMenu 相同的锚点定位，同时把 **主窗口坐标** 写出来。
 *
 * 关键：[PopupPositionProvider.calculatePosition] 的返回值就是弹窗在
 * **父窗口（主窗口）** 中的坐标，与 MiniPlayer 的 `boundsInWindow` 同一体系，
 * 可直接注册进玻璃区域，无需屏幕坐标换算。
 */
private class GlassMenuPositionProvider(
    private val contentOffset: DpOffset,
    private val reservedBottomPx: () -> Int,
    private val preferDownward: Boolean = false,
    private val onMenuPlacement: (bounds: Rect, opensUpward: Boolean, menuLeftOfAnchor: Boolean) -> Unit,
) : PopupPositionProvider {
    private var density: Density = Density(1f)

    fun attach(density: Density) {
        this.density = density
    }

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val offsetX = with(density) { contentOffset.x.roundToPx() }
        val offsetY = with(density) { contentOffset.y.roundToPx() }
        val position = resolveGlassMenuPosition(
            anchor = anchorBounds,
            windowSize = windowSize,
            popupSize = popupContentSize,
            offsetX = offsetX,
            offsetY = offsetY,
            reservedBottomPx = reservedBottomPx(),
            preferDownward = preferDownward,
        )
        val opensUpward = if (preferDownward) {
            false
        } else {
            glassMenuOpensUpward(
                position = position,
                anchor = anchorBounds,
                popupSize = popupContentSize,
            )
        }
        val menuLeftOfAnchor = glassMenuLeftOfAnchor(
            position = position,
            popupSize = popupContentSize,
            anchor = anchorBounds,
        )
        onMenuPlacement(
            glassMenuBoundsInMainWindow(position, popupContentSize),
            opensUpward,
            menuLeftOfAnchor,
        )
        return position
    }
}

/**
 * 下拉菜单透明材质（真模糊）。
 *
 * 与 MiniPlayer 同一套两层结构：
 * 1. 主窗口 content 背景层在菜单区域做模糊
 * 2. 菜单本体透明，只叠 tint，模糊从背后透出
 *
 * 开关/模糊度：设置 → 动效 → 高级模糊 / 模糊度。
 * 开合动效：设置 → 动效 → 连贯反馈（见 [GlassMenuMotion]）。
 *
 * 用法（任意 Composable 作用域）：
 * ```
 * IconButton(onClick = { expanded = true }) { ... }
 * GlassDropdownMenu(expanded, onDismissRequest = { expanded = false }) { ... }
 * ```
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    maxWidth: Dp = 200.dp,
    // 歌曲列表可达 8 项（含本地详情/分享），默认加高避免截断
    maxHeight: Dp = 440.dp,
    forceSolid: Boolean = false,
    preferDownward: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled && !forceSolid
    val density = LocalDensity.current
    val reservedBottom = LocalMiniPlayerHeight.current
    val coherentFeedbackEnabled by AppContainer.settingsRepo
        .coherentFeedbackEnabledFlow
        .collectAsState(initial = false)
    var menuBoundsInMainWindow by remember { mutableStateOf<Rect?>(null) }
    var opensUpward by remember { mutableStateOf(false) }
    var menuLeftOfAnchor by remember { mutableStateOf(true) }

    val fallbackColor = if (glassActive) {
        // 玻璃开启时的半透明底：要能透出模糊，又保证文字可读
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val positionProvider = remember(density, reservedBottom, preferDownward) {
        GlassMenuPositionProvider(
            contentOffset = DpOffset(0.dp, 8.dp),
            reservedBottomPx = { with(density) { reservedBottom.roundToPx() } },
            preferDownward = preferDownward,
            onMenuPlacement = { bounds, up, leftOfAnchor ->
                menuBoundsInMainWindow = bounds
                opensUpward = up
                menuLeftOfAnchor = leftOfAnchor
            },
        ).also { it.attach(density) }
    }

    // 连贯反馈关闭：保持原瞬时开合
    if (!coherentFeedbackEnabled) {
        if (!expanded) return
        GlassMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            positionProvider = positionProvider,
            shape = shape,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            fallbackColor = fallbackColor,
            glassActive = glassActive,
            menuBoundsInMainWindow = menuBoundsInMainWindow,
            appearProgress = 1f,
            opensUpward = opensUpward,
            menuLeftOfAnchor = menuLeftOfAnchor,
            content = content,
        )
        return
    }

    val transformProgress = remember { Animatable(0f) }
    val opacityProgress = remember { Animatable(0f) }
    var contentAlive by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            contentAlive = true
            transformProgress.snapTo(0f)
            opacityProgress.snapTo(0f)
            coroutineScope {
                // 变换走 Q 弹弹簧（可过冲/震荡）；透明度单独淡入，不跟着过冲
                launch {
                    transformProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = GlassMenuMotion.EnterTransformSpring,
                    )
                }
                launch {
                    opacityProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = GlassMenuMotion.EnterFadeMs,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            }
        } else {
            if (!contentAlive) return@LaunchedEffect
            coroutineScope {
                launch {
                    transformProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = GlassMenuMotion.ExitTransformSpring,
                    )
                }
                launch {
                    opacityProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = GlassMenuMotion.ExitFadeMs,
                            easing = FastOutLinearInEasing,
                        ),
                    )
                }
            }
            contentAlive = false
        }
    }

    if (!expanded && !contentAlive) return

    GlassMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        positionProvider = positionProvider,
        shape = shape,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        fallbackColor = fallbackColor,
        glassActive = glassActive,
        menuBoundsInMainWindow = menuBoundsInMainWindow,
        appearProgress = transformProgress.value,
        opensUpward = opensUpward,
        menuLeftOfAnchor = menuLeftOfAnchor,
        modifier = Modifier.graphicsLayer {
            val t = transformProgress.value
            transformOrigin = GlassMenuMotion.transformOrigin(opensUpward, menuLeftOfAnchor)
            val scale = GlassMenuMotion.appearScale(expanded, t)
            scaleX = scale
            scaleY = scale
            alpha = opacityProgress.value
            val slide = GlassMenuMotion.appearSlideFractions(opensUpward, menuLeftOfAnchor, t)
            translationX = slide.x * size.width
            translationY = slide.y * size.height
        },
        content = content,
    )
}

@Composable
private fun GlassMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    positionProvider: GlassMenuPositionProvider,
    shape: Shape,
    maxWidth: Dp,
    maxHeight: Dp,
    fallbackColor: Color,
    glassActive: Boolean,
    menuBoundsInMainWindow: Rect?,
    appearProgress: Float = 1f,
    opensUpward: Boolean = false,
    menuLeftOfAnchor: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    // 两层玻璃重叠时（菜单盖住 MiniPlayer/底栏），抬升标记让下层玻璃减淡；
    // depth 归零 + 强制允许注册，避免被外层「播放页禁用主 Tab 注册」误伤。
    // 模糊块与面板同步缩放，关闭时同时消失（不能提前整块抹掉，也不能拖到内容没了还留着）。
    val blurBounds = menuBoundsInMainWindow?.let {
        GlassMenuMotion.blurBounds(
            base = it,
            progress = appearProgress,
            expanding = expanded,
            opensUpward = opensUpward,
            menuLeftOfAnchor = menuLeftOfAnchor,
        )
    }
    val keepBlurRegion = expanded || appearProgress > 0.02f
    CompositionLocalProvider(
        LocalGlassOverlayElevated provides true,
        LocalAdvancedGlassDepth provides 0,
        LocalAdvancedGlassBackdropRegistrationEnabled provides keepBlurRegion,
    ) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            // 内容包住文字；widthIn 只做上下限，避免被量成一字或撑满
            Box(
                modifier
                    .heightIn(max = maxHeight)
            ) {
                AdvancedGlassSurface(
                    role = AdvancedGlassRole.PopupMenu,
                    shape = shape,
                    fallbackColor = fallbackColor,
                    tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    enabled = glassActive,
                    regionBoundsOverride = blurBounds,
                ) {
                    // 开发规则：下拉菜单文字居中
                    CompositionLocalProvider(
                        LocalTextStyle provides LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .widthIn(min = 112.dp, max = maxWidth)
                                .heightIn(max = maxHeight)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/**
 * 下拉菜单文案：居中，字号与列表操作一致，宽度跟内容走。
 */
@Composable
fun GlassMenuItemText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        style = MaterialTheme.typography.bodyLarge
    )
}

/**
 * 包住内容的菜单条目：图标 + 文案刚好撑开，不被拉成固定宽。
 */
@Composable
fun GlassMenuActionItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(10.dp))
        }
        content()
    }
}

/**
 * 面板/底部弹窗内的菜单条目：与 [GlassDropdownMenu] 同一套视觉
 * （图标 + 文字居中），可选副文案也居中。
 */
@Composable
fun GlassSheetMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
) {
    GlassMenuActionItem(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassMenuItemText(text)
            if (supportingContent != null) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center
                    )
                ) {
                    supportingContent()
                }
            }
        }
    }
}
