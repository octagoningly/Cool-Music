package moe.ouom.neriplayer.ui.effect.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGlassSurfaceTest {
    @Test
    fun inactiveNavigationOwnerCannotRenderGlassDuringAStageHandoff() {
        val activeOwner = Any()
        val inactiveOwner = Any()
        val activeOwners = setOf<Any>(activeOwner)

        assertTrue(
            isAdvancedGlassNavigationOwnerActive(
                requiresContentBackdrop = false,
                activeNavigationOwners = activeOwners,
                navigationOwner = activeOwner
            )
        )
        assertFalse(
            isAdvancedGlassNavigationOwnerActive(
                requiresContentBackdrop = false,
                activeNavigationOwners = activeOwners,
                navigationOwner = inactiveOwner
            )
        )
        assertTrue(
            isAdvancedGlassNavigationOwnerActive(
                requiresContentBackdrop = true,
                activeNavigationOwners = activeOwners,
                navigationOwner = inactiveOwner
            )
        )
    }

    @Test
    fun preparedInactiveNavigationOwnerRegistersForTheNextGlassHandoff() {
        assertTrue(
            shouldRegisterAdvancedGlassRegion(
                sceneActive = true,
                backdropRegistrationEnabled = true,
                belongsToActiveNavigationScreen = false,
                belongsToPrewarmedNavigationScreen = true
            )
        )
        assertFalse(
            shouldRegisterAdvancedGlassRegion(
                sceneActive = true,
                backdropRegistrationEnabled = true,
                belongsToActiveNavigationScreen = false,
                belongsToPrewarmedNavigationScreen = false
            )
        )
        assertFalse(
            shouldRegisterAdvancedGlassRegion(
                sceneActive = false,
                backdropRegistrationEnabled = true,
                belongsToActiveNavigationScreen = true,
                belongsToPrewarmedNavigationScreen = true
            )
        )
    }

    @Test
    fun prewarmedInactiveNavigationOwnerSuppressesItsGlassSurfaceDuringOnboarding() {
        assertTrue(
            shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
                suppressInactiveNavigationSurface = true,
                canRenderGlass = true,
                belongsToActiveNavigationScreen = false,
                belongsToPrewarmedNavigationScreen = true
            )
        )
        assertFalse(
            shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
                suppressInactiveNavigationSurface = false,
                canRenderGlass = true,
                belongsToActiveNavigationScreen = false,
                belongsToPrewarmedNavigationScreen = true
            )
        )
        assertFalse(
            shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
                suppressInactiveNavigationSurface = true,
                canRenderGlass = true,
                belongsToActiveNavigationScreen = true,
                belongsToPrewarmedNavigationScreen = true
            )
        )
        assertFalse(
            shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
                suppressInactiveNavigationSurface = true,
                canRenderGlass = false,
                belongsToActiveNavigationScreen = false,
                belongsToPrewarmedNavigationScreen = true
            )
        )
    }

    @Test
    fun miniPlayerAndBottomBarFallbackWhenContentBackdropIsNotReady() {
        // 媒体库等页面 content 捕获未就绪时，dock / 迷你播放器仍可采样背景层
        assertTrue(
            isAdvancedGlassBackdropReady(
                backgroundReady = true,
                contentReady = false,
                requiresContentBackdrop = true,
                canFallbackToBackground = true
            )
        )
        assertFalse(
            isAdvancedGlassBackdropReady(
                backgroundReady = true,
                contentReady = false,
                requiresContentBackdrop = true,
                canFallbackToBackground = false
            )
        )
        assertFalse(
            isAdvancedGlassBackdropReady(
                backgroundReady = false,
                contentReady = true,
                requiresContentBackdrop = true,
                canFallbackToBackground = true
            )
        )
        assertTrue(
            isAdvancedGlassBackdropReady(
                backgroundReady = true,
                contentReady = true,
                requiresContentBackdrop = true,
                canFallbackToBackground = false
            )
        )
    }

    @Test
    fun dockAndDialogRolesAllowBackgroundFallback() {
        assertTrue(roleCanFallbackToBackgroundBackdrop(AdvancedGlassRole.MiniPlayer))
        assertTrue(roleCanFallbackToBackgroundBackdrop(AdvancedGlassRole.BottomNavigation))
        assertTrue(roleCanFallbackToBackgroundBackdrop(AdvancedGlassRole.DialogPanel))
        assertTrue(roleCanFallbackToBackgroundBackdrop(AdvancedGlassRole.PopupMenu))
        assertFalse(roleCanFallbackToBackgroundBackdrop(AdvancedGlassRole.ScreenTopTab))
    }

    @Test
    fun dialogPanelRequiresContentBackdropLikePopup() {
        assertTrue(roleRequiresContentBackdrop(AdvancedGlassRole.DialogPanel))
        assertTrue(roleRequiresContentBackdrop(AdvancedGlassRole.MiniPlayer))
        assertTrue(roleRequiresContentBackdrop(AdvancedGlassRole.BottomNavigation))
        assertFalse(roleRequiresContentBackdrop(AdvancedGlassRole.PlaylistSheet))
    }
}
