package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.util.lerp

/**
 * 歌单卡片「纵向双向 Unfold」——上下边同时释放 + clip 揭示。
 *
 * 不是 scaleY 拉伸，也不是整卡放大飞全屏：
 * left/right 尽量稳住，top↑ / bottom↓ 扩出真实可见矩形，
 * 内容按完整布局绘制，仅被 clip 边界逐渐露出。
 */
object PlaylistVerticalUnfoldMotion {
    /** 主体展开（含边沿错峰） */
    const val DurationMs = 520

    /** bottom 先松动 4~8dp，再与 top 一起打开 */
    const val BottomLeadFraction = 0.06f
    const val TopDelayFraction = 0.14f

    /** 收起圆角 → 展开圆角 */
    const val CollapsedCornerRadius = 24f
    const val ExpandedCornerRadius = 12f

    val EdgeEasing: Easing = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)

    val UnfoldSpec = tween<Float>(durationMillis = DurationMs, easing = EdgeEasing)
    val FoldSpec = tween<Float>(durationMillis = 420, easing = EdgeEasing)

    /** 下沿：略早启动，先「松开」 */
    fun bottomEdgeProgress(progress: Float): Float {
        val lead = BottomLeadFraction
        val t = ((progress - lead * 0.35f) / (1f - lead * 0.35f)).coerceIn(0f, 1f)
        return t
    }

    /** 上沿：略晚启动，形成解锁感 */
    fun topEdgeProgress(progress: Float): Float {
        val delay = TopDelayFraction
        val t = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        return t
    }

    /**
     * 计算展开过程中的 clip 矩形。
     * 卡片靠近屏顶/屏底时，调整上下展开比例，但两边仍都有位移。
     */
    fun clipRect(
        card: Rect,
        window: Rect,
        progress: Float,
    ): Rect {
        val p = progress.coerceIn(0f, 1f)
        val topP = topEdgeProgress(p)
        val bottomP = bottomEdgeProgress(p)

        // 可用空间决定上下分配：默认 40% / 60%，顶贴屏则 15% / 85%
        val roomAbove = (card.top - window.top).coerceAtLeast(1f)
        val roomBelow = (window.bottom - card.bottom).coerceAtLeast(1f)
        val totalRoom = roomAbove + roomBelow
        var upShare = 0.40f
        if (roomAbove < totalRoom * 0.22f) upShare = 0.15f
        else if (roomBelow < totalRoom * 0.22f) upShare = 0.85f

        val expandUp = (card.top - window.top) * upShare
        val expandDown = (window.bottom - card.bottom) * (1f - upShare)

        // 左右轻微外扩，避免「贴条」感，但不是全屏 zoom
        val expandX = ((window.width - card.width) / 2f) * 0.22f

        val top = card.top - expandUp * topP
        val bottom = card.bottom + expandDown * bottomP
        val left = (card.left - expandX * p).coerceAtLeast(window.left)
        val right = (card.right + expandX * p).coerceAtMost(window.right)
        return Rect(left, top, right, bottom)
    }

    fun cornerRadius(progress: Float): Float =
        lerp(CollapsedCornerRadius, ExpandedCornerRadius, progress.coerceIn(0f, 1f))
}

/**
 * 将内容裁切到 Unfold 可见矩形：内容本身不缩放，只露出来。
 */
@Stable
fun Modifier.playlistVerticalUnfoldClip(
    card: Rect,
    window: Rect,
    progress: Float,
): Modifier {
    if (progress >= 0.999f) return this
    val rect = PlaylistVerticalUnfoldMotion.clipRect(card, window, progress)
    return this.drawWithContent {
        clipRect(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
        ) {
            this@drawWithContent.drawContent()
        }
    }
}

/** 驱动 Unfold 的 0..1 进度 */
@Stable
class PlaylistUnfoldState {
    val progress = Animatable(0f)
    var cardRect: Rect = Rect.Zero
    var windowRect: Rect = Rect.Zero
}
