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

        assertEquals(source.left, start.clipLeft, 0.001f)
        assertEquals(source.top, start.clipTop, 0.001f)
        assertEquals(source.right, start.clipRight, 0.001f)
        assertEquals(source.bottom, start.clipBottom, 0.001f)
        assertEquals(0f, start.contentAlpha, 0.001f)
        assertEquals(source.width / 1080f, start.contentScale, 0.001f)
        assertEquals(source.left, start.contentTranslationX, 0.001f)
        assertEquals(source.top, start.contentTranslationY, 0.001f)
        assertEquals(0f, end.clipLeft, 0.001f)
        assertEquals(0f, end.clipTop, 0.001f)
        assertEquals(1080f, end.clipRight, 0.001f)
        assertEquals(2400f, end.clipBottom, 0.001f)
        assertEquals(1f, end.contentAlpha, 0.001f)
        assertEquals(1f, end.contentScale, 0.001f)
        assertEquals(0f, end.contentTranslationX, 0.001f)
        assertEquals(0f, end.contentTranslationY, 0.001f)
        assertEquals(PlaylistCardContainerMotion.BackgroundExpandedScale, end.backgroundScale, 0.001f)
        assertEquals(PlaylistCardContainerMotion.BackgroundDimmedAlpha, end.backgroundAlpha, 0.001f)
        assertEquals(0.5f, end.backgroundPivotFractionX, 0.001f)
        assertEquals(380f / 2400f, end.backgroundPivotFractionY, 0.001f)
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

    @Test
    fun sourceBoundsSubtractsViewportOrigin() {
        val sourceInRoot = Rect(28f, 412f, 1052f, 532f)
        val viewportInRoot = Rect(0f, 96f, 1080f, 2256f)

        assertEquals(
            Rect(28f, 316f, 1052f, 436f),
            PlaylistCardContainerMotion.sourceBoundsInViewport(
                sourceInRoot = sourceInRoot,
                viewportInRoot = viewportInRoot
            )
        )
    }

    @Test
    fun contentTransformTracksMorphingContainerInsteadOfRevealingFixedPage() {
        val source = Rect(32f, 760f, 1048f, 900f)
        val frame = PlaylistCardContainerMotion.frame(
            source = source,
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            progress = 0.5f
        )

        assertEquals(frame.clipLeft, frame.contentTranslationX, 0.001f)
        assertEquals(frame.clipTop, frame.contentTranslationY, 0.001f)
        assertEquals(
            frame.clipRight - frame.clipLeft,
            1080f * frame.contentScale,
            0.001f
        )
        assertTrue(frame.cornerRadiusDp > PlaylistCardContainerMotion.SourceCornerRadiusDp * 0.5f)
        assertTrue(frame.contentAlpha in 0f..1f)
    }
}
