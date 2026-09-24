package moe.ouom.neriplayer.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledAlertDialog as AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.ui.feedback.showNeriSnackbar
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.download.DownloadStatus
import moe.ouom.neriplayer.core.download.DownloadTask
import moe.ouom.neriplayer.core.download.GlobalDownloadManager
import moe.ouom.neriplayer.core.download.isDownloadTaskCancellable
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.download.AudioDownloadManager
import moe.ouom.neriplayer.core.player.model.PlaybackAudioInfo
import moe.ouom.neriplayer.data.local.media.LocalMediaSupport
import moe.ouom.neriplayer.data.local.media.isLocalSong
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.data.stats.TrackStat
import moe.ouom.neriplayer.ui.component.overlay.GlassDialogShape
import moe.ouom.neriplayer.ui.component.playlist.GlassSheetMenuItem
import moe.ouom.neriplayer.ui.haptic.HapticTextButton
import moe.ouom.neriplayer.ui.viewmodel.NowPlayingViewModel
import moe.ouom.neriplayer.ui.viewmodel.album.isNeteaseAlbumNavigationSource
import moe.ouom.neriplayer.ui.viewmodel.album.neteaseAlbumDisplayName
import moe.ouom.neriplayer.ui.viewmodel.album.resolveNeteaseAlbum
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.util.media.buildRemoteSongShareUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun MoreOptionsMainContent(
    viewModel: NowPlayingViewModel,
    originalSong: SongItem,
    queue: List<SongItem>,
    isLocalSong: Boolean,
    lyricFontScale: Float,
    translationFontScale: Float,
    currentPlaybackAudioInfo: PlaybackAudioInfo?,
    isDismissing: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenSearch: () -> Unit,
    onOpenEditInfo: () -> Unit,
    onOpenPlaybackSound: () -> Unit,
    onOpenLyricBehavior: () -> Unit,
    onOpenFontSize: () -> Unit,
    onOpenBiliVideoSkip: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onShowSongDetails: () -> Unit,
    onShowQualitySwitch: () -> Unit,
    onEnterAlbum: (AlbumSummary) -> Unit,
    onDismissSheet: (() -> Unit) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 8.dp)
    ) {
        MetadataAndPlaybackActions(
            audioInfo = currentPlaybackAudioInfo,
            isDismissing = isDismissing,
            onOpenSearch = onOpenSearch,
            onOpenEditInfo = onOpenEditInfo,
            onOpenPlaybackSound = onOpenPlaybackSound,
            onShowQualitySwitch = onShowQualitySwitch
        )
        DownloadOrDetailsAction(
            viewModel = viewModel,
            song = originalSong,
            isLocalSong = isLocalSong,
            onShowSongDetails = onShowSongDetails
        )
        LyricsAndAlbumActions(
            song = originalSong,
            lyricFontScale = lyricFontScale,
            translationFontScale = translationFontScale,
            onOpenLyricBehavior = onOpenLyricBehavior,
            onOpenFontSize = onOpenFontSize,
            onEnterAlbum = onEnterAlbum,
            snackbarHostState = snackbarHostState
        )
        if (PlayerManager.isBiliTrack(originalSong)) {
            GlassSheetMenuItem(
                text = stringResource(R.string.bili_video_skip_manage),
                leadingIcon = { Icon(Icons.Outlined.SkipNext, null) },
                onClick = onOpenBiliVideoSkip
            )
        }
        ShareSongAction(
            song = originalSong,
            queue = queue,
            snackbarHostState = snackbarHostState,
            onDismissSheet = onDismissSheet
        )
        PlaybackStatsAction(originalSong)
        GlassSheetMenuItem(
            text = stringResource(R.string.listen_together_title),
            leadingIcon = { Icon(Icons.Outlined.Headphones, null) },
            onClick = onOpenListenTogether
        )
    }
}

@Composable
private fun MetadataAndPlaybackActions(
    audioInfo: PlaybackAudioInfo?,
    isDismissing: Boolean,
    onOpenSearch: () -> Unit,
    onOpenEditInfo: () -> Unit,
    onOpenPlaybackSound: () -> Unit,
    onShowQualitySwitch: () -> Unit
) {
    GlassSheetMenuItem(
        text = stringResource(R.string.music_get_info),
        leadingIcon = { Icon(Icons.Outlined.Info, null) },
        enabled = !isDismissing,
        onClick = onOpenSearch
    )
    GlassSheetMenuItem(
        text = stringResource(R.string.music_edit_info),
        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
        onClick = onOpenEditInfo
    )
    if (audioInfo?.qualityOptions.orEmpty().size > 1) {
        GlassSheetMenuItem(
            text = stringResource(R.string.nowplaying_quality_switch_title),
            leadingIcon = { Icon(Icons.Outlined.MusicNote, null) },
            supportingContent = audioInfo?.qualityLabel
                ?.takeIf { it.isNotBlank() }
                ?.let { label -> { Text(label) } },
            onClick = onShowQualitySwitch
        )
    }
    GlassSheetMenuItem(
        text = stringResource(R.string.nowplaying_audio_effects_title),
        leadingIcon = { Icon(Icons.Outlined.Tune, null) },
        supportingContent = { Text(stringResource(R.string.nowplaying_audio_effects_desc)) },
        onClick = onOpenPlaybackSound
    )
}

