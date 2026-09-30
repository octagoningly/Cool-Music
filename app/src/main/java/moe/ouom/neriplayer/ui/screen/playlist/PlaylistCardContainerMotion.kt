package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import moe.ouom.neriplayer.ui.component.common.SceneDepthMotion

/**
 * 歌曲行点击时上报 bounds（root 坐标），供播放页做上下沿开窗。
 * 未上报时播放页走迷你栏式 slide。
 */
val LocalNowPlayingOpenBoundsReporter =
    staticCompositionLocalOf<((Rect) -> Unit)?> { null }

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
    /** 背景缩放幅度：约 40%（在 SceneDepthMotion 1.20 的 20% 上 ×2，便于试手感） */
    const val BackgroundExpandedScale = 1.40f
    const val BackgroundDimmedAlpha = 0.76f
    /**
     * 起步稍慢：前 1/4 时间窗仍贴着行高，能看清「从上下沿长高」；
     * 中段拉完，末段柔和落位。过强 ease-out 会像一上来就撑满屏。
     */
    val OpenEasing = CubicBezierEasing(0.35f, 0f, 0.15f, 1f)
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
        // 窗口从卡片上下沿长高：前段贴住行高（二次方起步），后段撑满全屏。
        // 线性 lerp 会让前几帧就高出一截，看起来像「上下留白很大」。
        val grow = p * p
        val clipLeft = lerp(source.left, 0f, grow)
        val clipTop = lerp(source.top, 0f, grow)
        val clipRight = lerp(source.right, viewportWidth, grow)
        val clipBottom = lerp(source.bottom, viewportHeight, grow)

        // 详情钉在全屏坐标（scale=1、位移=0）：只靠裁切窗揭示。
        // 若 content 跟 clipTop 走，展开时内容会整体上移 → 抽屉感。
        val contentFade = smoothStep(start = 0.04f, end = 0.42f, value = p)
        val cornerProgress = smoothStep(start = 0.18f, end = 1f, value = p)
        // 背景景深：先快后慢（ease-out），前段就拉开层次，后段缓缓贴到终值
        val depthProgress = 1f - (1f - p) * (1f - p)
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

/**
 * 播放页/详情开窗：从行矩形上下沿同时撑到全屏。
 * 窗外透明露出下层页面；[visible] 驱动进入 0→1、退出 1→0。
 */
@Composable
fun Modifier.playlistOpenWindowClip(
    origin: Rect?,
    visible: Boolean,
    viewportWidth: Float,
    viewportHeight: Float,
): Modifier {
    if (origin == null ||
        !viewportWidth.isFinite() || !viewportHeight.isFinite() ||
        viewportWidth <= 1f || viewportHeight <= 1f
    ) {
        return this
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (visible) {
                PlaylistCardContainerMotion.OpenDurationMillis
            } else {
                PlaylistCardContainerMotion.CloseDurationMillis
            },
            easing = if (visible) {
                PlaylistCardContainerMotion.OpenEasing
            } else {
                PlaylistCardContainerMotion.CloseEasing
            }
        ),
        label = "playlist_open_window"
    )
    val frame = PlaylistCardContainerMotion.frame(
        source = origin,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        progress = progress
    )
    val cornerRadiusPx = with(LocalDensity.current) {
        frame.cornerRadiusDp.dp.toPx()
    }
    return playlistCardContainerClip(frame = frame, cornerRadiusPx = cornerRadiusPx)
}
