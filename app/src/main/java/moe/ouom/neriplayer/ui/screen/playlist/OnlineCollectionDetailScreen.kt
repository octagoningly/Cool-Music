package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.download.GlobalDownloadManager
import moe.ouom.neriplayer.core.player.resolver.lxmusic.LxOnlineCollection
import moe.ouom.neriplayer.core.player.resolver.lxmusic.LxOnlineCollectionType
import moe.ouom.neriplayer.core.player.resolver.lxmusic.loadLxOnlineCollectionSongs
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.screen.tab.SongRow
import moe.ouom.neriplayer.ui.viewmodel.playlist.NeteaseCollectionDetailUiState
import moe.ouom.neriplayer.ui.viewmodel.playlist.NeteaseCollectionHeader

/**
 * 探索页「在线」搜索点进的歌手 / 歌单详情。
 *
 * - 歌手：保持精简列表布局，**不铺**封面模糊底（不再走 [PlaylistDetailBlurCoverBackdrop]）。
 * - 歌单：复用网易云歌单详情 [DetailScreen] 的现代布局（Hero / 操作区 / 搜索 / 多选等）。
 */
@Composable
internal fun OnlineCollectionDetailScreen(
    collection: LxOnlineCollection,
    onBack: () -> Unit,
    onSongClick: (List<SongItem>, Int) -> Unit,
    onSongPlayPreservingQueue: (SongItem) -> Unit,
    onSongPlayNext: (SongItem) -> Unit,
    onSongAddToQueueEnd: (SongItem) -> Unit,
    offlineMode: Boolean
) {
    if (collection.type == LxOnlineCollectionType.PLAYLIST) {
        OnlinePlaylistCollectionDetailScreen(
            collection = collection,
            onBack = onBack,
            onSongClick = onSongClick,
            offlineMode = offlineMode
        )
    } else {
        OnlineArtistCollectionDetailScreen(
            collection = collection,
            onBack = onBack,
            onSongClick = onSongClick,
            onSongPlayPreservingQueue = onSongPlayPreservingQueue,
            onSongPlayNext = onSongPlayNext,
            onSongAddToQueueEnd = onSongAddToQueueEnd,
            offlineMode = offlineMode
        )
    }
}

/** 在线歌单：与网易云歌单页共用 [DetailScreen]，保证布局 / 按键一致。 */
@Composable
private fun OnlinePlaylistCollectionDetailScreen(
    collection: LxOnlineCollection,
    onBack: () -> Unit,
    onSongClick: (List<SongItem>, Int) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    var songs by remember(collection) { mutableStateOf<List<SongItem>>(emptyList()) }
    var page by remember(collection) { mutableIntStateOf(0) }
    var hasMore by remember(collection) { mutableStateOf(true) }
    var loading by remember(collection) { mutableStateOf(true) }
    var error by remember(collection) { mutableStateOf<String?>(null) }
    var reloadTick by remember(collection) { mutableIntStateOf(0) }

    LaunchedEffect(collection, reloadTick) {
        songs = emptyList()
        page = 0
        hasMore = true
        error = null
        loading = true
        try {
            // 兜底上限，避免接口一直 hasMore 时死循环
            while (hasMore && page < 20) {
                val result = loadLxOnlineCollectionSongs(collection, page + 1)
                songs = (songs + result.songs).distinctBy { it.stableKey() }
                page++
                hasMore = result.hasMore
            }
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: context.getString(R.string.search_error)
        } finally {
            loading = false
        }
    }

    val playlistId = remember(collection) {
        ("lxOnline|${collection.type}|${collection.sourceId}|${collection.id}")
            .hashCode()
            .toLong()
    }
    val trackCount = when {
        collection.trackCount > 0 -> collection.trackCount
        else -> songs.size
    }
    val ui = NeteaseCollectionDetailUiState(
        loading = loading,
        error = error,
        header = NeteaseCollectionHeader(
            id = playlistId,
            isAlbum = false,
            name = collection.name,
            coverUrl = collection.coverUrl.orEmpty(),
            playCount = collection.playCount,
            trackCount = trackCount
        ),
        tracks = songs
    )

    DetailScreen(
        ui = ui,
        playlistId = playlistId,
        playlistSource = "lxOnline",
        initialCoverUrl = collection.coverUrl,
        onRetry = { reloadTick++ },
        onBack = onBack,
        onSongClick = onSongClick,
        offlineMode = offlineMode
    )
}

/** 在线歌手：精简列表，无封面模糊底。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineArtistCollectionDetailScreen(
    collection: LxOnlineCollection,
    onBack: () -> Unit,
    onSongClick: (List<SongItem>, Int) -> Unit,
    onSongPlayPreservingQueue: (SongItem) -> Unit,
    onSongPlayNext: (SongItem) -> Unit,
    onSongAddToQueueEnd: (SongItem) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var songs by remember(collection) { mutableStateOf<List<SongItem>>(emptyList()) }
    var page by remember(collection) { mutableIntStateOf(0) }
    var hasMore by remember(collection) { mutableStateOf(true) }
    var loading by remember(collection) { mutableStateOf(false) }
    var error by remember(collection) { mutableStateOf<String?>(null) }

    suspend fun loadNextPage() {
        if (loading || !hasMore) return
        loading = true
        error = null
        try {
            val result = loadLxOnlineCollectionSongs(collection, page + 1)
            songs = (songs + result.songs).distinctBy { "${it.channelId}|${it.audioId}" }
            page++
            hasMore = result.hasMore
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: context.getString(R.string.search_error)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(collection) { loadNextPage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        // 故意不铺 PlaylistDetailBlurCoverBackdrop：在线歌手页取消背景模糊
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerHeight.current)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (collection.coverUrl != null) {
                        AsyncImage(
                            model = collection.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.small)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(88.dp)
                        )
                    }
                    Column {
                        Text(collection.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                when (collection.sourceId) {
                                    "tx" -> R.string.settings_lx_online_search_engines_tx
                                    "kw" -> R.string.settings_lx_online_search_engines_kw
                                    "kg" -> R.string.settings_lx_online_search_engines_kg
                                    else -> R.string.settings_lx_online_search_engines_wy
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            itemsIndexed(songs, key = { _, song -> "${song.channelId}|${song.audioId}" }) { index, song ->
                SongRow(
                    index = index + 1,
                    song = song,
                    isFavorite = false,
                    favoriteActionEnabled = false,
                    offlineMode = offlineMode,
                    snackbarHostState = snackbarHostState,
                    onClick = { onSongClick(songs, index) },
                    onPlayNow = { onSongPlayPreservingQueue(song) },
                    onPlayNext = { onSongPlayNext(song) },
                    onAddToQueueEnd = { onSongAddToQueueEnd(song) },
                    onDownload = { GlobalDownloadManager.startDownload(context, song) },
                    onToggleFavorite = {}
                )
            }
            if (loading) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            } else if (error != null) {
                item(key = "error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { loadNextPage() } }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            } else if (hasMore) {
                item(key = "more") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { scope.launch { loadNextPage() } }) {
                            Text(stringResource(R.string.online_collection_load_more))
                        }
                    }
                }
            } else if (songs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.search_no_result),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
