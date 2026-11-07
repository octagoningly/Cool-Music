package moe.ouom.neriplayer.ui.viewmodel.tab

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
 * File: moe.ouom.neriplayer.ui.viewmodel.tab/LibraryViewModel
 * Created: 2025/8/11
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicLibraryPlaylist
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlaylistSummary
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.auth.youtube.buildRefreshObserverFingerprint
import moe.ouom.neriplayer.data.cache.LibraryListsCacheRepository
import moe.ouom.neriplayer.data.cache.LibraryListsCacheSnapshot
import moe.ouom.neriplayer.data.cache.shouldApplyLibraryListCache
import moe.ouom.neriplayer.data.cache.toCacheDto
import moe.ouom.neriplayer.data.cache.toUiModel
import moe.ouom.neriplayer.data.platform.youtube.YouTubeFeatureGate
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistDeleteResult
import moe.ouom.neriplayer.data.local.playlist.runLocalPlaylistMutationSafely
import moe.ouom.neriplayer.data.local.playlist.importer.MAX_IMPORT_SONGS
import moe.ouom.neriplayer.data.model.SongItem
import moe.ouom.neriplayer.core.logging.NPLogger
import org.json.JSONObject
import java.io.IOException

private const val BILI_DETAIL_BATCH_SIZE = 6
private const val BILI_RESOURCE_TYPE_COLLECTION = 21
private const val LIBRARY_LISTS_CACHE_SAVE_DEBOUNCE_MS = 400L

/** 媒体库页面 UI 状态 */
data class LibraryUiState(
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val neteasePlaylists: List<PlaylistSummary> = emptyList(),
    val neteaseAlbums: List<AlbumSummary> = emptyList(),
    val neteaseError: String? = null,
    val youtubeMusicPlaylists: List<YouTubeMusicPlaylist> = emptyList(),
    val youtubeMusicError: String? = null,
    val biliPlaylists: List<BiliPlaylist> = emptyList(),
    val biliError: String? = null,
    val qqMusicPlaylists: List<QQMusicPlaylistSummary> = emptyList(),
    val qqMusicError: String? = null
)

