package moe.ouom.neriplayer.core.player.resolver.lxmusic

import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LxDefaultSearchTest {
    @Test
    fun `platform identity survives queue song fields without treating id as netease`() {
        val kuwo = toLxSongItem(hit("kw", "12345"))
        val kugou = toLxSongItem(hit("kg", "12345"))
        val netease = toLxSongItem(hit("wy", "12345"))

        assertEquals("lx:kw", kuwo.channelId)
        assertEquals("12345", kuwo.audioId)
        assertEquals("kw", kuwo.lxSearchHitOrNull()?.sourceId)
        assertNotEquals(kuwo.stableKey(), kugou.stableKey())
        assertNotEquals(kuwo.stableKey(), netease.stableKey())
        assertEquals("wy", netease.lxSearchHitOrNull()?.sourceId)
        assertNull(kuwo.copy(channelId = "netease").lxSearchHitOrNull())
    }

    @Test
    fun `kugou quality hashes survive queue fields and reach direct LX request`() {
        val song = toLxSongItem(
            hit("kg", "20505418").copy(
                qualityHashes = mapOf("128k" to "LOW_HASH", "flac" to "LOSSLESS_HASH")
            )
        )
        val restored = song.copy()
        val parsed = restored.lxSearchHitOrNull()!!
        val info = JSONObject(buildLxCrossPlatformMusicInfoJson(restored, parsed, "flac"))

        assertEquals("kg", info.getString("source"))
        assertEquals("20505418", info.getString("songmid"))
        assertEquals("LOSSLESS_HASH", info.getString("hash"))
        assertEquals("LOW_HASH", info.getJSONObject("_types").getJSONObject("128k").getString("hash"))
        assertFalse(restored.stableKey().isBlank())
    }

    @Test
    fun `LX lyrics never query netease with another platforms id`() {
        val kuwo = toLxSongItem(hit("kw", "12345"))
        val netease = toLxSongItem(hit("wy", "67890"))

        assertNull(kuwo.lxNeteaseLyricsIdOrNull())
        assertEquals(67890L, netease.lxNeteaseLyricsIdOrNull())
        assertEquals(
            98765L,
            kuwo.copy(
                matchedLyricSource = MusicPlatform.CLOUD_MUSIC,
                matchedSongId = "98765"
            ).lxNeteaseLyricsIdOrNull()
        )
    }

    private fun hit(platform: String, songMid: String) = LxCrossPlatformHit(
        sourceId = platform,
        songMid = songMid,
        name = "晴天",
        artist = "周杰伦",
        durationSec = 269,
        albumName = "叶惠美"
    )
}
