package moe.ouom.neriplayer.ui.screen.tab

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen.tab/LibraryScreen
 * Created: 2025/8/8
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Download
import moe.ouom.neriplayer.data.local.playlist.importer.ExternalPlaylistImportResult
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledAlertDialog as AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.ui.component.common.NeriTabLargeTitleTopBar
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.platform.youtube.YouTubeFeatureGate
import moe.ouom.neriplayer.data.stats.PlaybackStatsPeriod
import moe.ouom.neriplayer.data.stats.PlaybackStatsHotPlaylist
import moe.ouom.neriplayer.data.stats.buildPlaybackStatsHotPlaylist
import moe.ouom.neriplayer.data.playlist.favorite.FAVORITE_SOURCE_NETEASE_ARTIST
import moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylist
import moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylistRepository
import moe.ouom.neriplayer.ui.viewmodel.tab.toBiliPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.FavoritesPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.core.player.cache.cachedSongsSnapshot
import moe.ouom.neriplayer.core.player.cache.CachedSongsSnapshot
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.local.playlist.model.LocalArtistSummary
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.data.local.playlist.model.buildLocalArtistSummaries
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.system.SystemLocalPlaylists
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.feedback.AppFeedback
import moe.ouom.neriplayer.ui.component.playlist.showPlaylistDeleteResultGlobally
import moe.ouom.neriplayer.ui.util.shouldAllowCollapsingTopAppBar
import moe.ouom.neriplayer.data.model.NeteaseArtistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylistKind
import moe.ouom.neriplayer.ui.viewmodel.tab.LibraryViewModel
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.favoriteId
import moe.ouom.neriplayer.ui.util.rememberPlaylistDisplayCoverUrl
import moe.ouom.neriplayer.util.media.fastScrollableImageRequest
import moe.ouom.neriplayer.ui.haptic.HapticIconButton
import moe.ouom.neriplayer.ui.haptic.HapticTextButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialog
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsDialogContent
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextButton
import moe.ouom.neriplayer.ui.screen.tab.settings.miuix.MiuixSettingsTextField
import moe.ouom.neriplayer.ui.util.currentWindowWidthDp
import moe.ouom.neriplayer.util.format.formatPlayCount
import moe.ouom.neriplayer.util.media.offlineCachedImageRequest
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

enum class LibraryTab(val labelResId: Int) {
    LOCAL(R.string.library_tab_local),
    FAVORITE(R.string.library_tab_favorite),
    YTMUSIC(R.string.library_tab_youtube_music),
    NETEASE(R.string.library_tab_netease),
    NETEASEALBUM(R.string.library_tab_netease_album),
    BILI(R.string.library_tab_bilibili),
    QQMUSIC(R.string.library_tab_qqmusic)
}

private const val NETEASE_CATEGORY_PLAYLIST = 0
private const val NETEASE_CATEGORY_ALBUM = 1
private const val FAVORITE_CATEGORY_PLAYLIST = 0
private const val FAVORITE_CATEGORY_ARTIST = 1
private const val FAVORITE_CATEGORY_HOT = 2
private const val LOCAL_CATEGORY_PLAYLIST = 0
private const val LOCAL_CATEGORY_ARTIST = 1
private const val LIBRARY_UI_PREFS = "library_ui_preferences"
private const val KEY_LIBRARY_TAB_ORDER = "library_tab_order"
private const val KEY_LOCAL_ARTIST_SORT_MODE = "local_artist_sort_mode"
private val LibraryPrimaryTabShape = RoundedCornerShape(20.dp)
private val LibrarySearchFieldShape = RoundedCornerShape(16.dp)

private val HotPlaylistPeriods = listOf(
    PlaybackStatsPeriod.WEEK,
    PlaybackStatsPeriod.MONTH
)

private fun hotPlaylistTitleResId(period: PlaybackStatsPeriod): Int = when (period) {
    PlaybackStatsPeriod.MONTH -> R.string.library_hot_playlist_month
    else -> R.string.library_hot_playlist_week
}

internal enum class LocalArtistSortMode {
    SONG_COUNT,
    RECENT_ADDED,
    NAME
}

internal fun resolveLocalArtistSortMode(storageValue: String?): LocalArtistSortMode {
    return LocalArtistSortMode.entries.firstOrNull { it.name == storageValue }
        ?: LocalArtistSortMode.SONG_COUNT
}

internal fun localArtistSortModeStorageValue(sortMode: LocalArtistSortMode): String {
    return sortMode.name
}

private fun readLocalArtistSortMode(context: Context): LocalArtistSortMode {
    val prefs = context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
    return resolveLocalArtistSortMode(prefs.getString(KEY_LOCAL_ARTIST_SORT_MODE, null))
}

private fun persistLocalArtistSortMode(context: Context, sortMode: LocalArtistSortMode) {
    context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_LOCAL_ARTIST_SORT_MODE, localArtistSortModeStorageValue(sortMode))
        }
}

private fun readLibraryTabOrderStorage(context: Context): String? {
    return context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LIBRARY_TAB_ORDER, null)
}

private fun persistLibraryTabOrderStorage(context: Context, order: List<LibraryTab>) {
    context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_LIBRARY_TAB_ORDER, serializeLibraryTabOrder(order))
        }
}

@Composable
private fun rememberHotPlaylists(): List<PlaybackStatsHotPlaylist>? {
    val hotPlaylists by produceState<List<PlaybackStatsHotPlaylist>?>(initialValue = null) {
        val statsRepository = withContext(Dispatchers.IO) {
            AppContainer.playbackStatsRepo
        }
        combine(
            statsRepository.statsFlow,
            statsRepository.dailyStatsFlow
        ) { stats, dailyStats ->
            stats to dailyStats
        }.collect { (stats, dailyStats) ->
            value = withContext(Dispatchers.Default) {
                HotPlaylistPeriods.map { period ->
                    buildPlaybackStatsHotPlaylist(
                        stats = stats,
                        dailyStats = dailyStats,
                        period = period
                    )
                }
            }
        }
    }
    return hotPlaylists
}