@Suppress("unused")
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalPlaylistRepository.getInstance(application)

    private val neteaseCookieRepo = AppContainer.neteaseCookieRepo
    private val neteaseClient = AppContainer.neteaseClient

    private val biliCookieRepo = AppContainer.biliCookieRepo
    private val biliClient = AppContainer.biliClient
    private val youtubeAuthRepo = AppContainer.youtubeAuthRepo
    private val qqMusicCookieRepo = AppContainer.qqMusicCookieRepo
    private val qqMusicClient = AppContainer.qqMusicClient

    private val listsCacheRepo = LibraryListsCacheRepository(application)
    private var listsCacheSaveJob: Job? = null
    private var listsCacheBootstrapped = false

    private val _uiState = MutableStateFlow(
        LibraryUiState(localPlaylists = localRepo.playlists.value)
    )
    val uiState: StateFlow<LibraryUiState> = _uiState
    private var lastYouTubeAuthFingerprint: String? = null
    private var youtubeMusicPlaylistsJob: Job? = null
    private var youtubeMusicPlaylistsPending = false
    private var youtubeEnabled = YouTubeFeatureGate.isEnabled()
    private var lastObservedYouTubeEnabled: Boolean? = null
    private val autoImportedQQMusicDissIds = mutableSetOf<Long>()

    init {
        // 云端歌单列表：先画磁盘缓存，再由网络刷新覆盖（SWR）。
        bootstrapLibraryListsCache()

        // 本地歌单
        viewModelScope.launch {
            if (!localRepo.awaitInitialized()) return@launch
            localRepo.playlists.collect { list ->
                _uiState.value = _uiState.value.copy(localPlaylists = list)
            }
        }

        // 网易云 歌单
        viewModelScope.launch {
            neteaseCookieRepo.cookieFlow.collect { cookies ->
                val mutable = cookies.toMutableMap()
                mutable.putIfAbsent("os", "pc")
                if (!cookies["MUSIC_U"].isNullOrBlank()) {
                    refreshNeteasePlaylists()
                } else {
                    _uiState.value = _uiState.value.copy(
                        neteasePlaylists = emptyList(),
                        neteaseError = null
                    )
                }
            }
        }
        // 网易云 专辑
        viewModelScope.launch {
            neteaseCookieRepo.cookieFlow.collect { cookies ->
                val mutable = cookies.toMutableMap()
                mutable.putIfAbsent("os", "pc")
                if (!cookies["MUSIC_U"].isNullOrBlank()) {
                    refreshNeteaseAlbums()
                } else {
                    _uiState.value = _uiState.value.copy(
                        neteaseAlbums = emptyList(),
                        neteaseError = null
                    )
                }
            }
        }

        // YouTube Music
        viewModelScope.launch {
            combine(
                youtubeAuthRepo.authFlow,
                AppContainer.settingsRepo.youtubeEnabledFlow
            ) { bundle, enabled ->
                bundle to enabled
            }.collect { (bundle, enabled) ->
                youtubeEnabled = enabled
                val nextFingerprint = bundle.buildRefreshObserverFingerprint()
                val authChanged = nextFingerprint != lastYouTubeAuthFingerprint
                val enabledChanged = enabled != lastObservedYouTubeEnabled
                if (!authChanged && !enabledChanged) {
                    return@collect
                }
                lastYouTubeAuthFingerprint = nextFingerprint
                lastObservedYouTubeEnabled = enabled
                if (!enabled || !bundle.hasYouTubeMusicCookieContext()) {
                    youtubeMusicPlaylistsJob?.cancel()
                    youtubeMusicPlaylistsJob = null
                    youtubeMusicPlaylistsPending = false
                    _uiState.value = _uiState.value.copy(
                        youtubeMusicPlaylists = emptyList(),
                        youtubeMusicError = null
                    )
                } else {
                    refreshYouTubeMusicPlaylists()
                }
            }
        }

        // Bilibili
        viewModelScope.launch {
            biliCookieRepo.cookieFlow.collect { cookies ->
                if (!cookies["SESSDATA"].isNullOrBlank()) {
                    refreshBilibili()
                } else {
                    _uiState.value = _uiState.value.copy(
                        biliPlaylists = emptyList(),
                        biliError = null
                    )
                }
            }
        }

        // QQ Music 用户歌单（只读；歌单曲目仍通过现有外部导入链路落地到本地）
        viewModelScope.launch {
            qqMusicCookieRepo.authFlow.collect { auth ->
                if (auth.hasLoginCookies()) {
                    refreshQQMusicPlaylists()
                } else {
                    _uiState.value = _uiState.value.copy(
                        qqMusicPlaylists = emptyList(),
                        qqMusicError = null
                    )
                }
            }
        }
    }

    /** 冷启动时先恢复上次成功拉取的云端歌单列表，避免弱网/离线直接空白。 */
    private fun bootstrapLibraryListsCache() {
        if (listsCacheBootstrapped) return
        listsCacheBootstrapped = true
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = listsCacheRepo.read() ?: return@launch
            val plan = shouldApplyLibraryListCache(
                snapshot = snapshot,
                neteaseFingerprint = neteaseFingerprint(),
                youtubeFingerprint = youtubeFingerprint(),
                biliFingerprint = biliFingerprint(),
                qqMusicFingerprint = qqMusicFingerprint()
            )
            if (plan.isEmpty) return@launch
            val neteaseLoggedIn = neteaseCookieRepo.getCookiesOnce()["MUSIC_U"].isNullOrBlank().not()
            val biliLoggedIn = biliCookieRepo.getCookiesOnce()["SESSDATA"].isNullOrBlank().not()
            val qqLoggedIn = qqMusicCookieRepo.getAuthBundleOnce().hasLoginCookies()
            val ytLoggedIn = youtubeAuthRepo.getAuthOnce().hasSavedAuthMaterial()
            val restoredNeteasePlaylists = plan.neteasePlaylists.map { it.toUiModel() }
                .takeIf { neteaseLoggedIn }.orEmpty()
            val restoredNeteaseAlbums = plan.neteaseAlbums.map { it.toUiModel() }
                .takeIf { neteaseLoggedIn }.orEmpty()
            val restoredYtPlaylists = plan.youtubeMusicPlaylists.map { it.toUiModel() }
                .takeIf { ytLoggedIn && youtubeEnabled }.orEmpty()
            val restoredBiliPlaylists = plan.biliPlaylists.mapNotNull { it.toUiModel() }
                .takeIf { biliLoggedIn }.orEmpty()
            val restoredQqPlaylists = plan.qqMusicPlaylists.map { it.toUiModel() }
                .takeIf { qqLoggedIn }.orEmpty()
            // 只填充「当前仍为空」的列表，不覆盖已经由网络刷新的新数据。
            _uiState.update { state ->
                state.copy(
                    neteasePlaylists = state.neteasePlaylists.ifEmpty { restoredNeteasePlaylists },
                    neteaseAlbums = state.neteaseAlbums.ifEmpty { restoredNeteaseAlbums },
                    youtubeMusicPlaylists = state.youtubeMusicPlaylists.ifEmpty { restoredYtPlaylists },
                    biliPlaylists = state.biliPlaylists.ifEmpty { restoredBiliPlaylists },
                    qqMusicPlaylists = state.qqMusicPlaylists.ifEmpty { restoredQqPlaylists }
                )
            }
            NPLogger.d(
                "LibraryViewModel",
                "library lists cache applied: netease=${restoredNeteasePlaylists.size}, " +
                    "albums=${restoredNeteaseAlbums.size}, yt=${restoredYtPlaylists.size}, " +
                    "bili=${restoredBiliPlaylists.size}, qq=${restoredQqPlaylists.size}"
            )
        }
    }

    /** 任一平台列表更新后，debounce 写整份快照（各平台携带自己的账号指纹）。 */
    private fun scheduleLibraryListsCacheSave() {
        listsCacheSaveJob?.cancel()
        listsCacheSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(LIBRARY_LISTS_CACHE_SAVE_DEBOUNCE_MS)
            val state = _uiState.value
            val snapshot = LibraryListsCacheSnapshot(
                savedAtMs = System.currentTimeMillis(),
                neteaseFingerprint = neteaseFingerprint(),
                neteasePlaylists = state.neteasePlaylists.map { it.toCacheDto() },
                neteaseAlbums = state.neteaseAlbums.map { it.toCacheDto() },
                youtubeFingerprint = youtubeFingerprint(),
                youtubeMusicPlaylists = state.youtubeMusicPlaylists.map { it.toCacheDto() },
                biliFingerprint = biliFingerprint(),
                biliPlaylists = state.biliPlaylists.map { it.toCacheDto() },
                qqMusicFingerprint = qqMusicFingerprint(),
                qqMusicPlaylists = state.qqMusicPlaylists.map { it.toCacheDto() }
            )
            withContext(Dispatchers.IO) { listsCacheRepo.save(snapshot) }
        }
    }

    private fun neteaseFingerprint(): String = stableFingerprint(
        "netease",
        neteaseCookieRepo.getCookiesOnce()["MUSIC_U"]
    )

    private fun biliFingerprint(): String = stableFingerprint(
        "bili",
        biliCookieRepo.getCookiesOnce()["DedeUserID"],
        biliCookieRepo.getCookiesOnce()["SESSDATA"]
    )

    private fun qqMusicFingerprint(): String = stableFingerprint(
        "qq",
        qqMusicCookieRepo.getAuthBundleOnce().cookies["uin"]
            ?: qqMusicCookieRepo.getAuthBundleOnce().cookies["wxuin"]
    )

    private fun youtubeFingerprint(): String =
        "yt:" + youtubeAuthRepo.getAuthOnce().buildRefreshObserverFingerprint()

    /** 只取稳定标识位，避免把完整 cookie / token 写进缓存文件。 */
    private fun stableFingerprint(prefix: String, vararg parts: String?): String {
        return prefix + ":" + parts.joinToString("|") { it?.trim().orEmpty() }.hashCode().toString()
    }

    fun refreshBilibili() {
        viewModelScope.launch {
            try {
                val mid = biliCookieRepo.getCookiesOnce()["DedeUserID"]?.toLongOrNull() ?: 0L
                if (mid == 0L) {
                    _uiState.value = _uiState.value.copy(biliError = getApplication<Application>().getString(R.string.error_get_user_id))
                    return@launch
                }
                val mapped = withContext(Dispatchers.IO) {
                    val created = async { biliClient.getUserCreatedFavFolders(mid) }
                    val collected = async {
                        runCatching { biliClient.getUserCollectedFavFolders(mid) }
                            .onFailure { error ->
                                NPLogger.w(
                                    "LibraryViewModel-Bili",
                                    "Failed to fetch collected fav folders",
                                    error
                                )
                            }
                            .getOrDefault(emptyList())
                    }

                    val createdPlaylists = mapBiliFolders(
                        folders = created.await(),
                        kind = BiliPlaylistKind.CREATED_FAVORITE,
                        currentMid = mid
                    )
                    val collectedPlaylists = mapBiliFolders(
                        folders = collected.await(),
                        kind = BiliPlaylistKind.COLLECTED_FAVORITE,
                        currentMid = mid
                    )

                    (createdPlaylists + collectedPlaylists)
                        .filter { it.mediaId != 0L && it.title.isNotBlank() }
                        .distinctBy { "${it.kind}:${it.mediaId}" }
                }

                NPLogger.d("LibraryViewModel-Bili", mapped)

                _uiState.update { it.copy(biliPlaylists = mapped, biliError = null) }
                scheduleLibraryListsCacheSave()
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            }
        }
    }

    private suspend fun mapBiliFolders(
        folders: List<BiliClient.FavFolder>,
        kind: BiliPlaylistKind,
        currentMid: Long
    ): List<BiliPlaylist> = coroutineScope {
        folders
            .filter { it.state == 0 && it.mediaId != 0L }
            .chunked(BILI_DETAIL_BATCH_SIZE)
            .flatMap { batch ->
                batch.map { folder ->
                    async(Dispatchers.IO) {
                        mapBiliFolder(folder, kind, currentMid)
                    }
                }.awaitAll()
            }
            .filterNotNull()
    }

    private suspend fun mapBiliFolder(
        folder: BiliClient.FavFolder,
        kind: BiliPlaylistKind,
        currentMid: Long
    ): BiliPlaylist? {
        val resolvedKind = when {
            folder.itemType == BILI_RESOURCE_TYPE_COLLECTION -> BiliPlaylistKind.COLLECTION
            else -> kind
        }

        val detail = if (resolvedKind == BiliPlaylistKind.COLLECTION) {
            null
        } else {
            runCatching { biliClient.getFavFolderInfo(folder.mediaId) }
                .onFailure { error ->
                    NPLogger.e(
                        "LibraryViewModel-Bili",
                        getApplication<Application>().getString(R.string.music_get_detail_failed),
                        error
                    )
                }
                .getOrNull()
        }
        val source = detail ?: folder
        val ownerLabel = source.upperName.ifBlank {
            if (source.mid != 0L && source.mid != currentMid) source.mid.toString() else ""
        }

        return BiliPlaylist(
            mediaId = source.mediaId,
            fid = source.fid,
            mid = source.mid,
            title = source.title.takeIf { it.isNotBlank() } ?: return null,
            count = source.count,
            coverUrl = source.coverUrl.replace("http://", "https://"),
            kind = resolvedKind,
            subtitle = ownerLabel
        )
    }


    fun refreshNeteasePlaylists() {
        viewModelScope.launch {
            try {
                val uid = withContext(Dispatchers.IO) { neteaseClient.getCurrentUserId() }
                val raw = withContext(Dispatchers.IO) { neteaseClient.getUserPlaylists(uid) }
                val mapped = parseNeteasePlaylists(raw)
                _uiState.update { it.copy(neteasePlaylists = mapped, neteaseError = null) }
                scheduleLibraryListsCacheSave()
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            }
        }
    }
    
    fun refreshNeteaseAlbums() {
        viewModelScope.launch {
            try {
                val uid = withContext(Dispatchers.IO) { neteaseClient.getCurrentUserId() }
                val raw = withContext(Dispatchers.IO) { neteaseClient.getUserStaredAlbums(uid) }
                val mapped = parseNeteaseAlbums(raw)
                _uiState.update { it.copy(neteaseAlbums = mapped, neteaseError = null) }
                scheduleLibraryListsCacheSave()
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            }
        }
    }

    fun refreshYouTubeMusicPlaylists() {
        if (!youtubeEnabled) return
        val runningJob = youtubeMusicPlaylistsJob
        if (runningJob?.isActive == true) {
            youtubeMusicPlaylistsPending = true
            NPLogger.d("LibraryViewModel", "refreshYouTubeMusicPlaylists coalesced while loading")
            return
        }
        youtubeMusicPlaylistsPending = false
        youtubeMusicPlaylistsJob = viewModelScope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    AppContainer.youtubeMusicClient.getLibraryPlaylists(
                        resolveMissingTrackCounts = false
                    )
                }
                _uiState.update {
                    it.copy(
                        youtubeMusicPlaylists = playlists.map(::mapYouTubeMusicPlaylist),
                        youtubeMusicError = null
                    )
                }
                scheduleLibraryListsCacheSave()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // 弱网/离线时保留已恢复的缓存列表，不要清空成空白。
                _uiState.update { it.copy(youtubeMusicError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(youtubeMusicError = e.message) }
            } finally {
                val completedJob = coroutineContext[Job]
                if (youtubeMusicPlaylistsJob === completedJob) {
                    youtubeMusicPlaylistsJob = null
                    if (youtubeMusicPlaylistsPending && youtubeEnabled) {
                        youtubeMusicPlaylistsPending = false
                        refreshYouTubeMusicPlaylists()
                    }
                }
            }
        }
    }

    fun refreshQQMusicPlaylists() {
        viewModelScope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    qqMusicClient.getUserCreatedPlaylists()
                }
                _uiState.update { it.copy(qqMusicPlaylists = playlists, qqMusicError = null) }
                scheduleLibraryListsCacheSave()
                autoImportQQMusicPlaylists(playlists)
            } catch (error: Exception) {
                // 弱网/离线时保留已恢复的缓存列表，不要清空成空白。
                _uiState.update { it.copy(qqMusicError = error.message) }
            }
        }
    }

    /** 登录后的云端歌单落地为 QQ 音乐来源的本地歌单，播放统一走用户配置的在线音源。 */
    private suspend fun autoImportQQMusicPlaylists(playlists: List<QQMusicPlaylistSummary>) {
        val existingByName = localRepo.playlists.value
            .filter { playlist -> playlist.songs.any { it.channelId.equals("qqmusic", true) } }
            .associateBy { it.name.trim() }
        playlists.forEach { playlist ->
            val existing = existingByName[playlist.name.trim()]
            val expectedCount = playlist.songCount.coerceAtMost(MAX_IMPORT_SONGS)
            val alreadyComplete = existing != null &&
                (expectedCount <= 0 || existing.songs.size >= expectedCount)
            if (playlist.dissId in autoImportedQQMusicDissIds || alreadyComplete) {
                return@forEach
            }
            val result = importExternalPlaylist("https://y.qq.com/n/ryqq/playlist/${playlist.dissId}")
            if (result is moe.ouom.neriplayer.data.local.playlist.importer.ExternalPlaylistImportResult.Success) {
                autoImportedQQMusicDissIds += playlist.dissId
            }
        }
    }

    fun createLocalPlaylist(name: String) {
        launchPlaylistMutation("createLocalPlaylist") { localRepo.createPlaylist(name) }
    }

    suspend fun importExternalPlaylist(rawLink: String): moe.ouom.neriplayer.data.local.playlist.importer.ExternalPlaylistImportResult {
        val service = moe.ouom.neriplayer.data.local.playlist.importer.ExternalPlaylistImportService(
            context = getApplication()
        )
        return service.importFromText(rawLink)
    }

    fun addSongToFavorites(song: SongItem) {
        launchPlaylistMutation("addSongToFavorites") { localRepo.addToFavorites(song) }
    }

    fun renameLocalPlaylist(playlistId: Long, newName: String) {
        launchPlaylistMutation("renameLocalPlaylist") {
            localRepo.renamePlaylist(playlistId, newName)
        }
    }

    fun deleteLocalPlaylist(
        playlistId: Long,
        onResult: (Result<List<LocalPlaylistDeleteResult>>) -> Unit = {}
    ) {
        deleteLocalPlaylists(listOf(playlistId), onResult)
    }

    fun deleteLocalPlaylists(
        playlistIds: List<Long>,
        onResult: (Result<List<LocalPlaylistDeleteResult>>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(
                runLocalPlaylistMutationSafely("deleteLocalPlaylists") {
                    localRepo.deletePlaylistsWithResult(playlistIds)
                }
            )
        }
    }

    fun reorderLocalPlaylists(order: List<Long>) {
        launchPlaylistMutation("reorderLocalPlaylists") { localRepo.reorderPlaylists(order) }
    }

    private fun launchPlaylistMutation(
        operation: String,
        mutation: suspend () -> Unit
    ) {
        viewModelScope.launch {
            runLocalPlaylistMutationSafely(operation, mutation)
        }
    }

    private fun parseNeteasePlaylists(raw: String): List<PlaylistSummary> {
        val result = mutableListOf<PlaylistSummary>()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val size = arr.length()
        for (i in 0 until size) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", "")
            val cover = obj.optString("coverImgUrl", "").replaceFirst("http://", "https://")
            val playCount = obj.optLong("playCount", 0L)
            val trackCount = obj.optInt("trackCount", 0)
            if (id != 0L && name.isNotBlank()) {
                result.add(PlaylistSummary(id, name, cover, playCount, trackCount))
            }
        }
        return result
    }
    
    private fun parseNeteaseAlbums(raw: String): List<AlbumSummary> {
        val result = mutableListOf<AlbumSummary>()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val size = arr.length()
        for (i in 0 until size) {
            val obj = arr.optJSONObject(i)?.optJSONObject("dataInfo")?.optJSONObject("data") ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", "")
            val cover = arr.optJSONObject(i)?.optJSONObject("dataInfo")?.optString("picUrl", "")?.replaceFirst("http://", "https://") ?: continue
            val songSize = obj.optInt("size", 0)
            if (id != 0L && name.isNotBlank()) {
                result.add(AlbumSummary(id, name, cover, songSize))
            }
        }
        return result
    }

    private fun moe.ouom.neriplayer.data.auth.youtube.YouTubeAuthBundle.hasYouTubeMusicCookieContext(): Boolean {
        return hasSavedAuthMaterial()
    }

    private fun mapYouTubeMusicPlaylist(
        playlist: YouTubeMusicLibraryPlaylist
    ): YouTubeMusicPlaylist {
        return YouTubeMusicPlaylist(
            browseId = playlist.browseId,
            playlistId = playlist.playlistId,
            title = playlist.title,
            subtitle = playlist.subtitle,
            coverUrl = playlist.coverUrl,
            trackCount = playlist.trackCount ?: 0
        )
    }
}
