package moe.ouom.neriplayer.ui.component.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledAlertDialog as AlertDialog
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.ui.component.sheet.bottomSheetScrollGuard
import moe.ouom.neriplayer.ui.haptic.HapticTextButton
import moe.ouom.neriplayer.ui.haptic.performHapticFeedback
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextField
import java.util.concurrent.atomic.AtomicBoolean

private class PendingPlaylistExport(
    val targetName: String,
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistExportSheet(
    title: String,
    playlists: List<LocalPlaylist>,
    selectedCount: Int,
    onDismissRequest: () -> Unit,
    onCreateAndExport: (String) -> Unit,
    onExportToPlaylist: (LocalPlaylist) -> Unit,
    createActionLabel: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playlistListState = rememberLazyListState()
    var newName by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PendingPlaylistExport?>(null) }
    val resolvedCreateActionLabel =
        createActionLabel ?: stringResource(R.string.playlist_create_and_export)

    fun clearPendingExport() {
        pendingExport = null
    }

    fun dismissAnimated() {
        clearPendingExport()
        scope.launch {
            runCatching { sheetState.hide() }
            onDismissRequest()
        }
    }

    fun runThenDismiss(action: () -> Unit) {
        action()
        dismissAnimated()
    }

    fun requestExportConfirmation(targetName: String, action: () -> Unit) {
        if (pendingExport != null) return
        pendingExport = PendingPlaylistExport(targetName) {
            runThenDismiss(action)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissAnimated() },
        sheetState = sheetState,
        shape = moe.ouom.neriplayer.ui.component.overlay.GlassDialogShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        scrimColor = Color.Black.copy(alpha = 0.46f),
        dragHandle = null,
        panelPosition = moe.ouom.neriplayer.ui.component.overlay.GlassPanelPosition.Bottom,
        panelYOffset = -64.dp,
        panelContentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 16.dp
        )
    ) {
        Column(
            modifier = Modifier
                .bottomSheetScrollGuard()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    MiuixSettingsButton(
                        enabled = newName.isNotBlank() && selectedCount > 0,
                        onClick = {
                            val name = newName.trim()
                            if (name.isBlank()) return@MiuixSettingsButton
                            requestExportConfirmation(name) { onCreateAndExport(name) }
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(resolvedCreateActionLabel)
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.common_selected_count,
                        selectedCount,
                        selectedCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                MiuixSettingsTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(stringResource(R.string.playlist_create_name)) },
                    singleLine = true
                )

                HorizontalDivider(
                    thickness = DividerDefaults.Thickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Box {
                LazyColumn(
                    state = playlistListState,
                    modifier = Modifier.playlistExportListHeight()
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistExportRow(
                            playlist = playlist,
                            enabled = pendingExport == null,
                            onClick = {
                                context.performHapticFeedback()
                                requestExportConfirmation(playlist.name) {
                                    onExportToPlaylist(playlist)
                                }
                            }
                        )
                    }
                }
                PlaylistExportScrollbar(
                    listState = playlistListState,
                    itemCount = playlists.size,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    pendingExport?.let { export ->
        AlertDialog(
            onDismissRequest = {
                clearPendingExport()
            },
            title = { Text(stringResource(R.string.playlist_batch_export_confirm_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.playlist_batch_export_confirm_message,
                        selectedCount,
                        selectedCount,
                        export.targetName
                    )
                )
            },
            confirmButton = {
                HapticTextButton(
                    onClick = {
                        val action = export.onConfirm
                        clearPendingExport()
                        action()
                    }
                ) {
                    Text(stringResource(R.string.playlist_batch_export_confirm_button))
                }
            },
            dismissButton = {
                HapticTextButton(
                    onClick = {
                        clearPendingExport()
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistExportScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 1) return
    val layoutInfo = listState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo.size
    if (visible <= 0 || visible >= itemCount) return

    val maxIndex = (itemCount - visible).coerceAtLeast(1)
    val progress = (listState.firstVisibleItemIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
    val trackHeight = 72.dp
    val thumbHeight = 28.dp

    Box(
        modifier = modifier
            .width(3.dp)
            .height(trackHeight)
            .padding(end = 2.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                shape = RoundedCornerShape(2.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .offset(y = (trackHeight - thumbHeight) * progress)
                .width(3.dp)
                .height(thumbHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
private fun PlaylistExportRow(
    playlist: LocalPlaylist,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f))
            .playlistExportRowClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(
                R.plurals.explore_song_count,
                playlist.songs.size,
                playlist.songs.size
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun Modifier.playlistExportRowClick(
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val regularClickHandled = remember { AtomicBoolean(false) }

    pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            regularClickHandled.set(false)
            val touchSlopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
            var movedBeyondTapSlop = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val distanceX = change.position.x - down.position.x
                val distanceY = change.position.y - down.position.y
                if (distanceX * distanceX + distanceY * distanceY > touchSlopSquared) {
                    movedBeyondTapSlop = true
                }
                if (!change.pressed) {
                    if (enabled && !movedBeyondTapSlop && !regularClickHandled.get()) {
                        currentOnClick()
                    }
                    break
                }
            }
        }
    }.clickable(
        enabled = enabled,
        onClick = {
            regularClickHandled.set(true)
            currentOnClick()
        }
    )
}