internal fun parseLibraryTabOrderStorage(storage: String?): List<String> {
    if (storage.isNullOrBlank()) return emptyList()
    return storage.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

internal fun serializeLibraryTabOrder(tabs: List<LibraryTab>): String {
    return tabs.joinToString(separator = ",") { it.name }
}

internal fun resolveLibraryTabDisplayOrder(
    isInternational: Boolean,
    youtubeEnabled: Boolean,
    customOrderStorage: String?
): List<LibraryTab> {
    val defaultOrder = if (isInternational && youtubeEnabled) {
        listOf(
            LibraryTab.LOCAL,
            LibraryTab.FAVORITE,
            LibraryTab.YTMUSIC,
            LibraryTab.NETEASE,
            LibraryTab.BILI,
            LibraryTab.QQMUSIC
        )
    } else {
        listOf(
            LibraryTab.LOCAL,
            LibraryTab.FAVORITE,
            LibraryTab.NETEASE,
            LibraryTab.YTMUSIC,
            LibraryTab.BILI,
            LibraryTab.QQMUSIC
        )
    }
    val visibleDefaults = if (youtubeEnabled) {
        defaultOrder
    } else {
        defaultOrder - LibraryTab.YTMUSIC
    }
    val customNames = parseLibraryTabOrderStorage(customOrderStorage)
    if (customNames.isEmpty()) return visibleDefaults
    val byName = LibraryTab.entries.associateBy { it.name }
    val customVisible = customNames
        .mapNotNull { byName[it] }
        .map { it.asVisibleLibraryTab() }
        .filter { it in visibleDefaults }
        .distinct()
    if (customVisible.isEmpty()) return visibleDefaults
    val remaining = visibleDefaults.filterNot { it in customVisible }
    return customVisible + remaining
}

internal fun libraryTabDisplayOrder(
    isInternational: Boolean,
    youtubeEnabled: Boolean = true,
    customOrderStorage: String? = null
): List<LibraryTab> {
    return resolveLibraryTabDisplayOrder(
        isInternational = isInternational,
        youtubeEnabled = youtubeEnabled,
        customOrderStorage = customOrderStorage
    )
}

private fun LibraryTab.asVisibleLibraryTab(): LibraryTab {
    return if (this == LibraryTab.NETEASEALBUM) LibraryTab.NETEASE else this
}

private fun LibraryTab?.isRefreshable(): Boolean {
    return when (this?.asVisibleLibraryTab()) {
        LibraryTab.BILI,
        LibraryTab.YTMUSIC,
        LibraryTab.NETEASE -> true
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    initialTab: LibraryTab = LibraryTab.LOCAL,
    onTabChange: (LibraryTab) -> Unit = {},
    localListState: LazyListState,
    favoriteListState: LazyListState,
    neteaseAlbumState: LazyListState,
    neteaseListState: LazyListState,
    youtubeMusicListState: LazyListState,
    biliListState: LazyListState,
    qqMusicListState: LazyListState,
    topAppBarState: TopAppBarState,
    onLocalPlaylistClick: (LocalPlaylist) -> Unit = {},
    onCachedPlaylistClick: () -> Unit = {},
    onLocalArtistClick: (LocalArtistSummary) -> Unit = {},
    onHotPlaylistClick: (PlaybackStatsPeriod) -> Unit = {},
    onNeteasePlaylistClick: (PlaylistSummary) -> Unit = {},
    onNeteaseAlbumClick: (AlbumSummary) -> Unit = {},
    onNeteaseArtistClick: (NeteaseArtistSummary) -> Unit = {},
    onYouTubeMusicPlaylistClick: (YouTubeMusicPlaylist) -> Unit = {},
    onBiliPlaylistClick: (BiliPlaylist) -> Unit = {},
    onOpenRecent: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    offlineMode: Boolean = false
) {
    val vm: LibraryViewModel = viewModel()
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val defaultPlaylistName = stringResource(R.string.library_create_playlist_default)
    val localPlaylistRepo = remember(context) {
        LocalPlaylistRepository.getInstance(context)
    }
    val isInternational by AppContainer.settingsRepo.internationalizationEnabledFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val youtubeEnabled by AppContainer.settingsRepo.youtubeEnabledFlow
        .collectAsStateWithLifecycle(initialValue = YouTubeFeatureGate.isEnabled())
    var libraryTabOrderStorage by remember {
        mutableStateOf(readLibraryTabOrderStorage(context))
    }
    var showLibraryTabOrderEditor by remember { mutableStateOf(false) }
    var pendingLibraryTabKey by remember { mutableStateOf<LibraryTab?>(null) }

    val orderedTabs = remember(isInternational, youtubeEnabled, libraryTabOrderStorage) {
        libraryTabDisplayOrder(
            isInternational = isInternational,
            youtubeEnabled = youtubeEnabled,
            customOrderStorage = libraryTabOrderStorage
        )
    }
    val initialPage = remember(orderedTabs, initialTab) {
        orderedTabs.indexOf(initialTab.asVisibleLibraryTab()).takeIf { it >= 0 } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { orderedTabs.size }
    )
    var selectedNeteaseCategory by rememberSaveable {
        mutableIntStateOf(NETEASE_CATEGORY_PLAYLIST)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        canScroll = {
            when (orderedTabs.getOrNull(pagerState.currentPage)) {
                LibraryTab.LOCAL -> shouldAllowCollapsingTopAppBar(
                    localListState.canScrollForward,
                    localListState.canScrollBackward,
                    topAppBarState.collapsedFraction
                )
                LibraryTab.FAVORITE -> shouldAllowCollapsingTopAppBar(
                    favoriteListState.canScrollForward,
                    favoriteListState.canScrollBackward,
                    topAppBarState.collapsedFraction
                )
                LibraryTab.NETEASE,
                LibraryTab.NETEASEALBUM -> {
                    val activeListState = if (
                        selectedNeteaseCategory == NETEASE_CATEGORY_ALBUM
                    ) {
                        neteaseAlbumState
                    } else {
                        neteaseListState
                    }
                    shouldAllowCollapsingTopAppBar(
                        activeListState.canScrollForward,
                        activeListState.canScrollBackward,
                        topAppBarState.collapsedFraction
                    )
                }
                LibraryTab.YTMUSIC -> shouldAllowCollapsingTopAppBar(
                    youtubeMusicListState.canScrollForward,
                    youtubeMusicListState.canScrollBackward,
                    topAppBarState.collapsedFraction
                )
                LibraryTab.BILI -> shouldAllowCollapsingTopAppBar(
                    biliListState.canScrollForward,
                    biliListState.canScrollBackward,
                    topAppBarState.collapsedFraction
                )
                LibraryTab.QQMUSIC -> shouldAllowCollapsingTopAppBar(
                    qqMusicListState.canScrollForward,
                    qqMusicListState.canScrollBackward,
                    topAppBarState.collapsedFraction
                )
                null -> shouldAllowCollapsingTopAppBar(
                    canScrollForward = false,
                    canScrollBackward = false,
                    collapsedFraction = topAppBarState.collapsedFraction
                )
            }
        }
    )
    val scope = rememberCoroutineScope()
    val windowWidthDp = currentWindowWidthDp()
    val isTabletLayout = windowWidthDp >= 720.dp
    val pageHorizontalPadding = if (isTabletLayout) 28.dp else 0.dp

    LaunchedEffect(initialTab, orderedTabs) {
        val targetPage = orderedTabs.indexOf(initialTab.asVisibleLibraryTab()).takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(orderedTabs) {
        val keep = pendingLibraryTabKey ?: return@LaunchedEffect
        pendingLibraryTabKey = null
        val target = orderedTabs.indexOf(keep.asVisibleLibraryTab()).takeIf { it >= 0 } ?: return@LaunchedEffect
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(pagerState.currentPage, orderedTabs, initialTab) {
        val currentTab = orderedTabs.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (currentTab != initialTab) {
            onTabChange(currentTab)
        }
    }

    val currentLibraryTab = orderedTabs.getOrNull(pagerState.currentPage)
    val libraryRefreshEnabled = currentLibraryTab.isRefreshable()

    Column(
        Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶栏右侧：刷新 → 播放统计 → 最近播放（排序入口一并上移，放在刷新前）
        NeriTabLargeTitleTopBar(
            title = stringResource(R.string.library_title),
            actions = {
                HapticIconButton(
                    onClick = {
                        when (currentLibraryTab) {
                            LibraryTab.BILI -> vm.refreshBilibili()
                            LibraryTab.YTMUSIC -> vm.refreshYouTubeMusicPlaylists()
                            LibraryTab.NETEASE -> {
                                vm.refreshNeteasePlaylists()
                                vm.refreshNeteaseAlbums()
                            }
                            else -> Unit
                        }
                    },
                    enabled = libraryRefreshEnabled
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh)
                    )
                }
                HapticIconButton(onClick = onOpenStats) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = stringResource(R.string.stats_title)
                    )
                }
                HapticIconButton(onClick = onOpenRecent) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = stringResource(R.string.library_recent_played)
                    )
                }
                HapticIconButton(onClick = { showLibraryTabOrderEditor = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.library_tab_order_cd)
                    )
                }
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .padding(horizontal = pageHorizontalPadding, vertical = 4.dp)
                .widthIn(max = 1180.dp)
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(Modifier.fillMaxSize()) {
                LibraryMainTabs(
                    tabs = orderedTabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp
                ) { page ->
                    when (orderedTabs[page]) {
                        LibraryTab.LOCAL -> LocalPlaylistList(
                            playlists = ui.localPlaylists,
                            listState = localListState,
                            onCreate = { name ->
                                val finalName = name.trim().ifBlank { defaultPlaylistName }
                                vm.createLocalPlaylist(finalName)
                            },
                            onImportExternalPlaylist = { rawLink ->
                                vm.importExternalPlaylist(rawLink)
                            },
                            onClick = onLocalPlaylistClick,
                            onCachedPlaylistClick = onCachedPlaylistClick,
                            onArtistClick = onLocalArtistClick,
                            onRename = { playlistId, newName ->
                                vm.renameLocalPlaylist(playlistId, newName)
                            },
                            onDelete = { playlistIds ->
                                vm.deleteLocalPlaylists(playlistIds) { result ->
                                    showPlaylistDeleteResultGlobally(
                                        context = context,
                                        repository = localPlaylistRepo,
                                        result = result
                                    )
                                }
                            },
                            onReorder = { order ->
                                vm.reorderLocalPlaylists(order)
                            },
                            offlineMode = offlineMode
                        )

                        LibraryTab.FAVORITE -> FavoritePlaylistList(
                            listState = favoriteListState,
                            onHotPlaylistClick = onHotPlaylistClick,
                            onNeteasePlaylistClick = onNeteasePlaylistClick,
                            onNeteaseAlbumClick = onNeteaseAlbumClick,
                            onNeteaseArtistClick = onNeteaseArtistClick,
                            onBiliPlaylistClick = onBiliPlaylistClick,
                            onYouTubeMusicPlaylistClick = onYouTubeMusicPlaylistClick,
                            offlineMode = offlineMode
                        )

                        LibraryTab.NETEASE,
                        LibraryTab.NETEASEALBUM -> NeteaseLibraryList(
                            playlists = ui.neteasePlaylists,
                            albums = ui.neteaseAlbums,
                            playlistListState = neteaseListState,
                            albumListState = neteaseAlbumState,
                            selectedCategory = selectedNeteaseCategory,
                            onCategoryChange = { selectedNeteaseCategory = it },
                            onPlaylistClick = onNeteasePlaylistClick,
                            onAlbumClick = onNeteaseAlbumClick,
                            offlineMode = offlineMode
                        )

                        LibraryTab.YTMUSIC -> YouTubeMusicPlaylistList(
                            playlists = ui.youtubeMusicPlaylists,
                            error = ui.youtubeMusicError,
                            listState = youtubeMusicListState,
                            onClick = onYouTubeMusicPlaylistClick,
                            onRetry = { vm.refreshYouTubeMusicPlaylists() },
                            offlineMode = offlineMode
                        )

                        LibraryTab.BILI -> BiliPlaylistList(
                            playlists = ui.biliPlaylists,
                            error = ui.biliError,
                            listState = biliListState,
                            onClick = onBiliPlaylistClick,
                            offlineMode = offlineMode
                        )

                        LibraryTab.QQMUSIC -> QqMusicPlaylistList(
                            listState = qqMusicListState
                        )
                    }
                }
            }
        }
    }

    if (showLibraryTabOrderEditor) {
        LibraryTabOrderEditorDialog(
            tabs = orderedTabs,
            onDismiss = { showLibraryTabOrderEditor = false },
            onConfirm = { newOrder ->
                pendingLibraryTabKey = orderedTabs.getOrNull(pagerState.currentPage)
                persistLibraryTabOrderStorage(context, newOrder)
                libraryTabOrderStorage = serializeLibraryTabOrder(newOrder)
                showLibraryTabOrderEditor = false
            },
            onReset = {
                pendingLibraryTabKey = orderedTabs.getOrNull(pagerState.currentPage)
                context.getSharedPreferences(LIBRARY_UI_PREFS, Context.MODE_PRIVATE)
                    .edit { remove(KEY_LIBRARY_TAB_ORDER) }
                libraryTabOrderStorage = null
                showLibraryTabOrderEditor = false
            }
        )
    }
}

