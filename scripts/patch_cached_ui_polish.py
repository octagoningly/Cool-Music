from pathlib import Path

path = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\playlist\CachedSongsPlaylistScreen.kt"
)
text = path.read_text(encoding="utf-8")

# Imports
if "import androidx.compose.ui.text.style.TextAlign" not in text:
    text = text.replace(
        "import androidx.compose.ui.text.style.TextOverflow",
        "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow",
    )
if "import androidx.compose.foundation.layout.widthIn" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.size",
        "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.widthIn",
    )
if "import androidx.compose.material.icons.filled.Search" not in text:
    text = text.replace(
        "import androidx.compose.material.icons.filled.Shuffle",
        "import androidx.compose.material.icons.filled.Search\nimport androidx.compose.material.icons.filled.Shuffle",
    )

# Add showSearch state
if "var showSearch by remember" not in text:
    text = text.replace(
        "    var query by remember { mutableStateOf(\"\") }",
        "    var query by remember { mutableStateOf(\"\") }\n    var showSearch by remember { mutableStateOf(false) }",
    )

# TopAppBar: add search action
old_top = '''                TopAppBar(
                    title = { Text(stringResource(R.string.cached_songs_playlist)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },'''
new_top = '''                TopAppBar(
                    title = { Text(stringResource(R.string.cached_songs_playlist)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) query = ""
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search_songs)
                            )
                        }
                    },'''
if old_top not in text:
    raise SystemExit("top bar block not found")
text = text.replace(old_top, new_top, 1)

# Replace filter row + large search field with rounded chips/menus and compact search when active
start = text.find("                        Row(\n                            horizontalArrangement = Arrangement.spacedBy(8.dp),")
end = text.find("                    }\n                }\n                if (visibleSongs.isEmpty())")
if start < 0 or end < 0:
    raise SystemExit(f"filter/search block markers missing start={start} end={end}")

new_block = '''                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var cacheModeMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { cacheModeMenuExpanded = true },
                                    shape = RoundedCornerShape(50),
                                    label = {
                                        Text(
                                            stringResource(
                                                if (onlyComplete) {
                                                    R.string.cached_songs_filter_complete
                                                } else {
                                                    R.string.cached_songs_filter_all
                                                }
                                            )
                                        )
                                    }
                                )
                                DropdownMenu(
                                    expanded = cacheModeMenuExpanded,
                                    onDismissRequest = { cacheModeMenuExpanded = false },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.widthIn(min = 148.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.cached_songs_filter_complete),
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        },
                                        onClick = {
                                            onlyComplete = true
                                            cacheModeMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.cached_songs_filter_all),
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        },
                                        onClick = {
                                            onlyComplete = false
                                            cacheModeMenuExpanded = false
                                        }
                                    )
                                }
                            }

                            var artistMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                FilterChip(
                                    selected = selectedArtist != null,
                                    onClick = { artistMenuExpanded = true },
                                    shape = RoundedCornerShape(50),
                                    label = {
                                        Text(
                                            selectedArtist
                                                ?: stringResource(R.string.cached_songs_filter_all_artists),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                                DropdownMenu(
                                    expanded = artistMenuExpanded,
                                    onDismissRequest = { artistMenuExpanded = false },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.widthIn(min = 168.dp, max = 280.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.cached_songs_filter_all_artists),
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        },
                                        onClick = {
                                            selectedArtist = null
                                            artistMenuExpanded = false
                                        }
                                    )
                                    artistOptions.forEach { artist ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    artist,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center
                                                )
                                            },
                                            onClick = {
                                                selectedArtist = artist
                                                artistMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.cached_songs_showing, visibleSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (showSearch) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.cached_songs_search)) },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
'''

text = text[:start] + new_block + text[end:]
path.write_text(text, encoding="utf-8")
print("cached songs screen patched")
