package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.ui.feedback.AppFeedback
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledModalBottomSheet

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

    DensityScaledModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_add_to),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            val selectable = playlists.filterNot {
                LocalFilesPlaylist.isSystemPlaylist(it, context)
            }
            selectable.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
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
                                onDismissRequest()
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        pluralStringResource(
                            R.plurals.count_songs_format,
                            playlist.songs.size,
                            playlist.songs.size
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
