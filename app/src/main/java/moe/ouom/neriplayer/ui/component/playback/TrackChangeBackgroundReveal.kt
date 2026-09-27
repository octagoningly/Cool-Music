package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/**
 * 切歌背景揭示状态：飞出/飞入阶段锁旧底，新封面开始放大后按封面矩形向外涟漪揭示新底。
 */
@Stable
class TrackChangeBackgroundRevealState {
    /** 0=完全旧背景，1=完全新背景 */
    val progress = Animatable(1f)

    var fromCoverUrl: String? = null
    var toCoverUrl: String? = null

    /** 封面在根坐标中的矩形，涟漪从该矩形四边向外扩 */
    var coverBoundsInRoot: Rect = Rect.Zero

    var active: Boolean = false
}

val LocalTrackChangeBackgroundReveal =
    staticCompositionLocalOf { TrackChangeBackgroundRevealState() }

/**
 * 由封面矩形向外扩张的揭示区域（四边涟漪）。
 * progress=0 → 封面框内；progress=1 → 整窗。
 */
fun expandRevealRect(
    cover: Rect,
    progress: Float,
    window: Rect,
): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerp(cover.left, window.left, p),
        top = lerp(cover.top, window.top, p),
        right = lerp(cover.right, window.right, p),
        bottom = lerp(cover.bottom, window.bottom, p),
    )
}
