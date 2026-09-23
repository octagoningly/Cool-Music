package moe.ouom.neriplayer.ui.component.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.ui.effect.glass.LocalAdvancedGlassController

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
 * 主窗口坐标系定位。`calculatePosition()` 返回值即弹窗在父窗口（主窗口）的坐标，
 * 直接作为 `regionBoundsOverride`，与 `GlassDropdownMenu` 同一套对齐公式。
 */
private class GlassPanelPositionProvider(
    private val position: GlassPanelPosition,
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
                ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0)
            GlassPanelPosition.Bottom ->
                (windowSize.height - popupContentSize.height).coerceAtLeast(0)
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
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

/**
 * 通用玻璃面板：圆角 + 半透明底 + 细边，模糊开时走 [AdvancedGlassSurface] 真模糊，
 * 关时退 [fallbackColor] 实底。开关/模糊度：设置 → 动效 → 高级模糊 / 模糊度。
 */
@Composable
internal fun GlassPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassDialogShape,
    role: AdvancedGlassRole = AdvancedGlassRole.DialogPanel,
    position: GlassPanelPosition = GlassPanelPosition.Centered,
    maxWidth: Dp = 360.dp,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            horizontal = 24.dp,
            vertical = 20.dp
        ),
    content: @Composable ColumnScope.() -> Unit
) {
    val controller = LocalAdvancedGlassController.current
    val glassActive = controller.isBaseBlurEnabled
    var boundsInMainWindow by remember { mutableStateOf<Rect?>(null) }
    val positionProvider = remember(position) {
        GlassPanelPositionProvider(position) { boundsInMainWindow = it }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .width(IntrinsicSize.Max)
                .heightIn(max = 640.dp)
        ) {
            AdvancedGlassSurface(
                role = role,
                shape = shape,
                fallbackColor = glassDialogFallbackColor(glassActive),
                tintColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                enabled = glassActive,
                regionBoundsOverride = boundsInMainWindow,
                modifier = modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding),
                    content = content,
                )
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
) {
    GlassPanel(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
        role = AdvancedGlassRole.DialogPanel,
        position = GlassPanelPosition.Centered,
        maxWidth = 360.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        icon()
                    }
                }
            }
            if (title != null) {
                Box(Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        title()
                    }
                }
            }
            if (text != null) {
                Box(Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalContentColor provides textContentColor) {
                        text()
                    }
                }
            }
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

/**
 * 真模糊底部面板：顶部圆角 28.dp + 高级透明模糊。
 */
@Composable
internal fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassSheetShape,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        role = AdvancedGlassRole.PlaylistSheet,
        position = GlassPanelPosition.Bottom,
        maxWidth = 720.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 12.dp,
            bottom = 24.dp
        ),
        content = content
    )
}
