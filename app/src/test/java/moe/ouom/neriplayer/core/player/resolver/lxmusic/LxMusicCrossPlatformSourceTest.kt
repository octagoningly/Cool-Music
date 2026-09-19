package moe.ouom.neriplayer.core.player.resolver.lxmusic

import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicCrossPlatformSourceTest
 */

/**
 * 覆盖「用其他平台的曲目 ID 给在线音源取流」这条链路：
 * 解析酷我搜索响应、挑出原版候选、排除伴奏/翻唱等同名异版。
 */
class LxMusicCrossPlatformSourceTest {

    private fun song(
        id: Long = 186016L,
        name: String = "晴天",
        artist: String = "周杰伦",
        durationMs: Long = 269_000L
    ) = SongItem(
        id = id,
        name = name,
        artist = artist,
        album = "叶惠美",
        albumId = 1293L,
        durationMs = durationMs,
        coverUrl = null
    )

    private fun hit(
        songMid: String,
        name: String,
        artist: String,
        durationSec: Int
    ) = LxCrossPlatformHit(
        sourceId = LX_KUWO_PLATFORM_ID,
        songMid = songMid,
        name = name,
        artist = artist,
        durationSec = durationSec,
        albumName = ""
    )

    @Test
    fun `parses standard json response`() {
        val body = """
            {"ARTISTPIC":"","HIT":"6325","abslist":[
              {"AARTIST":"Jay Chou","ALBUM":"叶惠美","ARTIST":"周杰伦","DC_TARGETID":"228908",
               "DURATION":"269","MUSICRID":"MUSIC_228908","SONGNAME":"晴天","PAY":"16711935"},
              {"ARTIST":"周杰伦","DURATION":"249","MUSICRID":"MUSIC_80456317","SONGNAME":"晴天 (Live)"}
            ]}
        """.trimIndent()

        val hits = parseLxKuwoSearchBody(body)

        assertEquals(2, hits.size)
        assertEquals("228908", hits[0].songMid)
        assertEquals("晴天", hits[0].name)
        assertEquals("周杰伦", hits[0].artist)
        assertEquals(269, hits[0].durationSec)
        assertEquals(LX_KUWO_PLATFORM_ID, hits[0].sourceId)
    }

    @Test
    fun `parses legacy single quoted response`() {
        val body = "{'ARTISTPIC':'','abslist':[{'DC_TARGETID':'51685512','MUSICRID':'MUSIC_51685512'," +
            "'NAME':'晴天&nbsp;','ARTIST':'周杰伦','DURATION':'269','ALBUM':'叶惠美'}]}"

        val hits = parseLxKuwoSearchBody(body)

        assertEquals(1, hits.size)
        assertEquals("51685512", hits[0].songMid)
        assertEquals("晴天", hits[0].name)
        assertEquals(269, hits[0].durationSec)
    }

