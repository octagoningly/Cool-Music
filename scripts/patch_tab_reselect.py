from pathlib import Path

# 1) NeriApp: mainTabReselectTick + pass to hosts
app = Path(r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\NeriApp.kt")
text = app.read_text(encoding="utf-8")

if "mainTabReselectTick" not in text:
    text = text.replace(
        "            var settingsTabEntryTick by remember { mutableIntStateOf(0) }",
        "            var settingsTabEntryTick by remember { mutableIntStateOf(0) }\n"
        "            var mainTabReselectTick by remember { mutableIntStateOf(0) }",
        1,
    )

# Insert reselect increment after settingsTabEntryTick++ block in else if
old_else = """                } else if (route == Destinations.Settings.route) {
                    // 再次点击设置 Tab，回到一级页
                    settingsTabEntryTick++
                }"""
new_else = """                } else if (route == Destinations.Settings.route) {
                    // 再次点击设置 Tab，回到一级页
                    settingsTabEntryTick++
                } else if (
                    route == Destinations.Home.route ||
                    route == Destinations.Explore.route ||
                    route == Destinations.Library.route
                ) {
                    // 再次点击主页 Tab，关闭详情并回到顶部
                    mainTabReselectTick++
                }"""
if old_else not in text:
    raise SystemExit("settings else block not found")
text = text.replace(old_else, new_else, 1)

text = text.replace(
    "                        runtimeState = homeHostRuntimeState,",
    "                        runtimeState = homeHostRuntimeState,\n"
    "                        mainTabReselectTick = mainTabReselectTick,",
    1,
)
text = text.replace(
    "                        isTabActive = selectedMainTabRoute == Destinations.Explore.route,",
    "                        isTabActive = selectedMainTabRoute == Destinations.Explore.route,\n"
    "                        mainTabReselectTick = mainTabReselectTick,",
    1,
)
text = text.replace(
    "                    Destinations.Library.route -> LibraryHostScreen(\n"
    "                        onSongClick = ::playSongsAndOpenNowPlaying,",
    "                    Destinations.Library.route -> LibraryHostScreen(\n"
    "                        mainTabReselectTick = mainTabReselectTick,\n"
    "                        onSongClick = ::playSongsAndOpenNowPlaying,",
    1,
)
app.write_text(text, encoding="utf-8")
print("NeriApp patched")

# 2) HomeHostScreen
home = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\host\HomeHostScreen.kt"
)
t = home.read_text(encoding="utf-8")
t = t.replace(
    "    runtimeState: HomeHostRuntimeState = rememberHomeHostRuntimeState(),",
    "    runtimeState: HomeHostRuntimeState = rememberHomeHostRuntimeState(),\n"
    "    mainTabReselectTick: Int = 0,",
    1,
)
if "mainTabReselectTick" in t and "LaunchedEffect(mainTabReselectTick)" not in t:
    # insert after clearPendingHomeScrollRestore function or after selected state
    marker = "    val gridState = runtimeState.gridState\n"
    insert = (
        "    val gridState = runtimeState.gridState\n"
        "    LaunchedEffect(mainTabReselectTick) {\n"
        "        if (mainTabReselectTick <= 0) return@LaunchedEffect\n"
        "        skipDetailCloseAnimation = true\n"
        "        selected = null\n"
        "        clearPendingHomeScrollRestore()\n"
        "        runtimeState.radarPlaylistListState.scrollToItem(0)\n"
        "        gridState.scrollToItem(0)\n"
        "    }\n"
    )
    if marker not in t:
        raise SystemExit("home gridState marker missing")
    t = t.replace(marker, insert, 1)
home.write_text(t, encoding="utf-8")
print("HomeHostScreen patched")

# 3) ExploreHostScreen
exp = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\host\ExploreHostScreen.kt"
)
t = exp.read_text(encoding="utf-8")
# find function signature
if "mainTabReselectTick" not in t:
    t = t.replace(
        "fun ExploreHostScreen(\n",
        "fun ExploreHostScreen(\n    mainTabReselectTick: Int = 0,\n",
        1,
    )
