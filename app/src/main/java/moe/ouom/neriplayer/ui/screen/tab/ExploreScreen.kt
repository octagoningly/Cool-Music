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
 * File: moe.ouom.neriplayer.ui.screen.tab/ExploreScreen
 * Created: 2025/8/8
 */

import android.app.Application
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import moe.ouom.neriplayer.ui.component.overlay.DensityScaledModalBottomSheet as ModalBottomSheet
import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import moe.ouom.neriplayer.ui.util.shouldAllowCollapsingTopAppBar
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.ui.component.common.MainTabChrome
import moe.ouom.neriplayer.ui.component.common.NeriTabLargeTitleTopBar
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicCreatorSummary
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.core.download.GlobalDownloadManager
import moe.ouom.neriplayer.data.platform.youtube.YouTubeFeatureGate
import moe.ouom.neriplayer.data.search.ExploreSearchHistoryRepository
import moe.ouom.neriplayer.data.search.exploreSearchHistoryRecordKeyword
import moe.ouom.neriplayer.data.search.exploreSearchHistoryForDisplay
import moe.ouom.neriplayer.data.search.resolveExploreSearchKeyword
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassRole
import moe.ouom.neriplayer.ui.effect.glass.AdvancedGlassSurface
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.local.playlist.system.FavoritesPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.launchLocalPlaylistMutation
import moe.ouom.neriplayer.data.local.media.displayAlbum
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.model.sameIdentityAs
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.data.model.NeteaseArtistSummary
import moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylistRepository
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.component.playlist.AddSongToPlaylistSheet
import moe.ouom.neriplayer.ui.component.playlist.GlassDropdownMenu
import moe.ouom.neriplayer.ui.component.overlay.GlassMenuShape
import moe.ouom.neriplayer.ui.component.playlist.PlaylistExportSheet
import moe.ouom.neriplayer.ui.component.playlist.showPlaylistBatchExportAddedResult
import moe.ouom.neriplayer.ui.component.playlist.showPlaylistBatchExportCreatedResult
import moe.ouom.neriplayer.ui.component.sheet.bottomSheetScrollGuard
import moe.ouom.neriplayer.ui.feedback.AppFeedback
import moe.ouom.neriplayer.ui.feedback.NeriSnackbarHost
import moe.ouom.neriplayer.ui.feedback.showNeriSnackbar
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.core.player.resolver.lxmusic.LxOnlineCollection
import moe.ouom.neriplayer.core.player.resolver.lxmusic.LxOnlineCollectionType
import moe.ouom.neriplayer.ui.viewmodel.tab.DefaultExploreSearchType
import moe.ouom.neriplayer.ui.viewmodel.tab.ExploreSearchResult
import moe.ouom.neriplayer.ui.viewmodel.tab.ExploreUiState
import moe.ouom.neriplayer.ui.viewmodel.tab.ExploreViewModel
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteaseExploreSearchType
import moe.ouom.neriplayer.ui.viewmodel.tab.NeteaseSearchArtistResult
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.SearchSource
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeExploreSearchType
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.shouldLoadExploreSearchMore
import moe.ouom.neriplayer.ui.util.currentWindowWidthDp
import moe.ouom.neriplayer.ui.util.rememberSongDisplayCoverUrl
import moe.ouom.neriplayer.ui.util.ClipboardCopyResult
import moe.ouom.neriplayer.ui.util.copyPlainTextSafely
import moe.ouom.neriplayer.ui.haptic.HapticIconButton
import moe.ouom.neriplayer.ui.haptic.HapticTextButton
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.util.media.fastScrollableImageRequest
import moe.ouom.neriplayer.util.format.formatDuration
import moe.ouom.neriplayer.util.format.formatPlayCount
import moe.ouom.neriplayer.ui.haptic.performHapticFeedback

private const val SEARCH_INPUT_DEBOUNCE_MS = 300L
private val ExplorePrimaryTabShape = RoundedCornerShape(20.dp)
private val ExplorePillShape = RoundedCornerShape(999.dp)
private val ExploreSearchFieldShape = RoundedCornerShape(16.dp)
private val ExploreTypeChipShape = RoundedCornerShape(18.dp)
private val ExploreTypeChipSize = 44.dp

internal fun exploreSearchSourceDisplayOrder(
    isInternational: Boolean,
    youtubeEnabled: Boolean
): List<SearchSource> {
    return if (!youtubeEnabled) {
        listOf(SearchSource.DEFAULT, SearchSource.NETEASE, SearchSource.BILIBILI, SearchSource.LINK_RECOGNITION)
    } else if (isInternational) {
        listOf(
            SearchSource.YOUTUBE_MUSIC,
            SearchSource.DEFAULT,
            SearchSource.NETEASE,
            SearchSource.BILIBILI,
            SearchSource.LINK_RECOGNITION
        )
    } else {
        listOf(
            SearchSource.DEFAULT,
            SearchSource.NETEASE,
            SearchSource.BILIBILI,
            SearchSource.YOUTUBE_MUSIC,
            SearchSource.LINK_RECOGNITION
        )
    }
}

internal fun shouldClearExploreSearchQuery(
    previous: SearchSource,
    current: SearchSource
): Boolean {
    return previous != current && (
        previous == SearchSource.LINK_RECOGNITION ||
            current == SearchSource.LINK_RECOGNITION
        )
}

internal fun exploreSearchScrollContextKey(
    keyword: String,
    source: SearchSource,
    neteaseSearchType: NeteaseExploreSearchType,
    defaultSearchType: DefaultExploreSearchType = DefaultExploreSearchType.SONG,
    youtubeSearchType: YouTubeExploreSearchType = YouTubeExploreSearchType.SONG
): String? {
    val normalizedKeyword = keyword.trim()
    if (normalizedKeyword.isBlank()) return null
    val sourceType = when (source) {
        SearchSource.DEFAULT -> defaultSearchType.name
        SearchSource.NETEASE -> neteaseSearchType.name
        SearchSource.YOUTUBE_MUSIC -> youtubeSearchType.name
        else -> "-"
    }
    return "${source.name}|$sourceType|$normalizedKeyword"
}

internal fun shouldResetExploreSearchScroll(
    previousContextKey: String?,
    currentContextKey: String?
): Boolean {
    return currentContextKey != null && previousContextKey != currentContextKey
}

internal fun shouldRenderExploreSearchResults(
    page: Int,
    currentPage: Int
): Boolean = page == currentPage

internal fun exploreSearchResultsBottomPadding(miniPlayerHeight: Dp): Dp =
    16.dp + miniPlayerHeight

internal fun shouldShowBiliPartsPicker(song: SongItem): Boolean {
    return song.album == PlayerManager.BILI_SOURCE_TAG ||
        song.album.startsWith("${PlayerManager.BILI_SOURCE_TAG}|")
}

@Composable
private fun searchSourceLabel(source: SearchSource): String {
    return when (source) {
        SearchSource.DEFAULT -> stringResource(R.string.explore_tab_default)
        SearchSource.YOUTUBE_MUSIC -> stringResource(R.string.explore_tab_youtube)
        SearchSource.NETEASE -> stringResource(R.string.platform_netease_short)
        SearchSource.BILIBILI -> stringResource(R.string.platform_bilibili)
        SearchSource.LINK_RECOGNITION -> stringResource(R.string.explore_tab_links)
    }
}

@Composable
private fun defaultSearchTypeLabel(type: DefaultExploreSearchType): String {
    return when (type) {
        DefaultExploreSearchType.SONG -> stringResource(R.string.explore_search_type_song)
        DefaultExploreSearchType.PLAYLIST -> stringResource(R.string.explore_search_type_playlist)
        DefaultExploreSearchType.ARTIST -> stringResource(R.string.explore_search_type_artist)
    }
}

private fun defaultSearchTypeIcon(type: DefaultExploreSearchType): ImageVector {
    return when (type) {
        DefaultExploreSearchType.SONG -> Icons.Outlined.MusicNote
        DefaultExploreSearchType.ARTIST -> Icons.Filled.AccountCircle
        DefaultExploreSearchType.PLAYLIST -> Icons.AutoMirrored.Outlined.QueueMusic
    }
}

@Composable
private fun onlineCollectionSourceLabel(sourceId: String): String = stringResource(
    when (sourceId) {
        "tx" -> R.string.settings_lx_online_search_engines_tx
        "kg" -> R.string.settings_lx_online_search_engines_kg
        "kw" -> R.string.settings_lx_online_search_engines_kw
        else -> R.string.settings_lx_online_search_engines_wy
    }
)

