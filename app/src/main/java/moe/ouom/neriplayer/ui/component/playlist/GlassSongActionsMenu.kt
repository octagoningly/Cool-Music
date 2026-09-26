package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 收藏爱心强调色（与首页一致） */
internal val GlassSongFavoriteTint = Color(0xFFE53935)

/**
 * 歌曲「…」统一菜单（开发规则结构）：
 * 播放（保留队列）→ 接下来播放 → 加入队列 → 添加到歌单 → 收藏 → 复制信息。
 */
@Composable
fun GlassSongActionsMenuContent(
    isFavorite: Boolean,
    onPlayKeepQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueueEnd: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCopySongInfo: () -> Unit,
) {
    GlassMenuActionItem(
        onClick = onPlayKeepQueue,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null
            )
        }
    ) {
        GlassMenuItemText(stringResourceCompatPlayKeepQueue())
    }
    GlassMenuActionItem(
        onClick = onPlayNext,
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                contentDescription = null
            )
        }
    ) {
        GlassMenuItemText(stringResourceCompatPlayNext())
    }
    GlassMenuActionItem(
        onClick = onAddToQueueEnd,
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                contentDescription = null
            )
        }
    ) {
        GlassMenuItemText(stringResourceCompatAddQueue())
    }
    GlassMenuActionItem(
        onClick = onAddToPlaylist,
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                contentDescription = null
            )
        }
    ) {
        GlassMenuItemText(stringResourceCompatAddPlaylist())
    }
    GlassMenuActionItem(
        onClick = onToggleFavorite,
        leadingIcon = {
            Icon(
                imageVector = if (isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                contentDescription = null,
                tint = if (isFavorite) {
                    GlassSongFavoriteTint
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    ) {
        GlassMenuItemText(
            stringResourceCompatFavorite(if (isFavorite) FavoriteAction.Remove else FavoriteAction.Add)
        )
    }
    GlassMenuActionItem(
        onClick = onCopySongInfo,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null
            )
        }
    ) {
        GlassMenuItemText(stringResourceCompatCopyInfo())
    }
}

internal enum class FavoriteAction { Add, Remove }

// 资源字符串在调用方解析后传入可避免本文件依赖 R 顺序；这里用小包装保持菜单结构集中。
@Composable
private fun stringResourceCompatPlayKeepQueue(): String =
    androidx.compose.ui.res.stringResource(moe.ouom.neriplayer.R.string.search_result_play_keep_queue)

@Composable
private fun stringResourceCompatPlayNext(): String =
    androidx.compose.ui.res.stringResource(moe.ouom.neriplayer.R.string.local_playlist_play_next)

@Composable
private fun stringResourceCompatAddQueue(): String =
    androidx.compose.ui.res.stringResource(moe.ouom.neriplayer.R.string.search_result_add_to_current_queue)

@Composable
private fun stringResourceCompatAddPlaylist(): String =
    androidx.compose.ui.res.stringResource(moe.ouom.neriplayer.R.string.playlist_add_to)

@Composable
private fun stringResourceCompatFavorite(action: FavoriteAction): String =
    androidx.compose.ui.res.stringResource(
        when (action) {
            FavoriteAction.Add -> moe.ouom.neriplayer.R.string.favorite_add
            FavoriteAction.Remove -> moe.ouom.neriplayer.R.string.favorite_remove
        }
    )

@Composable
private fun stringResourceCompatCopyInfo(): String =
    androidx.compose.ui.res.stringResource(moe.ouom.neriplayer.R.string.action_copy_song_info)
