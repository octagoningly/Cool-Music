package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
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

/**
 * 与 Material DropdownMenu 相同的锚点定位，同时把 **主窗口坐标** 写出来。
 *
 * 关键：[PopupPositionProvider.calculatePosition] 的返回值就是弹窗在
 * **父窗口（主窗口）** 中的坐标，与 MiniPlayer 的 `boundsInWindow` 同一体系，
 * 可直接注册进玻璃区域，无需屏幕坐标换算。
 */
private class GlassMenuPositionProvider(
    private val contentOffset: DpOffset,
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

        // 默认对齐锚点左下；靠边时翻转，与 Material DropdownMenu 一致
        var x = anchorBounds.left + offsetX
        var y = anchorBounds.bottom + offsetY
        if (x + popupContentSize.width > windowSize.width) {
            x = anchorBounds.right - popupContentSize.width - offsetX
        }
        if (x < 0) x = 0
        if (y + popupContentSize.height > windowSize.height) {
            y = anchorBounds.top - popupContentSize.height - offsetY
        }
        if (y < 0) y = 0

        onMenuBoundsInMainWindow(
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

/**
 * 下拉菜单透明材质（真模糊）。
 *
 * 与 MiniPlayer 同一套两层结构：
 * 1. 主窗口 content 背景层在菜单区域做模糊
 * 2. 菜单本体透明，只叠 tint，模糊从背后透出
 *
 * 开关/模糊度：设置 → 动效 → 高级模糊 / 模糊度。
 *
 * 用法（锚点同级，放在 [Box] 内）：
 * ```
 * Box {
 *     IconButton(onClick = { expanded = true }) { ... }
 *     GlassDropdownMenu(expanded, onDismissRequest = { expanded = false }) { ... }
 * }
 * ```
 */
@Composable
fun BoxScope.GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled
    val density = LocalDensity.current
    var menuBoundsInMainWindow by remember { mutableStateOf<Rect?>(null) }

    val fallbackColor = if (glassActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val positionProvider = remember(density) {
        GlassMenuPositionProvider(
            contentOffset = DpOffset(0.dp, 8.dp),
            onMenuBoundsInMainWindow = { menuBoundsInMainWindow = it },
        ).also { it.attach(density) }
    }

    if (!expanded) return

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // DropdownMenuItem 自带 fillMaxWidth；Popup 无限宽时必须用 IntrinsicSize.Max
        // 收到最宽子项。不要同时套 verticalScroll（会触发无限高度崩溃）。
        Box(
            Modifier
                .width(IntrinsicSize.Max)
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
                Column(
                    Modifier.padding(vertical = 4.dp),
                    content = content,
                )
            }
        }
    }
}
