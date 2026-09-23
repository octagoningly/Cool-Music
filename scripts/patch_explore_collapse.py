from pathlib import Path

path = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\tab\ExploreScreen.kt"
)
text = path.read_text(encoding="utf-8")

anchor = """                    Spacer(Modifier.height(8.dp))
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    val currentSearchSource = ui.selectedSearchSource
                    // 第一行：左侧搜索类型（歌曲/歌单/歌手），右侧搜索源按钮 —— 整组水平居中
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {"""

replacement = """                    Spacer(Modifier.height(8.dp))
                    var sourceMenuExpanded by remember { mutableStateOf(false) }
                    val currentSearchSource = ui.selectedSearchSource
                    val activeListState = when {
                        searchQuery.isNotEmpty() -> searchListState
                        ui.selectedSearchSource == SearchSource.NETEASE -> gridState
                        ui.selectedSearchSource == SearchSource.YOUTUBE_MUSIC -> youtubeGridState
                        else -> searchListState
                    }
                    val showTypeFilterLayer by remember(activeListState) {
                        derivedStateOf {
                            activeListState.firstVisibleItemIndex == 0 &&
                                activeListState.firstVisibleItemScrollOffset < 48
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTypeFilterLayer,
                        enter = androidx.compose.animation.expandVertically() +
                            androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() +
                            androidx.compose.animation.fadeOut(),
                    ) {
                    // 第一行：左侧搜索类型（歌曲/歌单/歌手），右侧搜索源按钮 —— 整组水平居中
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {"""

if anchor not in text:
    raise SystemExit("anchor not found")
text = text.replace(anchor, replacement, 1)

# Close AnimatedVisibility after the type/source Row. Find the unique DropdownMenu block that follows
# the source button and close before the next sibling content.
# The Row contains ExploreSearchTypeBar + Box(source button + DropdownMenu). After that Row's closing `}`
# we need one more `}` for AnimatedVisibility.

# Find a unique marker after the Row content - the DropdownMenu for search sources ends then more UI.
marker = """                            DropdownMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            ) {
                                orderedSearchSources.forEach { source ->
                                    DropdownMenuItem(
                                        text = { Text(searchSourceLabel(source)) },
                                        onClick = {
                                            sourceMenuExpanded = false
                                            if (source != currentSearchSource) {
                                                vm.setSearchSource(source)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
"""
if marker not in text:
    raise SystemExit("dropdown close marker not found")
text = text.replace(
    marker,
    marker.replace(
        """                        }
                    }
""",
        """                        }
                    }
                    }
""",
        1,
    ),
    1,
)

path.write_text(text, encoding="utf-8")
print("collapse wrapper applied")
