package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    /** 新封面延后入厂：旧封面先飞出，再跟上新封面（避免像被赶走） */
    const val IncomingDelayMs = 110L

    /** 缩放延后启动：等新封面已入轨再放大 */
    const val ScaleStartDelayMs = 190L

    /** 背景涟漪再拖后：等新封面更稳一点才扩散 */
    const val BackgroundRevealDelayMs = 380L

    const val SinkYFraction = 0.05f

    const val RippleDurationMs = 420
    const val RippleScaleTo = 3.6f

    /** 揭示圆角全程保持，不再收到 0 */
    const val RevealCornerRadiusDp = 24f

    /** 扩到边界后的震荡回弹时长 */
    const val RevealSettleDurationMs = 320

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

    /**
     * 背景涟漪揭示：前段约 0.9 倍速、后段约 0.5 倍速（比封面放大更拖尾）。
     * 时长 700ms，曲线前平后缓。
     */
    val BackgroundRevealSpec = tween<Float>(
        durationMillis = 700,
        easing = CubicBezierEasing(0.40f, 0.12f, 0.25f, 1f),
    )

    /** 边界震荡：线性推进相位，衰减画在 [revealBoundaryRing] */
    val RevealSettleSpec = tween<Float>(
        durationMillis = RevealSettleDurationMs,
        easing = FastOutSlowInEasing,
    )

    /** 新背景在揭示过程中的淡入（减轻突兀） */
    const val RevealFadeFromAlpha = 0.72f

    fun revealAlpha(progress: Float): Float = lerp(RevealFadeFromAlpha, 1f, progress)

    /**
     * 扩到边界后的阻尼震荡：先略过冲，再回缩，最后贴住。
     * [t]=0 刚到边界，[t]=1 震荡结束（=1f）。
     */
    fun revealBoundaryRing(t: Float): Float {
        if (t <= 0f || t >= 1f) return 1f
        val decay = kotlin.math.exp(-4.8f * t)
        val wave = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * 1.75f * t)
        return 1f + 0.042f * decay * wave
    }

    val RippleSpec = tween<Float>(
        durationMillis = RippleDurationMs,
        easing = FastOutSlowInEasing,
    )

    fun outgoingScale(slideP: Float): Float = lerp(1f, SideScale, slideP)

    /**
     * [forward]=true 下一首：旧封面向左、新封面自右。
     * [forward]=false 上一首：完全镜像（旧向右、新自左）。
     */
    fun outgoingTranslationX(slideP: Float, widthPx: Float, forward: Boolean = true): Float {
        val sign = if (forward) -1f else 1f
        return sign * PageSlideFraction * widthPx * slideP
    }

    fun outgoingTranslationY(slideP: Float, heightPx: Float): Float =
        SinkYFraction * heightPx * slideP

    /** 飞出封面淡化 */
    fun outgoingAlpha(slideP: Float): Float = lerp(1f, 0f, slideP)

    fun incomingScale(settleP: Float): Float = lerp(SideScale, 1f, settleP)

    fun incomingTranslationX(slideP: Float, widthPx: Float, forward: Boolean = true): Float {
        val sign = if (forward) 1f else -1f
        return sign * PageSlideFraction * widthPx * (1f - slideP)
    }

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
    forward: Boolean = true,
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
    // 位移与缩放分轨：缩放更长、尾段缓速；入厂再分轨，旧封面先走
    val slideProgress = remember { Animatable(1f) }
    val incomingSlideProgress = remember { Animatable(1f) }
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
        incomingSlideProgress.snapTo(0f)
        scaleProgress.snapTo(0f)
        backgroundReveal?.progress?.snapTo(0f)
        backgroundReveal?.settle?.snapTo(0f)
        coroutineScope {
            // 旧封面先飞出
            launch { slideProgress.animateTo(1f, TrackChangeCoverMotion.SlideSpec) }
            // 新封面稍后再入厂，避免像被旧封面顶走
            launch {
                kotlinx.coroutines.delay(TrackChangeCoverMotion.IncomingDelayMs)
                incomingSlideProgress.animateTo(1f, TrackChangeCoverMotion.SlideSpec)
            }
            launch {
                kotlinx.coroutines.delay(TrackChangeCoverMotion.ScaleStartDelayMs)
                scaleProgress.animateTo(1f, TrackChangeCoverMotion.ScaleSettleSpec)
            }
            launch {
                // 等新封面更稳一点，背景再涟漪；到边界后再震荡回弹
                kotlinx.coroutines.delay(TrackChangeCoverMotion.BackgroundRevealDelayMs)
                backgroundReveal?.progress?.animateTo(
                    1f,
                    TrackChangeCoverMotion.BackgroundRevealSpec,
                )
                backgroundReveal?.settle?.animateTo(
                    1f,
                    TrackChangeCoverMotion.RevealSettleSpec,
                )
            }
        }
        outgoingUrl = null
        outgoingKey = null
        backgroundReveal?.let {
            it.active = false
            it.fromCoverUrl = null
            it.progress?.snapTo(1f)
            it.settle?.snapTo(1f)
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
                        translationX = TrackChangeCoverMotion.outgoingTranslationX(sp, widthPx, forward)
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
                    val sp = incomingSlideProgress.value
                    val zp = scaleProgress.value
                    val s = TrackChangeCoverMotion.incomingScale(zp)
                    scaleX = s
                    scaleY = s
                    translationX = TrackChangeCoverMotion.incomingTranslationX(sp, widthPx, forward)
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
