package moe.ouom.neriplayer.data.local.playlist.importer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QQMusicPlaylistPaginationTest {

    @Test
    fun requestIncludesOffsetAndPageSizeInsteadOfRelyingOnThirtySongDefault() {
        val root = JSONObject(buildQQMusicPlaylistRequestData(123456L, songBegin = 30))
        val request = root.getJSONObject("playlist")
        val param = request.getJSONObject("param")

        assertEquals("music.srfDissInfo.aiDissInfo", request.getString("module"))
        assertEquals("uniform_get_Dissinfo", request.getString("method"))
        assertEquals(123456L, param.getLong("disstid"))
        assertEquals(30, param.getInt("song_begin"))
        assertEquals(30, param.getInt("song_num"))
    }

    @Test
    fun parserReadsTotalAndHasMoreFromPagedResponse() {
        val page = parseQQMusicPlaylistPage(
            """{
              "playlist": {
                "code": 0,
                "data": {
                  "dirinfo": {"title": "长歌单", "songnum": 65},
                  "total_song_num": 65,
                  "hasmore": 1,
                  "songlist": [
                    {"songmid": "mid-31", "songname": "Song 31"},
                    {"songmid": "mid-32", "songname": "Song 32"}
                  ]
                }
              }
            }""".trimIndent()
        )

        requireNotNull(page)
        assertEquals("长歌单", page.name)
        assertEquals(65, page.totalSongCount)
        assertTrue(page.hasMore == true)
        assertEquals(listOf("mid-31", "mid-32"), page.songs.map { it.getString("songmid") })
    }

    @Test
    fun parserSupportsAlternateEnvelopeAndBooleanEndFlag() {
        val page = parseQQMusicPlaylistPage(
            """{"req_0":{"data":{"hasmore":false,"songlist":[]}}}"""
        )

        requireNotNull(page)
        assertFalse(page.hasMore ?: true)
        assertTrue(page.songs.isEmpty())
        assertEquals(0, page.totalSongCount)
        assertNull(parseQQMusicPlaylistPage("not-json"))
    }
}
