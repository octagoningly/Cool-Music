package moe.ouom.neriplayer.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsCachePolicyTest {

    private fun neteaseSnapshot(
        accountContext: String = "public-v1",
        hasSongs: Boolean = true
    ): HomeFeedCacheSnapshot {
        return HomeFeedCacheSnapshot(
            savedAtMs = 1L,
            neteaseAccountContext = accountContext,
            youtubeAuthFingerprint = "yt-a",
            useYouTubeHome = false,
            hasLogin = false,
            radarSongSections = if (hasSongs) {
                listOf(
                    CachedSongSectionDto(
                        source = "PERSONAL_RADAR",
                        items = listOf(CachedSongDto(id = 1L, name = "song"))
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun youtubeSnapshot(
        authFingerprint: String = "yt-a",
        hasPlaylists: Boolean = true
    ): HomeFeedCacheSnapshot {
        return HomeFeedCacheSnapshot(
            savedAtMs = 1L,
            neteaseAccountContext = "public-v1",
            youtubeAuthFingerprint = authFingerprint,
            useYouTubeHome = true,
            hasLogin = false,
            ytMusicPlaylists = if (hasPlaylists) {
                listOf(CachedYtPlaylistDto(browseId = "VLPL1", playlistId = "PL1", title = "mix"))
            } else {
                emptyList()
            }
        )
    }

    @Test
    fun `applies matching netease home cache`() {
        assertTrue(
            shouldApplyHomeFeedCache(
                snapshot = neteaseSnapshot(),
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-a",
                useYouTubeHome = false
            )
        )
    }

    @Test
    fun `rejects cache from another netease account`() {
        assertFalse(
            shouldApplyHomeFeedCache(
                snapshot = neteaseSnapshot(accountContext = "account-sha256-v1:dead"),
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-a",
                useYouTubeHome = false
            )
        )
    }

    @Test
    fun `rejects youtube cache when auth fingerprint changes`() {
        assertFalse(
            shouldApplyHomeFeedCache(
                snapshot = youtubeSnapshot(authFingerprint = "yt-old"),
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-new",
                useYouTubeHome = true
            )
        )
    }

    @Test
    fun `rejects mode mismatch between cache and current home source`() {
        assertFalse(
            shouldApplyHomeFeedCache(
                snapshot = neteaseSnapshot(),
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-a",
                useYouTubeHome = true
            )
        )
    }

    @Test
    fun `rejects empty cache payload`() {
        assertFalse(
            shouldApplyHomeFeedCache(
                snapshot = neteaseSnapshot(hasSongs = false),
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-a",
                useYouTubeHome = false
            )
        )
    }

    @Test
    fun `rejects null cache`() {
        assertFalse(
            shouldApplyHomeFeedCache(
                snapshot = null,
                neteaseAccountContext = "public-v1",
                youtubeAuthFingerprint = "yt-a",
                useYouTubeHome = false
            )
        )
    }

    @Test
    fun `explore grid returns tag bucket`() {
        val snapshot = ExploreGridCacheSnapshot(
            grids = mapOf(
                "tag_all" to listOf(CachedPlaylistDto(id = 1L, name = "a")),
                "tag_acg" to listOf(CachedPlaylistDto(id = 2L, name = "b"))
            )
        )
        assertEquals(1, exploreGridFromCache(snapshot, "tag_all").size)
        assertEquals(2L, exploreGridFromCache(snapshot, "tag_acg").first().id)
        assertTrue(exploreGridFromCache(snapshot, "tag_missing").isEmpty())
    }

    @Test
    fun `keeps previous items when refresh fails with content`() {
        val (items, error) = mergeSectionAfterFailure(
            previousItems = listOf("cached"),
            error = "network down"
        )
        assertEquals(listOf("cached"), items)
        assertEquals(null, error)
    }

    @Test
    fun `exposes error when refresh fails with empty list`() {
        val (items, error) = mergeSectionAfterFailure(
            previousItems = emptyList<String>(),
            error = "network down"
        )
        assertTrue(items.isEmpty())
        assertEquals("network down", error)
    }

    @Test
    fun `applies explore yt library cache only with matching fingerprint`() {
        val snapshot = ExploreYtLibraryCacheSnapshot(
            youtubeAuthFingerprint = "yt-a",
            playlists = listOf(CachedYtPlaylistDto(browseId = "VL1", title = "lib"))
        )
        assertTrue(shouldApplyExploreYtLibraryCache(snapshot, "yt-a"))
        assertFalse(shouldApplyExploreYtLibraryCache(snapshot, "yt-b"))
        assertFalse(shouldApplyExploreYtLibraryCache(null, "yt-a"))
    }
}
