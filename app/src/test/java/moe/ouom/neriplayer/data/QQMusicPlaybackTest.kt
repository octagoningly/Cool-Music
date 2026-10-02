package moe.ouom.neriplayer.data

import moe.ouom.neriplayer.core.api.qqmusic.QQMusicClient
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlFailure
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlParseResult
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlResult
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlayUrlSource
import moe.ouom.neriplayer.core.api.qqmusic.QQMusicPlaybackRepository
import moe.ouom.neriplayer.core.api.qqmusic.buildPlayUrlRequestJson
import moe.ouom.neriplayer.core.api.qqmusic.parsePlayUrlResponse
import moe.ouom.neriplayer.core.api.qqmusic.parseQQMusicUserPlaylists
import moe.ouom.neriplayer.data.auth.qqmusic.QQMusicAuthBundle
import moe.ouom.neriplayer.data.auth.qqmusic.qqMusicGtk
import moe.ouom.neriplayer.data.platform.qqmusic.QQMusicQuality
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QQMusicPlaybackTest {

    @Test
    fun userPlaylistsParser_readsCreatedPlaylistsAndDeduplicatesIds() {
        val result = parseQQMusicUserPlaylists(
            """{"code":0,"data":{"disslist":[
                {"dissid":123,"dissname":"我的收藏","songnum":8,"logo":"https://img/1.jpg"},
                {"dissid":123,"dissname":"重复项"},
                {"dirid":456,"name":"旅行歌单","song_cnt":3}
            ]}}"""
        )

        assertEquals(2, result.size)
        assertEquals(123L, result[0].dissId)
        assertEquals("我的收藏", result[0].name)
        assertEquals(8, result[0].songCount)
        assertEquals(456L, result[1].dissId)
        assertEquals("旅行歌单", result[1].name)
    }

    @Test
    fun userPlaylistsParser_ignoresMalformedEntries() {
        val result = parseQQMusicUserPlaylists(
            """{"data":{"list":[{"dissid":0,"dissname":"无效"},{"dissid":9,"dissname":"有效"}]}}"""
        )

        assertEquals(1, result.size)
        assertEquals(9L, result.single().dissId)
    }

    @Test
    fun userPlaylistsParser_readsCurrentCreatedAndCollectedFields() {
        val created = parseQQMusicUserPlaylists(
            """{"data":{"disslist":[{"dissid":123,"diss_name":"通勤","song_cnt":12,"diss_cover":"https://img/created.jpg"}]}}"""
        )
        val collected = parseQQMusicUserPlaylists(
            """{"data":{"cdlist":[{"dissid":456,"diss_name":"收藏歌单","total_song_num":8,"picurl":"https://img/collected.jpg"}]}}"""
        )

        assertEquals("通勤", created.single().name)
        assertEquals(12, created.single().songCount)
        assertEquals("https://img/created.jpg", created.single().coverUrl)
        assertEquals("收藏歌单", collected.single().name)
        assertEquals(8, collected.single().songCount)
        assertEquals("https://img/collected.jpg", collected.single().coverUrl)
    }

    // ---------- QQMusicQuality ----------

    @Test
    fun quality_fromKey_mapsKnownKeysAndDefaultsToMedium() {
        assertEquals(QQMusicQuality.LOSSLESS, QQMusicQuality.fromKey("flac"))
        assertEquals(QQMusicQuality.HIGH, QQMusicQuality.fromKey("320K"))
        assertEquals(QQMusicQuality.MEDIUM, QQMusicQuality.fromKey("128k"))
        assertEquals(QQMusicQuality.TRY, QQMusicQuality.fromKey("m4a"))
        assertEquals(QQMusicQuality.MEDIUM, QQMusicQuality.fromKey("unknown"))
    }

    @Test
    fun quality_degradeChainGoesHighToLowIncludingTry() {
        val chain = QQMusicQuality.degradeChain(QQMusicQuality.LOSSLESS)
        assertEquals(
            listOf(QQMusicQuality.LOSSLESS, QQMusicQuality.HIGH, QQMusicQuality.MEDIUM, QQMusicQuality.TRY),
            chain
        )
        val mediumChain = QQMusicQuality.degradeChain(QQMusicQuality.MEDIUM)
        assertEquals(listOf(QQMusicQuality.MEDIUM, QQMusicQuality.TRY), mediumChain)
    }

    @Test
    fun quality_fileNameAndMimeTypeFollowSpec() {
        assertEquals("M50000TEST.mp3", QQMusicQuality.MEDIUM.fileNameFor("00TEST"))
        assertEquals("M80000TEST.mp3", QQMusicQuality.HIGH.fileNameFor("00TEST"))
        assertEquals("F00000TEST.flac", QQMusicQuality.LOSSLESS.fileNameFor("00TEST"))
        assertEquals("C40000TEST.m4a", QQMusicQuality.TRY.fileNameFor("00TEST"))
        assertEquals("audio/mp4", QQMusicQuality.TRY.mimeType())
        assertEquals("audio/flac", QQMusicQuality.LOSSLESS.mimeType())
    }

    // ---------- buildPlayUrlRequestJson ----------

    @Test
    fun requestJson_usesCommReqStructureAndDerivedGtk() {
        val auth = QQMusicAuthBundle(
            cookies = mapOf("uin" to "3575053735", "qm_keyst" to "synthetic-key"),
            savedAt = 1L
        )
        val json = JSONObject(
            buildPlayUrlRequestJson(auth, songmid = "00TEST", filename = "M50000TEST.mp3", guid = "123456789")
        )

        val comm = json.getJSONObject("comm")
        assertEquals(qqMusicGtk("synthetic-key"), comm.getInt("g_tk"))
        assertEquals("yqq", comm.getString("platform"))
        assertEquals(24, comm.getInt("ct"))
        assertEquals("synthetic-key", comm.getString("authst"))

        val req = json.getJSONObject("req")
        assertEquals("vkey.GetVkeyServer", req.getString("module"))
        assertEquals("CgiGetVkey", req.getString("method"))
        val param = req.getJSONObject("param")
        assertEquals("M50000TEST.mp3", param.getJSONArray("filename").getString(0))
        assertEquals("00TEST", param.getJSONArray("songmid").getString(0))
        assertEquals("123456789", param.getString("guid"))
        assertEquals("3575053735", param.getString("uin"))
        assertEquals(1, param.getInt("loginflag"))
        assertEquals("20", param.getString("platform"))
    }

    @Test
    fun requestJson_withoutLoginCookieZeroesGtkAndKeepsStructure() {
        val json = JSONObject(
            buildPlayUrlRequestJson(QQMusicAuthBundle(), "00TEST", "M50000TEST.mp3", "1")
        )
        assertEquals(0, json.getJSONObject("comm").getInt("g_tk"))
        assertFalse(json.getJSONObject("comm").has("authst"))
        assertEquals("vkey.GetVkeyServer", json.getJSONObject("req").getString("module"))
    }

    // ---------- parsePlayUrlResponse ----------

    private fun successBody(purl: String, sip: List<String> = listOf("http://aqqmusic.tc.qq.com/", "http://sjy6.stream.qqmusic.qq.com/")): String {
        val sipJson = sip.joinToString(",") { "\"$it\"" }
        return """
            {"code":0,"req":{"code":0,"data":{
                "sip":[$sipJson],
                "midurlinfo":[{"songmid":"00TEST","filename":"M50000TEST.mp3","purl":"$purl","result":0}],
                "expiration":80400
            }}}
        """.trimIndent()
    }

    @Test
    fun parseResponse_successBuildsFullUrlAndExtractsVkey() {
        val purl = "M50000TEST.mp3?guid=1&vkey=ABCDEF123&uin=&fromtag=120032&src=x.m4a"
        val parsed = parsePlayUrlResponse(
            successBody(purl),
            songmid = "00TEST",
            quality = QQMusicQuality.MEDIUM,
            guid = "1"
        )
        assertTrue(parsed is QQMusicPlayUrlParseResult.Success)
        val stream = (parsed as QQMusicPlayUrlParseResult.Success).stream
        // 优先 stream CDN，且必须保持服务端给出的协议。
        assertEquals(
            "http://sjy6.stream.qqmusic.qq.com/M50000TEST.mp3?guid=1&vkey=ABCDEF123&uin=&fromtag=120032&src=x.m4a",
            stream.url
        )
        assertTrue(stream.candidateUrls.isEmpty())
        assertEquals("ABCDEF123", stream.vkey)
        assertEquals("120032", stream.fromTag)
        assertEquals("128k", stream.qualityKey)
        assertEquals("audio/mpeg", stream.mimeType)
    }

    @Test
    fun parseResponse_deniedOn104003EvenWithPurlPresent() {
        val body = """
            {"req":{"data":{"sip":["http://a.qq.com/"],"midurlinfo":[{"purl":"x.mp3","result":104003}]}}}
        """.trimIndent()
        val parsed = parsePlayUrlResponse(body, "00TEST", QQMusicQuality.MEDIUM, "1")
        assertTrue(parsed is QQMusicPlayUrlParseResult.Denied)
        assertEquals(104003, (parsed as QQMusicPlayUrlParseResult.Denied).httpCode)
    }

    @Test
    fun parseResponse_deniedOnBlankPurl() {
        val body = """
            {"req":{"data":{"sip":["http://a.qq.com/"],"midurlinfo":[{"purl":"","result":0}]}}}
        """.trimIndent()
        val parsed = parsePlayUrlResponse(body, "00TEST", QQMusicQuality.MEDIUM, "1")
        assertTrue(parsed is QQMusicPlayUrlParseResult.Denied)
    }

    @Test
    fun parseResponse_malformedOnBrokenJsonOrMissingFields() {
        assertTrue(parsePlayUrlResponse("not-json", "00TEST", QQMusicQuality.MEDIUM, "1")
            is QQMusicPlayUrlParseResult.Malformed)
        assertTrue(parsePlayUrlResponse("{}", "00TEST", QQMusicQuality.MEDIUM, "1")
            is QQMusicPlayUrlParseResult.Malformed)
        assertTrue(
            parsePlayUrlResponse(
                """{"req":{"data":{"midurlinfo":[]}}}""",
                "00TEST", QQMusicQuality.MEDIUM, "1"
            ) is QQMusicPlayUrlParseResult.Malformed
        )
    }

    @Test
    fun parseResponse_compatibleWithReq0FieldAlias() {
        val purl = "M50000TEST.mp3?guid=1&vkey=K1&fromtag=9"
        val body = """
            {"req_0":{"data":{"sip":["http://ws.foo/","http://bar.qq.com/"],"midurlinfo":[{"purl":"$purl","result":0}]}}}
        """.trimIndent()
        val parsed = parsePlayUrlResponse(body, "00TEST", QQMusicQuality.MEDIUM, "1")
        assertTrue(parsed is QQMusicPlayUrlParseResult.Success)
        // 非 QQ 标准 stream 域名时仍保留上游顺序。
        assertEquals(
            "http://ws.foo/M50000TEST.mp3?guid=1&vkey=K1&fromtag=9",
            (parsed as QQMusicPlayUrlParseResult.Success).stream.url
        )
    }

    @Test
    fun parseResponse_keepsAllCurrentStreamCdnsAsFallbacks() {
        val purl = "M50000TEST.mp3?guid=1&vkey=K1&fromtag=9"
        val parsed = parsePlayUrlResponse(
            successBody(
                purl,
                sip = listOf(
                    "http://aqqmusic.tc.qq.com/",
                    "http://ws.stream.qqmusic.qq.com/",
                    "https://dl.stream.qqmusic.qq.com/"
                )
            ),
            "00TEST",
            QQMusicQuality.MEDIUM,
            "1"
        ) as QQMusicPlayUrlParseResult.Success

        assertEquals("http://ws.stream.qqmusic.qq.com/$purl", parsed.stream.url)
        assertEquals(listOf("https://dl.stream.qqmusic.qq.com/$purl"), parsed.stream.candidateUrls)
    }

    // ---------- QQMusicPlaybackRepository degradation ----------

    private class FakeSource(
        private val script: (songmid: String, quality: QQMusicQuality) -> QQMusicPlayUrlResult
    ) : QQMusicPlayUrlSource {
        val calls = mutableListOf<QQMusicQuality>()
        override suspend fun getPlayUrl(
            songmid: String,
            quality: QQMusicQuality,
            guid: String
        ): QQMusicPlayUrlResult {
            calls.add(quality)
            return script(songmid, quality)
        }
    }

    private fun ok(quality: QQMusicQuality) = QQMusicPlayUrlResult.Success(
        moe.ouom.neriplayer.data.platform.qqmusic.QQMusicStreamInfo(
            songmid = "00TEST",
            qualityKey = quality.key,
            filename = quality.fileNameFor("00TEST"),
            url = "http://stream.qqmusic.qq.com/${quality.fileNameFor("00TEST")}?vkey=X",
            mimeType = quality.mimeType()
        )
    )

    @Test
    fun repository_degradesUntilHittingAvailableQuality() = runBlocking {
        val fake = FakeSource { _, q ->
            when (q) {
                QQMusicQuality.LOSSLESS, QQMusicQuality.HIGH ->
                    QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE)
                else -> ok(q)
            }
        }
        val repo = QQMusicPlaybackRepository(fake)
        val result = repo.getBestPlayableStream("00TEST", preferredKey = "flac")

        assertTrue(result is QQMusicPlayUrlResult.Success)
        assertEquals(
            listOf(QQMusicQuality.LOSSLESS, QQMusicQuality.HIGH, QQMusicQuality.MEDIUM),
            fake.calls
        )
        assertEquals("128k", (result as QQMusicPlayUrlResult.Success).stream.qualityKey)
    }

    @Test
    fun repository_stopsImmediatelyWhenNotLoggedIn() = runBlocking {
        val fake = FakeSource { _, _ ->
            QQMusicPlayUrlResult.Failure(QQMusicPlayUrlFailure.NOT_LOGGED_IN)
        }
        val repo = QQMusicPlaybackRepository(fake)
        val result = repo.getBestPlayableStream("00TEST", preferredKey = "flac")

        assertTrue(result is QQMusicPlayUrlResult.Failure)
        assertEquals(
            QQMusicPlayUrlFailure.NOT_LOGGED_IN,
            (result as QQMusicPlayUrlResult.Failure).reason
        )
        assertEquals(listOf(QQMusicQuality.LOSSLESS), fake.calls)
    }

    @Test
    fun repository_returnsLastFailureWhenChainExhausted() = runBlocking {
        val fake = FakeSource { _, q ->
            QQMusicPlayUrlResult.Failure(
                if (q == QQMusicQuality.TRY) QQMusicPlayUrlFailure.MALFORMED_RESPONSE
                else QQMusicPlayUrlFailure.NO_AUTHORITY_OR_NO_FILE
            )
        }
        val repo = QQMusicPlaybackRepository(fake)
        val result = repo.getBestPlayableStream("00TEST", preferredKey = "128k")

        assertTrue(result is QQMusicPlayUrlResult.Failure)
        assertEquals(
            QQMusicPlayUrlFailure.MALFORMED_RESPONSE,
            (result as QQMusicPlayUrlResult.Failure).reason
        )
        assertEquals(
            listOf(QQMusicQuality.MEDIUM, QQMusicQuality.TRY),
            fake.calls
        )
    }

    @Test
    fun repository_defaultPreferredKeyIsFreeAccountTop() = runBlocking {
        val fake = FakeSource { _, q -> ok(q) }
        val repo = QQMusicPlaybackRepository(fake)
        val result = repo.getBestPlayableStream("00TEST")
        assertTrue(result is QQMusicPlayUrlResult.Success)
        assertEquals(listOf(QQMusicQuality.MEDIUM), fake.calls)
    }

    @Test
    fun client_randomGuidIsNumeric() {
        val guid = QQMusicClient.randomGuid()
        assertTrue(guid.all { it.isDigit() })
        assertTrue(guid.length in 9..10)
    }
}