    @Test
    fun `parsing ignores malformed body`() {
        assertTrue(parseLxKuwoSearchBody("").isEmpty())
        assertTrue(parseLxKuwoSearchBody("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(parseLxKuwoSearchBody("{\"abslist\":[]}").isEmpty())
    }

    @Test
    fun `strips version suffix from title`() {
        assertEquals("晴天", normalizeLxCrossPlatformTitle("晴天 (Live)"))
        assertEquals("晴天", normalizeLxCrossPlatformTitle("晴天（KTV版）"))
        assertEquals("夜空中最亮的星", normalizeLxCrossPlatformTitle("夜空中最亮的星 [Remastered]"))
    }

    @Test
    fun `instrumental and cover variants are rejected`() {
        val target = song()
        assertEquals(0, scoreLxCrossPlatformHit(target, hit("1", "晴天 (KTV版伴奏)", "周杰伦", 269)))
        assertEquals(0, scoreLxCrossPlatformHit(target, hit("2", "晴天 (伴奏)", "周杰伦", 269)))
        assertEquals(0, scoreLxCrossPlatformHit(target, hit("3", "晴天 (纯音乐)", "周杰伦", 269)))
        assertEquals(0, scoreLxCrossPlatformHit(target, hit("4", "晴天 (翻唱版)", "周杰伦", 269)))
    }

    @Test
    fun `original version outranks live version`() {
        val target = song()
        val original = scoreLxCrossPlatformHit(target, hit("228908", "晴天", "周杰伦", 269))
        val live = scoreLxCrossPlatformHit(target, hit("80456317", "晴天 (Live)", "周杰伦", 249))

        assertTrue("original=$original live=$live", original > live)
        assertEquals(
            "228908",
            selectLxCrossPlatformHit(target, listOf(hit("80456317", "晴天 (Live)", "周杰伦", 249), hit("228908", "晴天", "周杰伦", 269)))?.songMid
        )
    }

    @Test
    fun `cover by another artist is rejected`() {
        val target = song()
        val cover = hit("9", "晴天", "青崖", 329)
        val original = hit("228908", "晴天", "周杰伦", 269)

        assertTrue(scoreLxCrossPlatformHit(target, cover) < LX_CROSS_PLATFORM_MIN_SCORE)
        assertEquals("228908", selectLxCrossPlatformHit(target, listOf(cover, original))?.songMid)
    }

    @Test
    fun `duration mismatch beyond tolerance is rejected`() {
        val target = song()
        // 同名同歌手，但时长差了 2 分钟（串烧/演唱会版）
        val remix = hit("5", "晴天", "周杰伦", 506)

        assertTrue(scoreLxCrossPlatformHit(target, remix) < LX_CROSS_PLATFORM_MIN_SCORE)
        assertNull(selectLxCrossPlatformHit(target, listOf(remix)))
    }

    @Test
    fun `no acceptable candidate returns null`() {
        val target = song()
        assertNull(selectLxCrossPlatformHit(target, emptyList()))
        assertNull(
            selectLxCrossPlatformHit(
                target,
                listOf(hit("6", "稻香", "周杰伦", 223), hit("7", "七里香", "周杰伦", 299))
            )
        )
    }

    @Test
    fun `hit without song mid is rejected`() {
        assertEquals(0, scoreLxCrossPlatformHit(song(), hit("", "晴天", "周杰伦", 269)))
    }

    @Test
    fun `parses kugou response with per quality hashes`() {
        val body = """
            {"status":1,"data":{"total":401,"lists":[
              {"Audioid":"20505418","OriSongName":"晴天","Suffix":"","Duration":"269",
               "AlbumName":"叶惠美","AlbumID":"966846","Singers":[{"name":"周杰伦","id":3520}],
               "FileHash":"B3A52A7A958BF0AED0EBFBA2E9A818B7",
               "HQFileHash":"1B56126A8A03924F1DD066259C095CBC",
               "SQFileHash":"0A69169202DE95AAF24A9944CCF0730D","ResFileHash":""}
            ]}}
        """.trimIndent()

        val hits = parseLxKugouSearchBody(body)

        assertEquals(1, hits.size)
        assertEquals(LX_KUGOU_PLATFORM_ID, hits[0].sourceId)
        assertEquals("20505418", hits[0].songMid)
        assertEquals("晴天", hits[0].name)
        assertEquals("周杰伦", hits[0].artist)
        assertEquals(269, hits[0].durationSec)
        assertEquals("1B56126A8A03924F1DD066259C095CBC", hits[0].qualityHashes["320k"])
        assertEquals("0A69169202DE95AAF24A9944CCF0730D", hits[0].qualityHashes["flac"])
    }

    @Test
    fun `kugou suffix becomes part of the song name`() {
        val body = """{"data":{"lists":[{"Audioid":"1","OriSongName":"晴天","Suffix":"Live",
            "Duration":"249","FileHash":"X","Singers":[{"name":"周杰伦"}]}]}}"""

        val hits = parseLxKugouSearchBody(body)

        assertEquals("晴天 Live", hits[0].name)
        // Live 版本应被判为次优，不能盖过原版
        assertTrue(scoreLxCrossPlatformHit(song(), hits[0]) < scoreLxCrossPlatformHit(song(), hit("228908", "晴天", "周杰伦", 269)))
    }

    @Test
    fun `kugou entry without any hash is dropped`() {
        val body = """{"data":{"lists":[{"Audioid":"1","OriSongName":"晴天","Duration":"269","Singers":[]}]}}"""
        assertTrue(parseLxKugouSearchBody(body).isEmpty())
    }

    @Test
    fun `parses qq response`() {
        val body = """
            {"code":0,"data":{"song":{"totalnum":823,"list":[
              {"mid":"0039MnYb0qxYhV","id":97773,"title":"晴天","interval":269,
               "singer":[{"id":4558,"name":"周杰伦"}],
               "album":{"id":8220,"mid":"000MkMni19ClKG","name":"叶惠美"}}
            ]}}}
        """.trimIndent()

        val hits = parseLxQqSearchBody(body)

        assertEquals(1, hits.size)
        assertEquals(LX_QQ_PLATFORM_ID, hits[0].sourceId)
        assertEquals("0039MnYb0qxYhV", hits[0].songMid)
        assertEquals("晴天", hits[0].name)
        assertEquals("周杰伦", hits[0].artist)
        assertEquals(269, hits[0].durationSec)
        assertEquals("叶惠美", hits[0].albumName)
        assertEquals("8220", hits[0].albumId)
    }

    @Test
    fun `parses qq new_json response with name and mid`() {
        val body = """
            {"code":0,"data":{"song":{"list":[
              {"mid":"001Zi7Ly4ZtVQk","name":"星晴","interval":259,
               "singer":[{"id":4558,"name":"周杰伦"}],
               "album":{"id":8218,"mid":"000f01724fd7TH","title":"Jay","name":"Jay"}}
            ]}}}
        """.trimIndent()

        val hits = parseLxQqSearchBody(body)

        assertEquals(1, hits.size)
        assertEquals("001Zi7Ly4ZtVQk", hits[0].songMid)
        assertEquals("星晴", hits[0].name)
        assertEquals("周杰伦", hits[0].artist)
        assertEquals(259, hits[0].durationSec)
        assertEquals("Jay", hits[0].albumName)
    }

    @Test
    fun `parsing ignores empty platform responses`() {
        assertTrue(parseLxKugouSearchBody("").isEmpty())
        assertTrue(parseLxKugouSearchBody("{\"data\":{\"lists\":[]}}").isEmpty())
        assertTrue(parseLxQqSearchBody("").isEmpty())
        assertTrue(parseLxQqSearchBody("{\"data\":{\"song\":{\"list\":[]}}}").isEmpty())
    }

    @Test
    fun `kugou music info carries hashes for the requested quality`() {
        val kugouHit = LxCrossPlatformHit(
            sourceId = LX_KUGOU_PLATFORM_ID,
            songMid = "20505418",
            name = "晴天",
            artist = "周杰伦",
            durationSec = 269,
            albumName = "叶惠美",
            qualityHashes = mapOf(
                "128k" to "LOW",
                "320k" to "HIGH320",
                "flac" to "LOSSLESS"
            )
        )

        val json = org.json.JSONObject(buildLxCrossPlatformMusicInfoJson(song(), kugouHit, "320k"))

        assertEquals(LX_KUGOU_PLATFORM_ID, json.getString("source"))
        assertEquals("20505418", json.getString("songmid"))
        assertEquals("HIGH320", json.getString("hash"))
        assertEquals("HIGH320", json.getJSONObject("_types").getJSONObject("320k").getString("hash"))
        assertEquals("LOSSLESS", json.getJSONObject("_types").getJSONObject("flac").getString("hash"))
    }

    @Test
    fun `music info without hashes stays plain`() {
        val kuwoHit = hit("228908", "晴天", "周杰伦", 269)
        val json = org.json.JSONObject(buildLxCrossPlatformMusicInfoJson(song(), kuwoHit, "320k"))

        assertEquals("228908", json.getString("songmid"))
        assertTrue(!json.has("hash"))
    }

    @Test
    fun `probe targets cover the three cross platform channels`() {
        val platforms = LX_CHANNEL_PROBE_TARGETS.map { it.sourceId }.toSet()

        assertEquals(setOf(LX_KUWO_PLATFORM_ID, LX_KUGOU_PLATFORM_ID, LX_QQ_PLATFORM_ID), platforms)
        assertTrue(LX_CHANNEL_PROBE_TARGETS.all { it.songMid.isNotBlank() })
    }
}
