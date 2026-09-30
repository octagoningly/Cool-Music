package moe.ouom.neriplayer.ui.viewmodel.tab

import moe.ouom.neriplayer.data.cache.CachedSongDto
import moe.ouom.neriplayer.data.cache.HomeFeedCacheSnapshot
import moe.ouom.neriplayer.data.cache.toCachedSongDto
import moe.ouom.neriplayer.data.cache.toSongItem
import moe.ouom.neriplayer.data.model.NeteaseArtistSummary
import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsCacheMappingTest {

    private fun sampleSong() = SongItem(
        id = 42L,
        name = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        albumId = 7L,
        durationMs = 269_000L,
        coverUrl = "https://example.com/cover.jpg",
        channelId = "netease",
        audioId = "42",
        neteaseArtists = listOf(NeteaseArtistSummary(id = 1L, name = "周杰伦"))
    )

    @Test
    fun `song dto round trip keeps identity fields`() {
        val dto = sampleSong().toCachedSongDto()
        val restored = dto.toSongItem()
        assertEquals(sampleSong().id, restored.id)
        assertEquals(sampleSong().name, restored.name)
        assertEquals(sampleSong().artist, restored.artist)
        assertEquals(sampleSong().coverUrl, restored.coverUrl)
        assertEquals(sampleSong().audioId, restored.audioId)
        assertEquals(1, restored.neteaseArtists?.size)
        assertEquals("周杰伦", restored.neteaseArtists?.first()?.name)
    }

    @Test
    fun `playlist section dto maps source by enum name`() {
        val section = HomeNeteasePlaylistSectionState(
            source = NeteaseHomePlaylistSource.HIGH_QUALITY,
            section = HomeSectionState(
                items = listOf(
                    PlaylistSummary(
                        id = 9L,
                        name = "精品",
                        picUrl = "https://example.com/p.jpg",
                        playCount = 100L,
                        trackCount = 20
                    )
                )
            )
        )
        val dto = section.toCachedPlaylistSectionDto()
        assertEquals("HIGH_QUALITY", dto.source)
        val restored = dto.toHomeNeteasePlaylistSectionState()
        assertEquals(NeteaseHomePlaylistSource.HIGH_QUALITY, restored?.source)
        assertEquals("精品", restored?.section?.items?.firstOrNull()?.name)
    }

    @Test
    fun `unknown section source is dropped instead of crashing`() {
        val restored = moe.ouom.neriplayer.data.cache.CachedSongSectionDto(
            source = "NOT_A_SOURCE",
            items = listOf(CachedSongDto(id = 1L, name = "x"))
        ).toHomeNeteaseSongSectionState()
        assertEquals(null, restored)
    }

    @Test
    fun `home ui state snapshot round trip fills sections`() {
        val state = HomeUiState(
            playlistSections = listOf(
                HomeNeteasePlaylistSectionState(
                    source = NeteaseHomePlaylistSource.PERSONALIZED,
                    section = HomeSectionState(
                        items = listOf(
                            PlaylistSummary(1L, "推荐", "pic", 1L, 2)
                        )
                    )
                )
            ),
            trendingSongSections = emptyList(),
            radarSongSections = listOf(
                HomeNeteaseSongSectionState(
                    source = NeteaseHomeSongSource.PERSONAL_RADAR,
                    section = HomeSectionState(items = listOf(sampleSong()))
                )
            ),
            radarPlaylists = HomeSectionState(
                items = listOf(PlaylistSummary(5L, "时光雷达", "", 0L, 0))
            ),
            hasLogin = true
        )
        val snapshot = state.toHomeFeedCacheSnapshot(
            neteaseAccountContext = "public-v1",
            youtubeAuthFingerprint = "yt-a",
            savedAtMs = 10L
        )
        val restored = HomeUiState().withHomeFeedCache(snapshot)
        assertEquals(1, restored.playlistSections.first().section.items.size)
        assertEquals("推荐", restored.playlistSections.first().section.items.first().name)
        assertEquals(1, restored.radarSongSections.first().section.items.size)
        assertEquals("晴天", restored.radarSongSections.first().section.items.first().name)
        assertEquals("时光雷达", restored.radarPlaylists.items.first().name)
        assertTrue(restored.radarSongSections.first().section.loading.not())
    }
}
