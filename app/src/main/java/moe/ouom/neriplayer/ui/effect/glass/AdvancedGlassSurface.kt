package moe.ouom.neriplayer.ui.effect.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified as isColorSpecified
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun roleRequiresContentBackdrop(role: AdvancedGlassRole): Boolean =
    role == AdvancedGlassRole.MiniPlayer ||
        role == AdvancedGlassRole.BottomNavigation ||
        role == AdvancedGlassRole.ScreenTopTab ||
        role == AdvancedGlassRole.ExploreSearchOverlay ||
        role == AdvancedGlassRole.PopupMenu ||
        role == AdvancedGlassRole.FeedbackBanner ||
        role == AdvancedGlassRole.DialogPanel

/**
 * MiniPlayer / 底栏在媒体库等页面 content 捕获层短暂未就绪时，
 * 仍允许用背景层采样出玻璃，避免 dock 与迷你播放器整块退化成实底。
 */
internal fun roleCanFallbackToBackgroundBackdrop(role: AdvancedGlassRole): Boolean =
    role == AdvancedGlassRole.MiniPlayer ||
        role == AdvancedGlassRole.BottomNavigation ||
        role == AdvancedGlassRole.ScreenTopTab ||
        role == AdvancedGlassRole.ExploreSearchOverlay ||
        role == AdvancedGlassRole.PopupMenu ||
        role == AdvancedGlassRole.FeedbackBanner ||
        role == AdvancedGlassRole.DialogPanel

internal fun isAdvancedGlassBackdropReady(
    backgroundReady: Boolean,
    contentReady: Boolean,
    requiresContentBackdrop: Boolean,
    canFallbackToBackground: Boolean
): Boolean {
    if (!backgroundReady) return false
    if (!requiresContentBackdrop) return true
    return contentReady || canFallbackToBackground
}

internal fun isAdvancedGlassNavigationOwnerActive(
    requiresContentBackdrop: Boolean,
    activeNavigationOwners: Set<Any>?,
    navigationOwner: Any?
): Boolean = requiresContentBackdrop ||
    activeNavigationOwners == null ||
    navigationOwner in activeNavigationOwners

internal fun shouldRegisterAdvancedGlassRegion(
    sceneActive: Boolean,
    backdropRegistrationEnabled: Boolean,
    belongsToActiveNavigationScreen: Boolean,
    belongsToPrewarmedNavigationScreen: Boolean
): Boolean = sceneActive && backdropRegistrationEnabled &&
    (belongsToActiveNavigationScreen || belongsToPrewarmedNavigationScreen)

internal fun shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
    suppressInactiveNavigationSurface: Boolean,
    canRenderGlass: Boolean,
    belongsToActiveNavigationScreen: Boolean,
    belongsToPrewarmedNavigationScreen: Boolean
): Boolean = suppressInactiveNavigationSurface &&
    canRenderGlass &&
    !belongsToActiveNavigationScreen &&
    belongsToPrewarmedNavigationScreen

/**
 * 注册从关到开时用来补注册的 bounds。
 * 不能只等 onGloballyPositioned：位置不再变化时它不会触发（GlassDropdownMenu 踩坑）。
 */
internal fun resolveAdvancedGlassRegionRestoreBounds(
    registersBackdrop: Boolean,
    regionBoundsOverride: androidx.compose.ui.geometry.Rect?,
    measuredBounds: androidx.compose.ui.geometry.Rect?
): androidx.compose.ui.geometry.Rect? = when {
    !registersBackdrop -> null
    regionBoundsOverride != null -> regionBoundsOverride
    else -> measuredBounds
}

