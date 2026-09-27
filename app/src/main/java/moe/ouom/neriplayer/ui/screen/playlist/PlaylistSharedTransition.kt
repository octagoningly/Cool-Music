package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 歌单卡片 → 详情 Container Transform 的 shared key。
 * 必须带 playlist 唯一 id，避免 Lazy 列表多卡片冲突。
 */
object PlaylistSharedKeys {
    fun bounds(playlistId: String): String = "playlist_container_$playlistId"
    fun artwork(playlistId: String): String = "playlist_artwork_$playlistId"
    fun title(playlistId: String): String = "playlist_title_$playlistId"
}

/** 由 LibraryHost 在 SharedTransitionLayout + AnimatedContent 内提供 */
val LocalPlaylistSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPlaylistSharedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 卡片容器 sharedBounds：列表卡片与详情根容器同一 key，位置/尺寸连续变形。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playlistSharedContainer(
    playlistId: String,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
): Modifier {
    val shared = LocalPlaylistSharedTransitionScope.current ?: return this
    val visibility = LocalPlaylistSharedVisibilityScope.current ?: return this
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    return with(shared) {
        this@playlistSharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key = PlaylistSharedKeys.bounds(playlistId)),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                androidx.compose.animation.core.tween(
                    durationMillis = 450,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                )
            },
            clipInOverlayDuringTransition = OverlayClip(shape),
            renderInOverlayDuringTransition = true,
        )
    }
}

/**
 * 封面 sharedElement：小封面连续放大成详情 Hero，禁止 fade 交叉溶解。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.playlistSharedArtwork(playlistId: String): Modifier {
    val shared = LocalPlaylistSharedTransitionScope.current ?: return this
    val visibility = LocalPlaylistSharedVisibilityScope.current ?: return this
    return with(shared) {
        this@playlistSharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(key = PlaylistSharedKeys.artwork(playlistId)),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ ->
                androidx.compose.animation.core.tween(
                    durationMillis = 450,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                )
            },
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 4f,
        )
    }
}
