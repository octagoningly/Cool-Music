package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.transform.Transformation
import moe.ouom.neriplayer.ui.BlurTransformation
import moe.ouom.neriplayer.util.media.offlineCachedImageRequest

/**
 * 歌单详情页背景：第一首歌封面的强模糊铺底（对齐播放页「正在播放模糊封面背景」）。
 * 读入时略有淡入，缺封面时退回主题背景，避免黑屏闪烁。
 */
@Composable
internal fun PlaylistDetailBlurCoverBackdrop(
    coverUrl: String?,
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
    blurRadius: Float = DefaultBlurRadius,
    darkenAlpha: Float = DefaultDarkenAlpha,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!coverUrl.isNullOrBlank()) {
            val request = remember(context, coverUrl, offlineMode, blurRadius) {
                offlineCachedImageRequest(
                    context = context,
                    data = coverUrl,
                    sizePx = BackdropDecodeSizePx,
                    allowHardware = false,
                    crossfade = true,
                    offlineMode = offlineMode,
                    transformations = listOf<Transformation>(
                        BlurTransformation(context = context, radius = blurRadius)
                    )
                )
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (darkenAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = darkenAlpha))
                )
            }
        }
    }
}

internal const val PlaylistDetailBlurCoverRadius = 150f

private const val DefaultBlurRadius = PlaylistDetailBlurCoverRadius
private const val DefaultDarkenAlpha = 0.28f
private const val BackdropDecodeSizePx = 720
