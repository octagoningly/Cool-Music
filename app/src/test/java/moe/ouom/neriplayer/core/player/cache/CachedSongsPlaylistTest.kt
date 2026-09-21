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
    fun `unrelated keys do not become songs`() {
        assertNull(legacySongFromKey("local-123"))
        assertNull(legacySongFromKey("random-cache-key"))
    }
}
