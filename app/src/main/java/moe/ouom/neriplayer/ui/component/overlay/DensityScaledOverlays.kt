package moe.ouom.neriplayer.ui.component.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val LocalOverlaySurfaceScale = compositionLocalOf { 1f }

internal fun shouldScaleOverlaySurface(surfaceScale: Float): Boolean =
    abs(surfaceScale - 1f) > 0.001f

internal fun resolveScaledOverlaySurfaceMaxWidth(
    availableWidthPx: Int,
    minimumWidthPx: Int,
    surfaceScale: Float
): Int {
    if (availableWidthPx == Constraints.Infinity) {
        return availableWidthPx
    }
    val normalizedScale = if (surfaceScale.isFinite()) {
        surfaceScale.coerceAtLeast(0f)
    } else {
        1f
    }
    return (availableWidthPx * normalizedScale)
        .roundToInt()
        .coerceIn(minimumWidthPx, availableWidthPx)
}

@Composable
internal fun DensityScaledAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = GlassDialogShape,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    // 统一走 GlassAlertDialog：圆角 28 + 高级透明模糊（设置 → 动效 → 高级模糊 / 模糊度）
    GlassAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = Modifier.scaleOverlaySurfaceWidth(LocalOverlaySurfaceScale.current).then(modifier),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DensityScaledModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = GlassSheetShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = {
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Top)
    },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    // 统一走 GlassModalBottomSheet：主窗口内真模糊（Material ModalBottomSheet 是独立
    // Window，只能半透明、采不到背后内容）。拖拽把手保留为视觉件，点外关闭。
    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = shape,
    ) {
        if (dragHandle != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                dragHandle()
            }
        }
        content()
    }
}

private fun Modifier.scaleOverlaySurfaceWidth(surfaceScale: Float): Modifier {
    if (!shouldScaleOverlaySurface(surfaceScale)) {
        return this
    }
    return layout { measurable, constraints ->
        val scaledConstraints = constraints.copy(
            maxWidth = resolveScaledOverlaySurfaceMaxWidth(
                availableWidthPx = constraints.maxWidth,
                minimumWidthPx = constraints.minWidth,
                surfaceScale = surfaceScale
            )
        )
        val placeable = measurable.measure(scaledConstraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
