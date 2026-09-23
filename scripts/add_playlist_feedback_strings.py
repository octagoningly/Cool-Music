from pathlib import Path

# Strings for add feedback
entries = {
    "values": {
        "playlist_add_already_exists": "已存在，未重复添加",
        "playlist_add_success_one": "已添加到「%1$s」",
    },
    "values-zh": {
        "playlist_add_already_exists": "已存在，未重复添加",
        "playlist_add_success_one": "已添加到「%1$s」",
    },
    "values-en": {
        "playlist_add_already_exists": "Already in playlist",
        "playlist_add_success_one": "Added to \"%1$s\"",
    },
}

base = Path(r"D:\study\AI\NeriPlayer\app\src\main\res")
for folder, items in entries.items():
    path = base / folder / "strings_playlist.xml"
    text = path.read_text(encoding="utf-8")
    if "playlist_add_already_exists" in text:
        print("skip", folder)
        continue
    block = "".join(
        f'    <string name="{name}">{value}</string>\n' for name, value in items.items()
    )
    # insert before closing </resources>
    text = text.replace("</resources>", block + "</resources>")
    path.write_text(text, encoding="utf-8")
    print("updated", folder)
