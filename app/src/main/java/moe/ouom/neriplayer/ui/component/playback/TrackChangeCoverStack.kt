package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.util.lerp

/**
 * 播放页切歌封面转场（方案 A，出/入同时）。
 *
 * 旧封面：缩小下沉 + 向左划走；新封面：自右飞入 + 放大到原尺寸。
 * 落位后回调 [onSettled]，上层播背景涟漪。
 */
object TrackChangeCoverMotion {
    const val DurationMs = 420
    const val OldScaleTo = 0.82f
    const val NewScaleFrom = 0.86f

    /** 相对封面宽度的横向位移（飞出/飞入） */
    const val SlideXFraction = 1.05f

    const val RippleDurationMs = 360
    const val RippleScaleTo = 3.2f

    val CoverSpec = tween<Float>(
        durationMillis = DurationMs,
        easing = FastOutSlowInEasing,
    )

    val RippleSpec = tween<Float>(
        durationMillis = RippleDurationMs,
        easing = FastOutSlowInEasing,
    )

    fun outgoingScale(progress: Float): Float = lerp(1f, OldScaleTo, progress)

    fun outgoingTranslationX(progress: Float, widthPx: Float): Float =
        -SlideXFraction * widthPx * progress

    fun incomingScale(progress: Float): Float = lerp(NewScaleFrom, 1f, progress)

    fun incomingTranslationX(progress: Float, widthPx: Float): Float =
        SlideXFraction * widthPx * (1f - progress)
}

/**
 * 双封面切歌栈：songKey/cover 变化时旧出新入**同时**进行。
 * [enabled] 关闭时直接显示当前封面（原行为）。
 */
@Composable
fun TrackChangeCoverStack(
    coverUrl: String?,
    songKey: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSettled: () -> Unit = {},
    onCoverCenterInRoot: (Offset) -> Unit = {},
    cover: @Composable (coverUrl: String?, songKey: String?) -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            onCoverCenterInRoot(
                Offset(
                    pos.x + coords.size.width / 2f,
                    pos.y + coords.size.height / 2f,
                )
            )
        }) {
            cover(coverUrl, songKey)
        }
        return
    }

    var outgoingUrl by remember { mutableStateOf<String?>(null) }
    var outgoingKey by remember { mutableStateOf<String?>(null) }
    var incomingUrl by remember { mutableStateOf(coverUrl) }
    var incomingKey by remember { mutableStateOf(songKey) }
    var animToken by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableStateOf(1f) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(songKey, coverUrl) {
        if (songKey == incomingKey && coverUrl == incomingUrl) {
            return@LaunchedEffect
        }
        outgoingUrl = incomingUrl
        outgoingKey = incomingKey
        incomingUrl = coverUrl
        incomingKey = songKey
        animToken += 1
    }

    LaunchedEffect(animToken) {
        if (animToken == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = TrackChangeCoverMotion.CoverSpec,
        )
        outgoingUrl = null
        outgoingKey = null
        onSettled()
    }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            widthPx = coords.size.width.toFloat().coerceAtLeast(1f)
            val pos = coords.positionInRoot()
            onCoverCenterInRoot(
                Offset(
                    pos.x + coords.size.width / 2f,
                    pos.y + coords.size.height / 2f,
                )
            )
        }
    ) {
        val p = progress.value
        val hasOutgoing = outgoingUrl != null || outgoingKey != null
        if (hasOutgoing) {
            Box(
                Modifier.graphicsLayer {
                    val s = TrackChangeCoverMotion.outgoingScale(p)
                    scaleX = s
                    scaleY = s
                    translationX = TrackChangeCoverMotion.outgoingTranslationX(p, widthPx)
                    alpha = (1f - p).coerceIn(0f, 1f)
                }
            ) {
                cover(outgoingUrl, outgoingKey)
            }
        }
        Box(
            Modifier.graphicsLayer {
                val s = TrackChangeCoverMotion.incomingScale(p)
                scaleX = s
                scaleY = s
                translationX = TrackChangeCoverMotion.incomingTranslationX(p, widthPx)
                alpha = if (hasOutgoing) p.coerceIn(0f, 1f) else 1f
            }
        ) {
            cover(incomingUrl, incomingKey)
        }
    }
}

/**
 * 落位涟漪：从封面中心撑开软光（YinYang 的 scaleEffect 思路）。
 */
@Composable
fun CoverSettleRipple(
    pulse: Int,
    origin: Offset,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (pulse <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = TrackChangeCoverMotion.RippleSpec,
        )
    }
    if (pulse <= 0) return
    val t = progress.value
    if (t <= 0f || t >= 1f) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = lerp(0.12f, TrackChangeCoverMotion.RippleScaleTo, t)
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleX = scale
                scaleY = scale
                alpha = (1f - t) * 0.5f
                translationX = origin.x - size.width / 2f
                translationY = origin.y - size.height / 2f
            }
            .background(color = Color.White.copy(alpha = 0.28f), shape = CircleShape)
    )
}
