package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/**
 * 媒体库歌单卡片到详情页的容器变换。
 *
 * 源矩形与详情根容器使用同一个根坐标系；详情内容始终按最终尺寸布局，
 * 这里只插值平移、非等比缩放与裁切圆角，避免抽屉式整页上移/下移。
 */
internal object PlaylistCardContainerMotion {
    const val OpenDurationMillis = 460
    const val CloseDurationMillis = 360
    const val SourceCornerRadiusDp = 12f

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
        return PlaylistCardContainerFrame(
            translationX = lerp(source.left, 0f, p),
            translationY = lerp(source.top, 0f, p),
            scaleX = lerp(source.width / viewportWidth, 1f, p),
            scaleY = lerp(source.height / viewportHeight, 1f, p),
            cornerRadiusDp = lerp(SourceCornerRadiusDp, 0f, p),
            // 开头先让原卡片承担视觉，再快速交给详情内容，避免缩小文字叠在卡片上。
            contentAlpha = ((p - 0.04f) / 0.22f).coerceIn(0f, 1f)
        )
    }
}

internal data class PlaylistCardContainerFrame(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val cornerRadiusDp: Float,
    val contentAlpha: Float
)
