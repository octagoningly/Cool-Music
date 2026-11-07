package moe.ouom.neriplayer.util.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImageUtilTest {
    @Test
    fun placeholderColorFollowsNightMode() {
        assertEquals(PLACEHOLDER_COLOR_LIGHT, resolvePlaceholderColor(nightMode = false))
        assertEquals(PLACEHOLDER_COLOR_DARK, resolvePlaceholderColor(nightMode = true))
        assertNotEquals(PLACEHOLDER_COLOR_LIGHT, PLACEHOLDER_COLOR_DARK)
    }
}
