package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/**
 * 切歌背景揭示状态：飞出/飞入阶段锁旧底，新封面入轨后按封面矩形向外涟漪揭示新底。
 * [progress]/[settle] 用普通 float state，切歌同帧可置 0（Animatable.snapTo 要挂起，会晚一帧）。
 */
@Stable
class TrackChangeBackgroundRevealState {
    /** 0=完全旧背景，1=完全新背景 */
    private val progressState = mutableFloatStateOf(1f)
    var progress: Float
        get() = progressState.floatValue
        set(value) {
            progressState.floatValue = value
        }

    /** 边界震荡相位；1f 表示已静止（不震） */
    private val settleState = mutableFloatStateOf(1f)
    var settle: Float
        get() = settleState.floatValue
        set(value) {
            settleState.floatValue = value
        }

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
 * progress=0 → 封面框内；progress=1 → 略超出整窗（圆角角点出屏，保持圆角不显直角）。
 * [bleedPx] 把目标矩形向外扩，避免收成直角贴边。
 */
fun expandRevealRect(
    cover: Rect,
    progress: Float,
    window: Rect,
    bleedPx: Float = 0f,
): Rect {
    val p = progress.coerceAtLeast(0f)
    val target = if (bleedPx > 0f) {
        Rect(
            window.left - bleedPx,
            window.top - bleedPx,
            window.right + bleedPx,
            window.bottom + bleedPx,
        )
    } else {
        window
    }
    return Rect(
        left = lerp(cover.left, target.left, p),
        top = lerp(cover.top, target.top, p),
        right = lerp(cover.right, target.right, p),
        bottom = lerp(cover.bottom, target.bottom, p),
    )
}

/** 以矩形自身中心缩放（边界震荡回弹用） */
fun scaleRectFromCenter(rect: Rect, scale: Float): Rect {
    if (scale == 1f) return rect
    val c = rect.center
    return Rect(
        left = c.x - rect.width * scale / 2f,
        top = c.y - rect.height * scale / 2f,
        right = c.x + rect.width * scale / 2f,
        bottom = c.y + rect.height * scale / 2f,
    )
}
