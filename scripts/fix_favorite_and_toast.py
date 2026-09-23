from pathlib import Path

root = Path(r"D:\study\AI\NeriPlayer\app\src\main\java")
RED = "Color(0xFFE53935)"

# Find favorite icon blocks that have no tint= and add red tint when favorited.
import re

pattern = re.compile(
    r"(imageVector = if \(isFavorite\) \{\s*Icons\.Filled\.Favorite\s*\} else \{\s*Icons\.Outlined\.FavoriteBorder\s*\},\s*contentDescription = null\s*\n)(\s*)\)",
    re.M,
)

def repl(m: re.Match) -> str:
    indent = m.group(2)
    return (
        m.group(1)
        + indent
        + "tint = if (isFavorite) {\n"
        + indent
        + "    "
        + RED
        + "\n"
        + indent
        + "} else {\n"
        + indent
        + "    MaterialTheme.colorScheme.onSurface\n"
        + indent
        + "}\n"
        + indent
        + ")"
    )

changed = []
for path in root.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    new_text, n = pattern.subn(repl, text)
    if n:
        # ensure Color import
        if "import androidx.compose.ui.graphics.Color" not in new_text and "Color(0xFFE53935)" in new_text:
            new_text = new_text.replace(
                "import androidx.compose.runtime.Composable",
                "import androidx.compose.runtime.Composable\nimport androidx.compose.ui.graphics.Color",
                1,
            )
        path.write_text(new_text, encoding="utf-8")
        changed.append(f"{path.name}:{n}")

print("menu favorites", changed)

# StyledToast: more rounded + softer translucent dark (less harsh)
app = root / "ui/feedback/AppFeedback.kt"
text = app.read_text(encoding="utf-8")
text = text.replace(
    "private const val StyledToastBackgroundColor = 0xFF323232.toInt()",
    "private const val StyledToastBackgroundColor = 0xE62B2B2E.toInt()",
)
text = text.replace(
    "cornerRadius = dp(8).toFloat()",
    "cornerRadius = dp(20).toFloat()",
)
app.write_text(text, encoding="utf-8")
print("styled toast softened")

# NeriSnackbar: rounded 24 + soft glass-like colors (not harsh white)
old_snack = """    Snackbar(
        modifier = Modifier
            .padding(12.dp)
            .testTag(NeriSnackbarTestTag),"""
new_snack = """    Snackbar(
        modifier = Modifier
            .padding(12.dp)
            .testTag(NeriSnackbarTestTag),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionContentColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,"""
if old_snack in text or old_snack in app.read_text(encoding="utf-8"):
    text = app.read_text(encoding="utf-8")
    text = text.replace(old_snack, new_snack, 1)
    app.write_text(text, encoding="utf-8")
    print("neri snackbar rounded")
else:
    print("snackbar anchor missing")
