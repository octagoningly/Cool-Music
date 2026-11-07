package moe.ouom.neriplayer.data.cache

/**
 * 媒体库「云端歌单列表」的磁盘快照 DTO。
 *
 * 与 UI 模型解耦，只保留展示列表必需的文字与封面 URL，便于 Gson 序列化与单元测试。
 * 各平台独立存账号指纹，换号后不会把上一个账号的歌单先展示出来。
 */
data class CachedNeteasePlaylistDto(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = "",
    val playCount: Long = 0L,
    val trackCount: Int = 0
)

data class CachedNeteaseAlbumDto(
    val id: Long = 0L,
    val name: String = "",
    val picUrl: String = "",
    val size: Int = 0
)

data class CachedYouTubeMusicPlaylistDto(
    val browseId: String = "",
    val playlistId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val creatorName: String = ""
)

data class CachedBiliPlaylistDto(
    val mediaId: Long = 0L,
    val fid: Long = 0L,
    val mid: Long = 0L,
    val title: String = "",
    val count: Int = 0,
    val coverUrl: String = "",
    val kind: String = "CREATED_FAVORITE",
    val subtitle: String = ""
)

data class CachedQQMusicPlaylistDto(
    val dissId: Long = 0L,
    val name: String = "",
    val coverUrl: String? = null,
    val songCount: Int = 0
)

data class LibraryListsCacheSnapshot(
    val version: Int = VERSION,
    val savedAtMs: Long = 0L,
    val neteaseFingerprint: String = "",
    val neteasePlaylists: List<CachedNeteasePlaylistDto> = emptyList(),
    val neteaseAlbums: List<CachedNeteaseAlbumDto> = emptyList(),
    val youtubeFingerprint: String = "",
    val youtubeMusicPlaylists: List<CachedYouTubeMusicPlaylistDto> = emptyList(),
    val biliFingerprint: String = "",
    val biliPlaylists: List<CachedBiliPlaylistDto> = emptyList(),
    val qqMusicFingerprint: String = "",
    val qqMusicPlaylists: List<CachedQQMusicPlaylistDto> = emptyList()
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * 换号/登出后不复用缓存：每个平台单独校验自己的账号指纹。
 */
fun shouldApplyLibraryListCache(
    snapshot: LibraryListsCacheSnapshot?,
    neteaseFingerprint: String,
    youtubeFingerprint: String,
    biliFingerprint: String,
    qqMusicFingerprint: String
): LibraryListsApplyPlan {
    if (snapshot == null || snapshot.version != LibraryListsCacheSnapshot.VERSION) {
        return LibraryListsApplyPlan.EMPTY
    }
    return LibraryListsApplyPlan(
        neteasePlaylists = if (snapshot.neteaseFingerprint == neteaseFingerprint) {
            snapshot.neteasePlaylists
        } else {
            emptyList()
        },
        neteaseAlbums = if (snapshot.neteaseFingerprint == neteaseFingerprint) {
            snapshot.neteaseAlbums
        } else {
            emptyList()
        },
        youtubeMusicPlaylists = if (snapshot.youtubeFingerprint == youtubeFingerprint) {
            snapshot.youtubeMusicPlaylists
        } else {
            emptyList()
        },
        biliPlaylists = if (snapshot.biliFingerprint == biliFingerprint) {
            snapshot.biliPlaylists
        } else {
            emptyList()
        },
        qqMusicPlaylists = if (snapshot.qqMusicFingerprint == qqMusicFingerprint) {
            snapshot.qqMusicPlaylists
        } else {
            emptyList()
        }
    )
}

data class LibraryListsApplyPlan(
    val neteasePlaylists: List<CachedNeteasePlaylistDto>,
    val neteaseAlbums: List<CachedNeteaseAlbumDto>,
    val youtubeMusicPlaylists: List<CachedYouTubeMusicPlaylistDto>,
    val biliPlaylists: List<CachedBiliPlaylistDto>,
    val qqMusicPlaylists: List<CachedQQMusicPlaylistDto>
) {
    val isEmpty: Boolean
        get() = neteasePlaylists.isEmpty() && neteaseAlbums.isEmpty() &&
            youtubeMusicPlaylists.isEmpty() && biliPlaylists.isEmpty() &&
            qqMusicPlaylists.isEmpty()

    companion object {
        val EMPTY = LibraryListsApplyPlan(
            neteasePlaylists = emptyList(),
            neteaseAlbums = emptyList(),
            youtubeMusicPlaylists = emptyList(),
            biliPlaylists = emptyList(),
            qqMusicPlaylists = emptyList()
        )
    }
}
