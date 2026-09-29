package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.animation.core.CubicBezierEasing
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
 * 观感：以点击的那一行为窗，**上下沿同时向外长高**（全宽行则左右已满）。
 * 详情内容钉在全屏坐标不动；窗外仍是歌单列表，窗内才是详情。
 * 不要把内容再 translationY 到 clipTop——那会把详情往上拽，慢放就是抽屉从底下顶上来。
 */
internal object PlaylistCardContainerMotion {
    const val OpenDurationMillis = 600
    const val CloseDurationMillis = 480
    const val SourceCornerRadiusDp = 12f
    /** 背景缩放幅度：约 20%（相对 2.5% 为 8 倍） */
    const val BackgroundExpandedScale = SceneDepthMotion.ExpandedScale
    const val BackgroundDimmedAlpha = 0.76f
    /**
     * Material emphasized 风格：起步保留卡片形态，中段完成主要形变，末段柔和落位。
     * 不复用景深层的强 ease-out，否则窄高差的歌单行会在前 1/3 时间内近乎铺满屏幕，
     * 剩余时间只剩静止长尾，观感会重新退化为抽屉弹出。
     */
    val OpenEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)
    val CloseEasing = CubicBezierEasing(0.32f, 0f, 0.20f, 1f)

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
        // 窗口：从卡片矩形同时向上下/左右撑到全屏（全宽行主要是上下沿同时长高）
        val clipLeft = lerp(source.left, 0f, p)
        val clipTop = lerp(source.top, 0f, p)
        val clipRight = lerp(source.right, viewportWidth, p)
        val clipBottom = lerp(source.bottom, viewportHeight, p)

        // 详情钉在全屏坐标（scale=1、位移=0）：只靠裁切窗揭示。
        // 若 content 跟 clipTop 走，展开时内容会整体上移 → 抽屉感。
        val contentFade = smoothStep(start = 0.04f, end = 0.42f, value = p)
        val cornerProgress = smoothStep(start = 0.18f, end = 1f, value = p)
        val depthProgress = smoothStep(start = 0.04f, end = 1f, value = p)
        val backgroundPivotFractionX =
            ((source.left + source.right) * 0.5f / viewportWidth).coerceIn(0f, 1f)
        val backgroundPivotFractionY =
            ((source.top + source.bottom) * 0.5f / viewportHeight).coerceIn(0f, 1f)
        return PlaylistCardContainerFrame(
            clipLeft = clipLeft,
            clipTop = clipTop,
            clipRight = clipRight,
            clipBottom = clipBottom,
            cornerRadiusDp = lerp(SourceCornerRadiusDp, 0f, cornerProgress),
            // 整层（含底色）跟 contentAlpha 一起淡，末帧露出真实歌单行
            contentAlpha = contentFade,
            contentScale = 1f,
            contentTranslationX = 0f,
            contentTranslationY = 0f,
            backgroundScale = lerp(1f, BackgroundExpandedScale, depthProgress),
            backgroundAlpha = lerp(1f, BackgroundDimmedAlpha, depthProgress),
            backgroundPivotFractionX = backgroundPivotFractionX,
            backgroundPivotFractionY = backgroundPivotFractionY,
            edgeAlpha = 0f
        )
    }

    private fun smoothStep(start: Float, end: Float, value: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }
}

internal data class PlaylistCardContainerFrame(
    val clipLeft: Float,
    val clipTop: Float,
    val clipRight: Float,
    val clipBottom: Float,
    val cornerRadiusDp: Float,
    val contentAlpha: Float,
    val contentScale: Float,
    val contentTranslationX: Float,
    val contentTranslationY: Float,
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
