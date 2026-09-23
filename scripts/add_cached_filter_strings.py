from pathlib import Path

entries = {
    "values": {
        "cached_songs_filter_complete": "完整缓存",
        "cached_songs_filter_all": "全部",
        "cached_songs_filter_artist": "歌手",
        "cached_songs_filter_all_artists": "全部歌手",
        "cached_songs_showing": "显示 %1$d 首",
    },
    "values-zh": {
        "cached_songs_filter_complete": "完整缓存",
        "cached_songs_filter_all": "全部",
        "cached_songs_filter_artist": "歌手",
        "cached_songs_filter_all_artists": "全部歌手",
        "cached_songs_showing": "显示 %1$d 首",
    },
    "values-en": {
        "cached_songs_filter_complete": "Fully cached",
        "cached_songs_filter_all": "All",
        "cached_songs_filter_artist": "Artist",
        "cached_songs_filter_all_artists": "All artists",
        "cached_songs_showing": "Showing %1$d songs",
    },
}

base = Path(r"D:\study\AI\NeriPlayer\app\src\main\res")
for folder, items in entries.items():
    path = base / folder / "strings_local_media.xml"
    text = path.read_text(encoding="utf-8")
    if "cached_songs_filter_complete" in text:
        print("skip", folder)
        continue
    block = "".join(
        f'    <string name="{name}">{value}</string>\n' for name, value in items.items()
    )
    anchor = '<string name="cached_songs_group_other">'
    idx = text.find(anchor)
    if idx < 0:
        raise SystemExit(f"anchor missing in {folder}")
    line_end = text.find("\n", idx)
    text = text[: line_end + 1] + block + text[line_end + 1 :]
    path.write_text(text, encoding="utf-8")
    print("updated", folder)
