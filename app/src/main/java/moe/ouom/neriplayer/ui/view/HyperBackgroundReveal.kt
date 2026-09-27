package moe.ouom.neriplayer.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.ouom.neriplayer.ui.component.playback.TrackChangeBackgroundRevealState
import moe.ouom.neriplayer.ui.component.playback.TrackChangeCoverMotion
import moe.ouom.neriplayer.ui.component.playback.expandRevealRect

/**
 * HyperBackground + 切歌涟漪揭示。
 *
 * **两层 HyperBackground 全程挂在树上**（禁止 if/else 拆掉再挂）：
 * 重挂载会让 shader 调色板空一拍，闪出「彩黑」默认底。
 * 下层 = 旧底，上层 = 新底并按封面矩形圆角裁切；未扩散到的区域露旧底。
 */
@Composable
fun HyperBackgroundReveal(
    reveal: TrackChangeBackgroundRevealState,
    currentCoverUrl: String?,
    isDark: Boolean,
    refreshKey: Int,
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = reveal.progress
    val settle = reveal.settle
    val density = LocalDensity.current
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    val fromUrl = reveal.fromCoverUrl
    val toUrl = reveal.toCoverUrl ?: currentCoverUrl
    val transitionLive = reveal.active && fromUrl != null && fromUrl != toUrl
    // 扩散中才裁切；回弹已去掉
    val clipping = transitionLive && progress < 1f
    val bottomUrl = if (transitionLive) fromUrl else toUrl

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { windowSize = it }
    ) {
        // 下层：旧底。同一实例只换 URL，调色板平滑过渡，不重挂载
        HyperBackground(
            modifier = Modifier.fillMaxSize(),
            isDark = isDark,
            coverUrl = bottomUrl,
            refreshKey = refreshKey,
            offlineMode = offlineMode,
        )

        val window = Rect(0f, 0f, windowSize.width.toFloat(), windowSize.height.toFloat())
        val cover = reveal.coverBoundsInRoot
            .takeIf { it.width > 0f && it.height > 0f }
            ?: Rect(
                window.center.x - 2f,
                window.center.y - 2f,
                window.center.x + 2f,
                window.center.y + 2f,
            )
        val cornerPx = with(density) { TrackChangeCoverMotion.RevealCornerRadiusDp.dp.toPx() }
        // 扩散阶段 bleed 把圆角角点推出屏幕
        val bleedPx = cornerPx
        val expanded = expandRevealRect(
            cover = cover,
            progress = progress.coerceAtMost(1f),
            window = window,
            bleedPx = bleedPx,
        )
        val revealed = expanded

        // 上层：新底。始终挂载；扩散/震荡时做圆角裁切
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (clipping) {
                        TrackChangeCoverMotion.revealAlpha(progress)
                    } else {
                        1f
                    }
                }
                .then(
                    if (clipping && windowSize.width > 0) {
                        Modifier.drawWithContent {
                            val path = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = revealed,
                                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                                    )
                                )
                            }
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            HyperBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark,
                coverUrl = toUrl,
                refreshKey = refreshKey,
                offlineMode = offlineMode,
            )
        }
    }
}
