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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import moe.ouom.neriplayer.ui.component.playback.TrackChangeBackgroundRevealState
import moe.ouom.neriplayer.ui.component.playback.expandRevealRect

/**
 * HyperBackground + 切歌涟漪揭示。
 *
 * [reveal].active 时：下层仍显示旧封面底，上层新封面底用
 * **从封面矩形四边向外扩张**的圆角矩形裁切；未扩散到的区域保持旧背景。
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
    val progress = reveal.progress.value
    val density = LocalDensity.current
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    val fromUrl = reveal.fromCoverUrl
    val toUrl = reveal.toCoverUrl ?: currentCoverUrl
    val revealing = reveal.active && progress < 1f && fromUrl != null && fromUrl != toUrl

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { windowSize = it }
    ) {
        if (revealing && windowSize.width > 0) {
            HyperBackground(
                modifier = Modifier.fillMaxSize(),
                isDark = isDark,
                coverUrl = fromUrl,
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
            val revealed = expandRevealRect(cover, progress, window)
            val cornerPx = with(density) { lerp(24.dp.toPx(), 0f, progress) }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
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
            ) {
                HyperBackground(
                    modifier = Modifier.fillMaxSize(),
                    isDark = isDark,
                    coverUrl = toUrl,
                    refreshKey = refreshKey,
                    offlineMode = offlineMode,
                )
            }
        } else {
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