@Composable
private fun LibraryTabOrderEditorDialog(
    tabs: List<LibraryTab>,
    onDismiss: () -> Unit,
    onConfirm: (List<LibraryTab>) -> Unit,
    onReset: () -> Unit
) {
    val draft = remember(tabs) { mutableStateListOf<LibraryTab>().apply { addAll(tabs) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_tab_order_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.library_tab_order_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(draft) { index, tab ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(tab.labelResId),
                                modifier = Modifier.weight(1f)
                            )
                            HapticIconButton(
                                onClick = {
                                    if (index > 0) {
                                        val item = draft.removeAt(index)
                                        draft.add(index - 1, item)
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.library_tab_order_move_up)
                                )
                            }
                            HapticIconButton(
                                onClick = {
                                    if (index < draft.lastIndex) {
                                        val item = draft.removeAt(index)
                                        draft.add(index + 1, item)
                                    }
                                },
                                enabled = index < draft.lastIndex
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.library_tab_order_move_down)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HapticTextButton(onClick = { onConfirm(draft.toList()) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            Row {
                HapticTextButton(onClick = onReset) {
                    Text(stringResource(R.string.action_reset))
                }
                HapticTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun LibraryMainTabs(
    tabs: List<LibraryTab>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        AdvancedGlassSurface(
            role = AdvancedGlassRole.ScreenTopTab,
            modifier = Modifier
                .fillMaxWidth()
                .clip(LibraryPrimaryTabShape),
            shape = LibraryPrimaryTabShape
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(tab.labelResId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeMusicPlaylistList(
    playlists: List<YouTubeMusicPlaylist>,
    error: String?,
    listState: LazyListState,
    onClick: (YouTubeMusicPlaylist) -> Unit,
    onRetry: () -> Unit,
    offlineMode: Boolean
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val context = LocalContext.current
    val composeResources = LocalResources.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val favoriteRepo = remember(context) { FavoritePlaylistRepository.getInstance(context) }
    val favorites by favoriteRepo.favorites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menuPlaylist by remember { mutableStateOf<YouTubeMusicPlaylist?>(null) }

    fun copyToClipboard(label: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        AppFeedback.show(
            context = context,
            message = composeResources.getString(R.string.toast_copied)
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp + miniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        if (playlists.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = error ?: stringResource(R.string.library_youtube_music_empty),
                                color = if (error != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Unspecified
                                }
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.library_youtube_music_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (error != null) {
                                    HapticTextButton(onClick = onRetry) {
                                        Text(text = stringResource(R.string.action_retry))
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_youtube),
                                contentDescription = stringResource(R.string.common_youtube),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        }
        items(
            items = playlists,
            key = { it.browseId }
        ) { playlist ->
            val playlistFavoriteId = remember(playlist.playlistId, playlist.browseId) {
                playlist.favoriteId()
            }
            val isFavorite = remember(favorites, playlistFavoriteId) {
                favoriteRepo.isFavorite(playlistFavoriteId, "youtubeMusic")
            }
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .combinedClickable(
                        onClick = { onClick(playlist) },
                        onLongClick = { menuPlaylist = playlist }
                    )
            ) {
                ListItem(
                    headlineContent = { Text(playlist.title) },
                    supportingContent = {
                        val trackCountText = playlist.trackCount
                            .takeIf { it > 0 }
                            ?.let { count ->
                                pluralStringResource(
                                    R.plurals.library_song_count,
                                    count,
                                    count
                                )
                            }
                        val subtitleText = playlist.subtitle.ifBlank {
                            stringResource(R.string.library_youtube_music_hint)
                        }
                        Text(
                            text = listOfNotNull(subtitleText, trackCountText)
                                .distinct()
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        if (playlist.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = offlineCachedImageRequest(
                                    context = context,
                                    data = playlist.coverUrl,
                                    sizePx = 192,
                                    allowHardware = false,
                                    offlineMode = offlineMode
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                )

                DropdownMenu(
                    expanded = menuPlaylist?.browseId == playlist.browseId,
                    onDismissRequest = { menuPlaylist = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_youtube_music_open_playlist)) },
                        onClick = {
                            menuPlaylist = null
                            onClick(playlist)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isFavorite) {
                                    stringResource(R.string.home_unfavorite_playlist)
                                } else {
                                    stringResource(R.string.home_favorite_playlist)
                                }
                            )
                        },
                        onClick = {
                            menuPlaylist = null
                            val toastMessage = if (isFavorite) {
                                composeResources.getString(R.string.home_unfavorited)
                            } else {
                                composeResources.getString(R.string.favorite_success)
                            }
                            scope.launch {
                                if (isFavorite) {
                                    favoriteRepo.removeFavorite(playlistFavoriteId, "youtubeMusic")
                                } else {
                                    favoriteRepo.addFavorite(
                                        id = playlistFavoriteId,
                                        name = playlist.title,
                                        coverUrl = playlist.coverUrl,
                                        trackCount = playlist.trackCount,
                                        source = "youtubeMusic",
                                        browseId = playlist.browseId,
                                        playlistId = playlist.playlistId,
                                        subtitle = playlist.subtitle,
                                        songs = emptyList()
                                    )
                                }
                                AppFeedback.show(
                                    context = context,
                                    message = toastMessage
                                )
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_youtube_music_copy_browse_id)) },
                        onClick = {
                            copyToClipboard("ytmusic_browse_id", playlist.browseId)
                            menuPlaylist = null
                        }
                    )
                    if (playlist.playlistId.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_youtube_music_copy_playlist_id)) },
                            onClick = {
                                copyToClipboard("ytmusic_playlist_id", playlist.playlistId)
                                menuPlaylist = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiliPlaylistList(
    playlists: List<BiliPlaylist>,
    error: String?,
    listState: LazyListState,
    onClick: (BiliPlaylist) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val createdLabel = stringResource(R.string.library_bili_created_favorite)
    val collectedLabel = stringResource(R.string.library_bili_collected_favorite)
    val collectionLabel = stringResource(R.string.library_bili_collection)
    val seriesLabel = stringResource(R.string.library_bili_series)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredPlaylists = remember(
        playlists,
        searchQuery,
        createdLabel,
        collectedLabel,
        collectionLabel,
        seriesLabel
    ) {
        filterBiliPlaylists(
            playlists = playlists,
            query = searchQuery,
            createdLabel = createdLabel,
            collectedLabel = collectedLabel,
            collectionLabel = collectionLabel,
            seriesLabel = seriesLabel
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        item(key = "bili_playlist_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.library_bili_search_hint)) },
                singleLine = true,
                shape = LibrarySearchFieldShape
            )
        }
        if (playlists.isNotEmpty() && filteredPlaylists.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.library_bili_search_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.library_bili_search_empty_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        } else if (filteredPlaylists.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = error ?: stringResource(R.string.library_bili_empty),
                                color = if (error != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Unspecified
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.library_bili_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        }
        items(
            items = filteredPlaylists,
            key = { "${it.kind}:${it.mediaId}" }
        ) { pl ->
            val kindLabel = when (pl.kind) {
                BiliPlaylistKind.CREATED_FAVORITE -> stringResource(R.string.library_bili_created_favorite)
                BiliPlaylistKind.COLLECTED_FAVORITE -> stringResource(R.string.library_bili_collected_favorite)
                BiliPlaylistKind.COLLECTION -> stringResource(R.string.library_bili_collection)
                BiliPlaylistKind.SERIES -> stringResource(R.string.library_bili_series)
            }
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .clickable { onClick(pl) }
            ) {
                ListItem(
                    headlineContent = { Text(pl.title) },
                    supportingContent = {
                        val countText = pluralStringResource(R.plurals.library_video_count, pl.count, pl.count)
                        Text(
                            listOf(kindLabel, pl.subtitle, countText)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        if (pl.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = offlineCachedImageRequest(
                                    context = context,
                                    data = pl.coverUrl,
                                    sizePx = 192,
                                    allowHardware = false,
                                    offlineMode = offlineMode
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistList(
    playlists: List<LocalPlaylist>,
    listState: LazyListState,
    onCreate: (String) -> Unit,
    onClick: (LocalPlaylist) -> Unit,
    onCachedPlaylistClick: () -> Unit,
    onArtistClick: (LocalArtistSummary) -> Unit,
    onImportExternalPlaylist: suspend (String) -> ExternalPlaylistImportResult,
    onRename: (Long, String) -> Unit = { _, _ -> },
    onDelete: (List<Long>) -> Unit = {},
    onReorder: (List<Long>) -> Unit = {},
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val cachedSnapshot by produceState(initialValue = CachedSongsSnapshot.Empty) {
        while (true) {
            value = runCatching { PlayerManager.cachedSongsSnapshot() }.getOrDefault(CachedSongsSnapshot.Empty)
            delay(5_000L)
        }
    }
    val composeResources = LocalResources.current
    var selectedLocalCategory by rememberSaveable {
        mutableIntStateOf(LOCAL_CATEGORY_PLAYLIST)
    }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    var localArtistSortMode by rememberSaveable {
        mutableStateOf(readLocalArtistSortMode(context))
    }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteSelectedConfirm by rememberSaveable { mutableStateOf(false) }
    var localSortMode by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importLink by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    var importLoading by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val importScope = rememberCoroutineScope()
    val onImportExternalPlaylistState = rememberUpdatedState(onImportExternalPlaylist)
    val defaultPlaylistName = composeResources.getString(R.string.library_create_playlist_default)
    val maxNameLength = LocalPlaylistRepository.MAX_PLAYLIST_NAME_LENGTH
    val autoShowKeyboard by AppContainer.settingsRepo.autoShowKeyboardFlow.collectAsStateWithLifecycle(
        initialValue = false
    )
    val localArtists = remember(playlists, context) {
        buildLocalArtistSummaries(playlists, context)
    }
    val filteredLocalArtists = remember(localArtists, localSearchQuery) {
        filterLocalArtists(localArtists, localSearchQuery)
    }
    val displayedLocalArtists = remember(filteredLocalArtists, localArtistSortMode) {
        sortLocalArtists(filteredLocalArtists, localArtistSortMode)
    }
    val editablePlaylists = remember(playlists, context) {
        // Include 我喜欢的音乐 in custom sort; keep 本地文件 pinned outside.
        playlists.filterNot {
            LocalFilesPlaylist.isSystemPlaylist(it, context) &&
                !FavoritesPlaylist.isSystemPlaylist(it, context)
        }
    }
    // Stable list identity: never recreate during drag (remember(editablePlaylists) caused flicker)
    val reorderablePlaylists = remember { mutableStateListOf<LocalPlaylist>() }

    LaunchedEffect(editablePlaylists, localSortMode) {
        if (!localSortMode) {
            reorderablePlaylists.clear()
            reorderablePlaylists.addAll(editablePlaylists)
        } else {
            val currentIds = reorderablePlaylists.map { it.id }.toSet()
            val editableIds = editablePlaylists.map { it.id }.toSet()
            if (currentIds != editableIds) {
                reorderablePlaylists.clear()
                reorderablePlaylists.addAll(editablePlaylists)
            }
        }
    }

    LaunchedEffect(showDialog) {
        if (showDialog && autoShowKeyboard) focusRequester.requestFocus()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        showDeleteSelectedConfirm = false
    }

    fun exitSortMode() {
        if (localSortMode) {
            onReorder(reorderablePlaylists.map { it.id })
            localSortMode = false
        }
    }

    fun enterSortMode() {
        selectionMode = false
        selectedIds = emptySet()
        localSortMode = true
        reorderablePlaylists.clear()
        reorderablePlaylists.addAll(editablePlaylists)
    }

    BackHandler(enabled = selectionMode || localSortMode) {
        when {
            selectionMode -> exitSelection()
            localSortMode -> exitSortMode()
        }
    }

    fun toggleSelection(playlistId: Long) {
        selectedIds =
            if (selectedIds.contains(playlistId)) selectedIds - playlistId else selectedIds + playlistId
    }

    fun deleteSelected() {
        if (selectedIds.isEmpty()) return
        showDeleteSelectedConfirm = true
    }

    LaunchedEffect(editablePlaylists) {
        val validIds = editablePlaylists.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(validIds)
        if (selectionMode && editablePlaylists.isEmpty()) {
            exitSelection()
        }
    }

    fun tryCreate(): Boolean {
        val trimmedInput = newName.trim().take(maxNameLength)
        val finalName = trimmedInput.ifBlank { defaultPlaylistName }.take(maxNameLength)

        val favoritesName = composeResources.getString(R.string.favorite_my_music)
        val localFilesName = composeResources.getString(R.string.local_files)
        if (FavoritesPlaylist.matches(finalName, context)) {
            nameError = composeResources.getString(R.string.library_name_reserved, favoritesName)
            return false
        }
        if (LocalFilesPlaylist.matches(finalName, context)) {
            nameError = composeResources.getString(R.string.library_name_reserved, localFilesName)
            return false
        }
        if (playlists.any { it.name.equals(finalName, ignoreCase = true) }) {
            nameError = composeResources.getString(R.string.library_name_exists)
            return false
        }

        onCreate(finalName)
        showDialog = false
        newName = ""
        nameError = null
        return true
    }

    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val favoritesPlaylist = playlists.firstOrNull { FavoritesPlaylist.isSystemPlaylist(it, context) }
    val localFilesPlaylist = playlists.firstOrNull { LocalFilesPlaylist.isSystemPlaylist(it, context) }
    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from: ItemPosition, to: ItemPosition ->
            if (!localSortMode) return@rememberReorderableLazyListState
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val fromIdx = reorderablePlaylists.indexOfFirst { it.id == fromId }
            val toIdx = reorderablePlaylists.indexOfFirst { it.id == toId }
            if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                reorderablePlaylists.add(toIdx, reorderablePlaylists.removeAt(fromIdx))
            }
        },
        canDragOver = { _, over ->
            localSortMode && over.key is Long
        },
        onDragEnd = { _, _ ->
            if (localSortMode) {
                onReorder(reorderablePlaylists.map { it.id })
            }
        }
    )

    // Track finger Y in LazyList coordinates so auto-scroll keeps running when the
    // held playlist is dragged toward the screen top (may leave visibleItemsInfo).
    var dragPointerY by remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    LaunchedEffect(localSortMode, listState, reorderState) {
        if (!localSortMode) return@LaunchedEffect
        val scrollStepDownPx = with(density) { 22.dp.toPx() }
        val scrollStepUpPx = with(density) { 16.dp.toPx() }
        val edgePx = with(density) { 72.dp.toPx() }
        while (localSortMode) {
            // Read pointer/state each frame; do not key this effect on dragPointerY
            val pointerY = dragPointerY
            if (pointerY.isNaN() || reorderState.draggingItemIndex == null) {
                delay(20L)
                continue
            }
            val info = listState.layoutInfo
            val viewportHeight =
                (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            val viewportBottom = info.viewportStartOffset + viewportHeight
            when {
                pointerY <= info.viewportStartOffset + edgePx &&
                    listState.canScrollBackward -> {
                    listState.scrollBy(-scrollStepUpPx)
                }
                pointerY >= viewportBottom - edgePx &&
                    listState.canScrollForward -> {
                    listState.scrollBy(scrollStepDownPx)
                }
            }
            delay(20L)
        }
    }

    val displayedFavoritesPlaylist = favoritesPlaylist
        ?.takeIf { playlist -> playlist.matchesLocalPlaylistSearch(localSearchQuery, context) }
        ?.takeIf { false } // favorites now lives in the sortable playlist list
    val displayedLocalFilesPlaylist = localFilesPlaylist
        ?.takeIf { playlist -> playlist.matchesLocalPlaylistSearch(localSearchQuery, context) }
    val displayedPlaylists = if (localSortMode) {
        reorderablePlaylists.toList()
    } else {
        reorderablePlaylists.filter { playlist -> playlist.matchesLocalPlaylistSearch(localSearchQuery, context) }
    }
    val hasPlaylistSearchMatches =
        displayedFavoritesPlaylist != null ||
            displayedPlaylists.isNotEmpty() ||
            displayedLocalFilesPlaylist != null ||
            composeResources.getString(R.string.cached_songs_playlist)
                .contains(localSearchQuery, ignoreCase = true)
    val windowWidthDp = currentWindowWidthDp()
    val localArtistColumnCount = remember(windowWidthDp) {
        ((windowWidthDp.value - 16f + 10f) / 130f).toInt().coerceAtLeast(1)
    }
    val localArtistRows = remember(displayedLocalArtists, localArtistColumnCount) {
        displayedLocalArtists.chunked(localArtistColumnCount)
    }

    LazyColumn(
        state = reorderState.listState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = if (localSortMode) 24.dp else 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            // Shrink list viewport above mini player so drag auto-scroll stops in the visible area
            .padding(bottom = miniPlayerHeight)
            .pointerInput(localSortMode) {
                if (!localSortMode) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragPointerY = down.position.y
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change == null) {
                            pressed = false
                        } else {
                            dragPointerY = change.position.y
                        }
                    }
                    dragPointerY = Float.NaN
                }
            }
            .reorderable(reorderState)
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        item(key = "local_library_header") {
            LocalLibraryHeaderContent(
                selectedLocalCategory = selectedLocalCategory,
                selectionMode = selectionMode,
                searchQuery = localSearchQuery,
                onSearchQueryChange = { localSearchQuery = it },
                artistSortMode = localArtistSortMode,
                onArtistSortModeChange = { sortMode ->
                    localArtistSortMode = sortMode
                    persistLocalArtistSortMode(context, sortMode)
                },
                onPlaylistSelected = {
                    if (selectedLocalCategory != LOCAL_CATEGORY_PLAYLIST) {
                        exitSelection()
                        selectedLocalCategory = LOCAL_CATEGORY_PLAYLIST
                    }
                },
                onArtistSelected = {
                    if (selectedLocalCategory != LOCAL_CATEGORY_ARTIST) {
                        exitSelection()
                        selectedLocalCategory = LOCAL_CATEGORY_ARTIST
                    }
                },
                showCreatePlaylist = selectedLocalCategory == LOCAL_CATEGORY_PLAYLIST &&
                    !selectionMode &&
                    !localSortMode &&
                    localSearchQuery.isBlank(),
                onCreatePlaylist = { showDialog = true },
                showImport = selectedLocalCategory == LOCAL_CATEGORY_PLAYLIST &&
                    !selectionMode &&
                    !localSortMode &&
                    localSearchQuery.isBlank(),
                onImportPlaylist = {
                    importError = null
                    if (importLink.isBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip
                        val pasted = (0 until (clip?.itemCount ?: 0))
                            .mapNotNull { index -> clip?.getItemAt(index)?.coerceToText(context)?.toString() }
                            .firstOrNull { it.isNotBlank() }
                            .orEmpty()
                        if (pasted.isNotBlank()) importLink = pasted
                    }
                    showImportDialog = true
                },
                showLocalSort = selectedLocalCategory == LOCAL_CATEGORY_PLAYLIST &&
                    !selectionMode &&
                    localSearchQuery.isBlank() &&
                    editablePlaylists.size >= 2,
                localSortMode = localSortMode,
                onToggleLocalSort = {
                    if (localSortMode) exitSortMode() else enterSortMode()
                }
            )
        }

        if (selectedLocalCategory == LOCAL_CATEGORY_ARTIST) {
            if (displayedLocalArtists.isEmpty()) {
                item(key = "local_artist_empty") {
                    LibrarySearchEmptyCard(
                        titleResId = if (localSearchQuery.isBlank()) {
                            R.string.library_local_artist_empty
                        } else {
                            R.string.library_local_search_empty
                        },
                        hintResId = if (localSearchQuery.isBlank()) {
                            R.string.library_local_artist_hint
                        } else {
                            R.string.library_local_search_empty_hint
                        },
                        iconIsArtist = true
                    )
                }
            }
            items(
                items = localArtistRows,
                key = { row -> row.joinToString(separator = "|") { artist -> artist.stableKey } }
            ) { rowArtists ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                ) {
                    rowArtists.forEach { artist ->
                        Box(modifier = Modifier.weight(1f)) {
                            LocalArtistGridCard(
                                artist = artist,
                                onClick = { onArtistClick(artist) },
                                offlineMode = offlineMode
                            )
                        }
                    }
                    repeat(localArtistColumnCount - rowArtists.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
                if (selectionMode) {
                    item(key = "local_playlist_selection_header") {
                        val allSelected =
                            selectedIds.size == displayedPlaylists.size && displayedPlaylists.isNotEmpty()
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.common_selected_count,
                                    selectedIds.size,
                                    selectedIds.size
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            HapticIconButton(onClick = { exitSelection() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_exit_multi_select)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HapticTextButton(
                                    onClick = {
                                        selectedIds = if (allSelected) {
                                            emptySet()
                                        } else {
                                            displayedPlaylists.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        if (allSelected) {
                                            stringResource(R.string.action_deselect_all)
                                        } else {
                                            stringResource(R.string.action_select_all)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticTextButton(
                                    enabled = selectedIds.isNotEmpty(),
                                    onClick = { deleteSelected() }
                                ) {
                                    Text(stringResource(R.string.common_delete_selected))
                                }
                            }
                        }
                    )
                }
            }
        }
        if (localSearchQuery.isBlank()) {
            item(key = "local_playlist_create") {
                // 新建入口已上移到 header 右侧「+新建」

            if (showDialog) {
                MiuixSettingsDialog(
                    onDismissRequest = {
                        showDialog = false
                        newName = ""
                        nameError = null
                    },
                    title = { Text(stringResource(R.string.playlist_create)) },
                    text = {
                        MiuixSettingsDialogContent(verticalSpacing = 12.dp) {
                            MiuixSettingsTextField(
                                value = newName,
                                onValueChange = {
                                    newName = it.take(maxNameLength)
                                    if (nameError != null) nameError = null
                                },
                                placeholder = { Text(stringResource(R.string.playlist_enter_name)) },
                                singleLine = true,
                                modifier = Modifier.focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { tryCreate() })
                            )
                            if (nameError != null) {
                                Text(
                                    text = nameError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        MiuixSettingsButton(
                            onClick = { tryCreate() },
                            enabled = newName.trim().isNotBlank()
                        ) {
                            Text(stringResource(R.string.action_create))
                        }
                    },
                    dismissButton = {
                        MiuixSettingsTextButton(
                            onClick = {
                                showDialog = false
                                newName = ""
                                nameError = null
                            }
                        ) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }

            if (showImportDialog) {
                MiuixSettingsDialog(
                    onDismissRequest = {
                        if (!importLoading) {
                            showImportDialog = false
                            importError = null
                        }
                    },
                    title = { Text(stringResource(R.string.library_import_playlist_dialog_title)) },
                    text = {
                        MiuixSettingsDialogContent(verticalSpacing = 12.dp) {
                            MiuixSettingsTextField(
                                value = importLink,
                                onValueChange = {
                                    importLink = it
                                    if (importError != null) importError = null
                                },
                                placeholder = {
                                    Text(stringResource(R.string.library_import_playlist_hint))
                                },
                                singleLine = true,
                                enabled = !importLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.library_import_playlist_supported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (importLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.library_import_playlist_loading),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            importError?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        MiuixSettingsButton(
                            enabled = !importLoading && importLink.isNotBlank(),
                            onClick = {
                                val raw = importLink.trim()
                                if (raw.isBlank() || importLoading) return@MiuixSettingsButton
                                importLoading = true
                                importError = null
                                importScope.launch {
                                    val result = try {
                                        onImportExternalPlaylistState.value(raw)
                                    } catch (error: Exception) {
                                        importLoading = false
                                        importError = error.message
                                            ?: composeResources.getString(R.string.error_network)
                                        return@launch
                                    }
                                    importLoading = false
                                    when (result) {
                                        is ExternalPlaylistImportResult.Success -> {
                                            showImportDialog = false
                                            importLink = ""
                                            importError = null
                                            AppFeedback.show(
                                                context = context,
                                                message = composeResources.getString(
                                                    R.string.library_import_playlist_success
                                                )
                                            )
                                        }
                                        is ExternalPlaylistImportResult.Failure -> {
                                            importError = result.message
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.library_import_playlist))
                        }
                    },
                    dismissButton = {
                        MiuixSettingsTextButton(
                            enabled = !importLoading,
                            onClick = {
                                showImportDialog = false
                                importError = null
                            }
                        ) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }

            if (showDeleteSelectedConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteSelectedConfirm = false },
                    title = { Text(stringResource(R.string.dialog_confirm_delete)) },
                    text = {
                        Text(
                            pluralStringResource(
                                R.plurals.library_delete_selected_confirm,
                                selectedIds.size,
                                selectedIds.size
                            )
                        )
                    },
                    confirmButton = {
                        HapticTextButton(
                            onClick = {
                                val idsToDelete = selectedIds.toList()
                                exitSelection()
                                onDelete(idsToDelete)
                            }
                        ) { Text(stringResource(R.string.action_delete)) }
                    },
                    dismissButton = {
                        HapticTextButton(
                            onClick = { showDeleteSelectedConfirm = false }
                        ) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }
            }
        }

        if (localSearchQuery.isNotBlank() && !hasPlaylistSearchMatches) {
            item(key = "local_playlist_search_empty") {
                LibrarySearchEmptyCard(
                    titleResId = R.string.library_local_search_empty,
                    hintResId = R.string.library_local_search_empty_hint,
                    iconIsArtist = false
                )
            }
        }

        displayedFavoritesPlaylist?.let { system ->
            item(key = "local_playlist_favorites") {
                val displayName = SystemLocalPlaylists.resolve(system.id, system.name, context)?.currentName ?: system.name
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                if (!selectionMode) onClick(system)
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, system.songs.size, system.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Spacer(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = rememberPlaylistDisplayCoverUrl(
                                    playlist = system,
                                    resolveLocalFallback = true
                                )
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = fastScrollableImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 160,
                                            offlineMode = offlineMode
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

            items(
                items = displayedPlaylists,
                key = { it.id }
            ) { pl ->
            ReorderableItem(state = reorderState, key = pl.id) { _ ->
                val systemPlaylist = SystemLocalPlaylists.resolve(pl.id, pl.name, context)
                val displayName = systemPlaylist?.currentName ?: pl.name
                val isSystemPlaylist = systemPlaylist != null
                val isFavoritesPlaylist = FavoritesPlaylist.isSystemPlaylist(pl, context)
                val isLocalFilesPlaylist = LocalFilesPlaylist.isSystemPlaylist(pl, context)
                val canCustomSort = !isLocalFilesPlaylist
                val isSelected = selectionMode && selectedIds.contains(pl.id)
                val rowContainerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }

                var showMenu by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(pl.name.take(maxNameLength)) }

                if (selectionMode && showMenu) showMenu = false

                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = rowContainerColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .then(if (localSortMode) Modifier else Modifier.animateItem())
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                when {
                                    localSortMode -> Unit
                                    selectionMode -> toggleSelection(pl.id)
                                    else -> onClick(pl)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode && !localSortMode && !isSystemPlaylist) {
                                    selectionMode = true
                                    selectedIds = setOf(pl.id)
                                }
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, pl.songs.size, pl.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (!isSystemPlaylist) toggleSelection(pl.id)
                                        },
                                        enabled = !isSystemPlaylist
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = rememberPlaylistDisplayCoverUrl(
                                    playlist = pl,
                                    resolveLocalFallback = true
                                )
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = fastScrollableImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 160,
                                            offlineMode = offlineMode
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            if (localSortMode && canCustomSort) {
                                Box(
                                    modifier = Modifier
                                        .detectReorder(reorderState)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.common_drag_handle),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (selectionMode && !isSystemPlaylist) {
                                Box(
                                    modifier = Modifier
                                        .detectReorder(reorderState)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.common_drag_handle),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (!selectionMode && !localSortMode && !isSystemPlaylist) {
                                Box {
                                    HapticIconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.common_more_options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            onClick = {
                                                showMenu = false
                                                renameText = pl.name.take(maxNameLength)
                                                showRenameDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_delete)) },
                                            onClick = {
                                                showMenu = false
                                                showDeleteDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                if (showRenameDialog) {
                    MiuixSettingsDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text(stringResource(R.string.action_rename)) },
                        text = {
                            MiuixSettingsDialogContent(verticalSpacing = 12.dp) {
                                MiuixSettingsTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it.take(maxNameLength) },
                                    placeholder = { Text(displayName) },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            MiuixSettingsButton(
                                onClick = {
                                    val trimmed = renameText.trim().take(maxNameLength)
                                    if (trimmed.isNotBlank()) {
                                        onRename(pl.id, trimmed)
                                        showRenameDialog = false
                                    }
                                },
                                enabled = renameText.trim().isNotBlank()
                            ) { Text(stringResource(R.string.action_confirm)) }
                        },
                        dismissButton = {
                            MiuixSettingsTextButton(
                                onClick = { showRenameDialog = false }
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text(stringResource(R.string.action_delete)) },
                        text = {
                            Text(stringResource(R.string.library_delete_playlist_confirm, displayName))
                        },
                        confirmButton = {
                            HapticTextButton(
                                onClick = {
                                    val playlistId = pl.id
                                    showDeleteDialog = false
                                    onDelete(listOf(playlistId))
                                }
                            ) { Text(stringResource(R.string.action_delete)) }
                        },
                        dismissButton = {
                            HapticTextButton(
                                onClick = { showDeleteDialog = false }
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }
            }
        }

        displayedLocalFilesPlaylist?.let { system ->
            item(key = "local_playlist_local_files") {
                val displayName = SystemLocalPlaylists.resolve(system.id, system.name, context)?.currentName ?: system.name
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                if (!selectionMode) onClick(system)
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, system.songs.size, system.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Spacer(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = rememberPlaylistDisplayCoverUrl(
                                    playlist = system,
                                    resolveLocalFallback = true
                                )
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = fastScrollableImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 160,
                                            offlineMode = offlineMode
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        if (composeResources.getString(R.string.cached_songs_playlist)
                .contains(localSearchQuery, ignoreCase = true)) {
            item(key = "local_playlist_cached_songs") {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                        .combinedClickable(onClick = {
                            if (!selectionMode && !localSortMode) onCachedPlaylistClick()
                        })
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.cached_songs_playlist)) },
                        supportingContent = {
                            Text(pluralStringResource(
                                R.plurals.library_song_count,
                                cachedSnapshot.songs.size,
                                cachedSnapshot.songs.size
                            ))
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun LocalLibraryHeaderContent(
    selectedLocalCategory: Int,
    selectionMode: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    artistSortMode: LocalArtistSortMode,
    onArtistSortModeChange: (LocalArtistSortMode) -> Unit,
    onPlaylistSelected: () -> Unit,
    onArtistSelected: () -> Unit,
    showCreatePlaylist: Boolean,
    onCreatePlaylist: () -> Unit,
    showImport: Boolean = false,
    onImportPlaylist: () -> Unit = {},
    showLocalSort: Boolean = false,
    localSortMode: Boolean = false,
    onToggleLocalSort: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        if (!selectionMode && !localSortMode) {
            if (selectedLocalCategory == LOCAL_CATEGORY_ARTIST) {
                LocalArtistSearchAndSortRow(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    sortMode = artistSortMode,
                    onSortModeChange = onArtistSortModeChange
                )
            } else {
                LibraryInlineSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholderResId = R.string.library_local_playlist_search_hint
                )
            }
        }
        // 歌单/歌手在左，排序与 +新建 靠右
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocalLibraryCategoryChips(
                selectedCategory = selectedLocalCategory,
                onPlaylistSelected = onPlaylistSelected,
                onArtistSelected = onArtistSelected
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showImport) {
                HapticTextButton(onClick = onImportPlaylist) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.library_import_playlist),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (showLocalSort) {
                // Text button keeps full two-character labels (排序 / 完成)
                HapticTextButton(onClick = onToggleLocalSort) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (localSortMode) {
                                Icons.Filled.Check
                            } else {
                                Icons.AutoMirrored.Filled.Sort
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (localSortMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (localSortMode) R.string.action_done
                                else R.string.library_local_playlist_sort
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = if (localSortMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            if (showCreatePlaylist && !selectionMode && !localSortMode) {
                HapticTextButton(
                    onClick = onCreatePlaylist,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(stringResource(R.string.library_create_new))
                }
            }
        }
    }
}

@Composable
private fun LocalLibraryCategoryChips(
    selectedCategory: Int,
    onPlaylistSelected: () -> Unit,
    onArtistSelected: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LocalLibraryCategoryChip(
            selected = selectedCategory == LOCAL_CATEGORY_PLAYLIST,
            label = stringResource(R.string.library_favorite_tab_playlists),
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            onClick = onPlaylistSelected
        )
        Spacer(modifier = Modifier.width(4.dp))
        LocalLibraryCategoryChip(
            selected = selectedCategory == LOCAL_CATEGORY_ARTIST,
            label = stringResource(R.string.library_favorite_tab_artists),
            icon = Icons.Filled.AccountCircle,
            onClick = onArtistSelected
        )
    }
}

@Composable
private fun LocalLibraryCategoryChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LocalArtistSearchAndSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: LocalArtistSortMode,
    onSortModeChange: (LocalArtistSortMode) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.library_local_artist_search_hint)) },
            singleLine = true,
            shape = LibrarySearchFieldShape,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    HapticIconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_clear)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Box {
            HapticIconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.library_local_artist_sort)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                LocalArtistSortMenuItem(
                    selected = sortMode == LocalArtistSortMode.SONG_COUNT,
                    text = stringResource(R.string.library_local_artist_sort_count),
                    onClick = {
                        onSortModeChange(LocalArtistSortMode.SONG_COUNT)
                        menuExpanded = false
                    }
                )
                LocalArtistSortMenuItem(
                    selected = sortMode == LocalArtistSortMode.RECENT_ADDED,
                    text = stringResource(R.string.library_local_artist_sort_recent),
                    onClick = {
                        onSortModeChange(LocalArtistSortMode.RECENT_ADDED)
                        menuExpanded = false
                    }
                )
                LocalArtistSortMenuItem(
                    selected = sortMode == LocalArtistSortMode.NAME,
                    text = stringResource(R.string.library_local_artist_sort_name),
                    onClick = {
                        onSortModeChange(LocalArtistSortMode.NAME)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalArtistSortMenuItem(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(text)
        },
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.common_selected)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        },
        onClick = onClick
    )
}

@Composable
private fun LibraryInlineSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderResId: Int
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text(stringResource(placeholderResId)) },
        singleLine = true,
        shape = LibrarySearchFieldShape,
        trailingIcon = {
            if (query.isNotEmpty()) {
                HapticIconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_clear)
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    )
}

@Composable
private fun LibrarySearchEmptyCard(
    titleResId: Int,
    hintResId: Int,
    iconIsArtist: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        ListItem(
            headlineContent = { Text(stringResource(titleResId)) },
            supportingContent = {
                Text(
                    stringResource(hintResId),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    imageVector = if (iconIsArtist) {
                        Icons.Filled.AccountCircle
                    } else {
                        Icons.AutoMirrored.Filled.QueueMusic
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
            }
        )
    }
}

private fun filterLocalArtists(
    artists: List<LocalArtistSummary>,
    query: String
): List<LocalArtistSummary> {
    if (query.isBlank()) return artists
    return artists.filter { artist -> artist.matchesLocalArtistSearch(query) }
}

internal fun sortLocalArtists(
    artists: List<LocalArtistSummary>,
    sortMode: LocalArtistSortMode
): List<LocalArtistSummary> {
    return when (sortMode) {
        LocalArtistSortMode.SONG_COUNT -> artists.sortedWith(
            compareByDescending<LocalArtistSummary> { artist ->
                artist.songs.size
            }.thenBy { artist ->
                artist.name.lowercase()
            }
        )
        LocalArtistSortMode.RECENT_ADDED -> artists.sortedWith(
            compareByDescending<LocalArtistSummary> { artist ->
                artist.coverSong?.addedAt ?: 0L
            }.thenBy { artist ->
                artist.name.lowercase()
            }
        )
        LocalArtistSortMode.NAME -> artists.sortedBy { artist ->
            artist.name.lowercase()
        }
    }
}

private fun LocalArtistSummary.matchesLocalArtistSearch(query: String): Boolean {
    return queryMatches(query, name) ||
        songs.any { song ->
            queryMatches(
                query,
                song.displayName(),
                song.displayArtist(),
                song.album,
                song.localFileName
            )
        }
}

private fun LocalPlaylist.matchesLocalPlaylistSearch(query: String, context: Context): Boolean {
    if (query.isBlank()) return true
    val displayName = SystemLocalPlaylists.resolve(id, name, context)?.currentName ?: name
    return queryMatches(query, id, name, displayName, songs.size) ||
        songs.any { song ->
            queryMatches(
                query,
                song.displayName(),
                song.displayArtist(),
                song.album,
                song.localFileName
            )
        }
}

private fun filterFavoritePlaylists(
    favorites: List<FavoritePlaylist>,
    query: String
): List<FavoritePlaylist> {
    if (query.isBlank()) return favorites
    return favorites.filter { favorite -> favorite.matchesFavoriteSearch(query) }
}

private fun FavoritePlaylist.matchesFavoriteSearch(query: String): Boolean {
    return queryMatches(
        query,
        id,
        name,
        subtitle,
        source,
        browseId,
        playlistId,
        trackCount,
        favoriteSourceSearchAliases(source)
    ) || songs.any { song ->
        queryMatches(
            query,
            song.displayName(),
            song.displayArtist(),
            song.album,
            song.localFileName
        )
    }
}

private fun favoriteSourceSearchAliases(source: String): List<String> {
    return when (source) {
        "youtubeMusic" -> listOf("YouTube", "YouTube Music")
        "neteaseAlbum" -> listOf("Netease Album", "网易云专辑", "专辑")
        "netease" -> listOf("Netease", "网易云", "歌单")
        "bili" -> listOf("Bilibili", "哔哩哔哩", "B站")
        FAVORITE_SOURCE_NETEASE_ARTIST -> listOf("Artist", "歌手")
        else -> listOf(source)
    }
}

private fun queryMatches(query: String, vararg values: Any?): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return true
    return values.any { value ->
        when (value) {
            null -> false
            is Iterable<*> -> value.any { item ->
                item?.toString()?.contains(normalizedQuery, ignoreCase = true) == true
            }
            else -> value.toString().contains(normalizedQuery, ignoreCase = true)
        }
    }
}

@Composable
private fun NeteaseLibraryList(
    playlists: List<PlaylistSummary>,
    albums: List<AlbumSummary>,
    playlistListState: LazyListState,
    albumListState: LazyListState,
    selectedCategory: Int,
    onCategoryChange: (Int) -> Unit,
    onPlaylistClick: (PlaylistSummary) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    offlineMode: Boolean
) {
    var playlistSearchQuery by rememberSaveable { mutableStateOf("") }
    var albumSearchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val isAlbumCategory = selectedCategory == NETEASE_CATEGORY_ALBUM
    val listState = if (isAlbumCategory) albumListState else playlistListState
    val searchQuery = if (isAlbumCategory) albumSearchQuery else playlistSearchQuery
    val filteredPlaylists = remember(playlists, playlistSearchQuery) {
        filterNeteasePlaylists(playlists, playlistSearchQuery)
    }
    val filteredAlbums = remember(albums, albumSearchQuery) {
        filterNeteaseAlbums(albums, albumSearchQuery)
    }

    fun updateSearchQuery(category: Int, value: String) {
        if (category == NETEASE_CATEGORY_ALBUM) {
            albumSearchQuery = value
        } else {
            playlistSearchQuery = value
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp + miniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        item(key = "netease_library_header") {
            NeteaseLibraryHeaderContent(
                selectedCategory = selectedCategory,
                onCategoryChange = { category ->
                    if (selectedCategory != category) {
                        onCategoryChange(category)
                    }
                }
            )
            LibraryInlineSearchField(
                query = searchQuery,
                onQueryChange = { value ->
                    updateSearchQuery(selectedCategory, value)
                },
                placeholderResId = R.string.library_netease_search_hint
            )
        }

        if (isAlbumCategory) {
            if (albums.isNotEmpty() && filteredAlbums.isEmpty()) {
                item(key = "netease_album_search_empty") {
                    NeteaseLibraryEmptyCard(
                        cardShape = cardShape,
                        title = stringResource(R.string.library_netease_search_empty),
                        hint = stringResource(R.string.library_netease_search_empty_hint),
                        iconIsAlbum = true
                    )
                }
            } else if (filteredAlbums.isEmpty()) {
                item(key = "netease_album_empty") {
                    NeteaseLibraryEmptyCard(
                        cardShape = cardShape,
                        title = stringResource(R.string.library_netease_album_empty),
                        hint = stringResource(R.string.library_netease_search_hint),
                        iconIsAlbum = true
                    )
                }
            }
            items(
                items = filteredAlbums,
                key = { album -> "album:${album.id}" }
            ) { album ->
                NeteaseAlbumRow(
                    album = album,
                    cardShape = cardShape,
                    onClick = { onAlbumClick(album) },
                    offlineMode = offlineMode
                )
            }
        } else {
            if (playlists.isNotEmpty() && filteredPlaylists.isEmpty()) {
                item(key = "netease_playlist_search_empty") {
                    NeteaseLibraryEmptyCard(
                        cardShape = cardShape,
                        title = stringResource(R.string.library_netease_search_empty),
                        hint = stringResource(R.string.library_netease_search_empty_hint),
                        iconIsAlbum = false
                    )
                }
            } else if (filteredPlaylists.isEmpty()) {
                item(key = "netease_playlist_empty") {
                    NeteaseLibraryEmptyCard(
                        cardShape = cardShape,
                        title = stringResource(R.string.library_netease_playlist_empty),
                        hint = stringResource(R.string.library_netease_search_hint),
                        iconIsAlbum = false
                    )
                }
            }
            items(
                items = filteredPlaylists,
                key = { playlist -> "playlist:${playlist.id}" }
            ) { playlist ->
                NeteasePlaylistRow(
                    playlist = playlist,
                    cardShape = cardShape,
                    context = context,
                    onClick = { onPlaylistClick(playlist) },
                    offlineMode = offlineMode
                )
            }
        }
    }
}

@Composable
private fun NeteaseLibraryHeaderContent(
    selectedCategory: Int,
    onCategoryChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        NeteaseCategoryTabs(
            selectedCategory = selectedCategory,
            onCategoryChange = onCategoryChange
        )
    }
}

@Composable
private fun NeteaseCategoryTabs(
    selectedCategory: Int,
    onCategoryChange: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        AdvancedGlassSurface(
            role = AdvancedGlassRole.ScreenTopTab,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            fallbackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            tintColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedCategory,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedCategory == NETEASE_CATEGORY_PLAYLIST,
                    onClick = { onCategoryChange(NETEASE_CATEGORY_PLAYLIST) },
                    text = { Text(stringResource(R.string.library_netease_tab_playlists)) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null
                        )
                    }
                )
                Tab(
                    selected = selectedCategory == NETEASE_CATEGORY_ALBUM,
                    onClick = { onCategoryChange(NETEASE_CATEGORY_ALBUM) },
                    text = { Text(stringResource(R.string.library_netease_tab_albums)) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Album,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NeteaseLibraryEmptyCard(
    cardShape: RoundedCornerShape,
    title: String,
    hint: String,
    iconIsAlbum: Boolean
) {
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(cardShape)
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Text(
                    text = hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    imageVector = if (iconIsAlbum) {
                        Icons.Filled.Album
                    } else {
                        Icons.AutoMirrored.Filled.QueueMusic
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
            }
        )
    }
}

@Composable
private fun NeteasePlaylistRow(
    playlist: PlaylistSummary,
    cardShape: RoundedCornerShape,
    context: Context,
    onClick: () -> Unit,
    offlineMode: Boolean
) {
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text(playlist.name) },
            supportingContent = {
                Text(
                    text = stringResource(
                        R.string.home_play_count_format,
                        formatPlayCount(context, playlist.playCount),
                        playlist.trackCount
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                AsyncImage(
                    model = offlineCachedImageRequest(
                        context = context,
                        data = playlist.picUrl,
                        sizePx = 192,
                        allowHardware = false,
                        offlineMode = offlineMode
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        )
    }
}

@Composable
private fun NeteaseAlbumRow(
    album: AlbumSummary,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text(album.name) },
            supportingContent = {
                Text(
                    text = pluralStringResource(
                        R.plurals.library_song_count,
                        album.size,
                        album.size
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                AsyncImage(
                    model = offlineCachedImageRequest(
                        context = context,
                        data = album.picUrl,
                        sizePx = 192,
                        allowHardware = false,
                        offlineMode = offlineMode
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        )
    }
}

@Composable
private fun NeteasePlaylistList(
    playlists: List<PlaylistSummary>,
    listState: LazyListState,
    selectedCategory: Int,
    onCategoryChange: (Int) -> Unit,
    onClick: (PlaylistSummary) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredPlaylists = remember(playlists, searchQuery) {
        filterNeteasePlaylists(playlists, searchQuery)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        item(key = "netease_library_header") {
            NeteaseLibraryHeaderContent(
                selectedCategory = selectedCategory,
                onCategoryChange = onCategoryChange
            )
        }
        item(key = "netease_playlist_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.library_netease_search_hint)) },
                singleLine = true,
                shape = LibrarySearchFieldShape
            )
        }
        if (playlists.isNotEmpty() && filteredPlaylists.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.library_netease_search_empty))
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.library_netease_search_empty_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        } else if (filteredPlaylists.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.library_netease_playlist_empty))
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.library_netease_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        }
        items(
            items = filteredPlaylists,
            key = { it.id }
        ) { pl ->
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .clickable { onClick(pl) }
            ) {
                ListItem(
                    headlineContent = { Text(pl.name) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.home_play_count_format,
                                formatPlayCount(context, pl.playCount),
                                pl.trackCount
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        AsyncImage(
                            model = offlineCachedImageRequest(
                                context = context,
                                data = pl.picUrl,
                                sizePx = 192,
                                allowHardware = false,
                                offlineMode = offlineMode
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NeteaseAlbumList(
    playlists: List<AlbumSummary>,
    listState: LazyListState,
    selectedCategory: Int,
    onCategoryChange: (Int) -> Unit,
    onClick: (AlbumSummary) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredAlbums = remember(playlists, searchQuery) {
        filterNeteaseAlbums(playlists, searchQuery)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        item(key = "netease_library_header") {
            NeteaseLibraryHeaderContent(
                selectedCategory = selectedCategory,
                onCategoryChange = onCategoryChange
            )
        }
        item(key = "netease_album_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.library_netease_search_hint)) },
                singleLine = true,
                shape = LibrarySearchFieldShape
            )
        }
        if (playlists.isNotEmpty() && filteredAlbums.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.library_netease_search_empty))
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.library_netease_search_empty_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        } else if (filteredAlbums.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.library_netease_album_empty))
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.library_netease_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        }
        items(
            items = filteredAlbums,
            key = { it.id }
        ) { pl ->
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .clickable { onClick(pl) }
            ) {
                ListItem(
                    headlineContent = { Text(pl.name) },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.library_song_count, pl.size, pl.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        AsyncImage(
                            model = offlineCachedImageRequest(
                                context = context,
                                data = pl.picUrl,
                                sizePx = 192,
                                allowHardware = false,
                                offlineMode = offlineMode
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FavoritePlaylistList(
    listState: LazyListState,
    onHotPlaylistClick: (PlaybackStatsPeriod) -> Unit,
    onNeteasePlaylistClick: (PlaylistSummary) -> Unit,
    onNeteaseAlbumClick: (AlbumSummary) -> Unit,
    onNeteaseArtistClick: (NeteaseArtistSummary) -> Unit,
    onBiliPlaylistClick: (BiliPlaylist) -> Unit,
    onYouTubeMusicPlaylistClick: (YouTubeMusicPlaylist) -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    val favoriteRepo = remember(context) { FavoritePlaylistRepository.getInstance(context) }
    val favorites by favoriteRepo.favorites.collectAsStateWithLifecycle()
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val scope = rememberCoroutineScope()
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var selectedFavoriteCategory by rememberSaveable {
        mutableIntStateOf(FAVORITE_CATEGORY_PLAYLIST)
    }
    var favoriteSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectedConfirm by rememberSaveable { mutableStateOf(false) }
    val reorderableFavorites = remember { mutableStateListOf<FavoritePlaylist>() }
    val playlistFavorites = remember(favorites) {
        favorites.filterNot { it.source == FAVORITE_SOURCE_NETEASE_ARTIST }
    }
    val artistFavorites = remember(favorites) {
        favorites.filter { it.source == FAVORITE_SOURCE_NETEASE_ARTIST }
    }
    val isHotCategory = selectedFavoriteCategory == FAVORITE_CATEGORY_HOT
    val visibleFavorites = remember(playlistFavorites, artistFavorites, selectedFavoriteCategory) {
        when (selectedFavoriteCategory) {
            FAVORITE_CATEGORY_ARTIST -> artistFavorites
            FAVORITE_CATEGORY_PLAYLIST -> playlistFavorites
            else -> emptyList()
        }
    }
    val hotPlaylists = if (isHotCategory) rememberHotPlaylists() else null

    fun favoriteKey(favorite: FavoritePlaylist): String {
        return "${favorite.source}:${favorite.id}"
    }

    fun exitEditMode() {
        sortMode = false
        selectedKeys = emptySet()
        showDeleteSelectedConfirm = false
    }

    fun toggleSelection(key: String) {
        selectedKeys = if (selectedKeys.contains(key)) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    BackHandler(enabled = sortMode) { exitEditMode() }

    LaunchedEffect(visibleFavorites) {
        reorderableFavorites.clear()
        reorderableFavorites.addAll(visibleFavorites)
        val validKeys = visibleFavorites.map(::favoriteKey).toSet()
        selectedKeys = selectedKeys.intersect(validKeys)
        if (sortMode && visibleFavorites.isEmpty()) {
            exitEditMode()
        }
    }

    LaunchedEffect(sortMode, visibleFavorites) {
        if (sortMode && visibleFavorites.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from: ItemPosition, to: ItemPosition ->
            if (!sortMode) return@rememberReorderableLazyListState
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = reorderableFavorites.indexOfFirst { favoriteKey(it) == fromKey }
            val toIndex = reorderableFavorites.indexOfFirst { favoriteKey(it) == toKey }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                reorderableFavorites.add(toIndex, reorderableFavorites.removeAt(fromIndex))
            }
        },
        canDragOver = { _, over -> sortMode && over.key is String },
        onDragEnd = { _, _ ->
            if (sortMode) {
                scope.launch {
                    favoriteRepo.reorderFavorites(
                        reorderableFavorites.map { "${it.source}:${it.id}" }
                    )
                }
            }
        }
    )

    LazyColumn(
        state = reorderState.listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .reorderable(reorderState)
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        val displayedFavorites = filterFavoritePlaylists(reorderableFavorites, favoriteSearchQuery)
        item(key = "favorite_category_tabs") {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                AdvancedGlassSurface(
                    role = AdvancedGlassRole.ScreenTopTab,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    fallbackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    tintColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedFavoriteCategory,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedFavoriteCategory == FAVORITE_CATEGORY_PLAYLIST,
                            onClick = {
                                if (selectedFavoriteCategory != FAVORITE_CATEGORY_PLAYLIST) {
                                    selectedFavoriteCategory = FAVORITE_CATEGORY_PLAYLIST
                                    exitEditMode()
                                }
                            },
                            text = { Text(stringResource(R.string.library_favorite_tab_playlists)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null
                                )
                            }
                        )
                        Tab(
                            selected = selectedFavoriteCategory == FAVORITE_CATEGORY_ARTIST,
                            onClick = {
                                if (selectedFavoriteCategory != FAVORITE_CATEGORY_ARTIST) {
                                    selectedFavoriteCategory = FAVORITE_CATEGORY_ARTIST
                                    exitEditMode()
                                }
                            },
                            text = { Text(stringResource(R.string.library_favorite_tab_artists)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null
                                )
                            }
                        )
                        Tab(
                            selected = selectedFavoriteCategory == FAVORITE_CATEGORY_HOT,
                            onClick = {
                                if (selectedFavoriteCategory != FAVORITE_CATEGORY_HOT) {
                                    selectedFavoriteCategory = FAVORITE_CATEGORY_HOT
                                    exitEditMode()
                                }
                            },
                            text = { Text(stringResource(R.string.library_favorite_tab_hot)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
        if (!sortMode && !isHotCategory) {
            item(key = "favorite_search") {
                LibraryInlineSearchField(
                    query = favoriteSearchQuery,
                    onQueryChange = { favoriteSearchQuery = it },
                    placeholderResId = R.string.library_favorite_search_hint
                )
            }
        }
        if (sortMode && !isHotCategory) {
            item(key = "favorite_sort_mode_header") {
                val allSelected =
                    selectedKeys.size == displayedFavorites.size && displayedFavorites.isNotEmpty()
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.common_selected_count,
                                    selectedKeys.size,
                                    selectedKeys.size
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            HapticIconButton(onClick = { exitEditMode() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_exit_multi_select)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HapticTextButton(
                                    onClick = {
                                        selectedKeys = if (allSelected) {
                                            emptySet()
                                        } else {
                                            displayedFavorites.map(::favoriteKey).toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        if (allSelected) {
                                            stringResource(R.string.action_deselect_all)
                                        } else {
                                            stringResource(R.string.action_select_all)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticTextButton(
                                    enabled = selectedKeys.isNotEmpty(),
                                    onClick = { showDeleteSelectedConfirm = true }
                                ) {
                                    Text(stringResource(R.string.common_delete_selected))
                                }
                            }
                        }
                    )
                }
            }
        }
        if (isHotCategory) {
            if (hotPlaylists == null) {
                item(key = "favorite_hot_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(
                    items = hotPlaylists,
                    key = { playlist -> "hot_playlist_${playlist.period.name}" }
                ) { playlist ->
                    val titleResId = hotPlaylistTitleResId(playlist.period)
                    val subtitle = if (playlist.tracks.isEmpty()) {
                        stringResource(R.string.library_hot_empty_hint)
                    } else {
                        stringResource(
                            R.string.library_hot_playlist_summary,
                            playlist.tracks.size,
                            formatPlayCount(context, playlist.totalPlayCount)
                        )
                    }
                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .animateItem()
                            .clip(cardShape)
                            .clickable { onHotPlaylistClick(playlist.period) }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(titleResId))
                            },
                            supportingContent = {
                                Text(
                                    text = subtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        )
                    }
                }
            }
        } else if (displayedFavorites.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    val isArtistCategory = selectedFavoriteCategory == FAVORITE_CATEGORY_ARTIST
                    val isSearchEmpty = favoriteSearchQuery.isNotBlank() && visibleFavorites.isNotEmpty()
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (isSearchEmpty) {
                                        R.string.library_favorite_search_empty
                                    } else if (isArtistCategory) {
                                        R.string.library_no_favorite_artist
                                    } else {
                                        R.string.playlist_no_favorite
                                    }
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (isSearchEmpty) {
                                        R.string.library_favorite_search_empty_hint
                                    } else if (isArtistCategory) {
                                        R.string.library_favorite_artist_hint
                                    } else {
                                        R.string.playlist_favorite_hint
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Icon(
                                imageVector = if (isArtistCategory) {
                                    Icons.Filled.AccountCircle
                                } else {
                                    Icons.AutoMirrored.Filled.QueueMusic
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        } else {
            items(
                items = displayedFavorites,
                key = { favoriteKey(it) }
            ) { favorite ->
                val itemKey = favoriteKey(favorite)
                val isSelected = sortMode && selectedKeys.contains(itemKey)
                ReorderableItem(state = reorderState, key = itemKey) {
                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                            } else if (sortMode) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .animateItem()
                            .clip(cardShape)
                            .combinedClickable(
                                onClick = {
                                    if (sortMode) {
                                        toggleSelection(itemKey)
                                        return@combinedClickable
                                    }
                                    when (favorite.source) {
                                        "netease" -> {
                                            onNeteasePlaylistClick(
                                                PlaylistSummary(
                                                    id = favorite.id,
                                                    name = favorite.name,
                                                    picUrl = favorite.coverUrl ?: "",
                                                    playCount = 0,
                                                    trackCount = favorite.trackCount
                                                )
                                            )
                                        }
                                        "neteaseAlbum" -> {
                                            onNeteaseAlbumClick(
                                                AlbumSummary(
                                                    id = favorite.id,
                                                    name = favorite.name,
                                                    picUrl = favorite.coverUrl.orEmpty(),
                                                    size = favorite.trackCount
                                                )
                                            )
                                        }
                                        FAVORITE_SOURCE_NETEASE_ARTIST -> {
                                            onNeteaseArtistClick(
                                                NeteaseArtistSummary(
                                                    id = favorite.id,
                                                    name = favorite.name
                                                )
                                            )
                                        }
                                        "youtubeMusic" -> {
                                            val resolvedBrowseId = favorite.browseId
                                                ?.takeIf { it.isNotBlank() }
                                                ?: favorite.playlistId
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?.let { "VL$it" }
                                            val resolvedPlaylistId = favorite.playlistId
                                                ?.takeIf { it.isNotBlank() }
                                                ?: resolvedBrowseId?.removePrefix("VL")
                                            if (
                                                !resolvedBrowseId.isNullOrBlank() &&
                                                !resolvedPlaylistId.isNullOrBlank()
                                            ) {
                                                onYouTubeMusicPlaylistClick(
                                                    YouTubeMusicPlaylist(
                                                        browseId = resolvedBrowseId,
                                                        playlistId = resolvedPlaylistId,
                                                        title = favorite.name,
                                                        subtitle = favorite.subtitle.orEmpty(),
                                                        coverUrl = favorite.coverUrl.orEmpty(),
                                                        trackCount = favorite.trackCount
                                                    )
                                                )
                                            }
                                        }
                                        "bili" -> {
                                            onBiliPlaylistClick(favorite.toBiliPlaylist())
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!sortMode) sortMode = true
                                    toggleSelection(itemKey)
                                }
                            )
                    ) {
                        ListItem(
                            headlineContent = { Text(favorite.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.library_favorite_source_format,
                                        favorite.trackCount,
                                        favoriteSourceLabel(favorite.source)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            leadingContent = {
                                if (!favorite.coverUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = offlineCachedImageRequest(
                                            context = context,
                                            data = favorite.coverUrl,
                                            sizePx = 192,
                                            allowHardware = false,
                                            offlineMode = offlineMode
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (favorite.source) {
                                            FAVORITE_SOURCE_NETEASE_ARTIST -> Icons.Filled.AccountCircle
                                            "neteaseAlbum" -> Icons.Filled.Album
                                            else -> Icons.AutoMirrored.Filled.QueueMusic
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                if (sortMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { toggleSelection(itemKey) }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .detectReorder(reorderState)
                                                .padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DragHandle,
                                                contentDescription = stringResource(R.string.common_drag_handle),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.dialog_confirm_delete)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.library_delete_selected_confirm,
                        selectedKeys.size,
                        selectedKeys.size
                    )
                )
            },
            confirmButton = {
                HapticTextButton(
                    onClick = {
                        val targets = reorderableFavorites.filter { favoriteKey(it) in selectedKeys }
                        scope.launch {
                            targets.forEach { favoriteRepo.removeFavorite(it.id, it.source) }
                            exitEditMode()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                HapticTextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun favoriteSourceLabel(source: String): String {
    return when (source) {
        "youtubeMusic" -> "YouTube"
        "neteaseAlbum" -> "Netease Album"
        "netease" -> "Netease"
        "bili" -> "Bilibili"
        FAVORITE_SOURCE_NETEASE_ARTIST -> stringResource(R.string.library_favorite_source_artist)
        else -> source
    }
}

@Composable
private fun QqMusicPlaylistList(
    listState: LazyListState
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        // TODO: Implement QQ Music playlist list when type is available
        item {
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(cardShape)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_qqmusic_coming)) },
                    supportingContent = {
                        Text(stringResource(R.string.library_coming_soon), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                )
            }
        }
    }
}
