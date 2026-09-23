package moe.ouom.neriplayer.ui.component.playlist

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 下拉菜单透明材质（真模糊）。
 *
 * 与 MiniPlayer 同一套 AdvancedGlass：菜单窗体透明，主窗口 content 背景
 * 在菜单区域被模糊后透出；开关/模糊度跟随设置 → 动效 → 高级模糊。
 *
 * 坐标：Material [DropdownMenu] 在独立 Window，必须换算到主窗口坐标
 * （屏幕坐标 − **Activity** DecorView 原点；不能用弹窗自己的 rootView）。
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
    val popupView = LocalView.current
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var menuBoundsInMainWindow by remember { mutableStateOf<Rect?>(null) }

    val fallbackColor = if (glassActive) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    if (!expanded) return

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
                    if (!coordinates.isAttached) {
                        menuBoundsInMainWindow = null
                        return@onGloballyPositioned
                    }
                    val local = coordinates.boundsInWindow()
                    val posInPopup = coordinates.positionInWindow()
                    val popupLoc = IntArray(2)
                    val decorLoc = IntArray(2)
                    popupView.getLocationOnScreen(popupLoc)
                    // 必须用 Activity 的 DecorView，弹窗 rootView 是另一个 Window
                    val decor: View? = popupView.context.findActivity()?.window?.decorView
                        ?: (popupView.parent as? View)
                    decor?.getLocationOnScreen(decorLoc)
                    val screenLeft = popupLoc[0] + posInPopup.x
                    val screenTop = popupLoc[1] + posInPopup.y
                    menuBoundsInMainWindow = Rect(
                        left = screenLeft - decorLoc[0],
                        top = screenTop - decorLoc[1],
                        right = screenLeft - decorLoc[0] + local.width,
                        bottom = screenTop - decorLoc[1] + local.height,
                    )
                }
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
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    content = content,
                )
            }
        }
    }
}
