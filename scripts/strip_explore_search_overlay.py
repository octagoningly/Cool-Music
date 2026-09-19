from pathlib import Path

p = Path(r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\tab\ExploreScreen.kt")
text = p.read_text(encoding="utf-8")

old_state = """    var previousSearchSource by remember { mutableStateOf(ui.selectedSearchSource) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    val isSearchOverlayActive = isSearchFieldFocused && searchQuery.isBlank()
    // 键盘收起后自动退出搜索层，无需再侧滑返回
    val density = LocalDensity.current
    val imeVisible = with(density) { WindowInsets.ime.getBottom(this) > 0 }
    var wasImeVisibleWhileSearchActive by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, isSearchOverlayActive) {
        if (imeVisible && isSearchOverlayActive) {
            wasImeVisibleWhileSearchActive = true
        } else if (!imeVisible && wasImeVisibleWhileSearchActive) {
            wasImeVisibleWhileSearchActive = false
            if (isSearchOverlayActive) {
                isSearchFieldFocused = false
                focusManager.clearFocus()
            }
        }
    }
"""
new_state = """    var previousSearchSource by remember { mutableStateOf(ui.selectedSearchSource) }
"""
if old_state not in text:
    raise SystemExit("state block not found")
text = text.replace(old_state, new_state, 1)
print("state removed")

text = text.replace(
    """    val shouldShowSearchHistory = shouldShowExploreSearchHistory(
        history = visibleSearchHistory,
        contentScrolled = isExploreContentScrolled,
        searchOverlayActive = isSearchOverlayActive
    )
""",
    "",
)
print("shouldShowSearchHistory removed")

text = text.replace(
    """        onSearchQueryChange(normalizedQuery)
        isSearchFieldFocused = false
        focusManager.clearFocus()
""",
    """        onSearchQueryChange(normalizedQuery)
        focusManager.clearFocus()
""",
)
print("submit simplified")

text = text.replace(
    """                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                isSearchFieldFocused = focusState.isFocused
                            }
""",
    """                        modifier = Modifier.fillMaxWidth()
""",
)
print("onFocusChanged removed")

text = text.replace(" &&\n                        !isSearchOverlayActive\n", "\n")
text = text.replace(" && !isSearchOverlayActive\n", "\n")
print("overlay conditions cleaned")

text = text.replace("                    if (!isSearchOverlayActive) {\n", "")
print("if overlay opening for tabs removed")

text = text.replace(
    """                        borderAlpha = tagChipBorderAlpha
                    )
                    }
                }
""",
    """                        borderAlpha = tagChipBorderAlpha
                    )
                }
""",
)
print("extra brace after type bar removed")

text = text.replace("                // 底层探索内容始终保留，供毛玻璃采样\n", "")

start = text.find("                if (isSearchOverlayActive) {")
if start < 0:
    raise SystemExit("overlay start not found")
i = start
depth = 0
end = None
while i < len(text):
    if text[i] == "{":
        depth += 1
    elif text[i] == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            if end < len(text) and text[end] == "\n":
                end += 1
            break
    i += 1
if end is None:
    raise SystemExit("overlay end not found")
print("overlay block bytes", start, end)
print(repr(text[start:end][:120]))
text = text[:start] + text[end:]
print("overlay removed")

bal = 0
for ch in text:
    if ch == "{":
        bal += 1
    elif ch == "}":
        bal -= 1
print("brace balance", bal)

idx2 = text.find("if (showPartsSheet && partsInfo != null)")
print("--- context before parts ---")
print(text[idx2 - 120 : idx2 + 40])

p.write_text(text, encoding="utf-8")
print("written ok")
