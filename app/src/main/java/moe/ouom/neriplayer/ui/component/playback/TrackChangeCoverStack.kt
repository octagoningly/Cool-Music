package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * 播放页切歌封面转场 —— 仿苹果横滑（pager）+ 轻缩放。
 *
 * 同一 progress：旧封面向左滑出一页并略缩小，新封面自右滑入并放大到原尺寸。
 * 不要贴在 clip 的封面框内做，否则会「在框里消失」。
 */
object TrackChangeCoverMotion {
    /** 偏慢速版，先保证「看得出在滑、在缩放」 */
    const val DurationMs = 500

    /** 横滑距离 = 封面宽 × 1.0（正好一页，并排露出） */
    const val PageSlideFraction = 1.0f

    /** 轻缩放：离场略小、入场略小 → 回到 1 */
    const val SideScale = 0.86f

    const val SinkYFraction = 0.06f

    const val RippleDurationMs = 420
    const val RippleScaleTo = 3.6f

    /** iOS 风格：整段匀顺缓入缓出，不用甩飞曲线 */
    val CoverSpec = tween<Float>(
        durationMillis = DurationMs,
        easing = FastOutSlowInEasing,
    )

    val RippleSpec = tween<Float>(
        durationMillis = RippleDurationMs,
        easing = FastOutSlowInEasing,
    )

    fun outgoingScale(p: Float): Float = lerp(1f, SideScale, p)

    fun outgoingTranslationX(p: Float, widthPx: Float): Float =
        -PageSlideFraction * widthPx * p

    fun outgoingTranslationY(p: Float, heightPx: Float): Float =
        SinkYFraction * heightPx * p

    fun incomingScale(p: Float): Float = lerp(SideScale, 1f, p)

    fun incomingTranslationX(p: Float, widthPx: Float): Float =
        PageSlideFraction * widthPx * (1f - p)

    fun incomingTranslationY(p: Float, heightPx: Float): Float =
        SinkYFraction * heightPx * (1f - p)

    fun outgoingAlpha(p: Float): Float = lerp(1f, 0f, p)

    fun incomingAlpha(p: Float): Float = lerp(0.55f, 1f, p)
}

/**
 * 双封面切歌栈：出/入同时横滑。[enabled] 关闭时只显示当前封面。
 */
@Composable
fun TrackChangeCoverStack(
    coverUrl: String?,
    songKey: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: RoundedCornerShape = RoundedCornerShape(24.dp),
    onSettled: () -> Unit = {},
    onCoverCenterInRoot: (Offset) -> Unit = {},
    cover: @Composable (coverUrl: String?, songKey: String?) -> Unit,
) {
    if (!enabled) {
        Box(
            modifier = modifier
                .clip(cornerRadius)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    onCoverCenterInRoot(
                        Offset(
                            pos.x + coords.size.width / 2f,
                            pos.y + coords.size.height / 2f,
                        )
                    )
                }
        ) {
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
    var heightPx by remember { mutableStateOf(1f) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(songKey, coverUrl) {
        if (songKey == incomingKey && coverUrl == incomingUrl) return@LaunchedEffect
        outgoingUrl = incomingUrl
        outgoingKey = incomingKey
        incomingUrl = coverUrl
        incomingKey = songKey
        animToken += 1
    }

    LaunchedEffect(animToken) {
        if (animToken == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, TrackChangeCoverMotion.CoverSpec)
        outgoingUrl = null
        outgoingKey = null
        onSettled()
    }

    // 根节点不 clip，位移可离开封面框
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .onGloballyPositioned { coords ->
                widthPx = coords.size.width.toFloat().coerceAtLeast(1f)
                heightPx = coords.size.height.toFloat().coerceAtLeast(1f)
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
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = false
                        val s = TrackChangeCoverMotion.outgoingScale(p)
                        scaleX = s
                        scaleY = s
                        translationX = TrackChangeCoverMotion.outgoingTranslationX(p, widthPx)
                        translationY = TrackChangeCoverMotion.outgoingTranslationY(p, heightPx)
                        alpha = TrackChangeCoverMotion.outgoingAlpha(p)
                    }
                    .clip(cornerRadius)
            ) {
                cover(outgoingUrl, outgoingKey)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                    val s = TrackChangeCoverMotion.incomingScale(p)
                    scaleX = s
                    scaleY = s
                    translationX = TrackChangeCoverMotion.incomingTranslationX(p, widthPx)
                    translationY = TrackChangeCoverMotion.incomingTranslationY(p, heightPx)
                    alpha = if (hasOutgoing) {
                        TrackChangeCoverMotion.incomingAlpha(p)
                    } else {
                        1f
                    }
                }
                .clip(cornerRadius)
        ) {
            cover(incomingUrl, incomingKey)
        }
    }
}

/**
 * 落位涟漪：从封面中心撑开。
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
        progress.animateTo(1f, TrackChangeCoverMotion.RippleSpec)
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
