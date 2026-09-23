from pathlib import Path

path = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\effect\glass\AdvancedGlassSurface.kt"
)
text = path.read_text(encoding="utf-8")

start = text.find("    val regionRegistrationModifier = if (registersBackdrop) {")
end = text.find("    Box(\n        modifier = modifier\n            .clip(shape)")
if start < 0 or end < 0:
    raise SystemExit(f"markers missing start={start} end={end}")

replacement = '''    var measuredBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

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

    val regionRegistrationModifier = if (registersBackdrop) {
        Modifier.onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) {
                availableBackdrops?.regionRegistry?.remove(regionKey)
                measuredBounds = null
                return@onGloballyPositioned
            }
            // Popup 内 boundsInWindow 是弹窗本地坐标，必须用已换算的 override
            if (role == AdvancedGlassRole.PopupMenu && regionBoundsOverride == null) {
                availableBackdrops?.regionRegistry?.remove(regionKey)
                return@onGloballyPositioned
            }
            val bounds = regionBoundsOverride ?: coordinates.boundsInWindow()
            measuredBounds = bounds
            updateRegion(bounds)
        }
    } else {
        Modifier
    }

    // override 就绪后 position 可能不再变化，必须在此补注册
    LaunchedEffect(regionBoundsOverride, registersBackdrop) {
        if (!registersBackdrop) {
            availableBackdrops?.regionRegistry?.remove(regionKey)
            return@LaunchedEffect
        }
        if (regionBoundsOverride != null) {
            measuredBounds = regionBoundsOverride
            updateRegion(regionBoundsOverride)
        }
    }

'''

text = text[:start] + replacement + text[end:]

if "import androidx.compose.runtime.LaunchedEffect" not in text:
    text = text.replace(
        "import androidx.compose.runtime.Composable",
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect",
        1,
    )
if "import androidx.compose.runtime.mutableStateOf" not in text:
    text = text.replace(
        "import androidx.compose.runtime.remember",
        "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember",
        1,
    )
if "import androidx.compose.runtime.getValue" not in text:
    text = text.replace(
        "import androidx.compose.runtime.mutableStateOf",
        "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf",
        1,
    )
if "import androidx.compose.runtime.setValue" not in text:
    text = text.replace(
        "import androidx.compose.runtime.remember",
        "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue",
        1,
    )

path.write_text(text, encoding="utf-8")
print("AdvancedGlassSurface patched")
