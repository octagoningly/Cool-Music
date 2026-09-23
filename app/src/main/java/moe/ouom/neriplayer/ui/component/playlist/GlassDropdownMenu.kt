package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController

/**
 * 下拉菜单透明材质（真模糊）。
 *
 * 与 MiniPlayer 同一套 AdvancedGlass：菜单窗体透明，主窗口 content 背景
 * 在菜单区域被模糊后透出；开关/模糊度跟随设置 → 动效 → 高级模糊。
 *
 * 用法（锚点同级，放在 [Box] 内）：
 * ```
 * Box {
 *     IconButton(onClick = { expanded = true }) { ... }
 *     GlassDropdownMenu(expanded, onDismissRequest = { expanded = false }) { ... }
 * }
 * ```
 *
 * 注意：内容区不要再包 [androidx.compose.foundation.verticalScroll] /
 * [androidx.compose.foundation.layout.IntrinsicSize]，否则会在无限高度约束下崩溃。
 * 菜单滚动由 Material [DropdownMenu] 自身负责。
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
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }

    // 测量锚点父 Box 在主窗口中的位置（弹窗是独立 Window，需映射回主窗口）
    Box(
        Modifier
            .fillMaxWidth(0f)
            .heightIn(max = 0.dp)
            .onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) {
                    anchorBounds = coordinates.boundsInWindow()
                }
            }
    )

    if (!expanded) return

    val anchor = anchorBounds
    val regionOverride = if (anchor != null && menuSize.width > 0 && menuSize.height > 0) {
        Rect(
            left = anchor.left,
            top = anchor.bottom + 4f,
            right = anchor.left + menuSize.width.toFloat(),
            bottom = anchor.bottom + 4f + menuSize.height.toFloat(),
        )
    } else {
        null
    }

    val fallbackColor = if (glassActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .onGloballyPositioned { coordinates ->
                    menuSize = coordinates.size
                }
        ) {
            AdvancedGlassSurface(
                role = AdvancedGlassRole.PopupMenu,
                shape = shape,
                fallbackColor = fallbackColor,
                tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                enabled = glassActive,
                regionBoundsOverride = regionOverride,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    content = content,
                )
            }
        }
    }
}
