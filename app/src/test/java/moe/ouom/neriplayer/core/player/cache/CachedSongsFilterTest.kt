package moe.ouom.neriplayer.core.player.cache

import moe.ouom.neriplayer.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedSongsFilterTest {
    private fun song(id: Long, name: String, artist: String): SongItem =
        SongItem(
            id = id,
            name = name,
            artist = artist,
            album = "",
            albumId = 0L,
            durationMs = 0L,
            coverUrl = null,
        )

    private fun entry(song: SongItem, complete: Boolean): CachedSong =
        CachedSong(song = song, bytes = 1L, complete = complete, lastTouched = 0L)

    @Test
    fun defaultFilterKeepsOnlyCompleteSongs() {
        val complete = entry(song(1, "A", "ArtistA"), complete = true)
        val partial = entry(song(2, "B", "ArtistA"), complete = false)

        val result = filterCachedSongs(
            songs = listOf(complete, partial),
            onlyComplete = true,
            selectedArtist = null,
            query = "",
        )

        assertEquals(listOf(complete), result)
    }

    @Test
    fun artistFilterMatchesIgnoreCaseAndIgnoresOthers() {
        val target = entry(song(1, "A", "ArtistA"), complete = true)
        val other = entry(song(2, "B", "ArtistB"), complete = true)

        val result = filterCachedSongs(
            songs = listOf(target, other),
            onlyComplete = true,
            selectedArtist = "artista",
            query = "",
        )

        assertEquals(listOf(target), result)
    }

    @Test
    fun queryStillAppliesTogetherWithOtherFilters() {
        val hit = entry(song(1, "Hello", "ArtistA"), complete = true)
        val miss = entry(song(2, "World", "ArtistA"), complete = true)

        val result = filterCachedSongs(
            songs = listOf(hit, miss),
            onlyComplete = true,
            selectedArtist = "ArtistA",
            query = "hello",
        )

        assertEquals(listOf(hit), result)
    }

    @Test
    fun duetSongMatchesEachSplitArtist() {
        val duet = entry(song(1, "合唱曲", "歌手甲 / 歌手乙"), complete = true)
        val other = entry(song(2, "独唱", "歌手丙"), complete = true)

        val forA = filterCachedSongs(
            songs = listOf(duet, other),
            onlyComplete = true,
            selectedArtist = "歌手甲",
            query = "",
        )
        val forB = filterCachedSongs(
            songs = listOf(duet, other),
            onlyComplete = true,
            selectedArtist = "歌手乙",
            query = "",
        )

        assertEquals(listOf(duet), forA)
        assertEquals(listOf(duet), forB)
        assertEquals(listOf("歌手丙", "歌手乙", "歌手甲").sorted(), cachedSongArtistOptions(listOf(duet, other)))
    }

    @Test
    fun artistOptionsAreUniqueAndSorted() {
        val options = cachedSongArtistOptions(
            listOf(
                entry(song(1, "A", "B"), complete = true),
                entry(song(2, "B", "A"), complete = false),
                entry(song(3, "C", "B"), complete = true),
                entry(song(4, "D", " "), complete = true),
            )
        )

        assertEquals(listOf("A", "B"), options)
        assertTrue(options == options.sorted())
    }
}
