package moe.ouom.neriplayer.core.player.cache

import moe.ouom.neriplayer.data.platform.youtube.extractYouTubeMusicVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedSongsPlaylistTest {
    @Test
    fun `legacy Netease keys retain the song id`() {
        assertEquals(123L, legacySongFromKey("netease-123-exhigh")?.id)
        assertEquals(123L, legacySongFromKey("netease-123-exhigh-fallback-v1")?.id)
        assertEquals(123L, legacySongFromKey("netease-preview-v1-123-standard")?.id)
        assertEquals(123L, legacySongFromKey("lx-123-lossless")?.id)
        assertEquals(123L, legacySongFromKey("lxjs-source-9-wy-123-flac")?.id)
        assertEquals("ID 123", legacySongFromKey("lxjs-source-9-kw-123-flac")?.name)
        assertNull(legacySongFromKey("lx-123-456-flac"))
    }

    @Test
    fun `legacy Bili key retains aid and cid for playback`() {
        val song = legacySongFromKey("bili-123-456-hires")
        assertEquals(123L, song?.id)
        assertEquals("456", song?.subAudioId)
        assertEquals("Bilibili|456", song?.album)
    }

    @Test
    fun `legacy YouTube key retains video id with hyphen`() {
        val song = legacySongFromKey("ytmusic-ab-cd123-high-stable-m4a")
        assertEquals("ab-cd123", extractYouTubeMusicVideoId(song?.mediaUri))
        assertEquals("ab-cd123", song?.audioId)
    }

    @Test
    fun `old LX and Bili fallback keys can be listed without fabricating a song match`() {
        val lx = legacySongFromKey("lxjs-source-9-qq-ABC123-flac")!!
        val bili = legacySongFromKey("bili-auto-BV1ABC123-456-id-3")!!
        assertEquals("ID ABC123", lx.name)
        assertEquals("LX QQ", lx.artist)
        assertEquals("ID BV1ABC123", bili.name)
        assertEquals("Bilibili Cache", bili.album)
    }

    @Test
    fun `unrelated keys do not become songs`() {
        assertNull(legacySongFromKey("local-123"))
        assertNull(legacySongFromKey("random-cache-key"))
    }

    @Test
    fun `unmatched cache keys retain source categories`() {
        assertEquals(CachedUnrecognizedKind.LX, unrecognizedCacheKind("lx-provider-999-flac"))
        assertEquals(CachedUnrecognizedKind.BILI_FALLBACK, unrecognizedCacheKind("bili-auto-BV1-2-id-3"))
        assertEquals(CachedUnrecognizedKind.DIRECT_URL, unrecognizedCacheKind("https://example.com/audio"))
    }

    @Test
    fun `LX JS cache prefers a named history song over a placeholder`() {
        val placeholder = legacySongFromKey("lxjs-source-wy-123-flac")!!
        val named = placeholder.copy(name = "Real Song", artist = "Singer")
        assertEquals(
            "Real Song",
            matchingKnownCachedSong("lxjs-source-wy-123-flac", listOf(placeholder, named))?.name
        )
    }
}
