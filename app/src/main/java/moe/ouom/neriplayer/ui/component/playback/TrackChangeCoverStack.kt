package moe.ouom.neriplayer.ui.component.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 播放页切歌封面转场（方案 A，出/入同时）。
 *
 * 旧封面：缩小 + **飞出屏幕左侧**；新封面：自右飞入 + 放大到原尺寸。
 * 位移偏快、落位缩放偏慢；落位后回调 [onSettled] 播背景涟漪（先快后慢）。
 */
object TrackChangeCoverMotion {
    const val DurationMs = 420
    const val OldScaleTo = 0.82f
    const val NewScaleFrom = 0.86f

    const val RippleDurationMs = 360
    const val RippleScaleTo = 3.2f

    /** 飞出/飞入：快（先快后慢） */
    val SlideEasing: Easing = LinearOutSlowInEasing

    /** 新封面放大到原尺寸：尾段缓速收束 */
    val SettleEasing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** 背景涟漪：先快后慢 */
    val RippleEasing: Easing = LinearOutSlowInEasing

    val SlideSpec = tween<Float>(durationMillis = 300, easing = SlideEasing)
    val ScaleSpec = tween<Float>(durationMillis = DurationMs, easing = SettleEasing)
    val RippleSpec = tween<Float>(durationMillis = RippleDurationMs, easing = RippleEasing)

    fun outgoingScale(slideProgress: Float): Float = lerp(1f, OldScaleTo, slideProgress)

    /** [flyDistancePx] 需覆盖「封面中心 → 屏幕左缘」 */
    fun outgoingTranslationX(slideProgress: Float, flyDistancePx: Float): Float =
        -flyDistancePx * slideProgress

    fun incomingScale(scaleProgress: Float): Float = lerp(NewScaleFrom, 1f, scaleProgress)

    fun incomingTranslationX(slideProgress: Float, flyDistancePx: Float): Float =
        flyDistancePx * (1f - slideProgress)
}

/**
 * 双封面切歌栈：songKey/cover 变化时旧出新入**同时**。
 *
 * 注意：本组件**不要**再被父级 clip 到封面圆角框，否则飞出会「在框里消失」。
 * 圆角画在每个封面层上，飞行时仍可离开原位置。
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
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

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
    val slideProgress = remember { Animatable(1f) }
    val scaleProgress = remember { Animatable(1f) }

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
        slideProgress.snapTo(0f)
        scaleProgress.snapTo(0f)
        // 位移更快跑完；缩放更长，尾段缓速落到原大小
        coroutineScope {
            launch {
                slideProgress.animateTo(1f, TrackChangeCoverMotion.SlideSpec)
            }
            launch {
                scaleProgress.animateTo(1f, TrackChangeCoverMotion.ScaleSpec)
            }
        }
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
        // 从封面中心飞到屏幕左缘（略过头），保证整张划出屏幕
        val flyDistancePx = (screenWidthPx * 0.5f + widthPx).coerceAtLeast(widthPx * 2f)
        val sp = slideProgress.value
        val zp = scaleProgress.value
        val hasOutgoing = outgoingUrl != null || outgoingKey != null

        if (hasOutgoing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = TrackChangeCoverMotion.outgoingScale(sp)
                        scaleX = s
                        scaleY = s
                        translationX = TrackChangeCoverMotion.outgoingTranslationX(sp, flyDistancePx)
                        // 略下沉
                        translationY = 24f * sp
                        alpha = (1f - sp * 1.15f).coerceIn(0f, 1f)
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
                    val s = TrackChangeCoverMotion.incomingScale(zp)
                    scaleX = s
                    scaleY = s
                    translationX = TrackChangeCoverMotion.incomingTranslationX(sp, flyDistancePx)
                    alpha = if (hasOutgoing) sp.coerceIn(0f, 1f) else 1f
                }
                .clip(cornerRadius)
        ) {
            cover(incomingUrl, incomingKey)
        }
    }
}

/**
 * 落位涟漪：从封面中心撑开，先快后慢。
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
