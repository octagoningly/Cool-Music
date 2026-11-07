package moe.ouom.neriplayer.data.cache

import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylistKind
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryListsCachePolicyTest {
    private fun snapshot() = LibraryListsCacheSnapshot(
        neteaseFingerprint = "netease-a",
        neteasePlaylists = listOf(CachedNeteasePlaylistDto(id = 1, name = "歌单", picUrl = "https://x/1.jpg")),
        neteaseAlbums = listOf(CachedNeteaseAlbumDto(id = 2, name = "专辑", picUrl = "https://x/2.jpg")),
        youtubeFingerprint = "yt-a",
        youtubeMusicPlaylists = listOf(CachedYouTubeMusicPlaylistDto(title = "YT", coverUrl = "https://x/3.jpg")),
        biliFingerprint = "bili-a",
        biliPlaylists = listOf(CachedBiliPlaylistDto(title = "Bili", kind = "COLLECTION")),
        qqMusicFingerprint = "qq-a",
        qqMusicPlaylists = listOf(CachedQQMusicPlaylistDto(name = "QQ", coverUrl = "https://x/4.jpg"))
    )

    @Test
    fun matchingFingerprintsRestoreAllPlatforms() {
        val plan = shouldApplyLibraryListCache(
            snapshot = snapshot(),
            neteaseFingerprint = "netease-a",
            youtubeFingerprint = "yt-a",
            biliFingerprint = "bili-a",
            qqMusicFingerprint = "qq-a"
        )
        assertEquals(1, plan.neteasePlaylists.size)
        assertEquals(1, plan.neteaseAlbums.size)
        assertEquals(1, plan.youtubeMusicPlaylists.size)
        assertEquals(1, plan.biliPlaylists.size)
        assertEquals(1, plan.qqMusicPlaylists.size)
        assertTrue(!plan.isEmpty)
    }

    @Test
    fun accountChangeOnlyDropsThatPlatform() {
        val plan = shouldApplyLibraryListCache(
            snapshot = snapshot(),
            neteaseFingerprint = "netease-B",
            youtubeFingerprint = "yt-a",
            biliFingerprint = "bili-a",
            qqMusicFingerprint = "qq-a"
        )
        assertTrue(plan.neteasePlaylists.isEmpty())
        assertTrue(plan.neteaseAlbums.isEmpty())
        assertEquals(1, plan.youtubeMusicPlaylists.size)
        assertEquals(1, plan.biliPlaylists.size)
        assertEquals(1, plan.qqMusicPlaylists.size)
    }

    @Test
    fun versionMismatchOrNullDropsEverything() {
        assertTrue(shouldApplyLibraryListCache(null, "a", "b", "c", "d").isEmpty)
        val stale = snapshot().copy(version = LibraryListsCacheSnapshot.VERSION + 1)
        assertTrue(shouldApplyLibraryListCache(stale, "netease-a", "yt-a", "bili-a", "qq-a").isEmpty)
    }

    @Test
    fun coverUrlsSurviveDtoRoundTrip() {
        val netease = PlaylistSummary(1, "n", "https://cover/1", 9, 3)
        assertEquals(netease, netease.toCacheDto().toUiModel())
        val album = AlbumSummary(2, "a", "https://cover/2", 5)
        assertEquals(album, album.toCacheDto().toUiModel())
        val yt = YouTubeMusicPlaylist("b", "p", "t", "s", "https://cover/3", 7, "c")
        assertEquals(yt, yt.toCacheDto().toUiModel())
        val qq = QQMusicPlaylistSummary(3, "q", "https://cover/4", 8)
        assertEquals(qq, qq.toCacheDto().toUiModel())
    }

    @Test
    fun biliKindRoundTripAndInvalidKindRejected() {
        val source = BiliPlaylist(4, 5, 6, "b", 10, "https://cover/5", BiliPlaylistKind.SERIES, "sub")
        assertEquals(source, source.toCacheDto().toUiModel())
        assertEquals(null, CachedBiliPlaylistDto(kind = "NOT_A_KIND").toUiModel())
    }
}
