package moe.ouom.neriplayer.ui.screen.tab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTabOrderTest {

    @Test
    fun `custom order is applied and remaining tabs append`() {
        val tabs = libraryTabDisplayOrder(
            isInternational = false,
            youtubeEnabled = true,
            customOrderStorage = "LOCAL,NETEASE,BILI"
        )
        assertEquals(
            listOf(
                LibraryTab.LOCAL,
                LibraryTab.NETEASE,
                LibraryTab.BILI,
                LibraryTab.FAVORITE,
                LibraryTab.YTMUSIC,
                LibraryTab.QQMUSIC
            ),
            tabs
        )
    }

    @Test
    fun `invalid custom names fall back to defaults`() {
        val tabs = libraryTabDisplayOrder(
            isInternational = false,
            youtubeEnabled = false,
            customOrderStorage = "NOT_A_TAB"
        )
        assertFalse(tabs.contains(LibraryTab.YTMUSIC))
        assertEquals(LibraryTab.LOCAL, tabs.first())
        assertTrue(tabs.contains(LibraryTab.NETEASE))
    }

    @Test
    fun `serialize and parse round trip`() {
        val order = listOf(LibraryTab.LOCAL, LibraryTab.NETEASE, LibraryTab.BILI)
        val storage = serializeLibraryTabOrder(order)
        assertEquals("LOCAL,NETEASE,BILI", storage)
        assertEquals(listOf("LOCAL", "NETEASE", "BILI"), parseLibraryTabOrderStorage(storage))
    }
}
