package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.ui.feedback.AppFeedback

/**
 * Bottom sheet to add [song] into a local playlist, with success / already-exists toast.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AddSongToPlaylistSheet(
    song: SongItem,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { LocalPlaylistRepository.getInstance(context) }
    val playlists by repo.playlists.collectAsStateWithLifecycle()

    val selectable = playlists.filterNot {
        LocalFilesPlaylist.isSystemPlaylist(it, context)
    }
    PlaylistTargetPickerSheet(
        title = stringResource(R.string.playlist_add_to),
        playlists = selectable,
        onDismissRequest = onDismissRequest,
        createDialogTitle = stringResource(R.string.playlist_add_to_new),
        createActionLabel = stringResource(R.string.playlist_create_and_add),
        canCreate = true,
        onCreateTarget = { name ->
            scope.launch {
                val result = runCatching {
                    repo.createPlaylistWithSongs(name, listOf(song))
                }
                result.onSuccess { playlist ->
                    AppFeedback.showToast(
                        context,
                        context.getString(R.string.playlist_add_success_one, playlist.name)
                    )
                }.onFailure {
                    AppFeedback.showToast(
                        context,
                        context.getString(R.string.playlist_export_failed)
                    )
                }
            }
        },
        onSelectTarget = { playlist ->
            scope.launch {
                val result = runCatching {
                    repo.addSongsToPlaylistWithResult(playlist.id, listOf(song))
                }
                result.onSuccess { addResult ->
                    val message = if (addResult.allDuplicates) {
                        context.getString(R.string.playlist_add_already_exists)
                    } else {
                        context.getString(R.string.playlist_add_success_one, playlist.name)
                    }
                    AppFeedback.showToast(context, message)
                }.onFailure {
                    AppFeedback.showToast(
                        context,
                        context.getString(R.string.playlist_export_failed)
                    )
                }
            }
        }
    )
}
