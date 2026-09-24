package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassDepth
import moe.ouom.neriplayer.ui.effect.glass.LocalGlassOverlayElevated
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight

/**
 * 菜单定位：优先向上弹出；向下时不允许进入底部保留区（迷你播放器 + 底栏）。
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
    var x = anchor.left + offsetX
    if (x + popupSize.width > windowSize.width) {
        x = anchor.right - popupSize.width - offsetX
    }
    if (x < 0) x = 0
    if (x + popupSize.width > windowSize.width) {
        x = (windowSize.width - popupSize.width).coerceAtLeast(0)
    }

    // 1) 尽量向上
    var y = anchor.top - popupSize.height - offsetY
    val maxBottom = (windowSize.height - reservedBottomPx).coerceAtLeast(0)
    val maxTop = (maxBottom - popupSize.height).coerceAtLeast(0)

    // 2) 向上放不下，再向下；仍不得压住底部保留区
    if (y < offsetY) {
        y = anchor.bottom + offsetY
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
    private val onMenuBoundsInMainWindow: (Rect) -> Unit,
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
        onMenuBoundsInMainWindow(glassMenuBoundsInMainWindow(position, popupContentSize))
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled
    val density = LocalDensity.current
    val reservedBottom = LocalMiniPlayerHeight.current
    var menuBoundsInMainWindow by remember { mutableStateOf<Rect?>(null) }

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
            onMenuBoundsInMainWindow = { menuBoundsInMainWindow = it },
        ).also { it.attach(density) }
    }

    if (!expanded) return

    // 两层玻璃重叠时（菜单盖住 MiniPlayer/底栏），抬升标记让下层玻璃减淡；
    // depth 归零，避免从设置卡片等玻璃面内弹出时被禁止采样。
    CompositionLocalProvider(
        LocalGlassOverlayElevated provides true,
        LocalAdvancedGlassDepth provides 0,
    ) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            // DropdownMenuItem 自带 fillMaxWidth；Popup 无限宽时用 IntrinsicSize.Max
            // 收成「最宽一项」，再 clamp 到 240.dp（开发规则：选项下拉）。
            Box(
                Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(max = 240.dp)
                    .heightIn(max = 360.dp)
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
                        Column(Modifier.padding(vertical = 4.dp), content = content)
                    }
                }
            }
        }
    }
}

/**
 * 下拉菜单文案：居中 + 自适应省略（开发规则：下拉菜单文字居中）。
 */
@Composable
fun GlassMenuItemText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        style = LocalTextStyle.current
    )
}