marker = "    val topAppBarState = rememberTopAppBarState()\n    var searchQuery by rememberSaveable { mutableStateOf(\"\") }"
if marker not in t:
    # fallback
    marker = "    val topAppBarState = rememberTopAppBarState()\n"
    insert = (
        "    val topAppBarState = rememberTopAppBarState()\n"
        "    LaunchedEffect(mainTabReselectTick) {\n"
        "        if (mainTabReselectTick <= 0) return@LaunchedEffect\n"
        "        selected = null\n"
        "        pendingGridRestoreIndex = null\n"
        "        pendingGridRestoreOffset = 0\n"
        "        pendingTopAppBarHeightOffset = Float.NaN\n"
        "        pendingTopAppBarContentOffset = Float.NaN\n"
        "        searchListState.scrollToItem(0)\n"
        "        gridState.scrollToItem(0)\n"
        "    }\n"
    )
    t = t.replace(marker, insert, 1)
else:
    insert = (
        "    val topAppBarState = rememberTopAppBarState()\n"
        "    LaunchedEffect(mainTabReselectTick) {\n"
        "        if (mainTabReselectTick <= 0) return@LaunchedEffect\n"
        "        selected = null\n"
        "        pendingGridRestoreIndex = null\n"
        "        pendingGridRestoreOffset = 0\n"
        "        pendingTopAppBarHeightOffset = Float.NaN\n"
        "        pendingTopAppBarContentOffset = Float.NaN\n"
        "        searchListState.scrollToItem(0)\n"
        "        gridState.scrollToItem(0)\n"
        "    }\n"
        "    var searchQuery by rememberSaveable { mutableStateOf(\"\") }"
    )
    t = t.replace(marker, insert, 1)
exp.write_text(t, encoding="utf-8")
print("ExploreHostScreen patched")

# 4) LibraryHostScreen
lib = Path(
    r"D:\study\AI\NeriPlayer\app\src\main\java\moe\ouom\neriplayer\ui\screen\host\LibraryHostScreen.kt"
)
t = lib.read_text(encoding="utf-8")
if "mainTabReselectTick" not in t:
    t = t.replace(
        "fun LibraryHostScreen(\n",
        "fun LibraryHostScreen(\n    mainTabReselectTick: Int = 0,\n",
        1,
    )
marker = "    val topAppBarState = rememberTopAppBarState()\n    fun listStateFor"
if marker not in t:
    marker = "    val topAppBarState = rememberTopAppBarState()\n"
    insert = (
        "    val topAppBarState = rememberTopAppBarState()\n"
        "    LaunchedEffect(mainTabReselectTick) {\n"
        "        if (mainTabReselectTick <= 0) return@LaunchedEffect\n"
        "        selected = null\n"
        "        pendingListRestoreIndex = 0\n"
        "        pendingListRestoreOffset = 0\n"
        "        pendingTopAppBarHeightOffset = Float.NaN\n"
        "        pendingTopAppBarContentOffset = Float.NaN\n"
        "        localListState.scrollToItem(0)\n"
        "        favoriteListState.scrollToItem(0)\n"
        "        neteaseListState.scrollToItem(0)\n"
        "        neteaseAlbumState.scrollToItem(0)\n"
        "        youtubeMusicListState.scrollToItem(0)\n"
        "        biliListState.scrollToItem(0)\n"
        "        qqMusicListState.scrollToItem(0)\n"
        "        topAppBarState.heightOffset = 0f\n"
        "        topAppBarState.contentOffset = 0f\n"
        "    }\n"
    )
    t = t.replace(marker, insert, 1)
else:
    insert = (
        "    val topAppBarState = rememberTopAppBarState()\n"
        "    LaunchedEffect(mainTabReselectTick) {\n"
        "        if (mainTabReselectTick <= 0) return@LaunchedEffect\n"
        "        selected = null\n"
        "        pendingListRestoreIndex = 0\n"
        "        pendingListRestoreOffset = 0\n"
        "        pendingTopAppBarHeightOffset = Float.NaN\n"
        "        pendingTopAppBarContentOffset = Float.NaN\n"
        "        localListState.scrollToItem(0)\n"
        "        favoriteListState.scrollToItem(0)\n"
        "        neteaseListState.scrollToItem(0)\n"
        "        neteaseAlbumState.scrollToItem(0)\n"
        "        youtubeMusicListState.scrollToItem(0)\n"
        "        biliListState.scrollToItem(0)\n"
        "        qqMusicListState.scrollToItem(0)\n"
        "        topAppBarState.heightOffset = 0f\n"
        "        topAppBarState.contentOffset = 0f\n"
        "    }\n"
        "    fun listStateFor"
    )
    t = t.replace(marker, insert, 1)
lib.write_text(t, encoding="utf-8")
print("LibraryHostScreen patched")
print("done")
