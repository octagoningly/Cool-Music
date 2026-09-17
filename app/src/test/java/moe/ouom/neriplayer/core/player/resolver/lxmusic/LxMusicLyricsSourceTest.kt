package moe.ouom.neriplayer.core.player.resolver.lxmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicLyricsSourceTest
 */

/** 覆盖在线音源歌词同步的平台响应解析 */
class LxMusicLyricsSourceTest {

    @Test
    fun `parses qq lyric response`() {
        val body = """
            {"retcode":0,"code":0,"subcode":0,
             "lyric":"[ti:晴天]\n[ar:周杰伦]\n[00:00.00]晴天 - 周杰伦\n[00:29.26]故事的小黄花\n",
             "trans":"[00:29.26]The little yellow flower\n"}
        """.trimIndent()

        val fetched = parseLxQqLyricBody(body)

        assertNotNull(fetched)
        assertEquals(LX_QQ_PLATFORM_ID, fetched?.sourceId)
        assertEquals(true, fetched?.lyric?.contains("故事的小黄花"))
        assertEquals(true, fetched?.translatedLyric?.contains("little yellow flower"))
    }

    @Test
    fun `qq response without lyric is treated as failure`() {
        assertNull(parseLxQqLyricBody("{\"retcode\":0,\"lyric\":\"\"}"))
        assertNull(parseLxQqLyricBody("<html>502</html>"))
        assertNull(parseLxQqLyricBody(""))
    }

    @Test
    fun `parses kugou base64 lyric download`() {
        val lrc = "[ti:晴天]\n[00:29.26]故事的小黄花\n"
        val encoded = java.util.Base64.getEncoder().encodeToString(lrc.toByteArray(Charsets.UTF_8))
        val body = "{\"status\":200,\"fmt\":\"lrc\",\"content\":\"$encoded\"}"

        val fetched = parseLxKugouLyricDownloadBody(body)

        assertNotNull(fetched)
        assertEquals(LX_KUGOU_PLATFORM_ID, fetched?.sourceId)
        assertEquals(lrc, fetched?.lyric)
    }

    @Test
    fun `kugou download without content is treated as failure`() {
        assertNull(parseLxKugouLyricDownloadBody("{\"status\":200,\"content\":\"\"}"))
        assertNull(parseLxKugouLyricDownloadBody(""))
    }
}