@Composable
private fun DownloadOrDetailsAction(
    viewModel: NowPlayingViewModel,
    song: SongItem,
    isLocalSong: Boolean,
    onShowSongDetails: () -> Unit
) {
    if (isLocalSong) {
        GlassSheetMenuItem(
            text = stringResource(R.string.local_song_open_details),
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = onShowSongDetails
        )
        return
    }

    val context = LocalContext.current
    val downloadPresenceVersion by GlobalDownloadManager.downloadPresenceVersion
        .collectAsStateWithLifecycle()
    val hasLocalDownload = remember(downloadPresenceVersion, song) {
        hasCachedLocalDownload(song)
    }
    val downloadSongKey = remember(song) { song.stableKey() }
    val currentTaskFlow = remember(downloadSongKey) {
        GlobalDownloadManager.downloadTasks
            .map { tasks -> tasks.firstOrNull { it.song.stableKey() == downloadSongKey } }
            .distinctUntilChanged()
    }
    val currentTask by currentTaskFlow.collectAsStateWithLifecycle(initialValue = null)
    if (shouldHideDownloadActionForSong(hasLocalDownload, currentTask)) return

    val canCancel = remember(currentTask) { isDownloadTaskCancellable(currentTask) }
    val status = currentTask?.status
    val canClick = (
        status != DownloadStatus.QUEUED &&
            status != DownloadStatus.DOWNLOADING &&
            status != DownloadStatus.WAITING_NETWORK
        ) || canCancel
    GlassSheetMenuItem(
        text = stringResource(downloadActionLabel(currentTask)),
        leadingIcon = { Icon(Icons.Outlined.Download, null) },
        enabled = canClick,
        supportingContent = { DownloadProgressContent(currentTask) },
        onClick = {
            when (currentTask?.status) {
                DownloadStatus.QUEUED,
                DownloadStatus.WAITING_NETWORK,
                DownloadStatus.DOWNLOADING -> viewModel.cancelDownload(downloadSongKey)
                DownloadStatus.CANCELLED -> viewModel.resumeDownload(context, downloadSongKey)
                DownloadStatus.FAILED -> viewModel.retryDownload(context, song)
                else -> viewModel.downloadSong(context, song)
            }
        }
    )
}

private fun downloadActionLabel(task: DownloadTask?): Int {
    return when (task?.status) {
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.WAITING_NETWORK -> R.string.download_cancel_download
        DownloadStatus.FAILED -> R.string.action_retry
        else -> R.string.download_to_local
    }
}

