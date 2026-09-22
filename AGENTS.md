# AGENTS.md — Cool Music 开发代理必读

给 AI / 自动化协作者的 **3 分钟上手**。发版细节见 [`开发规则.md`](./开发规则.md)，架构与扩展见 [`CONTRIBUTING.md`](./CONTRIBUTING.md)、[`README.md`](./README.md)。

## 身份（禁止随意改）

| 项 | 值 |
|----|-----|
| 显示名 | Cool Music |
| applicationId | `moe.ouom.coolmusic`（**禁止改**，改了丢用户数据） |
| Kotlin 包名 / namespace | `moe.ouom.neriplayer`（可保持，不必全库重命名） |
| 本仓库 | <https://github.com/octagoningly/Cool-Music> |
| 上游 | <https://github.com/cwuom/NeriPlayer>（GPL-3.0） |

## 常用命令

```powershell
# 单元测试（PR 自检首选）
.\gradlew.bat :app:testDebugUnitTest

# Debug 构建并 ADB 安装（推荐）
.\scripts\install-debug.ps1
# 无线： .\scripts\install-debug.ps1 -Wireless 192.168.1.100:5555

# 仅构建
.\gradlew.bat :app:assembleDebug

# Release（须签名就绪，见开发规则.md §2）
.\gradlew.bat :app:assembleRelease
```

- 包名：`moe.ouom.coolmusic`
- 启动：`adb shell am start -n moe.ouom.coolmusic/moe.ouom.neriplayer.activity.MainActivity`
- 日志 tag 前缀：`NERI-` / `[NeriPlayer]`
- 签名凭据只放 `~/.gradle/gradle.properties`（`KEYSTORE_*`），**禁止写入仓库文件**（含 `local.properties`）
- 本地 APK 统一放 `dist/apk/`（已 gitignore），对外只走 GitHub Release

## 模块地图（改哪里）

| 要改 | 入口 |
|------|------|
| 播放 / 取流 / 兜底 | `app/src/main/java/moe/ouom/neriplayer/core/player/`（核心 `PlayerManager.kt`） |
| LX 在线音源解析 | `core/player/resolver/lxmusic/` + `data/source/lxmusic/` |
| 外部歌单导入 | `data/local/playlist/importer/ExternalPlaylistImport.kt` |
| 首页板块 | `ui/screen/tab/HomeScreen.kt` + `ui/viewmodel/tab/HomeViewModel.kt` |
| 探索页 | `ui/screen/tab/`（Explore*）+ `ui/viewmodel/tab/ExploreViewModel.kt` |
| 播放页 UI | `ui/screen/NowPlayingScreen.kt`（**巨型文件，改前先定位区块**） |
| 设置页 | `ui/screen/tab/settings/` + `ui/screen/tab/SettingsScreen.kt` |
| 应用内更新 | `core/update/GitHubAppUpdateChecker.kt` / `AppUpdateInstaller.kt` |
| 约定插件 / versionCode | `build-logic/convention/src/main/kotlin/Common.kt` |
| 发行脚本 | `scripts/install-debug.ps1`、`scripts/sign-release.ps1` |

数据流简图：`ui → viewmodel → core/player|api|download → data/*`。

## 高频任务路径

| 任务 | 怎么做 |
|------|--------|
| 加一个设置项 | `ksp-annotations` 的 `@AutoSetting` → `data/settings/` 存取 → `ui/screen/tab/settings/` 挂 UI；跑 `AutoSettingsGeneratedTest` |
| 加 / 修 LX 音源行为 | `data/source/lxmusic/`（导入/解析/QuickJS）+ `core/player/resolver/lxmusic/`（取流/搜索/歌词） |
| 改播放失败兜底 | `core/player/policy/failure/`、`core/player/url/`、`core/player/resolver/` |
| 改首页次级板块 | `HomeViewModel` / `HomeScreen`，注意「更多」合并刷新逻辑 |
| 改歌词补全 | `core/api/lyrics/`、`core/player/metadata/PlayerLyricsProvider.kt` |
| 改发行 / 发版 | **只**按 [`开发规则.md`](./开发规则.md) §3–§4、§7 清单 |

## 测试与验收

- 单元测试：`app/src/test/`（约 400+）；仪器测试：`app/src/androidTest/`
- 改播放 / 下载 / 同步 / 歌词 / 设置策略时，优先搜同名 `*Test.kt` 并补覆盖
- 提交前至少跑：`.\gradlew.bat :app:testDebugUnitTest`

## 协作约定

1. **在工作分支上改**；同步 GitHub 以 [`开发规则.md`](./开发规则.md) §6 为准（rebase 优先，禁止 force-push `main`，冲突优先两边都保留；签名 / applicationId / versionCode 冲突必须人工确认）
2. 提交信息：`修复xx功能_n` / `新增xx功能_n`（全仓统一）
3. 每完成一项任务，更新根目录 [`更新记录.md`](./更新记录.md)：新条目**插在 `# 更新记录` 正下方**（新在上），格式 `lrq--简短说明--YYYY-MM-DD`
4. **禁止提交**：`*.jks`、`*.keystore`、密码、Token、Cookie、完整配置备份、`logs*.txt`、`__pycache__`、`*.pyc`、`dist/` 安装包、IDE 本地配置、缓存/临时构建产物；签名口令只放 `~/.gradle/gradle.properties` 或 GitHub Secrets
5. Debug APK **禁止**上传 GitHub Release；正式发版只用 `assembleRelease` 签名包

## 文档地图

| 文档 | 用途 |
|------|------|
| `README.md` | 用户向：功能、安装、快速开始、模块入口 |
| `开发规则.md` | **发版 / 签名 / versionCode / ADB**（冲突时最高优先） |
| `CONTRIBUTING.md` | 实现边界、扩展路径、质量护栏 |
| `更新记录.md` | 功能演进流水 |
| 本文件 `AGENTS.md` | AI / 自动化协作者入口 |