@Composable
private fun neteaseSearchTypeLabel(type: NeteaseExploreSearchType): String {
    return when (type) {
        NeteaseExploreSearchType.SONG -> stringResource(R.string.explore_search_type_song)
        NeteaseExploreSearchType.PLAYLIST -> stringResource(R.string.explore_search_type_playlist)
        NeteaseExploreSearchType.ARTIST -> stringResource(R.string.explore_search_type_artist)
    }
}

private fun neteaseSearchTypeIcon(type: NeteaseExploreSearchType): ImageVector {
    return when (type) {
        NeteaseExploreSearchType.SONG -> Icons.Outlined.MusicNote
        NeteaseExploreSearchType.PLAYLIST -> Icons.AutoMirrored.Outlined.QueueMusic
        NeteaseExploreSearchType.ARTIST -> Icons.Filled.AccountCircle
    }
}

@Composable
private fun youtubeSearchTypeLabel(type: YouTubeExploreSearchType): String {
    return when (type) {
        YouTubeExploreSearchType.SONG -> stringResource(R.string.explore_search_type_song)
        YouTubeExploreSearchType.VIDEO -> stringResource(R.string.explore_search_type_video)
        YouTubeExploreSearchType.CREATOR -> stringResource(R.string.explore_search_type_creator)
    }
}

