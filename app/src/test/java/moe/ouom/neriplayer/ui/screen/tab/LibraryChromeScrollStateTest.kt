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
                initialEmission = true,
                compensatingContentTop = false
            )
        )
    }

    @Test
    fun `content top compensation cannot reopen collapsed tabs`() {
        assertFalse(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 420,
                currentScrollTotal = 40,
                initialEmission = false,
                compensatingContentTop = true
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
                initialEmission = false,
                compensatingContentTop = false
            )
        )
        assertTrue(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 120,
                currentScrollTotal = 100,
                initialEmission = false,
                compensatingContentTop = false
            )
        )
        assertTrue(
            resolveLibraryTabsVisibility(
                currentVisible = false,
                previousScrollTotal = 100,
                currentScrollTotal = 40,
                initialEmission = false,
                compensatingContentTop = false
            )
        )
    }
}
