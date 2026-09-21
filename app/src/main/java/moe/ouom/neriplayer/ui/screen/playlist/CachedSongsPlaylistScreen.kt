package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.core.player.cache.CachedSong
import moe.ouom.neriplayer.core.player.cache.CachedSongsSnapshot
import moe.ouom.neriplayer.core.player.cache.CachedUnrecognizedKind
import moe.ouom.neriplayer.core.player.cache.cachedSongsSnapshot
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayCoverUrl
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.data.traffic.hasValidatedDefaultInternetAccess
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.feedback.AppFeedback
import moe.ouom.neriplayer.util.format.formatFileSize
import moe.ouom.neriplayer.util.media.offlineCachedImageRequest
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedSongsPlaylistScreen(
    onBack: () -> Unit,
    onSongClick: (List<SongItem>, Int) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val snapshot by produceState(initialValue = CachedSongsSnapshot.Empty) {
        while (true) {
            value = runCatching { PlayerManager.cachedSongsSnapshot() }.getOrDefault(CachedSongsSnapshot.Empty)
            delay(3_000L)
        }
    }
    val cachedSongs = snapshot.songs
    var query by remember { mutableStateOf("") }
    val visibleSongs = remember(cachedSongs, query) {
        if (query.isBlank()) cachedSongs else cachedSongs.filter {
            it.song.displayName().contains(query, ignoreCase = true) ||
                it.song.displayArtist().contains(query, ignoreCase = true)
        }
    }
    val playbackSongs = remember(cachedSongs) { cachedSongs.map(CachedSong::song) }
    val completeSongs = remember(cachedSongs) {
        cachedSongs.filter(CachedSong::complete).map(CachedSong::song)
    }
    val offlineNow = offlineMode || !context.hasValidatedDefaultInternetAccess()
    val partialOfflineMessage = stringResource(R.string.cached_songs_partial_offline)
    fun playableSongsNow(): List<SongItem> =
        if (offlineMode || !context.hasValidatedDefaultInternetAccess()) completeSongs else playbackSongs

    Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.cached_songs_playlist)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerHeight.current)
            ) {
                item(key = "cache_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${pluralStringResource(R.plurals.library_song_count, cachedSongs.size, cachedSongs.size)} · " +
                                formatFileSize(cachedSongs.sumOf(CachedSong::bytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.cached_songs_offline_ready, completeSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (snapshot.unrecognizedBytes > 0L) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.cached_songs_unrecognized,
                                        formatFileSize(snapshot.unrecognizedBytes),
                                        snapshot.unrecognizedResources
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                snapshot.unrecognizedGroups.entries
                                    .sortedByDescending { it.value }
                                    .forEach { (kind, bytes) ->
                                        val label = when (kind) {
                                            CachedUnrecognizedKind.LX -> R.string.cached_songs_group_lx
                                            CachedUnrecognizedKind.BILI_FALLBACK -> R.string.cached_songs_group_bili_fallback
                                            CachedUnrecognizedKind.SHARED_STREAM -> R.string.cached_songs_group_shared
                                            CachedUnrecognizedKind.DIRECT_URL -> R.string.cached_songs_group_url
                                            CachedUnrecognizedKind.OTHER -> R.string.cached_songs_group_other
                                        }
                                        Text(
                                            text = "${stringResource(label)} · ${formatFileSize(bytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                            }
                        }
                        if (snapshot.otherDiskBytes > 1024L * 1024L) {
                            Text(
                                text = stringResource(
                                    R.string.cached_songs_other_disk,
                                    formatFileSize(snapshot.otherDiskBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val playableSongs = playableSongsNow()
                                if (playableSongs.isNotEmpty()) {
                                    PlayerManager.setShuffle(false)
                                    onSongClick(playableSongs, 0)
                                }
                            }, enabled = if (offlineNow) completeSongs.isNotEmpty() else playbackSongs.isNotEmpty()) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text(stringResource(R.string.cached_songs_play_all))
                            }
                            IconButton(
                                onClick = {
                                    val playableSongs = playableSongsNow()
                                    if (playableSongs.isNotEmpty()) {
                                        PlayerManager.setShuffle(true)
                                        onSongClick(playableSongs, Random.nextInt(playableSongs.size))
                                    }
                                },
                                enabled = if (offlineNow) completeSongs.isNotEmpty() else playbackSongs.isNotEmpty()
                            ) {
                                Icon(Icons.Filled.Shuffle, contentDescription = stringResource(R.string.cached_songs_shuffle))
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.cached_songs_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (visibleSongs.isEmpty()) {
                    item(key = "cache_empty") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(
                                    if (query.isBlank()) R.string.cached_songs_empty else R.string.cached_songs_no_results
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(visibleSongs, key = { _, entry -> entry.song.stableKey() }) { _, entry ->
                    val song = entry.song
                    val cover = song.displayCoverUrl()
                    ListItem(
                        modifier = Modifier.clickable {
                            if (!entry.complete &&
                                (offlineMode || !context.hasValidatedDefaultInternetAccess())) {
                                AppFeedback.showToast(context, partialOfflineMessage)
                            } else {
                                val playableSongs = playableSongsNow()
                                val index = playableSongs.indexOfFirst { it.stableKey() == song.stableKey() }
                                if (index >= 0) onSongClick(playableSongs, index)
                            }
                        },
                        headlineContent = {
                            Text(song.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                "${song.displayArtist()} · ${formatFileSize(entry.bytes)} · " +
                                    stringResource(if (entry.complete) R.string.cached_songs_complete else R.string.cached_songs_partial),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            if (cover.isNullOrBlank()) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                                }
                            } else {
                                AsyncImage(
                                    model = offlineCachedImageRequest(
                                        context = context,
                                        data = cover,
                                        sizePx = 160,
                                        allowHardware = false,
                                        offlineMode = offlineMode
                                    ),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}
