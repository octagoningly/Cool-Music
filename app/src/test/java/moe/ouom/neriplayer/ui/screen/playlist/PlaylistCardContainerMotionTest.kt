package moe.ouom.neriplayer.ui.screen.playlist

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCardContainerMotionTest {
    @Test
    fun frameStartsAtClickedCardAndEndsAtViewport() {
        val source = Rect(24f, 320f, 1056f, 440f)

        val start = PlaylistCardContainerMotion.frame(source, 1080f, 2400f, 0f)
        val end = PlaylistCardContainerMotion.frame(source, 1080f, 2400f, 1f)

        assertEquals(source.left, start.translationX, 0.001f)
        assertEquals(source.top, start.translationY, 0.001f)
        assertEquals(source.width / 1080f, start.scaleX, 0.001f)
        assertEquals(source.height / 2400f, start.scaleY, 0.001f)
        assertEquals(0f, start.contentAlpha, 0.001f)
        assertEquals(0f, end.translationX, 0.001f)
        assertEquals(0f, end.translationY, 0.001f)
        assertEquals(1f, end.scaleX, 0.001f)
        assertEquals(1f, end.scaleY, 0.001f)
        assertEquals(1f, end.contentAlpha, 0.001f)
    }

    @Test
    fun sanitizeSourceBoundsClipsToViewportAndRejectsInvalidRects() {
        val clipped = PlaylistCardContainerMotion.sanitizeSourceBounds(
            source = Rect(-12f, 100f, 1100f, 260f),
            viewportWidth = 1080f,
            viewportHeight = 2400f
        )

        assertEquals(Rect(0f, 100f, 1080f, 260f), clipped)
        assertNull(
            PlaylistCardContainerMotion.sanitizeSourceBounds(
                source = Rect.Zero,
                viewportWidth = 1080f,
                viewportHeight = 2400f
            )
        )
        assertTrue(
            PlaylistCardContainerMotion.frame(
                source = Rect(10f, 20f, 110f, 80f),
                viewportWidth = 1080f,
                viewportHeight = 2400f,
                progress = 0.5f
            ).cornerRadiusDp in 0f..PlaylistCardContainerMotion.SourceCornerRadiusDp
        )
    }
}
