package moe.ouom.neriplayer.core.player.metadata

import moe.ouom.neriplayer.ui.component.lyrics.LyricEntry
import org.junit.Assert.assertFalse
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
 * File: moe.ouom.neriplayer.core.player.metadata/PlaceholderLyricsTest
 */

/**
 * 占位歌词判定：网易云对无歌词曲目返回「[00:00.00]暂无歌词」，
 * 把它当有歌词会挡住在线音源歌词同步。
 */
class PlaceholderLyricsTest {

    private fun entry(text: String) = LyricEntry(
        text = text,
        startTimeMs = 0L,
        endTimeMs = 0L
    )

    @Test
    fun `netease placeholder lyric is treated as missing`() {
        assertTrue(isPlaceholderLyrics(listOf(entry("暂无歌词"))))
    }

    @Test
    fun `instrumental notice is treated as missing`() {
        assertTrue(isPlaceholderLyrics(listOf(entry("纯音乐，请欣赏"))))
        assertTrue(isPlaceholderLyrics(listOf(entry("该歌曲暂无歌词"))))
        assertTrue(isPlaceholderLyrics(listOf(entry("Instrumental"))))
    }

    @Test
    fun `empty lyrics are treated as missing`() {
        assertTrue(isPlaceholderLyrics(emptyList()))
        assertTrue(isPlaceholderLyrics(listOf(entry("   "))))
    }

    @Test
    fun `real lyrics are kept`() {
        val real = listOf(
            entry("故事的小黄花"),
            entry("从出生那年就飘着"),
            entry("童年的荡秋千")
        )
        assertFalse(isPlaceholderLyrics(real))
    }

    @Test
    fun `short real lyric is kept`() {
        assertFalse(isPlaceholderLyrics(listOf(entry("故事的小黄花"))))
        assertFalse(isPlaceholderLyrics(listOf(entry("故事的小黄花"), entry("从出生那年就飘着"))))
    }

    @Test
    fun `long text containing placeholder word is not dropped`() {
        // 正常歌词里出现「暂无」等字样时不能误判
        val lyric = listOf(
            entry("我暂不知道该如何说出口"),
            entry("这句话藏在心里很久了"),
            entry("直到今天才明白"),
            entry("原来是这样的感觉")
        )
        assertFalse(isPlaceholderLyrics(lyric))
    }

    @Test
    fun `stored placeholder lyrics are not accepted for display`() {
        assertFalse(shouldAcceptStoredLyricEntries(emptyList()))
        assertFalse(shouldAcceptStoredLyricEntries(listOf(entry("暂无歌词"))))
        assertFalse(shouldAcceptStoredLyricEntries(listOf(entry("纯音乐，请欣赏"))))
        assertTrue(
            shouldAcceptStoredLyricEntries(
                listOf(entry("故事的小黄花"), entry("从出生那年就飘着"))
            )
        )
    }
}
