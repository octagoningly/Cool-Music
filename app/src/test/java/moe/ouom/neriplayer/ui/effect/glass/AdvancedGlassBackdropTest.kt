package moe.ouom.neriplayer.ui.effect.glass

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGlassBackdropTest {
    @Test
    fun localBlurHandoffGuardsRemainEnabledUntilEveryHandoffEnds() {
        val backdrop = AdvancedGlassBackdrop()
        val firstHandoff = Any()
        val secondHandoff = Any()

        assertFalse(backdrop.hasLocalBlurHandoffGuard)

        backdrop.setLocalBlurHandoffGuard(firstHandoff, enabled = true)
        backdrop.setLocalBlurHandoffGuard(secondHandoff, enabled = true)
        backdrop.setLocalBlurHandoffGuard(firstHandoff, enabled = false)

        assertTrue(backdrop.hasLocalBlurHandoffGuard)

        backdrop.removeLocalBlurHandoffGuard(secondHandoff)

        assertFalse(backdrop.hasLocalBlurHandoffGuard)
    }

    @Test
    fun localBlurRequiresASupportedBackend() {
        val plan = requireNotNull(
            resolveAdvancedGlassLocalBlurPlan(
                regions = listOf(region()),
                radiusPx = 24f,
                maximumMergedInputAreaRatio = 1f
            )
        )

        assertTrue(
            plan.groups.isNotEmpty()
        )
        assertFalse(
            ADVANCED_GLASS_BACKEND_MIN_SDK <= 0
        )
    }

    @Test
    fun mainAndNowPlayingScenesKeepIndependentCaptureState() {
        val mainBackdrop = AdvancedGlassBackdrop()
        val nowPlayingBackdrop = AdvancedGlassBackdrop()
        val mainPlan = requireNotNull(
            resolveAdvancedGlassLocalBlurPlan(
                regions = listOf(region()),
                radiusPx = 24f,
                maximumMergedInputAreaRatio = 1f
            )
        )

        mainBackdrop.positionInWindow = Offset(12f, 24f)
        mainBackdrop.localBlurPlan = mainPlan
        nowPlayingBackdrop.positionInWindow = Offset.Zero

        assertNotSame(mainBackdrop, nowPlayingBackdrop)
        assertEquals(Offset(12f, 24f), mainBackdrop.positionInWindow)
        assertEquals(Offset.Zero, nowPlayingBackdrop.positionInWindow)
        assertEquals(mainPlan, mainBackdrop.localBlurPlan)
        assertNull(nowPlayingBackdrop.localBlurPlan)
    }

    private fun region() = AdvancedGlassRenderRegion(
        left = 0f,
        top = 0f,
        right = 100f,
        bottom = 100f,
        cornerRadiiPx = AdvancedGlassCornerRadii.Zero
    )
}
