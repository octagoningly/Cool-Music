package moe.ouom.neriplayer.ui.screen.tab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryChromeScrollStateTest {
    @Test
    fun `initial emission preserves collapsed tabs after detail recreation`() {
        assertFalse(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 420,
                currentScrollTotal = 420,
                initialEmission = true
            )
        )
    }

    @Test
    fun `real user scroll still hides and shows tabs`() {
        assertFalse(
            resolveLibraryTabsVisibility(
                currentVisible = true,
                previousScrollTotal = 100,
                currentScrollTotal = 120,
                initialEmission = false
            )
        )
        assertTrue(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 120,
                currentScrollTotal = 100,
                initialEmission = false
            )
        )
        assertTrue(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 100,
                currentScrollTotal = 40,
                initialEmission = false
            )
        )
    }
}
