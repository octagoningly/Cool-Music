package moe.ouom.neriplayer.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * AnimatedContent/SizeTransform 会用无限 maxHeight 测量子树，
 * 滚动容器（LazyColumn / verticalScroll）不允许无限高。此修饰符在无限时钳到 [fallbackMaxHeightPx]。
 */
fun Modifier.boundedMaxHeight(fallbackMaxHeightPx: Int): Modifier = layout { measurable, constraints ->
    val coerced = if (constraints.hasBoundedHeight) {
        constraints
    } else {
        Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = constraints.minHeight,
            maxHeight = fallbackMaxHeightPx.coerceAtLeast(constraints.minHeight)
        )
    }
    val placeable = measurable.measure(coerced)
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}