@Composable
internal fun AdvancedGlassSurface(
    role: AdvancedGlassRole,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    fallbackColor: Color = Color.Transparent,
    tintColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    suppressInactiveNavigationSurface: Boolean = false,
    regionBoundsOverride: androidx.compose.ui.geometry.Rect? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val controller = LocalAdvancedGlassController.current
    val availableBackdrops = LocalAdvancedGlassBackdrops.current
    val glassDepth = LocalAdvancedGlassDepth.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigationOwner = LocalAdvancedGlassNavigationOwner.current ?: lifecycleOwner
    val activeNavigationOwners = LocalAdvancedGlassActiveNavigationOwners.current
    val prewarmedNavigationOwners = LocalAdvancedGlassPrewarmedNavigationOwners.current
    val sceneActive = LocalAdvancedGlassSceneActive.current
    val backdropRegistrationEnabled = LocalAdvancedGlassBackdropRegistrationEnabled.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isDarkTheme = isSystemInDarkTheme()
    val enhancedBlurRadiusDp = if (controller.isBaseBlurEnabled) {
        controller.normalizedBlurAmountDp
    } else {
        null
    }
    val tokens = advancedGlassTokens(role, isDarkTheme, enhancedBlurRadiusDp)
    val resolvedTintColor = if (tintColor.isColorSpecified) tintColor else advancedGlassRoleColor(role)
    val edgeBaseColor = MaterialTheme.colorScheme.onSurface
    val requiresContentBackdrop = roleRequiresContentBackdrop(role)
    val backdropsReady = availableBackdrops?.let { backdrops ->
        isAdvancedGlassBackdropReady(
            backgroundReady = backdrops.background.positionInWindow.isSpecified,
            contentReady = backdrops.content.positionInWindow.isSpecified,
            requiresContentBackdrop = requiresContentBackdrop,
            canFallbackToBackground = roleCanFallbackToBackgroundBackdrop(role)
        )
    } == true
    val belongsToActiveNavigationScreen = isAdvancedGlassNavigationOwnerActive(
        requiresContentBackdrop = requiresContentBackdrop,
        activeNavigationOwners = activeNavigationOwners,
        navigationOwner = navigationOwner
    )
    val belongsToPrewarmedNavigationScreen = navigationOwner in prewarmedNavigationOwners
    val canRenderGlass = enabled && sceneActive && backdropsReady &&
        canSampleAdvancedGlassBackdrop(controller, glassDepth, role)
    val glassEnabled = canRenderGlass && belongsToActiveNavigationScreen
    val suppressesInactiveNavigationSurface =
        shouldSuppressAdvancedGlassSurfaceForInactiveNavigationOwner(
        suppressInactiveNavigationSurface = suppressInactiveNavigationSurface,
        canRenderGlass = canRenderGlass,
        belongsToActiveNavigationScreen = belongsToActiveNavigationScreen,
        belongsToPrewarmedNavigationScreen = belongsToPrewarmedNavigationScreen
    )
    val registersBackdrop = canRenderGlass && shouldRegisterAdvancedGlassRegion(
        sceneActive = sceneActive,
        backdropRegistrationEnabled = backdropRegistrationEnabled,
        belongsToActiveNavigationScreen = belongsToActiveNavigationScreen,
        belongsToPrewarmedNavigationScreen = belongsToPrewarmedNavigationScreen
    )
    val regionKey = remember { Any() }

    var measuredBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    fun updateRegion(bounds: androidx.compose.ui.geometry.Rect?) {
        val registry = availableBackdrops?.regionRegistry ?: return
        if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
            registry.remove(regionKey)
            return
        }
        registry.update(
            regionKey,
            AdvancedGlassRegion(
                role = role,
                boundsInWindow = bounds,
                cornerRadiiPx = resolveCornerRadiiPx(
                    shape = shape,
                    size = bounds.size,
                    layoutDirection = layoutDirection,
                    density = density
                ),
                navigationOwner = if (requiresContentBackdrop) null else navigationOwner
            )
        )
    }

    // 始终记录 bounds：播放页开关时注册会被关掉再开，
    // 若只在 registersBackdrop 时挂 onGloballyPositioned，
    // 重新打开时位置往往已稳定，回调不会触发，区域就永远丢了。
    val regionRegistrationModifier = Modifier.onGloballyPositioned { coordinates ->
        val registry = availableBackdrops?.regionRegistry
        if (!coordinates.isAttached) {
            if (registersBackdrop) {
                registry?.remove(regionKey)
            }
            measuredBounds = null
            return@onGloballyPositioned
        }
        // Popup 内 boundsInWindow 是弹窗本地坐标，必须用已换算的 override
        if (role == AdvancedGlassRole.PopupMenu && regionBoundsOverride == null) {
            if (registersBackdrop) {
                registry?.remove(regionKey)
            }
            return@onGloballyPositioned
        }
        val bounds = regionBoundsOverride ?: coordinates.boundsInWindow()
        measuredBounds = bounds
        if (registersBackdrop) {
            updateRegion(bounds)
        }
    }

    DisposableEffect(availableBackdrops, regionKey, registersBackdrop) {
        val registry = availableBackdrops?.regionRegistry
        if (!registersBackdrop) {
            registry?.remove(regionKey)
        }
        onDispose {
            registry?.remove(regionKey)
        }
    }

    // 注册重新打开时立刻用最近一次 bounds 补注册；
    // override 就绪后 position 也可能不再变化（见 GlassDropdownMenu 踩坑）。
    LaunchedEffect(availableBackdrops, regionKey, registersBackdrop, regionBoundsOverride) {
        val registry = availableBackdrops?.regionRegistry
        if (!registersBackdrop) {
            registry?.remove(regionKey)
            return@LaunchedEffect
        }
        val bounds = resolveAdvancedGlassRegionRestoreBounds(
            registersBackdrop = registersBackdrop,
            regionBoundsOverride = regionBoundsOverride,
            measuredBounds = measuredBounds
        ) ?: return@LaunchedEffect
        measuredBounds = bounds
        updateRegion(bounds)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(regionRegistrationModifier)
    ) {
        if (glassEnabled) {
            GlassColorLayer(
                shape = shape,
                color = resolvedTintColor.copy(
                    alpha = resolvedTintColor.alpha * tokens.tintAlpha
                )
            )
        } else if (
            fallbackColor != Color.Transparent &&
            !suppressesInactiveNavigationSurface
        ) {
            GlassColorLayer(shape, fallbackColor)
        }

        CompositionLocalProvider(
            LocalAdvancedGlassDepth provides if (glassEnabled) glassDepth + 1 else glassDepth
        ) {
            content()
        }

        if (glassEnabled && tokens.edgeAlpha > 0f) {
            GlassEdgeLayer(
                role = role,
                shape = shape,
                color = edgeBaseColor.copy(alpha = tokens.edgeAlpha)
            )
        }
    }
}

