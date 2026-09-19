package moe.ouom.neriplayer.ui.component.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformSliderTest {
    @Test
    fun `short track progress advances at the rendered frame cadence`() {
        assertEquals(
            0.225f,
            resolveWaveProgress(
                anchorValue = 0.20f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 125_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.25f,
            resolveWaveProgress(
                anchorValue = 0.20f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
    }

    @Test
    fun `wave progress prediction clamps values and ignores invalid inputs`() {
        assertEquals(
            0f,
            resolveWaveProgress(
                anchorValue = -0.2f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = -1L
            ),
            0.0001f
        )
        assertEquals(
            1f,
            resolveWaveProgress(
                anchorValue = 0.8f,
                durationMs = 5_000L,
                playbackSpeed = 1f,
                elapsedNs = 5_000_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.8f,
            resolveWaveProgress(
                anchorValue = 0.8f,
                durationMs = 0L,
                playbackSpeed = 1f,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
        assertEquals(
            0.8f,
            resolveWaveProgress(
                anchorValue = 0.8f,
                durationMs = 5_000L,
                playbackSpeed = Float.NaN,
                elapsedNs = 250_000_000L
            ),
            0.0001f
        )
    }

    @Test
    fun `wave progress prediction stops for waiting and seek previews`() {
        assertTrue(
            shouldPredictWaveProgress(
                isWaveAnimating = true,
                isProgressStalled = false,
                isProgressPreviewing = false
            )
        )
        assertFalse(
            shouldPredictWaveProgress(
                isWaveAnimating = true,
                isProgressStalled = true,
                isProgressPreviewing = false
            )
        )
        assertFalse(
            shouldPredictWaveProgress(
                isWaveAnimating = true,
                isProgressStalled = false,
                isProgressPreviewing = true
            )
        )
        assertFalse(
            shouldPredictWaveProgress(
                isWaveAnimating = false,
                isProgressStalled = false,
                isProgressPreviewing = false
            )
        )
    }

    @Test
    fun `predictor reanchors each real progress update without sampler lag`() {
        val predictor = WaveProgressPredictor(0.20f)

        predictor.updateTarget(
            targetValue = 0.20f,
            durationMs = 5_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_125_000_000L)
        assertEquals(0.225f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.25f,
            durationMs = 5_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_250_000_000L)
        assertEquals(0.25f, predictor.currentValue, 0.0001f)
        predictor.onFrame(1_375_000_000L)
        assertEquals(0.275f, predictor.currentValue, 0.0001f)
    }

    @Test
    fun `predictor immediately aligns after pause and seek`() {
        val predictor = WaveProgressPredictor(0.40f)

        predictor.updateTarget(
            targetValue = 0.40f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_200_000_000L)
        assertEquals(0.43f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.15f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = false
        )
        assertEquals(0.15f, predictor.currentValue, 0.0001f)

        predictor.updateTarget(
            targetValue = 0.15f,
            durationMs = 10_000L,
            playbackSpeed = 1.5f,
            animate = true
        )
        predictor.onFrame(2_000_000_000L)
        assertEquals(0.15f, predictor.currentValue, 0.0001f)
        predictor.onFrame(2_200_000_000L)
        assertEquals(0.18f, predictor.currentValue, 0.0001f)
    }

    @Test
    fun `predictor resets its frame anchor when animation resumes`() {
        val predictor = WaveProgressPredictor(0.40f)

        predictor.updateTarget(
            targetValue = 0.40f,
            durationMs = 10_000L,
            playbackSpeed = 1f,
            animate = true
        )
        predictor.onFrame(1_000_000_000L)
        predictor.onFrame(1_200_000_000L)
        assertEquals(0.42f, predictor.currentValue, 0.0001f)

        predictor.resetFrameAnchor()
        predictor.onFrame(10_000_000_000L)
        assertEquals(0.42f, predictor.currentValue, 0.0001f)
        predictor.onFrame(10_200_000_000L)
        assertEquals(0.44f, predictor.currentValue, 0.0001f)
    }
}
