from pathlib import Path

root = Path(r"D:\study\AI\NeriPlayer\app\src\main\java")

# 1) Favorite heart: full red (not translucent)
fav_tint_old = "Color.Red.copy(alpha = 0.6f)"
fav_tint_new = "Color(0xFFE53935)"

changed = []
for path in root.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    orig = text
    text = text.replace(fav_tint_old, fav_tint_new)
    if text != orig:
        path.write_text(text, encoding="utf-8")
        changed.append(path.name)
print("tint sites", changed)

# 2) Menu favorite icons: add red tint when favorited
# Home / Explore style blocks without tint
old_icon_home = """                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = null
                        )
                    },"""
new_icon_home = """                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = null,
                            tint = if (isFavorite) {
                                Color(0xFFE53935)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },"""

for name in [
    "ui/screen/tab/HomeScreen.kt",
    "ui/screen/tab/ExploreScreen.kt",
    "ui/screen/playlist/BiliPlaylistDetailScreen.kt",
    "ui/screen/playlist/NeteaseCollectionDetailScreen.kt",
    "ui/screen/RecentScreen.kt",
    "ui/screen/playlist/LocalPlaylistDetailScreen.kt",
]:
    path = root / name
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    if old_icon_home in text:
        text = text.replace(old_icon_home, new_icon_home)
        path.write_text(text, encoding="utf-8")
        changed.append(name + ":menu")
        print("menu tint", name)

print("done")