private fun resolveCornerRadiiPx(
    shape: Shape,
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: androidx.compose.ui.unit.Density
): AdvancedGlassCornerRadii = when (
    val outline = shape.createOutline(size, layoutDirection, density)
) {
    is Outline.Rounded -> {
        val roundRect = outline.roundRect
        AdvancedGlassCornerRadii(
            topLeft = roundRect.topLeftCornerRadius.x,
            topRight = roundRect.topRightCornerRadius.x,
            bottomRight = roundRect.bottomRightCornerRadius.x,
            bottomLeft = roundRect.bottomLeftCornerRadius.x
        )
    }
    else -> AdvancedGlassCornerRadii.Zero
}

@Composable
private fun BoxScope.GlassColorLayer(shape: Shape, color: Color) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val path = Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                }
                onDrawBehind { drawPath(path = path, color = color) }
            }
    )
}

@Composable
private fun BoxScope.GlassEdgeLayer(
    role: AdvancedGlassRole,
    shape: Shape,
    color: Color
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val path = Path().apply {
                    addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                }
                val stroke = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    if (role == AdvancedGlassRole.BottomNavigation) {
                        drawLine(
                            color = color,
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = stroke.width
                        )
                    } else {
                        drawPath(path = path, color = color, style = stroke)
                    }
                }
            }
    )
}

@Composable
private fun advancedGlassRoleColor(role: AdvancedGlassRole): Color = when (role) {
    AdvancedGlassRole.MiniPlayer -> MaterialTheme.colorScheme.secondaryContainer
    AdvancedGlassRole.BottomNavigation,
    AdvancedGlassRole.ScreenTopTab,
    AdvancedGlassRole.SettingsGroup,
    AdvancedGlassRole.SettingsSection -> MaterialTheme.colorScheme.surfaceContainerHighest
    AdvancedGlassRole.SettingsHeader -> MaterialTheme.colorScheme.primaryContainer
    AdvancedGlassRole.PlaylistSheet,
    AdvancedGlassRole.PopupMenu,
    AdvancedGlassRole.DialogPanel,
    AdvancedGlassRole.FeedbackBanner,
    AdvancedGlassRole.SemanticCard -> MaterialTheme.colorScheme.surfaceContainerHigh
    AdvancedGlassRole.ExploreTag -> MaterialTheme.colorScheme.surface
    AdvancedGlassRole.ExploreSearchOverlay -> MaterialTheme.colorScheme.surfaceContainerHighest
    AdvancedGlassRole.ThemeModeToggle -> MaterialTheme.colorScheme.surfaceVariant
    AdvancedGlassRole.InlineControl -> Color.Transparent
}
