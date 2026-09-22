package moe.ouom.neriplayer.core.player.resolver.lxmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LxOnlineCollectionSearchTest {
    @Test
    fun `QQ artist and playlist results retain platform identity`() {
        val artist = parseLxOnlineCollectionPage(
            LX_QQ_PLATFORM_ID,
            LxOnlineCollectionType.ARTIST,
            """{"code":0,"data":{"singer":{"totalnum":21,"list":[{"singerMID":"artist-mid","singerName":"Artist","singerPic":"http://example.com/a.jpg","songNum":12}]}}}""",
            page = 1
        )
        val playlist = parseLxOnlineCollectionPage(
            LX_QQ_PLATFORM_ID,
            LxOnlineCollectionType.PLAYLIST,
            """{"code":0,"data":{"sum":3,"list":[{"dissid":"123","dissname":"Mix","imgurl":"http://example.com/p.jpg","song_count":8,"creator":{"name":"DJ"}}]}}""",
            page = 1
        )

        assertTrue(artist.hasMore)
        assertEquals("artist-mid", artist.items.single().id)
        assertEquals("https://example.com/a.jpg", artist.items.single().coverUrl)
        assertFalse(playlist.hasMore)
        assertEquals("tx", playlist.items.single().sourceId)
        assertEquals("DJ", playlist.items.single().creator)
    }

    @Test
    fun `Kuwo single quoted playlist response and Kugou singer array parse`() {
        val kuwo = parseLxOnlineCollectionPage(
            LX_KUWO_PLATFORM_ID,
            LxOnlineCollectionType.PLAYLIST,
            """{'TOTAL':'1','abslist':[{'playlistid':'456','name':'Mix','pic':'http://example.com/p.jpg','songnum':'5'}]}""",
            page = 1
        )
        val kugou = parseLxOnlineCollectionPage(
            LX_KUGOU_PLATFORM_ID,
            LxOnlineCollectionType.ARTIST,
            """{"errcode":0,"data":[{"singerid":789,"singername":"Singer"}]}""",
            page = 1
        )

        assertEquals("456", kuwo.items.single().id)
        assertEquals(5, kuwo.items.single().trackCount)
        assertEquals("789", kugou.items.single().id)
        assertEquals(LxOnlineCollectionType.ARTIST, kugou.items.single().type)
    }

    @Test
    fun `public Netease results are treated as an optional online engine`() {
        val playlist = parseLxOnlineCollectionPage(
            LX_NETEASE_PLATFORM_ID,
            LxOnlineCollectionType.PLAYLIST,
            """{"code":200,"result":{"playlistCount":1,"playlists":[{"id":77,"name":"Mix","trackCount":12,"coverImgUrl":"https://example.com/p.jpg"}]}}""",
            page = 1
        )

        assertEquals("wy", playlist.items.single().sourceId)
        assertEquals("77", playlist.items.single().id)
        assertEquals(12, playlist.items.single().trackCount)
        assertFalse(playlist.hasMore)
    }

    @Test
    fun `playlist tracks keep playable LX IDs and Kugou quality hash`() {
        val qq = parseQqPlaylistSongs(
            """{"cdlist":[{"song_begin":0,"total_song_num":2,"songlist":[{"mid":"song-mid","title":"Song","singer":[{"name":"Singer"}],"album":{"mid":"album-mid","name":"Album"},"interval":180}]}]}"""
        )
        val kugou = parseKugouPlaylistSongs(
            """{"data":{"total":101,"info":[{"audio_id":42,"hash":"LOW","320hash":"HIGH","filename":"Singer - Song","duration":180}]}}""",
            page = 1
        )

        assertEquals("lx:tx", qq.songs.single().channelId)
        assertEquals("song-mid", qq.songs.single().audioId)
        assertTrue(qq.hasMore)
        assertEquals("lx:kg", kugou.songs.single().channelId)
        assertEquals("42", kugou.songs.single().audioId)
        assertTrue(kugou.songs.single().subAudioId.orEmpty().contains("HIGH"))
        assertTrue(kugou.hasMore)
    }

    @Test
    fun `Kuwo playlist tracks retain their own IDs`() {
        val page = parseKuwoPlaylistSongs(
            """{"pn":0,"total":2,"musiclist":[{"id":"456","name":"Song","artist":"Singer","duration":"180","album":"Album"}]}"""
        )

        assertEquals("lx:kw", page.songs.single().channelId)
        assertEquals("456", page.songs.single().audioId)
        assertTrue(page.hasMore)
    }

    @Test
    fun `public Netease song search keeps duration and artist`() {
        val hits = parseNeteaseSongHits(
            """{"code":200,"result":{"songs":[{"id":42,"name":"Song","duration":183000,"artists":[{"name":"Singer"}],"album":{"name":"Album"}}]}}"""
        )

        assertEquals("42", hits.single().songMid)
        assertEquals("Singer", hits.single().artist)
        assertEquals(183, hits.single().durationSec)
    }
}