private fun youtubeSearchTypeIcon(type: YouTubeExploreSearchType): ImageVector {
    return when (type) {
        YouTubeExploreSearchType.SONG -> Icons.Outlined.MusicNote
        YouTubeExploreSearchType.VIDEO -> Icons.Filled.PlayCircle
        YouTubeExploreSearchType.CREATOR -> Icons.Filled.AccountCircle
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
@Suppress("AssignedValueIsNeverRead")
fun ExploreScreen(
    gridState: LazyGridState,
    topAppBarState: TopAppBarState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchListState: LazyListState,
    searchScrollContextKey: String?,
    onSearchScrollContextKeyChange: (String?) -> Unit,
    offlineMode: Boolean = false,
    isTabActive: Boolean = true,
    onPlay: (PlaylistSummary) -> Unit,
    onBiliPlaylistClick: (BiliPlaylist) -> Unit = {},
    onYouTubeMusicPlaylistClick: (YouTubeMusicPlaylist) -> Unit = {},
    onYouTubeCreatorClick: (YouTubeMusicCreatorSummary) -> Unit = {},
    onNeteaseArtistClick: (NeteaseArtistSummary) -> Unit = {},
    onOnlineCollectionClick: (LxOnlineCollection) -> Unit = {},
    onSongClick: (List<SongItem>, Int) -> Unit = { _, _ -> },
    onSongPlayPreservingQueue: (SongItem) -> Unit = {},
    onSongPlayNext: (SongItem) -> Unit = {},
    onSongAddToQueueEnd: (SongItem) -> Unit = {},
    onPlayParts: (BiliClient.VideoBasicInfo, Int, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val composeResources = LocalResources.current
    if (offlineMode) {
        ExploreOfflineContent(topAppBarState)
        return
    }

    val vm: ExploreViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExploreViewModel(context.applicationContext as Application) }
        }
    )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val searchHistoryRepository = remember(context) {
        ExploreSearchHistoryRepository(context)
    }
    val searchHistory by searchHistoryRepository.historyFlow.collectAsStateWithLifecycle(
        initialValue = emptyList()
    )
    val searchHistoryEnabled by AppContainer.settingsRepo.exploreSearchHistoryEnabledFlow
        .collectAsStateWithLifecycle(initialValue = true)
    val availableSearchHistory = remember(searchHistoryEnabled, searchHistory) {
        exploreSearchHistoryForDisplay(searchHistoryEnabled, searchHistory)
    }
    val effectiveSearchKeyword = remember(searchQuery, availableSearchHistory, ui.selectedSearchSource) {
        if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION) {
            searchQuery.trim()
        } else {
            resolveExploreSearchKeyword(searchQuery, availableSearchHistory)
        }
    }
    val visibleSearchHistory = remember(availableSearchHistory, ui.selectedSearchSource) {
        if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION) {
            emptyList()
        } else {
            filteredExploreSearchHistory(availableSearchHistory)
        }
    }
    val backgroundImageUri by AppContainer.settingsRepo.backgroundImageUriFlow.collectAsStateWithLifecycle(
        initialValue = null
    )

    val repo = remember(context) { LocalPlaylistRepository.getInstance(context) }
    val allLocalPlaylists by repo.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    val localPlaylistsReady by repo.initializationReadyFlow.collectAsStateWithLifecycle(
        initialValue = false
    )
    val favoriteSongKeys = remember(allLocalPlaylists, context) {
        FavoritesPlaylist.firstOrNull(allLocalPlaylists, context)
            ?.songs
            .orEmpty()
            .mapTo(mutableSetOf()) { it.stableKey() }
    }
    val favoriteRepo = remember(context) { FavoritePlaylistRepository.getInstance(context) }
    val favorites by favoriteRepo.favorites.collectAsStateWithLifecycle()
    val favoriteKeys = remember(favorites) {
        favorites.mapTo(mutableSetOf()) { "${it.source}:${it.id}" }
    }

    var showPartsSheet by remember { mutableStateOf(false) }
    var partsInfo by remember { mutableStateOf<BiliClient.VideoBasicInfo?>(null) }
    var clickedSongCoverUrl by remember { mutableStateOf("") }
    val partsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var partsSelectionMode by remember { mutableStateOf(false) }
    var selectedParts by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var showExportSheet by remember { mutableStateOf(false) }

    val isInternational by AppContainer.settingsRepo.internationalizationEnabledFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val youtubeEnabled by AppContainer.settingsRepo.youtubeEnabledFlow
        .collectAsStateWithLifecycle(initialValue = YouTubeFeatureGate.isEnabled())
    val orderedSearchSources = remember(isInternational, youtubeEnabled) {
        exploreSearchSourceDisplayOrder(isInternational, youtubeEnabled)
    }
    val initialSearchPage = remember(orderedSearchSources, ui.selectedSearchSource) {
        orderedSearchSources.indexOf(ui.selectedSearchSource).takeIf { it >= 0 } ?: 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialSearchPage,
        pageCount = { orderedSearchSources.size }
    )
    // 搜索结果下标索引：列表项需要知道自己在 searchResults 中的位置，
    // 逐项 indexOfFirst 会让列表退化成 O(n²)，滚动时每帧重算
    val searchResultIndexByKey = remember(ui.searchResults) {
        ui.searchResults.withIndex().associate { (index, song) -> song.stableKey() to index }
    }
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val snackbarHostState = remember { SnackbarHostState() }
    val favoriteAddedText = stringResource(R.string.favorite_added)
    val favoriteRemovedText = stringResource(R.string.favorite_removed)
    val windowWidthDp = currentWindowWidthDp()
    val isTabletLayout = windowWidthDp >= 720.dp
    val searchPanelHorizontalPadding = if (isTabletLayout) 28.dp else 16.dp
    val searchResultHorizontalPadding = if (isTabletLayout) 88.dp else 0.dp
    val youtubeGridState = rememberLazyGridState()
    val tagChipSelectedAlpha = if (backgroundImageUri == null) 1f else 0.86f
    val tagChipUnselectedAlpha = if (backgroundImageUri == null) 1f else 0.74f
    val tagChipBorderAlpha = if (backgroundImageUri == null) 1f else 0.58f
    var previousSearchSource by remember { mutableStateOf(ui.selectedSearchSource) }
    val isExploreContentScrolled by remember(
        searchQuery,
        ui.selectedSearchSource,
        searchListState,
        gridState,
        youtubeGridState,
        topAppBarState
    ) {
        derivedStateOf {
            when {
                topAppBarState.collapsedFraction > 0f -> true
                searchQuery.isNotBlank() -> searchListState.canScrollBackward
                ui.selectedSearchSource == SearchSource.NETEASE -> gridState.canScrollBackward
                ui.selectedSearchSource == SearchSource.YOUTUBE_MUSIC -> {
                    youtubeGridState.canScrollBackward
                }
                else -> false
            }
        }
    }
    val searchTypeBarSource = exploreSearchTypeBarSource(
        selectedSearchSource = ui.selectedSearchSource,
        contentScrolled = isExploreContentScrolled
    )

    val shouldLoadMoreSearch by remember(
        searchListState,
        ui.searchItems.size,
        ui.searchHasMore,
        ui.searching,
        ui.searchLoadingMore,
        ui.searchLoadMoreError
    ) {
        derivedStateOf {
            shouldLoadExploreSearchMore(
                resultCount = ui.searchItems.size,
                lastVisibleItemIndex = searchListState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index,
                hasMore = ui.searchHasMore,
                searching = ui.searching,
                loadingMore = ui.searchLoadingMore,
                loadMoreFailed = ui.searchLoadMoreError != null
            )
        }
    }

    fun exitPartsSelection() {
        partsSelectionMode = false
        selectedParts = emptySet()
    }

    // 每次成为探索 Tab 时回到首页，避免残留「新发现」二级页
    LaunchedEffect(isTabActive) {
        if (!isTabActive) return@LaunchedEffect
        vm.resetToExploreHome()
        if (
            ui.featuredPlaylists.isEmpty() &&
            ui.playlists.isEmpty() &&
            !ui.neteaseDiscoveryOpen
        ) {
            vm.loadFeaturedPlaylist()
        }
    }

    if (
        ui.neteaseDiscoveryOpen &&
        ui.selectedSearchSource == SearchSource.NETEASE &&
        searchQuery.isBlank()
    ) {
        BackHandler { vm.closeNeteaseDiscovery() }
    }

    LaunchedEffect(ui.selectedSearchSource, orderedSearchSources) {
        val targetPage = orderedSearchSources.indexOf(ui.selectedSearchSource)
            .takeIf { it >= 0 }
            ?: return@LaunchedEffect
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, orderedSearchSources, ui.selectedSearchSource) {
        val currentSource = orderedSearchSources.getOrNull(pagerState.currentPage)
            ?: return@LaunchedEffect
        if (ui.selectedSearchSource != currentSource) {
            vm.setSearchSource(currentSource)
        }
        if (currentSource == SearchSource.YOUTUBE_MUSIC && ui.ytMusicPlaylists.isEmpty()) {
            vm.loadYtMusicPlaylists()
        }
    }

    // 国际化模式默认跳到 YouTube Music 标签
    LaunchedEffect(isInternational, youtubeEnabled) {
        if (isInternational && youtubeEnabled) {
            if (ui.selectedSearchSource != SearchSource.YOUTUBE_MUSIC) {
                vm.setSearchSource(SearchSource.YOUTUBE_MUSIC)
            }
            if (ui.ytMusicPlaylists.isEmpty()) {
                vm.loadYtMusicPlaylists()
            }
        }
    }

    // Tag keys for API calls
    val tagKeys = listOf(
        "tag_all", "tag_pop", "tag_soundtrack", "tag_chinese", "tag_nostalgia", "tag_rock", "tag_acg", "tag_western", "tag_fresh", "tag_night", "tag_children", "tag_folk", "tag_japanese", "tag_romantic",
        "tag_study", "tag_korean", "tag_work", "tag_electronic", "tag_cantonese", "tag_dance", "tag_sad", "tag_game", "tag_afternoon_tea", "tag_healing", "tag_rap", "tag_light_music"
    )

    // Translated tag labels for display
    val tagLabels = listOf(
        stringResource(R.string.tag_all), stringResource(R.string.tag_pop), stringResource(R.string.tag_soundtrack), stringResource(R.string.tag_chinese), stringResource(R.string.tag_nostalgia), stringResource(R.string.tag_rock), stringResource(R.string.tag_acg), stringResource(R.string.tag_western), stringResource(R.string.tag_fresh), stringResource(R.string.tag_night), stringResource(R.string.tag_children), stringResource(R.string.tag_folk), stringResource(R.string.tag_japanese), stringResource(R.string.tag_romantic),
        stringResource(R.string.tag_study), stringResource(R.string.tag_korean), stringResource(R.string.tag_work), stringResource(R.string.tag_electronic), stringResource(R.string.tag_cantonese), stringResource(R.string.tag_dance), stringResource(R.string.tag_sad), stringResource(R.string.tag_game), stringResource(R.string.tag_afternoon_tea), stringResource(R.string.tag_healing), stringResource(R.string.tag_rap), stringResource(R.string.tag_light_music)
    )

    // Initialize with default tag
    LaunchedEffect(Unit) {
        // 首页精选走 featuredPlaylists；完整网格仅在「新发现」二级页需要
        if (
            ui.neteaseDiscoveryOpen &&
            ui.selectedTag == "tag_all" &&
            ui.playlists.isEmpty()
        ) {
            vm.loadHighQuality("tag_all")
        }
    }

    var lastRecordedSearchKeyword by remember { mutableStateOf<String?>(null) }
    var pendingSearchHistoryRecord by remember { mutableStateOf<String?>(null) }

    fun queueExploreSearchRecord(query: String) {
        if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION) {
            return
        }
        val keyword = exploreSearchHistoryRecordKeyword(
            query = query,
            enabled = searchHistoryEnabled,
            history = searchHistory
        ) ?: return
        if (keyword.equals(lastRecordedSearchKeyword, ignoreCase = true)) {
            return
        }
        lastRecordedSearchKeyword = keyword
        pendingSearchHistoryRecord = keyword
    }

    LaunchedEffect(pendingSearchHistoryRecord) {
        val keyword = pendingSearchHistoryRecord ?: return@LaunchedEffect
        searchHistoryRepository.record(keyword)
        if (pendingSearchHistoryRecord == keyword) {
            pendingSearchHistoryRecord = null
        }
    }

    LaunchedEffect(
        searchQuery,
        effectiveSearchKeyword,
        searchHistoryEnabled,
        ui.selectedSearchSource,
        ui.selectedDefaultSearchType,
        ui.selectedNeteaseSearchType,
        ui.selectedYouTubeMusicSearchType
    ) {
        if (searchQuery.isBlank()) {
            lastRecordedSearchKeyword = null
            pendingSearchHistoryRecord = null
            vm.search("")
            return@LaunchedEffect
        }
        delay(SEARCH_INPUT_DEBOUNCE_MS)
        val displayQuery = searchQuery.trim()
        if (
            ui.searchKeyword != effectiveSearchKeyword ||
            ui.searchDisplayQuery != displayQuery
        ) {
            vm.search(effectiveSearchKeyword, displayQuery = displayQuery)
        }
    }

    LaunchedEffect(ui.selectedSearchSource) {
        if (shouldClearExploreSearchQuery(previousSearchSource, ui.selectedSearchSource)) {
            onSearchQueryChange("")
        }
        previousSearchSource = ui.selectedSearchSource
    }

    val currentSearchScrollContextKey = remember(
        effectiveSearchKeyword,
        ui.selectedSearchSource,
        ui.selectedDefaultSearchType,
        ui.selectedNeteaseSearchType,
        ui.selectedYouTubeMusicSearchType
    ) {
        exploreSearchScrollContextKey(
            keyword = effectiveSearchKeyword,
            source = ui.selectedSearchSource,
            defaultSearchType = ui.selectedDefaultSearchType,
            neteaseSearchType = ui.selectedNeteaseSearchType,
            youtubeSearchType = ui.selectedYouTubeMusicSearchType
        )
    }

    LaunchedEffect(
        currentSearchScrollContextKey,
        searchScrollContextKey
    ) {
        if (
            shouldResetExploreSearchScroll(
                previousContextKey = searchScrollContextKey,
                currentContextKey = currentSearchScrollContextKey
            )
        ) {
            searchListState.scrollToItem(0)
        }
        if (searchScrollContextKey != currentSearchScrollContextKey) {
            onSearchScrollContextKeyChange(currentSearchScrollContextKey)
        }
    }

    LaunchedEffect(
        shouldLoadMoreSearch,
        ui.searchItems.size,
        ui.selectedSearchSource,
        ui.selectedDefaultSearchType,
        ui.selectedNeteaseSearchType,
        ui.selectedYouTubeMusicSearchType
    ) {
        if (shouldLoadMoreSearch) {
            vm.loadMoreSearchResults()
        }
    }

    fun submitExploreSearch(query: String = searchQuery) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        val keyword = if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION) {
            normalizedQuery
        } else {
            resolveExploreSearchKeyword(normalizedQuery, availableSearchHistory)
        }
        onSearchQueryChange(normalizedQuery)
        focusManager.clearFocus()
        vm.search(keyword, displayQuery = normalizedQuery)
        queueExploreSearchRecord(normalizedQuery)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        canScroll = {
            when {
                searchQuery.isNotEmpty() -> shouldAllowCollapsingTopAppBar(
                    canScrollForward = searchListState.canScrollForward,
                    canScrollBackward = searchListState.canScrollBackward,
                    collapsedFraction = topAppBarState.collapsedFraction
                )
                ui.selectedSearchSource == SearchSource.NETEASE ->
                    shouldAllowCollapsingTopAppBar(
                        canScrollForward = gridState.canScrollForward,
                        canScrollBackward = gridState.canScrollBackward,
                        collapsedFraction = topAppBarState.collapsedFraction
                    )
                ui.selectedSearchSource == SearchSource.YOUTUBE_MUSIC ->
                    shouldAllowCollapsingTopAppBar(
                        canScrollForward = youtubeGridState.canScrollForward,
                        canScrollBackward = youtubeGridState.canScrollBackward,
                        collapsedFraction = topAppBarState.collapsedFraction
                    )
                else -> shouldAllowCollapsingTopAppBar(
                    canScrollForward = false,
                    canScrollBackward = false,
                    collapsedFraction = topAppBarState.collapsedFraction
                )
            }
        }
    )

    val showNeteaseDiscoveryPage = ui.neteaseDiscoveryOpen &&
        ui.selectedSearchSource == SearchSource.NETEASE &&
        searchQuery.isBlank()

    Box(modifier = Modifier.fillMaxSize()) {
    MainTabChrome(route = moe.ouom.neriplayer.navigation.Destinations.Explore.route) {
        if (showNeteaseDiscoveryPage) {
            // 「新发现」二级页：chrome 换成返回+标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.closeNeteaseDiscovery() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.explore_new_discovery)
                    )
                }
                Text(
                    text = stringResource(R.string.explore_new_discovery),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                NeriTabLargeTitleTopBar(
                    title = {
                        Text(
                            text = stringResource(R.string.nav_explore),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = searchPanelHorizontalPadding,
                            end = searchPanelHorizontalPadding,
                            top = 4.dp,
                            bottom = 4.dp
                        )
                ) {
                    // 搜索框：玻璃在底、输入在上，避免文字被糊
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(ExploreSearchFieldShape)
                    ) {
                        AdvancedGlassSurface(
                            role = AdvancedGlassRole.ExploreSearchOverlay,
                            shape = ExploreSearchFieldShape,
                            fallbackColor = MaterialTheme.colorScheme.background,
                            tintColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.matchParentSize()
                        ) {
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { onSearchQueryChange(it) },
                            label = {
                                Text(
                                    stringResource(
                                        if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION) {
                                            R.string.explore_link_input_label
                                        } else {
                                            R.string.search_keyword
                                        }
                                    )
                                )
                            },
                            placeholder = {
                                when {
                                    ui.selectedSearchSource == SearchSource.LINK_RECOGNITION -> {
                                        Text(stringResource(R.string.explore_link_input_placeholder))
                                    }
                                    ui.selectedSearchSource == SearchSource.NETEASE && !ui.isNeteaseLoggedIn -> {
                                        Text(stringResource(R.string.netease_login_required_search_placeholder))
                                    }
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Search, "Search") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    HapticIconButton(onClick = {
                                        onSearchQueryChange("")
                                        vm.search("")
                                    }) { Icon(Icons.Default.Clear, "Clear") }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                submitExploreSearch()
                            }),
                            singleLine = true,
                            shape = ExploreSearchFieldShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = {
            NeriSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = miniPlayerHeight
            )
        },
        topBar = {}
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth()
                    .padding(
                        start = searchPanelHorizontalPadding,
                        end = searchPanelHorizontalPadding,
                        // 标题+搜索在 chrome；列表用 contentPadding 从顶铺开以便滚入玻璃
                        top = 0.dp,
                        bottom = 8.dp
                    )
            ) {
                    if (ui.selectedSearchSource == SearchSource.NETEASE && !ui.isNeteaseLoggedIn
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.netease_login_required_search),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (ui.selectedSearchSource == SearchSource.LINK_RECOGNITION
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.explore_link_input_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    val currentSearchSource = ui.selectedSearchSource
                    val activeListState = when {
                        searchQuery.isNotEmpty() -> searchListState
                        ui.selectedSearchSource == SearchSource.NETEASE -> gridState
                        ui.selectedSearchSource == SearchSource.YOUTUBE_MUSIC -> youtubeGridState
                        else -> searchListState
                    }
                    val showTypeFilterLayer by remember(activeListState) {
                        derivedStateOf {
                            when {
                                ui.selectedSearchSource == SearchSource.NETEASE ->
                                    gridState.firstVisibleItemIndex == 0 &&
                                        gridState.firstVisibleItemScrollOffset < 48
                                ui.selectedSearchSource == SearchSource.YOUTUBE_MUSIC ->
                                    youtubeGridState.firstVisibleItemIndex == 0 &&
                                        youtubeGridState.firstVisibleItemScrollOffset < 48
                                else ->
                                    searchListState.firstVisibleItemIndex == 0 &&
                                        searchListState.firstVisibleItemScrollOffset < 48
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTypeFilterLayer,
                        enter = androidx.compose.animation.expandVertically() +
                            androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() +
                            androidx.compose.animation.fadeOut(),
                    ) {
                    // 为 chrome（标题+搜索）让位；收起后列表可滚入玻璃区
                    Column(Modifier.padding(top = 136.dp)) {
                    // 第一行：左侧搜索类型（歌曲/歌单/歌手），右侧搜索源按钮 —— 整组水平居中
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExploreSearchTypeBar(
                            source = searchTypeBarSource,
                            selectedDefaultSearchType = ui.selectedDefaultSearchType,
                            selectedNeteaseSearchType = ui.selectedNeteaseSearchType,
                            selectedYouTubeSearchType = ui.selectedYouTubeMusicSearchType,
                            onDefaultSearchTypeClick = vm::setDefaultSearchType,
                            onNeteaseSearchTypeClick = vm::setNeteaseSearchType,
                            onYouTubeSearchTypeClick = vm::setYouTubeMusicSearchType,
                            selectedAlpha = tagChipSelectedAlpha,
                            unselectedAlpha = tagChipUnselectedAlpha,
                            borderAlpha = tagChipBorderAlpha
                        )
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            Surface(
                                shape = ExplorePrimaryTabShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = tagChipUnselectedAlpha),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = tagChipBorderAlpha)
                                ),
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple()
                                ) { sourceMenuExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = searchSourceLabel(currentSearchSource),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            GlassDropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false },
                                shape = GlassMenuShape,
                                modifier = Modifier
                            ) {
                                orderedSearchSources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(searchSourceLabel(source)) },
                                        onClick = {
                                            sourceMenuExpanded = false
                                            if (source != currentSearchSource) {
                                                vm.setSearchSource(source)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    }
                    }

                    // 类型标签行仅在「新发现」二级页展示（见 NeteaseDiscoveryPage）
                }

            // 列表从屏幕顶铺开（chrome 浮在上面），滚动后条目进入搜索框/标题玻璃区
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    // 搜索源已由右侧按钮切换，关闭左右滑动以免误触
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val currentSource = orderedSearchSources[page]
                    if (searchQuery.isNotEmpty()) {
                        if (shouldRenderExploreSearchResults(page, pagerState.currentPage)) {
                            when {
                                ui.searching -> {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = miniPlayerHeight),
                                        Alignment.Center
                                    ) { CircularProgressIndicator() }
                                }
                                ui.searchError != null -> {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = miniPlayerHeight),
                                        Alignment.Center
                                    ) {
                                        Text(ui.searchError!!, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                ui.searchItems.isEmpty() -> {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = miniPlayerHeight),
                                        Alignment.Center
                                    ) { Text(stringResource(R.string.search_no_result)) }
                                }
                                else -> {
                                LazyColumn(
                                    state = searchListState,
                                    contentPadding = PaddingValues(
                                        start = searchResultHorizontalPadding,
                                        end = searchResultHorizontalPadding,
                                        // 让开 chrome（标题+搜索），滚动后条目可进入玻璃采样区
                                        top = 136.dp,
                                        bottom = exploreSearchResultsBottomPadding(miniPlayerHeight)
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                itemsIndexed(
                                    items = ui.searchItems,
                                    key = { _, item -> item.stableKey }
                                ) { index, item ->
                                    when (item) {
                                        is ExploreSearchResult.Song -> {
                                            val song = item.song
                                            // 预计算的位置查找：逐项 indexOfFirst 会让列表变成 O(n²)，
                                            // 滚动/重组时每帧都要重算
                                            val songListIndex = searchResultIndexByKey[song.stableKey()] ?: index
                                            val isFavoriteSong = favoriteSongKeys.contains(song.stableKey())
                                            SongRow(
                                                index = index + 1,
                                                song = song,
                                                isFavorite = isFavoriteSong,
                                                favoriteActionEnabled = localPlaylistsReady,
                                                offlineMode = offlineMode,
                                                snackbarHostState = snackbarHostState,
                                                onClick = {
                                                    if (shouldShowBiliPartsPicker(song)) {
                                                        scope.launch {
                                                            try {
                                                                val info = vm.getVideoInfoByAvid(song.id)
                                                                if (info.pages.size <= 1) {
                                                                    onSongClick(ui.searchResults, songListIndex)
                                                                } else {
                                                                    partsInfo = info
                                                                    clickedSongCoverUrl = song.coverUrl ?: ""
                                                                    showPartsSheet = true
                                                                }
                                                            } catch (e: Exception) {
                                                                NPLogger.e("ExploreScreen", composeResources.getString(R.string.search_error), e)
                                                            }
                                                        }
                                                    } else {
                                                        onSongClick(ui.searchResults, songListIndex)
                                                    }
                                                },
                                                onPlayNow = { onSongPlayPreservingQueue(song) },
                                                onPlayNext = { onSongPlayNext(song) },
                                                onAddToQueueEnd = { onSongAddToQueueEnd(song) },
                                                onDownload = {
                                                    GlobalDownloadManager.startDownload(context, song)
                                                    scope.launch {
                                                        snackbarHostState.showNeriSnackbar(
                                                            composeResources.getString(
                                                                R.string.download_starting,
                                                                song.displayName()
                                                            )
                                                        )
                                                    }
                                                },
                                                onToggleFavorite = {
                                                    if (localPlaylistsReady) {
                                                        scope.launchLocalPlaylistMutation(
                                                            operation = "toggleFavoriteFromExplore",
                                                            onResult = { result ->
                                                                result.onSuccess { added ->
                                                                    AppFeedback.show(
                                                                        context,
                                                                        if (added) {
                                                                            favoriteAddedText
                                                                        } else {
                                                                            favoriteRemovedText
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        ) {
                                                            val isFavoriteAtAction = FavoritesPlaylist
                                                                .firstOrNull(repo.playlists.value, context)
                                                                ?.songs
                                                                ?.any { it.sameIdentityAs(song) } == true
                                                            if (isFavoriteAtAction) {
                                                                repo.removeFromFavorites(song)
                                                            } else {
                                                                repo.addToFavorites(song)
                                                            }
                                                            !isFavoriteAtAction
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        is ExploreSearchResult.Playlist -> {
                                            NeteasePlaylistSearchRow(
                                                playlist = item.playlist,
                                                offlineMode = offlineMode,
                                                onClick = { onPlay(item.playlist) }
                                            )
                                        }
                                        is ExploreSearchResult.YouTubePlaylist -> {
                                            YouTubePlaylistSearchRow(
                                                playlist = item.playlist,
                                                offlineMode = offlineMode,
                                                onClick = { onYouTubeMusicPlaylistClick(item.playlist) }
                                            )
                                        }
                                        is ExploreSearchResult.BilibiliPlaylist -> {
                                            BiliPlaylistSearchRow(
                                                playlist = item.playlist,
                                                offlineMode = offlineMode,
                                                onClick = { onBiliPlaylistClick(item.playlist) }
                                            )
                                        }
                                        is ExploreSearchResult.Artist -> {
                                            NeteaseArtistSearchRow(
                                                result = item.result,
                                                offlineMode = offlineMode,
                                                onClick = { onNeteaseArtistClick(item.result.artist) }
                                            )
                                        }
                                        is ExploreSearchResult.OnlineCollection -> {
                                            OnlineCollectionSearchRow(
                                                collection = item.collection,
                                                offlineMode = offlineMode,
                                                onClick = { onOnlineCollectionClick(item.collection) }
                                            )
                                        }
                                        is ExploreSearchResult.YouTubeCreator -> {
                                            YouTubeCreatorSearchRow(
                                                creator = item.creator,
                                                offlineMode = offlineMode,
                                                onClick = { onYouTubeCreatorClick(item.creator) }
                                            )
                                        }
                                        is ExploreSearchResult.Notice -> {
                                            ExploreSearchNoticeRow(item)
                                        }
                                    }
                                }
                                if (ui.searchLoadingMore) {
                                    item(key = "search-loading-more") {
                                        SearchLoadingMoreRow()
                                    }
                                } else if (ui.searchLoadMoreError != null) {
                                    item(key = "search-load-more-error") {
                                        SearchLoadMoreErrorRow(
                                            error = ui.searchLoadMoreError!!,
                                            onRetry = vm::loadMoreSearchResults
                                        )
                                    }
                                }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                } else {
                    when (currentSource) {
                        SearchSource.DEFAULT -> {
                            NeteaseFeaturedHomeContent(
                                ui = ui,
                                favoriteKeys = favoriteKeys,
                                onPlay = onPlay,
                                onDiscover = {
                                    vm.setSearchSource(SearchSource.NETEASE)
                                    vm.openNeteaseDiscovery()
                                }
                            )
                        }
                        SearchSource.NETEASE -> {
                            NeteaseFeaturedHomeContent(
                                ui = ui,
                                favoriteKeys = favoriteKeys,
                                onPlay = onPlay,
                                onDiscover = vm::openNeteaseDiscovery
                            )
                        }
                        SearchSource.BILIBILI -> {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(stringResource(R.string.explore_bili_desc), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        SearchSource.YOUTUBE_MUSIC -> {
                            YouTubeMusicExploreContent(
                                ui = ui,
                                vm = vm,
                                onClick = onYouTubeMusicPlaylistClick,
                                offlineMode = offlineMode,
                                isTabletLayout = isTabletLayout,
                                gridState = youtubeGridState
                            )
                        }
                        SearchSource.LINK_RECOGNITION -> {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.explore_link_recognition_placeholder),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            }
        }
    }

    if (showNeteaseDiscoveryPage) {
        BackHandler(enabled = true) { vm.closeNeteaseDiscovery() }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NeteaseDiscoveryPage(
                onBack = vm::closeNeteaseDiscovery,
                ui = ui,
                tagKeys = tagKeys,
                tagLabels = tagLabels,
                favoriteKeys = favoriteKeys,
                vm = vm,
                onPlay = onPlay,
                tagChipSelectedAlpha = tagChipSelectedAlpha,
                tagChipUnselectedAlpha = tagChipUnselectedAlpha,
                tagChipBorderAlpha = tagChipBorderAlpha,
                isTabletLayout = isTabletLayout,
                gridState = gridState
            )
        }
    }
    }

    if (showPartsSheet && partsInfo != null) {
        val currentPartsInfo = partsInfo!!
        BackHandler(enabled = partsSelectionMode) { exitPartsSelection() }
        ModalBottomSheet(
            onDismissRequest = {
                showPartsSheet = false
                exitPartsSelection()
            },
            sheetState = partsSheetState,
            sheetGesturesEnabled = false
        ) {
            Column(
                Modifier
                    .bottomSheetScrollGuard()
                    .padding(bottom = 12.dp)
            ) {
                AnimatedVisibility(visible = partsSelectionMode) {
                    val allSelected = selectedParts.size == currentPartsInfo.pages.size
                    TopAppBar(
                    title = {
                        Text(
                            pluralStringResource(
                                R.plurals.common_selected_count,
                                selectedParts.size,
                                selectedParts.size
                            )
                        )
                    },
                        navigationIcon = {
                            HapticIconButton(onClick = { exitPartsSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.explore_exit_selection))
                            }
                        },
                        actions = {
                            HapticIconButton(onClick = {
                                selectedParts = if (allSelected) {
                                    emptySet()
                                } else {
                                    currentPartsInfo.pages.map { it.page }.toSet()
                                }
                            }) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = if (allSelected) stringResource(R.string.explore_deselect_all) else stringResource(R.string.explore_select_all)
                                )
                            }
                            HapticIconButton(
                                onClick = {
                                    if (selectedParts.isNotEmpty()) {
                                        scope.launch { partsSheetState.hide() }.invokeOnCompletion {
                                            if (!partsSheetState.isVisible) {
                                                showPartsSheet = false
                                                showExportSheet = true
                                            }
                                        }
                                    }
                                },
                                enabled = selectedParts.isNotEmpty()
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.explore_export_to_playlist))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }

                AnimatedVisibility(visible = !partsSelectionMode) {
                    Text(
                        text = currentPartsInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider()

                LazyColumn {
                    itemsIndexed(currentPartsInfo.pages, key = { _, page -> page.page }) { index, page ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (partsSelectionMode) {
                                            selectedParts = if (selectedParts.contains(page.page)) {
                                                selectedParts - page.page
                                            } else {
                                                selectedParts + page.page
                                            }
                                        } else {
                                            onPlayParts(currentPartsInfo, index, clickedSongCoverUrl)
                                            scope.launch { partsSheetState.hide() }.invokeOnCompletion {
                                                if (!partsSheetState.isVisible) showPartsSheet = false
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!partsSelectionMode) {
                                            partsSelectionMode = true
                                            selectedParts = setOf(page.page)
                                        }
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (partsSelectionMode) {
                                Checkbox(
                                    checked = selectedParts.contains(page.page),
                                    onCheckedChange = {
                                        selectedParts = if (selectedParts.contains(page.page)) {
                                            selectedParts - page.page
                                        } else {
                                            selectedParts + page.page
                                        }
                                    }
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Text(
                                text = "P${page.page}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                text = page.part,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        PlaylistExportSheet(
            title = stringResource(R.string.playlist_export_to_local),
            playlists = allLocalPlaylists.filterNot {
                LocalFilesPlaylist.isSystemPlaylist(it, context)
            },
            selectedCount = selectedParts.size,
            onDismissRequest = { showExportSheet = false },
            onCreateAndExport = { name ->
                val songs = partsInfo!!.pages
                    .filter { selectedParts.contains(it.page) }
                    .map { page -> vm.toSongItem(page, partsInfo!!, clickedSongCoverUrl) }
                scope.launchLocalPlaylistMutation(
                    operation = "createPlaylistFromExplore",
                    onResult = { result ->
                        scope.showPlaylistBatchExportCreatedResult(
                            context = context,
                            snackbarHostState = snackbarHostState,
                            repository = repo,
                            result = result
                        )
                    }
                ) {
                    repo.createPlaylistWithSongs(name, songs)
                }
                exitPartsSelection()
            },
            onExportToPlaylist = { playlist ->
                val songs = partsInfo!!.pages
                    .filter { selectedParts.contains(it.page) }
                    .map { page -> vm.toSongItem(page, partsInfo!!, clickedSongCoverUrl) }
                scope.launchLocalPlaylistMutation(
                    operation = "exportSongsFromExplore",
                    onResult = { result ->
                        scope.showPlaylistBatchExportAddedResult(
                            context = context,
                            snackbarHostState = snackbarHostState,
                            repository = repo,
                            targetPlaylistId = playlist.id,
                            targetPlaylistName = playlist.name,
                            result = result
                        )
                    }
                ) {
                    repo.addSongsToPlaylistWithResult(playlist.id, songs)
                }
                exitPartsSelection()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreOfflineContent(topAppBarState: TopAppBarState) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = topAppBarState,
        canScroll = {
            shouldAllowCollapsingTopAppBar(
                canScrollForward = false,
                canScrollBackward = false,
                collapsedFraction = topAppBarState.collapsedFraction
            )
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            NeriTabLargeTitleTopBar(
                title = stringResource(R.string.nav_explore)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(start = 32.dp, end = 32.dp, bottom = miniPlayerHeight),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.offline_mode_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.explore_offline_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreSearchHistoryRow(
    history: List<String>,
    visible: Boolean,
    query: String,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    AnimatedVisibility(visible = visible && history.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.search_history),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (query.isBlank()) {
                    HapticTextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
            val historyListState = rememberLazyListState()
            val showStartFade by remember(historyListState) {
                derivedStateOf { historyListState.canScrollBackward }
            }
            val showEndFade by remember(historyListState) {
                derivedStateOf { historyListState.canScrollForward }
            }
            LazyRow(
                state = historyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .exploreHorizontalEdgeFade(
                        showStartFade = showStartFade,
                        showEndFade = showEndFade
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(history) { _, item ->
                    ExploreSearchHistoryChip(
                        text = item,
                        onClick = { onHistoryClick(item) }
                    )
                }
            }
        }
    }
}

internal fun Modifier.exploreHorizontalEdgeFade(
    showStartFade: Boolean,
    showEndFade: Boolean,
    fadeWidth: Dp = 28.dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val resolvedFadeWidth = fadeWidth.toPx().coerceAtMost(size.width / 2f)
        if (resolvedFadeWidth <= 0f) return@drawWithContent
        if (showStartFade) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = resolvedFadeWidth
                ),
                topLeft = Offset.Zero,
                size = Size(resolvedFadeWidth, size.height),
                blendMode = BlendMode.DstIn
            )
        }
        if (showEndFade) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - resolvedFadeWidth,
                    endX = size.width
                ),
                topLeft = Offset(size.width - resolvedFadeWidth, 0f),
                size = Size(resolvedFadeWidth, size.height),
                blendMode = BlendMode.DstIn
            )
        }
    }

@Composable
private fun ExploreSearchHistoryChip(
    text: String,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    ExploreGlassPillSurface(
        fallbackColor = containerColor,
        tintColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun filteredExploreSearchHistory(history: List<String>): List<String> {
    return history.take(EXPLORE_HISTORY_DISPLAY_LIMIT)
}

internal fun shouldShowExploreSearchHistory(
    history: List<String>,
    contentScrolled: Boolean,
    searchOverlayActive: Boolean = false
): Boolean {
    return history.isNotEmpty() && searchOverlayActive && !contentScrolled
}

internal fun shouldShowExploreNeteaseSearchTypeBar(
    selectedSearchSource: SearchSource,
    contentScrolled: Boolean
): Boolean {
    return exploreSearchTypeBarSource(selectedSearchSource, contentScrolled) ==
        SearchSource.NETEASE
}

internal fun shouldShowExploreYouTubeSearchTypeBar(
    selectedSearchSource: SearchSource,
    contentScrolled: Boolean
): Boolean {
    return exploreSearchTypeBarSource(selectedSearchSource, contentScrolled) ==
        SearchSource.YOUTUBE_MUSIC
}

internal fun exploreSearchTypeBarSource(
    selectedSearchSource: SearchSource,
    contentScrolled: Boolean
): SearchSource? {
    // 上滑时仍保留类型图标栏，避免筛选入口在滚动中消失
    @Suppress("UNUSED_PARAMETER")
    return selectedSearchSource.takeIf {
        it == SearchSource.DEFAULT ||
            it == SearchSource.NETEASE ||
            it == SearchSource.YOUTUBE_MUSIC
    }
}

internal fun isExploreSearchTypeBarSourceSwap(
    initialSource: SearchSource?,
    targetSource: SearchSource?
): Boolean {
    return initialSource != targetSource &&
        initialSource in EXPLORE_SEARCH_TYPE_BAR_SOURCES &&
        targetSource in EXPLORE_SEARCH_TYPE_BAR_SOURCES
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExploreSearchTypeBar(
    source: SearchSource?,
    selectedDefaultSearchType: DefaultExploreSearchType,
    selectedNeteaseSearchType: NeteaseExploreSearchType,
    selectedYouTubeSearchType: YouTubeExploreSearchType,
    onDefaultSearchTypeClick: (DefaultExploreSearchType) -> Unit,
    onNeteaseSearchTypeClick: (NeteaseExploreSearchType) -> Unit,
    onYouTubeSearchTypeClick: (YouTubeExploreSearchType) -> Unit,
    selectedAlpha: Float,
    unselectedAlpha: Float,
    borderAlpha: Float
) {
    AnimatedContent(
        targetState = source,
        modifier = Modifier.testTag(EXPLORE_SEARCH_TYPE_BAR_CONTAINER_TAG),
        transitionSpec = {
            val sourceSwap = isExploreSearchTypeBarSourceSwap(initialState, targetState)
            val enter = if (sourceSwap) {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_ENTER_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + slideInVertically(
                    initialOffsetY = { height ->
                        if (targetState == SearchSource.YOUTUBE_MUSIC) {
                            height / EXPLORE_SEARCH_TYPE_BAR_SLIDE_DIVISOR
                        } else {
                            -height / EXPLORE_SEARCH_TYPE_BAR_SLIDE_DIVISOR
                        }
                    },
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_ENTER_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            } else {
                fadeIn(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_ENTER_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + expandVertically(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_SIZE_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            val exit = if (sourceSwap) {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + slideOutVertically(
                    targetOffsetY = { height ->
                        if (targetState == SearchSource.YOUTUBE_MUSIC) {
                            -height / EXPLORE_SEARCH_TYPE_BAR_SLIDE_DIVISOR
                        } else {
                            height / EXPLORE_SEARCH_TYPE_BAR_SLIDE_DIVISOR
                        }
                    },
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            } else {
                fadeOut(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_EXIT_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                ) + shrinkVertically(
                    animationSpec = tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_SIZE_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            enter togetherWith exit using SizeTransform(
                clip = true,
                sizeAnimationSpec = { _, _ ->
                    tween(
                        durationMillis = EXPLORE_SEARCH_TYPE_BAR_SIZE_DURATION_MS,
                        easing = FastOutSlowInEasing
                    )
                }
            )
        },
        label = "explore_search_type_bar"
    ) { displayedSource ->
        when (displayedSource) {
            SearchSource.DEFAULT -> {
                LazyRow(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(EXPLORE_DEFAULT_SEARCH_TYPE_BAR_TAG),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(DefaultExploreSearchType.entries) { _, type ->
                        ExploreTagChip(
                            label = defaultSearchTypeLabel(type),
                            icon = defaultSearchTypeIcon(type),
                            showLabel = false,
                            selected = selectedDefaultSearchType == type,
                            onClick = {
                                if (source == displayedSource) {
                                    onDefaultSearchTypeClick(type)
                                }
                            },
                            selectedAlpha = selectedAlpha,
                            unselectedAlpha = unselectedAlpha,
                            borderAlpha = borderAlpha
                        )
                    }
                }
            }

            SearchSource.NETEASE -> {
                LazyRow(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(EXPLORE_NETEASE_SEARCH_TYPE_BAR_TAG),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(NeteaseExploreSearchType.entries) { _, type ->
                        ExploreTagChip(
                            label = neteaseSearchTypeLabel(type),
                            icon = neteaseSearchTypeIcon(type),
                            showLabel = false,
                            selected = selectedNeteaseSearchType == type,
                            onClick = {
                                if (source == displayedSource) {
                                    onNeteaseSearchTypeClick(type)
                                }
                            },
                            selectedAlpha = selectedAlpha,
                            unselectedAlpha = unselectedAlpha,
                            borderAlpha = borderAlpha
                        )
                    }
                }
            }

            SearchSource.YOUTUBE_MUSIC -> {
                LazyRow(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(EXPLORE_YOUTUBE_SEARCH_TYPE_BAR_TAG),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(YouTubeExploreSearchType.entries) { _, type ->
                        ExploreTagChip(
                            label = youtubeSearchTypeLabel(type),
                            icon = youtubeSearchTypeIcon(type),
                            showLabel = false,
                            selected = selectedYouTubeSearchType == type,
                            onClick = {
                                if (source == displayedSource) {
                                    onYouTubeSearchTypeClick(type)
                                }
                            },
                            selectedAlpha = selectedAlpha,
                            unselectedAlpha = unselectedAlpha,
                            borderAlpha = borderAlpha
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}

private const val EXPLORE_HISTORY_DISPLAY_LIMIT = 15
private val EXPLORE_SEARCH_TYPE_BAR_SOURCES = setOf(
    SearchSource.DEFAULT,
    SearchSource.NETEASE,
    SearchSource.YOUTUBE_MUSIC
)
private const val EXPLORE_SEARCH_TYPE_BAR_ENTER_DURATION_MS = 180
private const val EXPLORE_SEARCH_TYPE_BAR_EXIT_DURATION_MS = 140
private const val EXPLORE_SEARCH_TYPE_BAR_SIZE_DURATION_MS = 220
private const val EXPLORE_SEARCH_TYPE_BAR_SLIDE_DIVISOR = 5
internal const val EXPLORE_SEARCH_TYPE_BAR_CONTAINER_TAG = "explore_search_type_bar"
internal const val EXPLORE_DEFAULT_SEARCH_TYPE_BAR_TAG = "explore_default_search_type_bar"
internal const val EXPLORE_NETEASE_SEARCH_TYPE_BAR_TAG = "explore_netease_search_type_bar"
internal const val EXPLORE_YOUTUBE_SEARCH_TYPE_BAR_TAG = "explore_youtube_search_type_bar"

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NeteaseFeaturedHomeContent(
    ui: ExploreUiState,
    favoriteKeys: Set<String>,
    onPlay: (PlaylistSummary) -> Unit,
    onDiscover: () -> Unit
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    // 冷启动由 VM 随机定一张；首页静止展示，不自动轮播
    val featured = remember(ui.featuredPlaylists, ui.playlists) {
        ui.featuredPlaylists.firstOrNull() ?: ui.playlists.firstOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = miniPlayerHeight)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            featured != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .widthIn(max = 280.dp)
                ) {
                    PlaylistCard(
                        playlist = featured,
                        isFavorite = favoriteKeys.contains("netease:${featured.id}"),
                        onClick = { onPlay(featured) }
                    )
                }
            }
            ui.loading -> {
                CircularProgressIndicator()
            }
            ui.error != null -> {
                Text(
                    text = ui.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.search_no_result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDiscover) {
            Text(stringResource(R.string.explore_new_discovery))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NeteaseDiscoveryPage(
    onBack: () -> Unit,
    ui: ExploreUiState,
    tagKeys: List<String>,
    tagLabels: List<String>,
    favoriteKeys: Set<String>,
    vm: ExploreViewModel,
    onPlay: (PlaylistSummary) -> Unit,
    tagChipSelectedAlpha: Float,
    tagChipUnselectedAlpha: Float,
    tagChipBorderAlpha: Float,
    isTabletLayout: Boolean = false,
    gridState: LazyGridState
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val gridHorizontalPadding = if (isTabletLayout) 56.dp else 16.dp
    val gridMinCellSize = if (isTabletLayout) 170.dp else 150.dp
    val gridSpacing = if (isTabletLayout) 16.dp else 12.dp

    // 独立全屏二级页：标题在 chrome（返回+新发现），这里不再重复顶栏
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = miniPlayerHeight)
    ) {
        ExploreNeteasePlaylistTagRow(
            tagKeys = tagKeys,
            tagLabels = tagLabels,
            selectedTag = ui.selectedTag,
            onTagClick = { tagKey ->
                if (ui.selectedTag != tagKey) vm.loadHighQuality(tagKey)
            },
            selectedAlpha = tagChipSelectedAlpha,
            unselectedAlpha = tagChipUnselectedAlpha,
            borderAlpha = tagChipBorderAlpha
        )
        if (ui.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(gridMinCellSize),
            verticalArrangement = Arrangement.spacedBy(gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            contentPadding = PaddingValues(
                start = gridHorizontalPadding,
                end = gridHorizontalPadding,
                top = 12.dp,
                bottom = 16.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (ui.playlists.isNotEmpty()) {
                items(items = ui.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        isFavorite = favoriteKeys.contains("netease:${playlist.id}"),
                        onClick = { onPlay(playlist) }
                    )
                }
            } else if (!ui.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        Alignment.Center
                    ) {
                        Text(
                            text = ui.error ?: stringResource(R.string.search_no_result),
                            color = if (ui.error != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NeteaseDefaultContent(
    gridState: LazyGridState,
    ui: ExploreUiState,
    tagKeys: List<String>,
    tagLabels: List<String>,
    favoriteKeys: Set<String>,
    vm: ExploreViewModel,
    onPlay: (PlaylistSummary) -> Unit,
    tagChipSelectedAlpha: Float,
    tagChipUnselectedAlpha: Float,
    tagChipBorderAlpha: Float,
    isTabletLayout: Boolean = false
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val gridHorizontalPadding = if (isTabletLayout) 56.dp else 16.dp
    val gridMinCellSize = if (isTabletLayout) 170.dp else 150.dp
    val gridSpacing = if (isTabletLayout) 16.dp else 12.dp
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(gridMinCellSize),
        verticalArrangement = Arrangement.spacedBy(gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        contentPadding = PaddingValues(
            start = gridHorizontalPadding,
            end = gridHorizontalPadding,
            top = 16.dp,
            bottom = 16.dp + miniPlayerHeight
        ),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (ui.playlists.isEmpty() && ui.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (ui.playlists.isNotEmpty()) {
            items(items = ui.playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    isFavorite = favoriteKeys.contains("netease:${playlist.id}"),
                    onClick = { onPlay(playlist) }
                )
            }
        } else if (ui.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (ui.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(ui.error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ExploreNeteasePlaylistTagRow(
    tagKeys: List<String>,
    tagLabels: List<String>,
    selectedTag: String,
    onTagClick: (String) -> Unit,
    selectedAlpha: Float,
    unselectedAlpha: Float,
    borderAlpha: Float
) {
    val tagListState = rememberLazyListState()
    val showTagStartFade by remember(tagListState) {
        derivedStateOf { tagListState.canScrollBackward }
    }
    val showTagEndFade by remember(tagListState) {
        derivedStateOf { tagListState.canScrollForward }
    }
    LazyRow(
        state = tagListState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .exploreHorizontalEdgeFade(
                showStartFade = showTagStartFade,
                showEndFade = showTagEndFade
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(tagKeys) { index, tagKey ->
            ExploreTagChip(
                label = tagLabels[index],
                selected = selectedTag == tagKey,
                onClick = { onTagClick(tagKey) },
                selectedAlpha = selectedAlpha,
                unselectedAlpha = unselectedAlpha,
                borderAlpha = borderAlpha
            )
        }
    }
}

@Composable
private fun ExploreTagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedAlpha: Float,
    unselectedAlpha: Float,
    borderAlpha: Float,
    icon: ImageVector? = null,
    showLabel: Boolean = true
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = selectedAlpha)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = unselectedAlpha)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.secondary.copy(alpha = borderAlpha)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)
    }

    ExploreGlassPillSurface(
        fallbackColor = containerColor,
        tintColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shape = if (showLabel) ExplorePillShape else ExploreTypeChipShape,
        modifier = if (showLabel) Modifier else Modifier.requiredSize(48.dp),
        onClick = onClick
    ) {
        if (!showLabel && icon != null) {
            Box(
                modifier = Modifier.size(ExploreTypeChipSize),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = if (showLabel) 14.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (showLabel) null else label,
                    modifier = Modifier.size(20.dp)
                )
                if (showLabel) {
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }
    }
}

@Composable
internal fun ExploreGlassPillSurface(
    fallbackColor: Color,
    tintColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    border: BorderStroke? = null,
    shape: androidx.compose.ui.graphics.Shape = ExplorePillShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AdvancedGlassSurface(
            role = AdvancedGlassRole.ExploreTag,
            shape = shape,
            fallbackColor = fallbackColor,
            tintColor = tintColor
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .indication(interactionSource, ripple()),
                shape = shape,
                color = Color.Transparent,
                contentColor = contentColor,
                border = border,
                content = content
            )
        }
    }
}

@Composable
private fun NeteasePlaylistSearchRow(
    playlist: PlaylistSummary,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    LinkedCollectionRow(
        title = playlist.name,
        subtitle = stringResource(
            R.string.playlist_play_count_format,
            formatPlayCount(context, playlist.playCount),
            playlist.trackCount
        ),
        coverUrl = playlist.picUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun BiliPlaylistSearchRow(
    playlist: BiliPlaylist,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    LinkedCollectionRow(
        title = playlist.title,
        subtitle = listOfNotNull(
            playlist.subtitle.takeIf { it.isNotBlank() },
            pluralStringResource(
                R.plurals.bili_content_count,
                playlist.count,
                playlist.count
            )
        ).joinToString(" · "),
        coverUrl = playlist.coverUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun YouTubePlaylistSearchRow(
    playlist: YouTubeMusicPlaylist,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    LinkedCollectionRow(
        title = playlist.title,
        subtitle = listOfNotNull(
            playlist.subtitle.takeIf { it.isNotBlank() },
            pluralStringResource(
                R.plurals.count_songs_format,
                playlist.trackCount,
                playlist.trackCount
            ).takeIf { playlist.trackCount > 0 }
        ).joinToString(" · "),
        coverUrl = playlist.coverUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun NeteaseArtistSearchRow(
    result: NeteaseSearchArtistResult,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    LinkedCollectionRow(
        title = result.artist.name,
        subtitle = listOf(
            pluralStringResource(
                R.plurals.artist_song_count,
                result.musicSize,
                result.musicSize
            ),
            pluralStringResource(
                R.plurals.artist_album_count,
                result.albumSize,
                result.albumSize
            )
        ).joinToString(" · "),
        coverUrl = result.picUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun OnlineCollectionSearchRow(
    collection: LxOnlineCollection,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    val sourceLabel = onlineCollectionSourceLabel(collection.sourceId)
    val countLabel = collection.trackCount.takeIf { it > 0 }?.let { count ->
        pluralStringResource(R.plurals.count_songs_format, count, count)
    }
    val subtitle = listOfNotNull(
        sourceLabel,
        collection.creator.takeIf { it.isNotBlank() },
        countLabel
    ).joinToString(" · ")
    LinkedCollectionRow(
        title = collection.name,
        subtitle = subtitle,
        coverUrl = collection.coverUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = if (collection.type == LxOnlineCollectionType.ARTIST) {
                    Icons.Filled.AccountCircle
                } else {
                    Icons.AutoMirrored.Filled.QueueMusic
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun YouTubeCreatorSearchRow(
    creator: YouTubeMusicCreatorSummary,
    offlineMode: Boolean,
    onClick: () -> Unit
) {
    LinkedCollectionRow(
        title = creator.title,
        subtitle = creator.subtitle.ifBlank {
            stringResource(R.string.explore_search_type_creator)
        },
        coverUrl = creator.coverUrl,
        offlineMode = offlineMode,
        fallbackIcon = {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun LinkedCollectionRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    offlineMode: Boolean,
    fallbackIcon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.performHapticFeedback()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = fastScrollableImageRequest(
                        context = context,
                        data = coverUrl,
                        sizePx = 144,
                        offlineMode = offlineMode
                    ),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                fallbackIcon()
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExploreSearchNoticeRow(item: ExploreSearchResult.Notice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = item.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchLoadingMoreRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SearchLoadMoreErrorRow(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        HapticTextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongRow(
    index: Int,
    song: SongItem,
    isFavorite: Boolean,
    favoriteActionEnabled: Boolean,
    offlineMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onClick: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueueEnd: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    val composeResources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val coverUrl = rememberSongDisplayCoverUrl(song)
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.performHapticFeedback()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = fastScrollableImageRequest(
                    context = context,
                    data = coverUrl,
                    sizePx = 128,
                    offlineMode = offlineMode
                ),
                contentDescription = song.displayName(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = song.displayName(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = song.displayArtist().orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (song.durationMs > 0L) {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))
        Box {
            HapticIconButton(onClick = { showMoreMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GlassDropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_result_play_keep_queue)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        context.performHapticFeedback()
                        onPlayNow()
                        showMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.local_playlist_play_next)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        context.performHapticFeedback()
                        onPlayNext()
                        showMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_result_add_to_current_queue)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        context.performHapticFeedback()
                        onAddToQueueEnd()
                        showMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.playlist_add_to)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        context.performHapticFeedback()
                        showMoreMenu = false
                        showAddToPlaylistSheet = true
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isFavorite) {
                                stringResource(R.string.favorite_remove)
                            } else {
                                stringResource(R.string.favorite_add)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = null,
                        tint = if (isFavorite) {
                            Color(0xFFE53935)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        )
                    },
                    enabled = favoriteActionEnabled,
                    onClick = {
                        context.performHapticFeedback()
                        onToggleFavorite()
                        showMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_to_local)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        context.performHapticFeedback()
                        onDownload()
                        showMoreMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_copy_song_info)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        scope.launch {
                            val messageRes = when (
                                val result = clipboard.copyPlainTextSafely(
                                    label = "text",
                                    text = buildExploreSongInfo(song)
                                )
                            ) {
                                is ClipboardCopyResult.Copied -> if (result.wasTruncated) {
                                    R.string.toast_copy_truncated
                                } else {
                                    R.string.toast_copied
                                }
                                ClipboardCopyResult.TransactionTooLarge -> R.string.toast_copy_failed
                            }
                            snackbarHostState.showNeriSnackbar(
                                composeResources.getString(messageRes)
                            )
                        }
                        showMoreMenu = false
                    }
                )
            }
        }
        if (showAddToPlaylistSheet) {
            AddSongToPlaylistSheet(
                song = song,
                onDismissRequest = { showAddToPlaylistSheet = false }
            )
        }
    }
}

internal fun buildExploreSongInfo(song: SongItem): String {
    return "${song.displayName()}-${song.displayArtist()}"
}

@Composable
private fun YouTubeMusicExploreContent(
    ui: ExploreUiState,
    vm: ExploreViewModel,
    onClick: (YouTubeMusicPlaylist) -> Unit,
    offlineMode: Boolean,
    gridState: LazyGridState,
    isTabletLayout: Boolean = false
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val gridHorizontalPadding = if (isTabletLayout) 56.dp else 16.dp
    val gridMinCellSize = if (isTabletLayout) 156.dp else 120.dp
    val gridSpacing = if (isTabletLayout) 14.dp else 10.dp
    when {
        ui.ytMusicPlaylistsLoading -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = miniPlayerHeight),
                Alignment.Center
            ) { CircularProgressIndicator() }
        }
        ui.ytMusicPlaylistsError != null -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = miniPlayerHeight),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        ui.ytMusicPlaylistsError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    HapticTextButton(onClick = { vm.loadYtMusicPlaylists() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
        ui.ytMusicPlaylists.isEmpty() -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = miniPlayerHeight),
                Alignment.Center
            ) {
                Text(
                    stringResource(R.string.explore_tag_youtube_music),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        else -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(gridMinCellSize),
                contentPadding = PaddingValues(
                    start = gridHorizontalPadding, end = gridHorizontalPadding,
                    top = 8.dp,
                    bottom = 16.dp + miniPlayerHeight
                ),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = ui.ytMusicPlaylists,
                    key = { it.browseId }
                ) { playlist ->
                    YtMusicExploreCard(
                        playlist = playlist,
                        onClick = { onClick(playlist) },
                        offlineMode = offlineMode
                    )
                }
            }
        }
    }
}

@Composable
private fun YtMusicExploreCard(
    playlist: YouTubeMusicPlaylist,
    onClick: () -> Unit,
    offlineMode: Boolean
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = fastScrollableImageRequest(
                context = context,
                data = playlist.coverUrl,
                sizePx = 384,
                offlineMode = offlineMode
            ),
            contentDescription = playlist.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)) {
            Text(
                text = playlist.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            if (playlist.subtitle.isNotBlank()) {
                Text(
                    text = playlist.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