@Composable
private fun DownloadProgressContent(task: DownloadTask?) {
    val progress = task?.progress
    when {
        progress?.stage == AudioDownloadManager.DownloadStage.FINALIZING -> {
            Column {
                Text(stringResource(R.string.download_finalizing))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        progress != null -> {
            Column {
                Text(
                    stringResource(
                        R.string.download_progress_file_label,
                        progress.percentage,
                        progress.fileName
                    )
                )
                if (progress.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = {
                            (progress.bytesRead.toFloat() / progress.totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        task?.status == DownloadStatus.FAILED -> Text(stringResource(R.string.download_failed))
        task?.status == DownloadStatus.WAITING_NETWORK -> {
            Text(stringResource(R.string.download_waiting_network_recovery))
        }
    }
}

@Composable
private fun LyricsAndAlbumActions(
    song: SongItem,
    lyricFontScale: Float,
    translationFontScale: Float,
    onOpenLyricBehavior: () -> Unit,
    onOpenFontSize: () -> Unit,
    onEnterAlbum: (AlbumSummary) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    GlassSheetMenuItem(
        text = stringResource(R.string.lyrics_adjust_behavior),
        leadingIcon = { Icon(Icons.Outlined.Timer, null) },
        onClick = onOpenLyricBehavior
    )
    GlassSheetMenuItem(
        text = stringResource(R.string.lyrics_font_size),
        leadingIcon = { Icon(Icons.Outlined.FormatSize, null) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_lyrics_font_scale_pair_value,
                    (lyricFontScale * 100).roundToInt(),
                    (translationFontScale * 100).roundToInt()
                )
            )
        },
        onClick = onOpenFontSize
    )
    if (!isNeteaseAlbumNavigationSource(song)) return

    val albumName = neteaseAlbumDisplayName(song)
    val context = LocalContext.current
    val composeResources = LocalResources.current
    var albumResolveRequest by remember(song) { mutableIntStateOf(0) }
    var resolvingAlbum by remember(song) { mutableStateOf(false) }

    LaunchedEffect(song, albumResolveRequest) {
        if (albumResolveRequest == 0) return@LaunchedEffect
        val album = try {
            resolveNeteaseAlbum(song)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            NPLogger.e("MoreOptionsMainContent", "解析网易云专辑失败", error)
            null
        }
        resolvingAlbum = false
        if (album != null) {
            onEnterAlbum(album)
        } else {
            snackbarHostState.showNeriSnackbar(composeResources.getString(R.string.music_get_detail_failed))
        }
    }

    GlassSheetMenuItem(
        text = stringResource(R.string.music_view_album, albumName),
        enabled = !resolvingAlbum,
        leadingIcon = {
            if (resolvingAlbum) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Outlined.LibraryMusic, null)
            }
        },
        onClick = {
            resolvingAlbum = true
            albumResolveRequest++
        }
    )
}

@Composable
private fun ShareSongAction(
    song: SongItem,
    queue: List<SongItem>,
    snackbarHostState: SnackbarHostState,
    onDismissSheet: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val composeResources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    GlassSheetMenuItem(
        text = stringResource(R.string.action_share),
        leadingIcon = { Icon(Icons.Outlined.Share, null) },
        onClick = shareClick@{
            if (song.isLocalSong()) {
                coroutineScope.launch {
                    val shared = runCatching {
                        LocalMediaSupport.shareSongFile(context, song)
                    }.getOrDefault(false)
                    if (shared) {
                        onDismissSheet {}
                    } else {
                        snackbarHostState.showNeriSnackbar(
                            composeResources.getString(R.string.local_song_share_failed)
                        )
                    }
                }
                return@shareClick
            }

            val shareUrl = buildRemoteSongShareUrl(song, queue)
            val shareText = if (shareUrl.isNullOrBlank()) {
                "${song.displayName()} - ${song.displayArtist()}"
            } else {
                composeResources.getString(
                    R.string.nowplaying_share_song,
                    song.displayName(),
                    song.displayArtist(),
                    shareUrl,
                )
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            onDismissSheet { context.startActivity(shareIntent) }
        }
    )
}

@Composable
private fun PlaybackStatsAction(song: SongItem) {
    val songKey = remember(song) { song.stableKey() }
    val trackStat by produceState<TrackStat?>(initialValue = null, songKey) {
        value = withContext(Dispatchers.IO) {
            AppContainer.playbackStatsRepo.getStatForTrack(songKey)
        }
    }
    val resolvedTrackStat = trackStat ?: return
    var showDialog by remember { mutableStateOf(false) }
    GlassSheetMenuItem(
        text = stringResource(R.string.stats_title),
        leadingIcon = { Icon(Icons.Outlined.BarChart, null) },
        onClick = { showDialog = true }
    )
    if (!showDialog) return

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val firstPlayedText = remember(resolvedTrackStat.firstPlayedAt) {
        dateFormat.format(Date(resolvedTrackStat.firstPlayedAt))
    }
    val totalListenText = remember(resolvedTrackStat.totalListenMs) {
        val totalSeconds = resolvedTrackStat.totalListenMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }
    AlertDialog(
        onDismissRequest = { showDialog = false },
        icon = { Icon(Icons.Outlined.BarChart, null) },
        title = { Text(stringResource(R.string.stats_title)) },
        shape = GlassDialogShape,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsCard(R.string.stats_song_first_played, firstPlayedText)
                StatsCard(R.string.stats_song_total_listen, totalListenText)
                StatsCard(
                    labelRes = R.string.stats_song_play_count_label,
                    value = pluralStringResource(
                        R.plurals.stats_play_count_value,
                        resolvedTrackStat.playCount,
                        resolvedTrackStat.playCount
                    )
                )
            }
        },
        confirmButton = {
            HapticTextButton(onClick = { showDialog = false }) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun StatsCard(labelRes: Int, value: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        )
    ) {
        ListItem(
            headlineContent = { Text(stringResource(labelRes)) },
            supportingContent = { Text(value) }
        )
    }
}
