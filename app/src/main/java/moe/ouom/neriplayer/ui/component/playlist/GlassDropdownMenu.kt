package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
    const val EnterDurationMs = 180
    const val ExitDurationMs = 120
    const val EnterScaleFrom = 0.86f
    const val ExitScaleTo = 0.92f

    /** 相对菜单高度的微位移比例（从按钮方向长出） */
    const val SlideFraction = 0.08f

    fun transformOrigin(opensUpward: Boolean): TransformOrigin =
        if (opensUpward) TransformOrigin(1f, 1f) else TransformOrigin(1f, 0f)

    /** progress: 0=收起, 1=展开；展开从 EnterScaleFrom，收回停在 ExitScaleTo */
    fun appearScale(expanded: Boolean, progress: Float): Float =
        if (expanded) {
            lerp(EnterScaleFrom, 1f, progress)
        } else {
            lerp(ExitScaleTo, 1f, progress)
        }

    /** progress: 0=收起, 1=展开；返回相对菜单高度的 Y 位移（px 由调用方乘 height） */
    fun appearSlideYFraction(opensUpward: Boolean, progress: Float): Float {
        val fromButton = if (opensUpward) SlideFraction else -SlideFraction
        return fromButton * (1f - progress)
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

/**
 * 菜单定位：优先向下弹出；下方几乎放不下才翻到上方（上弹时额外上移避让底栏）。
 * 返回主窗口坐标，供玻璃区域注册。
 */
internal fun resolveGlassMenuPosition(
    anchor: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    offsetX: Int,
    offsetY: Int,
    reservedBottomPx: Int,
): IntOffset {
    // 右侧按钮优先右对齐，避免弹窗甩到左边
    var x = anchor.right - popupSize.width
    if (x < offsetX) {
        x = anchor.left + offsetX
    }
    if (x < 0) x = 0
    if (x + popupSize.width > windowSize.width) {
        x = (windowSize.width - popupSize.width).coerceAtLeast(0)
    }

    // 优先向下弹出；下方几乎放不下才翻到上方
    val maxBottom = (windowSize.height - reservedBottomPx).coerceAtLeast(0)
    val maxTop = (maxBottom - popupSize.height).coerceAtLeast(0)
    var y = anchor.bottom + offsetY
    val roomBelow = maxBottom - y
    if (roomBelow < minOf(popupSize.height / 4, 96)) {
        // 上弹时额外上移，避免与底部 Dock/工具栏重叠
        y = anchor.top - popupSize.height - offsetY - 72
    }
    if (y + popupSize.height > maxBottom) {
        y = maxTop
    }
    if (y < offsetY) y = offsetY
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
    private val onMenuPlacement: (bounds: Rect, opensUpward: Boolean) -> Unit,
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
        )
        val opensUpward = glassMenuOpensUpward(
            position = position,
            anchor = anchorBounds,
            popupSize = popupContentSize,
        )
        onMenuPlacement(glassMenuBoundsInMainWindow(position, popupContentSize), opensUpward)
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled
    val density = LocalDensity.current
    val reservedBottom = LocalMiniPlayerHeight.current
    val coherentFeedbackEnabled by AppContainer.settingsRepo
        .coherentFeedbackEnabledFlow
        .collectAsState(initial = false)
    var menuBoundsInMainWindow by remember { mutableStateOf<Rect?>(null) }
    var opensUpward by remember { mutableStateOf(false) }

    val fallbackColor = if (glassActive) {
        // 玻璃开启时的半透明底：要能透出模糊，又保证文字可读
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val positionProvider = remember(density, reservedBottom) {
        GlassMenuPositionProvider(
            contentOffset = DpOffset(0.dp, 8.dp),
            reservedBottomPx = { with(density) { reservedBottom.roundToPx() } },
            onMenuPlacement = { bounds, up ->
                menuBoundsInMainWindow = bounds
                opensUpward = up
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
            content = content,
        )
        return
    }

    val progress = remember { Animatable(0f) }
    var contentAlive by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            contentAlive = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GlassMenuMotion.EnterDurationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            if (!contentAlive) return@LaunchedEffect
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = GlassMenuMotion.ExitDurationMs,
                    easing = FastOutLinearInEasing,
                ),
            )
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
        modifier = Modifier.graphicsLayer {
            val t = progress.value
            transformOrigin = GlassMenuMotion.transformOrigin(opensUpward)
            val scale = GlassMenuMotion.appearScale(expanded, t)
            scaleX = scale
            scaleY = scale
            alpha = t
            translationY = GlassMenuMotion.appearSlideYFraction(opensUpward, t) * size.height
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
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    // 两层玻璃重叠时（菜单盖住 MiniPlayer/底栏），抬升标记让下层玻璃减淡；
    // depth 归零 + 强制允许注册，避免被外层「播放页禁用主 Tab 注册」误伤。
    CompositionLocalProvider(
        LocalGlassOverlayElevated provides true,
        LocalAdvancedGlassDepth provides 0,
        LocalAdvancedGlassBackdropRegistrationEnabled provides true,
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
                    regionBoundsOverride = menuBoundsInMainWindow,
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
