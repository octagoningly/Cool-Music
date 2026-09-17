package moe.ouom.neriplayer.core.player.resolver.lxmusic

import moe.ouom.neriplayer.data.model.SongItem
import org.json.JSONObject
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
 * File: moe.ouom.neriplayer.core.player.resolver.lxmusic/LxMusicSourceResolverTest
 */

/**
 * 覆盖在线音源解析里「用哪个平台的曲目 ID」「请求哪些音质」这两个决定，
 * 它们决定了在线音源能否出流、以及出的流是不是同一首歌。
 */
class LxMusicSourceResolverTest {

    private val scriptQualities = setOf("128k", "320k", "flac", "flac24bit")

    private fun neteaseSong(
        id: Long = 186016L,
        name: String = "晴天",
        artist: String = "周杰伦",
        album: String = "叶惠美",
        albumId: Long = 12345L,
        durationMs: Long = 269_000L,
        coverUrl: String? = "https://p1.music.126.net/cover.jpg"
    ) = SongItem(
        id = id,
        name = name,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        coverUrl = coverUrl
    )

    @Test
    fun `netease track uses wy platform with its own song id`() {
        val target = resolveLxJsPlatformTarget(
            song = neteaseSong(id = 186016L),
            isNeteaseTrack = true,
            supportedSourceIds = setOf("kw", "kg", "tx", "wy", "mg")
        )

        assertEquals("wy", target?.sourceId)
        assertEquals("186016", target?.songMid)
    }

    @Test
    fun `netease id is never sent as another platform's song mid`() {
        // 脚本声明全部平台时，我们仍然只用 wy + 网易云 ID，
        // 不会把网易云 ID 当作 kw/kg/tx/mg 的曲目 ID 去请求。
        val allPlatforms = setOf("kw", "kg", "tx", "wy", "mg")
        val target = resolveLxJsPlatformTarget(
            song = neteaseSong(),
            isNeteaseTrack = true,
            supportedSourceIds = allPlatforms
        )

        assertEquals("wy", target?.sourceId)
    }

    @Test
    fun `bili and youtube tracks have no platform target`() {
        assertNull(
            resolveLxJsPlatformTarget(
                song = neteaseSong(),
                isNeteaseTrack = false,
                supportedSourceIds = setOf("wy")
            )
        )
    }

    @Test
    fun `missing song id has no platform target`() {
        assertNull(
            resolveLxJsPlatformTarget(
                song = neteaseSong(id = 0L),
                isNeteaseTrack = true,
                supportedSourceIds = setOf("wy")
            )
        )
    }

    @Test
    fun `source without wy support is skipped`() {
        assertNull(
            resolveLxJsPlatformTarget(
                song = neteaseSong(),
                isNeteaseTrack = true,
                supportedSourceIds = setOf("kw", "tx")
            )
        )
    }

    @Test
    fun `music info carries the requested platform id and track metadata`() {
        val json = JSONObject(
            buildLxOldMusicInfoJson(
                song = neteaseSong(),
                platformSourceId = "wy",
                songMid = "186016"
            )
        )

        assertEquals("wy", json.getString("source"))
        assertEquals("186016", json.getString("songmid"))
        assertEquals("晴天", json.getString("name"))
        assertEquals("周杰伦", json.getString("singer"))
        assertEquals("04:29", json.getString("interval"))
        assertEquals("叶惠美", json.getString("albumName"))
        assertEquals("12345", json.getString("albumId"))
        assertEquals("https://p1.music.126.net/cover.jpg", json.getString("img"))
    }

    @Test
    fun `music info prefers original title and artist`() {
        val song = neteaseSong().copy(
            name = "cover version",
            artist = "someone else",
            originalName = "晴天",
            originalArtist = "周杰伦"
        )

        val json = JSONObject(
            buildLxOldMusicInfoJson(song = song, platformSourceId = "wy", songMid = "1")
        )

        assertEquals("晴天", json.getString("name"))
        assertEquals("周杰伦", json.getString("singer"))
    }

    @Test
    fun `quality order only asks for qualities the source declares`() {
        val order = selectLxQualityOrder(
            preferredNeteaseQuality = "exhigh",
            supportedQualities = scriptQualities,
            maxAttempts = 2
        )

        assertEquals(listOf("320k", "flac"), order)
    }

    @Test
    fun `quality order never asks for non lx qualities`() {
        val order = selectLxQualityOrder(
            preferredNeteaseQuality = "hires",
            supportedQualities = emptySet(),
            maxAttempts = 8
        )

        assertTrue(order.isNotEmpty())
        assertTrue(order.all { it in DEFAULT_LX_QUALITIES })
    }

    @Test
    fun `quality order honours the attempt budget`() {
        val single = selectLxQualityOrder(
            preferredNeteaseQuality = "lossless",
            supportedQualities = scriptQualities,
            maxAttempts = 1
        )

        assertEquals(listOf("flac"), single)
        assertTrue(
            selectLxQualityOrder(
                preferredNeteaseQuality = "lossless",
                supportedQualities = scriptQualities,
                maxAttempts = 0
            ).isEmpty()
        )
    }

    @Test
    fun `quality order falls back to what the source supports`() {
        val order = selectLxQualityOrder(
            preferredNeteaseQuality = "exhigh",
            supportedQualities = setOf("128k"),
            maxAttempts = 2
        )

        assertEquals(listOf("128k"), order)
    }
}
