package moe.ouom.neriplayer.data.cache

import android.content.Context
import com.google.gson.Gson
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlaylistSummary
import moe.ouom.neriplayer.core.logging.NPLogger
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylistKind
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import java.io.File

/**
 * 媒体库云端歌单列表的磁盘缓存（SWR：先展示后刷新）。
 *
 * 之前四平台的用户歌单列表只存在内存，冷启动/弱网下直接空白；
 * 现在持久化到 filesDir，进程重启后可立即恢复文字与封面 URL。
 */
class LibraryListsCacheRepository internal constructor(
    private val cacheDir: File
) {
    private val gson = Gson()

    constructor(context: Context) : this(
        cacheDir = File(context.applicationContext.filesDir, CACHE_DIR_NAME)
    )

    fun read(): LibraryListsCacheSnapshot? {
        val file = File(cacheDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, LibraryListsCacheSnapshot::class.java)
            }
        }.onFailure { error ->
            NPLogger.w(TAG, "Failed to read library lists cache", error)
        }.getOrNull()
    }

    fun save(snapshot: LibraryListsCacheSnapshot) {
        runCatching {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, FILE_NAME)
            val tmp = File(cacheDir, "$FILE_NAME.tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                gson.toJson(snapshot, writer)
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { error ->
            NPLogger.w(TAG, "Failed to write library lists cache", error)
        }
    }

    fun clear() {
        runCatching { File(cacheDir, FILE_NAME).delete() }
    }

    companion object {
        private const val TAG = "NERI-LibraryListsCache"
        const val CACHE_DIR_NAME = "library_lists_cache"
        const val FILE_NAME = "library_lists.json"
    }
}

// ---- UI 模型 <-> DTO 映射（纯函数，便于单测）----

internal fun PlaylistSummary.toCacheDto() = CachedNeteasePlaylistDto(
    id = id, name = name, picUrl = picUrl, playCount = playCount, trackCount = trackCount
)

internal fun CachedNeteasePlaylistDto.toUiModel() = PlaylistSummary(
    id = id, name = name, picUrl = picUrl, playCount = playCount, trackCount = trackCount
)

internal fun AlbumSummary.toCacheDto() = CachedNeteaseAlbumDto(
    id = id, name = name, picUrl = picUrl, size = size
)

internal fun CachedNeteaseAlbumDto.toUiModel() = AlbumSummary(
    id = id, name = name, picUrl = picUrl, size = size
)

internal fun YouTubeMusicPlaylist.toCacheDto() = CachedYouTubeMusicPlaylistDto(
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    subtitle = subtitle,
    coverUrl = coverUrl,
    trackCount = trackCount,
    creatorName = creatorName
)

internal fun CachedYouTubeMusicPlaylistDto.toUiModel() = YouTubeMusicPlaylist(
    browseId = browseId,
    playlistId = playlistId,
    title = title,
    subtitle = subtitle,
    coverUrl = coverUrl,
    trackCount = trackCount,
    creatorName = creatorName
)

internal fun BiliPlaylist.toCacheDto() = CachedBiliPlaylistDto(
    mediaId = mediaId,
    fid = fid,
    mid = mid,
    title = title,
    count = count,
    coverUrl = coverUrl,
    kind = kind.name,
    subtitle = subtitle
)

internal fun CachedBiliPlaylistDto.toUiModel(): BiliPlaylist? {
    val resolvedKind = runCatching { BiliPlaylistKind.valueOf(kind) }.getOrNull() ?: return null
    return BiliPlaylist(
        mediaId = mediaId,
        fid = fid,
        mid = mid,
        title = title,
        count = count,
        coverUrl = coverUrl,
        kind = resolvedKind,
        subtitle = subtitle
    )
}

internal fun QQMusicPlaylistSummary.toCacheDto() = CachedQQMusicPlaylistDto(
    dissId = dissId, name = name, coverUrl = coverUrl, songCount = songCount
)

internal fun CachedQQMusicPlaylistDto.toUiModel() = QQMusicPlaylistSummary(
    dissId = dissId, name = name, coverUrl = coverUrl, songCount = songCount
)
