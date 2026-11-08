package moe.ouom.neriplayer.ui.screen.tab.settings.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 关于页 hero 的流动渐变背景（mesh gradient 观感）。
 *
 * 设计参考自常见开源项目的关于页：
 * - 底色用当前 [ColorScheme] 的 container 色轮换生成，用户换主题色时自动跟随；
 * - 四个径向光斑缓慢游走，叠加出层次；
 * - 底部用 [BlendMode.DstIn] 竖直淡出，让背景自然融进 surface 而不是一条硬边。
 *
 * 所有颜色插值都发生在 draw 阶段（Canvas 内），因此动画每帧只重绘、不重组。
 */
@Composable
internal fun AboutGradientBackdrop(
    fadeProgress: Float,
    scrollTranslationPx: Float,
    modifier: Modifier = Modifier,
    height: Dp = 320.dp
) {
    val animationTime = rememberAboutAnimationTime()
    val scheme = MaterialTheme.colorScheme
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                alpha = fadeProgress.coerceIn(0f, 1f)
                translationY = -scrollTranslationPx * 0.12f
                // 内部用 DstIn 做竖直蒙版，必须独立图层，否则会拿整块 surface 当混合目标。
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
            val colors = animatedGradientColors(animationTime.value, scheme)
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors.map { it.copy(alpha = 0.58f) },
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            val radius = size.maxDimension * 0.62f
            val t = animationTime.value * BACKGROUND_SPEED
            val centers = listOf(
                Offset(
                    size.width * (0.18f + 0.10f * sin(t)),
                    size.height * (0.20f + 0.08f * cos(t * 0.8f))
                ),
                Offset(
                    size.width * (0.82f + 0.10f * cos(t * 0.9f)),
                    size.height * (0.78f + 0.10f * sin(t * 0.7f))
                ),
                Offset(
                    size.width * (0.22f + 0.12f * cos(t * 0.65f)),
                    size.height * (0.80f + 0.08f * sin(t * 0.85f))
                ),
                Offset(
                    size.width * (0.80f + 0.12f * sin(t * 0.72f)),
                    size.height * (0.20f + 0.08f * cos(t * 0.62f))
                )
            )
            centers.forEachIndexed { index, center ->
                val color = colors[index]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.84f), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius
                    ),
                    center = center,
                    radius = radius
                )
            }
            // 竖直淡出蒙版：底边平滑融进 surface
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White,
                        0.68f to Color.White,
                        1f to Color.Transparent
                    )
                ),
                blendMode = BlendMode.DstIn
            )
    }
}

/**
 * 帧驱动的累计时间；只有关于页在前台时才会跑。
 * 返回 [State] 而不是 Float：调用方只能在 draw 阶段读，避免每帧重组整页。
 */
@Composable
private fun rememberAboutAnimationTime(): State<Float> {
    val animationTime = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frameTime ->
                if (previousFrame != 0L) {
                    animationTime.floatValue += (frameTime - previousFrame) / 1_000_000_000f
                }
                previousFrame = frameTime
            }
        }
    }
    return animationTime
}

/**
 * 三套渐变调色板全部由当前 [ColorScheme] 轮换生成 —— 用户换配色，关于页跟着换。
 * 段与段之间用 smoothstep 做缓动，避免色彩切换时出现「顿一下」的线性折点。
 */
private fun animatedGradientColors(animationTime: Float, cs: ColorScheme): List<Color> {
    val p0 = listOf(cs.primaryContainer, cs.secondaryContainer, cs.tertiaryContainer, cs.primary)
    val p1 = listOf(cs.secondaryContainer, cs.tertiaryContainer, cs.primaryContainer, cs.secondary)
    val p2 = listOf(cs.tertiaryContainer, cs.primaryContainer, cs.secondaryContainer, cs.tertiary)
    val palettes = listOf(p0, p1, p2)

    val segmentValue = animationTime / COLOR_INTERPOLATION_SECONDS
    val segment = floor(segmentValue).toInt()
    val rawProgress = segmentValue - floor(segmentValue)
    val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
    val start = palettes[segment % 3]
    val end = palettes[(segment + 1) % 3]
    return start.indices.map { index -> lerp(start[index], end[index], progress) }
}

private const val BACKGROUND_SPEED = 0.12f
private const val COLOR_INTERPOLATION_SECONDS = 12f
