from pathlib import Path

path = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\playlist\CachedSongsPlaylistScreen.kt"
)
text = path.read_text(encoding="utf-8")

# Replace the multi-chip row with two dropdown filter chips.
start = text.find("                        Row(\n                            horizontalArrangement = Arrangement.spacedBy(8.dp),")
if start < 0:
    raise SystemExit("filter row start not found")
end = text.find("                        Text(\n                            text = stringResource(R.string.cached_songs_showing, visibleSongs.size),")
if end < 0:
    raise SystemExit("showing text not found")

new_ui = '''                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var cacheModeMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                FilterChip(
                                    selected = true,
                                    onClick = { cacheModeMenuExpanded = true },
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
                                    onDismissRequest = { cacheModeMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.cached_songs_filter_complete)) },
                                        onClick = {
                                            onlyComplete = true
                                            cacheModeMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.cached_songs_filter_all)) },
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
                                    onDismissRequest = { artistMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.cached_songs_filter_all_artists)) },
                                        onClick = {
                                            selectedArtist = null
                                            artistMenuExpanded = false
                                        }
                                    )
                                    artistOptions.forEach { artist ->
                                        DropdownMenuItem(
                                            text = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                selectedArtist = artist
                                                artistMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
'''

text = text[:start] + new_ui + text[end:]

# Imports for DropdownMenu / DropdownMenuItem
if "import androidx.compose.material3.DropdownMenu" not in text:
    text = text.replace(
        "import androidx.compose.material3.FilterChip",
        "import androidx.compose.material3.DropdownMenu\n"
        "import androidx.compose.material3.DropdownMenuItem\n"
        "import androidx.compose.material3.FilterChip",
    )

path.write_text(text, encoding="utf-8")
print("filter ui updated")
