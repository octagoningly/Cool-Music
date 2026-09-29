package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import moe.ouom.neriplayer.ui.component.common.SceneDepthMotion

/**
 * 媒体库歌单卡片到详情页的容器变换。
 *
 * 源矩形与详情根容器使用同一个根坐标系；详情内容始终按最终尺寸布局，
 * 这里只让圆角裁切窗口从卡片的上下/左右边沿展开，避免缩放详情内容导致错位。
 */
internal object PlaylistCardContainerMotion {
    const val OpenDurationMillis = SceneDepthMotion.OpenDurationMillis
    const val CloseDurationMillis = SceneDepthMotion.CloseDurationMillis
    const val SourceCornerRadiusDp = 12f
    /** 背景缩放幅度：约 20%（相对 2.5% 为 8 倍） */
    const val BackgroundExpandedScale = SceneDepthMotion.ExpandedScale
    const val BackgroundDimmedAlpha = 0.76f
    val OpenEasing = SceneDepthMotion.OpenEasing
    val CloseEasing = SceneDepthMotion.CloseEasing

    /** 上下边缘描边峰值透明度；暂时关掉柔边 */
    const val EdgeStrokeAlpha = 0f

    fun sourceBoundsInViewport(sourceInRoot: Rect, viewportInRoot: Rect): Rect = Rect(
        left = sourceInRoot.left - viewportInRoot.left,
        top = sourceInRoot.top - viewportInRoot.top,
        right = sourceInRoot.right - viewportInRoot.left,
        bottom = sourceInRoot.bottom - viewportInRoot.top
    )

    fun sanitizeSourceBounds(
        source: Rect?,
        viewportWidth: Float,
        viewportHeight: Float
    ): Rect? {
        if (
            source == null ||
            !viewportWidth.isFinite() ||
            !viewportHeight.isFinite() ||
            viewportWidth <= 1f ||
            viewportHeight <= 1f
        ) {
            return null
        }
        val clipped = Rect(
            left = source.left.coerceIn(0f, viewportWidth),
            top = source.top.coerceIn(0f, viewportHeight),
            right = source.right.coerceIn(0f, viewportWidth),
            bottom = source.bottom.coerceIn(0f, viewportHeight)
        )
        return clipped.takeIf { it.width > 1f && it.height > 1f }
    }

    fun frame(
        source: Rect,
        viewportWidth: Float,
        viewportHeight: Float,
        progress: Float
    ): PlaylistCardContainerFrame {
        val p = progress.coerceIn(0f, 1f)
        val contentReveal = (p / 0.46f).coerceIn(0f, 1f)
        val contentFade = contentReveal * contentReveal * (3f - 2f * contentReveal)
        val backgroundPivotFractionX =
            ((source.left + source.right) * 0.5f / viewportWidth).coerceIn(0f, 1f)
        val backgroundPivotFractionY =
            ((source.top + source.bottom) * 0.5f / viewportHeight).coerceIn(0f, 1f)
        return PlaylistCardContainerFrame(
            clipLeft = lerp(source.left, 0f, p),
            clipTop = lerp(source.top, 0f, p),
            clipRight = lerp(source.right, viewportWidth, p),
            clipBottom = lerp(source.bottom, viewportHeight, p),
            cornerRadiusDp = lerp(SourceCornerRadiusDp, 0f, p),
            // 实底始终不透明；详情内容在卡片边界附近平滑淡入/淡出。
            contentAlpha = contentFade,
            backgroundScale = lerp(1f, BackgroundExpandedScale, p),
            backgroundAlpha = lerp(1f, BackgroundDimmedAlpha, p),
            backgroundPivotFractionX = backgroundPivotFractionX,
            backgroundPivotFractionY = backgroundPivotFractionY,
            edgeAlpha = 0f
        )
    }
}

internal data class PlaylistCardContainerFrame(
    val clipLeft: Float,
    val clipTop: Float,
    val clipRight: Float,
    val clipBottom: Float,
    val cornerRadiusDp: Float,
    val contentAlpha: Float,
    val backgroundScale: Float,
    val backgroundAlpha: Float,
    val backgroundPivotFractionX: Float,
    val backgroundPivotFractionY: Float,
    val edgeAlpha: Float = 0f
)

/** 只裁切详情绘制，不改变它的全屏布局与触摸坐标。 */
internal fun Modifier.playlistCardContainerClip(
    frame: PlaylistCardContainerFrame,
    cornerRadiusPx: Float,
    edgeColor: Color = Color.White
): Modifier = drawWithCache {
    val revealPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = frame.clipLeft,
                top = frame.clipTop,
                right = frame.clipRight,
                bottom = frame.clipBottom,
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
            )
        )
    }
    val strokePx = 2.dp.toPx()
    val edgeAlpha = frame.edgeAlpha
    onDrawWithContent {
        clipPath(revealPath) {
            this@onDrawWithContent.drawContent()
        }
        // 上下柔边：让「窗在长高」在深色底上看得见；圆角处留空避免生硬直角
        if (edgeAlpha > 0.01f) {
            val color = edgeColor.copy(alpha = edgeColor.alpha * edgeAlpha)
            val halfStroke = strokePx / 2f
            val leftInset = frame.clipLeft + cornerRadiusPx + halfStroke
            val rightInset = frame.clipRight - cornerRadiusPx - halfStroke
            if (rightInset > leftInset) {
                drawLine(
                    color = color,
                    start = Offset(leftInset, frame.clipTop + halfStroke),
                    end = Offset(rightInset, frame.clipTop + halfStroke),
                    strokeWidth = strokePx
                )
                drawLine(
                    color = color,
                    start = Offset(leftInset, frame.clipBottom - halfStroke),
                    end = Offset(rightInset, frame.clipBottom - halfStroke),
                    strokeWidth = strokePx
                )
            }
        }
    }
}
