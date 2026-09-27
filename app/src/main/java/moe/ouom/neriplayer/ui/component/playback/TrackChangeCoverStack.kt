package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 播放页切歌封面转场 —— 仿苹果横滑（pager）+ 轻缩放。
 *
 * 同一 progress：旧封面向左滑出一页并略缩小，新封面自右滑入并放大到原尺寸。
 * 不要贴在 clip 的封面框内做，否则会「在框里消失」。
 */
object TrackChangeCoverMotion {
    /** 横滑段 */
    const val SlideDurationMs = 480

    /** 缩放段更长，尾段缓速落到标准大小 */
    const val ScaleDurationMs = 580

    /** 横滑距离 = 封面宽 × 1.0（一页，并排露出） */
    const val PageSlideFraction = 1.0f

    /** 离场/入场端的缩小幅度（再压到当前 0.8 倍，更明显） */
    const val SideScale = 0.58f

    /** 缩放延后启动：先横滑，再放大（放大时才刷新背景） */
    const val ScaleStartDelayMs = 170L

    const val SinkYFraction = 0.05f

    const val RippleDurationMs = 420
    const val RippleScaleTo = 3.6f

    /** 横滑：匀顺 */
    val SlideSpec = tween<Float>(
        durationMillis = SlideDurationMs,
        easing = FastOutSlowInEasing,
    )

    /** 放大到标准大小：尾段缓速，更优雅 */
    val ScaleSettleSpec = tween<Float>(
        durationMillis = ScaleDurationMs,
        easing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f),
    )

    val RippleSpec = tween<Float>(
        durationMillis = RippleDurationMs,
        easing = FastOutSlowInEasing,
    )

    fun outgoingScale(slideP: Float): Float = lerp(1f, SideScale, slideP)

    fun outgoingTranslationX(slideP: Float, widthPx: Float): Float =
        -PageSlideFraction * widthPx * slideP

    fun outgoingTranslationY(slideP: Float, heightPx: Float): Float =
        SinkYFraction * heightPx * slideP

    /** 飞出封面淡化 */
    fun outgoingAlpha(slideP: Float): Float = lerp(1f, 0f, slideP)

    fun incomingScale(settleP: Float): Float = lerp(SideScale, 1f, settleP)

    fun incomingTranslationX(slideP: Float, widthPx: Float): Float =
        PageSlideFraction * widthPx * (1f - slideP)

    fun incomingTranslationY(slideP: Float, heightPx: Float): Float =
        SinkYFraction * heightPx * (1f - slideP)

    fun incomingAlpha(slideP: Float): Float = lerp(0.7f, 1f, slideP)
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
    backgroundReveal: TrackChangeBackgroundRevealState? = null,
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
    // 位移与缩放分轨：缩放更长、尾段缓速
    val slideProgress = remember { Animatable(1f) }
    val scaleProgress = remember { Animatable(1f) }

    LaunchedEffect(songKey, coverUrl) {
        if (songKey == incomingKey && coverUrl == incomingUrl) return@LaunchedEffect
        val fromUrl = incomingUrl
        outgoingUrl = fromUrl
        outgoingKey = incomingKey
        incomingUrl = coverUrl
        incomingKey = songKey
        backgroundReveal?.let { rev ->
            rev.fromCoverUrl = fromUrl
            rev.toCoverUrl = coverUrl
            rev.active = true
        }
        animToken += 1
    }

    LaunchedEffect(animToken) {
        if (animToken == 0) return@LaunchedEffect
        slideProgress.snapTo(0f)
        scaleProgress.snapTo(0f)
        backgroundReveal?.progress?.snapTo(0f)
        coroutineScope {
            launch { slideProgress.animateTo(1f, TrackChangeCoverMotion.SlideSpec) }
            launch {
                // 先横滑；新封面开始放大后，背景才涟漪揭示
                kotlinx.coroutines.delay(TrackChangeCoverMotion.ScaleStartDelayMs)
                scaleProgress.animateTo(1f, TrackChangeCoverMotion.ScaleSettleSpec)
            }
            launch {
                kotlinx.coroutines.delay(TrackChangeCoverMotion.ScaleStartDelayMs)
                backgroundReveal?.progress?.animateTo(
                    1f,
                    TrackChangeCoverMotion.ScaleSettleSpec,
                )
            }
        }
        outgoingUrl = null
        outgoingKey = null
        backgroundReveal?.let {
            it.active = false
            it.fromCoverUrl = null
            it.progress?.snapTo(1f)
        }
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
                val center = Offset(
                    pos.x + coords.size.width / 2f,
                    pos.y + coords.size.height / 2f,
                )
                onCoverCenterInRoot(center)
                backgroundReveal?.coverBoundsInRoot = androidx.compose.ui.geometry.Rect(
                    pos.x,
                    pos.y,
                    pos.x + coords.size.width,
                    pos.y + coords.size.height,
                )
            }
    ) {
        val hasOutgoing = outgoingUrl != null || outgoingKey != null
        // 在 graphicsLayer 内读 Animatable，避免每帧重组
        if (hasOutgoing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = false
                        val sp = slideProgress.value
                        val s = TrackChangeCoverMotion.outgoingScale(sp)
                        scaleX = s
                        scaleY = s
                        translationX = TrackChangeCoverMotion.outgoingTranslationX(sp, widthPx)
                        translationY = TrackChangeCoverMotion.outgoingTranslationY(sp, heightPx)
                        alpha = TrackChangeCoverMotion.outgoingAlpha(sp)
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
                    val sp = slideProgress.value
                    val zp = scaleProgress.value
                    val s = TrackChangeCoverMotion.incomingScale(zp)
                    scaleX = s
                    scaleY = s
                    translationX = TrackChangeCoverMotion.incomingTranslationX(sp, widthPx)
                    translationY = TrackChangeCoverMotion.incomingTranslationY(sp, heightPx)
                    alpha = if (hasOutgoing) {
                        TrackChangeCoverMotion.incomingAlpha(sp)
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
