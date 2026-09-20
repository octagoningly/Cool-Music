[中文](./README.md) | [English upstream](./README_EN.md)

# Cool Music

## 一、二次开发说明与简介

Cool Music 是基于开源项目 **[NeriPlayer](https://github.com/cwuom/NeriPlayer)**（**GPL-3.0**）的二次开发衍生版，面向 Android 的**本地优先**音频播放器。

| 项 | 说明 |
|----|------|
| 本仓库 | <https://github.com/octagoningly/Cool-Music> |
| 上游项目 | <https://github.com/cwuom/NeriPlayer>（作者：NeriPlayer developers / cwuom） |
| 应用包名 | `moe.ouom.coolmusic`（与上游 `moe.ouom.neriplayer` 独立，可**共存安装**） |
| 显示名称 | Cool Music |
| 许可证 | **GPL-3.0**，与上游一致 |
| 声明 | Cool Music **与原作者无关**；不对上游作任何担保 |

本衍生版在上游「**多源在线探索**、**本地优先数据**、**丰富歌词与播放体验**、**可选自有同步**」的定位上，继续强化：

- **在线音源取流**（LX Music 风格 JSON / JS）
- **歌词补全**（在线音源 / QQ / 酷狗等）
- **首页与歌单交互**（信息架构、去序号、自定义排序）
- **播放页操作**（Apple Music 风格控件）
- **发行与应用内更新**（独立包名、GitHub Releases）

上游原有的多平台播放、本地库、下载、同步、USB 独占、安全模式等能力**默认保留**（见第四节）。

分发时须遵守 **GPL-3.0**：保留版权与许可证声明，并提供对应源码或有效获取方式。完整文本见 [`LICENSE`](./LICENSE)。

> [!WARNING]
> 本项目仅供学习与研究使用，请勿将其用于任何非法用途。
> 请只在你拥有权利、授权或第三方平台规则允许的范围内访问、播放或保存内容。
> 本项目不提供媒体内容、密钥，也不提供规避付费 / DRM / 地区限制的方案，不提供公共媒体代理或再分发服务。
> 在线音源依赖**你自己配置**的第三方接口 / 脚本；会员或受限内容仍需遵循原平台规则与你的账号权限。
> 本项目及维护者不接受任何形式的赞助、捐赠或商业资助。

> [!NOTE]
> 本应用**不提供**公共云端曲库或媒体分发服务。
> 文档中提到的第三方平台能力，仅指在用户具备账号授权时进行搜索、播放兼容与错误恢复，**不代表**绕过平台限制或复制受保护内容。

---

## 二、快速定位

| 你想做什么 | 去哪看 |
|------------|--------|
| 了解本衍生版相对上游**新增**了什么 | 第三节 · 新增的功能 |
| 了解上游**保留**了哪些能力 | 第四节 · 原项目保留功能 |
| 安装 APK 或本地编译调试 | 第五节 · 快速开始 |
| 查找代码入口、模块结构 | 第六节 · 模块与代码入口 |
| 功能演进流水（每次改了什么） | [`更新记录.md`](./更新记录.md) |
| 发行签名、`versionCode`、ADB 约定 | [`开发规则.md`](./开发规则.md) |
| 下载最新安装包 | [Releases · `v0.2.0`](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0) |
| 阅读上游完整文档 | [NeriPlayer README](https://github.com/cwuom/NeriPlayer/blob/master/README.md) |

```text
Cool Music（本仓库结构速览）
├── 新增：LX 在线音源、歌词兜底、首页/歌单交互、播放页与发行更新
├── 保留：Media3 多源播放、本地库与下载、歌词、GitHub/WebDAV 同步、USB 独占等
└── 文档：README · 更新记录.md · 开发规则.md · LICENSE
```

---

## 三、新增的功能

以下为本仓库相对上游的主要**功能性**改动（不含单纯改名 / 换图标），依据 [`更新记录.md`](./更新记录.md) 与代码整理。

### 3.1 在线 LX 音源（核心新能力）

在上游网易云 / Bilibili / YouTube Music / 本地之外，新增 **LX Music 风格第三方在线音源**，在用户已授权的平台曲目上**优先解析播放**。

| 能力 | 说明 |
|------|------|
| 音源导入 | 支持 LX Music **JSON** 与 **JS 脚本**；JS 由内置 **QuickJS** 执行 |
| 优先播放 | `设置 → 第三方平台登录 → 在线音乐源`；封面区显示「**在线音源**」角标 |
| 匹配与回落 | 按**歌名 / 歌手 / 时长**打分，过滤伴奏 / 翻唱等；修复 `songmid` 错配与失败回落顺序 |
| 跨平台兜底 | 主通道不可用时按 **酷我 → 酷狗 → QQ** 等搜原版取流（如网易云故障时用酷我 ID） |
| 通道自检 | 设置内检查**脚本 / API** 是否可用 |
| 每日验证清单 | GitHub Actions 每天搜索并验证候选音源，只保留至少一个通道可用的地址；App 内可获取、复制或直接导入 |
| 导入体验 | 失败可**清空**、可**查看完整地址**；优化过高弹窗与重复探测 |
| 脚本环境 | QuickJS 补齐 `console`、**MD5** 等，便于通过常见 API 校验；播放热路径优化 |

> [!NOTE]
> 在线音源**不会**绕过平台账号权限或版权限制。本仓库**不内置、不维护**任何公共曲库或第三方音源地址。

相关代码：`data/source/lxmusic/`、`core/player/resolver/lxmusic/`、`LxMusicSourceSettings.kt`

### 3.2 歌词补全

- 无歌词时从**在线音源平台**同步；也可从 **QQ / 酷狗公开接口**匹配。
- 识别网易云**占位歌词**，避免空壳歌词挡住真正歌词。
- 修复播放页**占位歌词压过音源 LRC**；兼容 QQ 新搜索字段。
- 歌词按歌曲身份**本地缓存**。

### 3.3 首页与歌曲列表

```text
私人雷达 → 每日推荐 → 雷达歌单 →「更多」
                （更多内：私人 FM / 新歌榜等 / 推荐歌单）
```

- 次级板块收进「**更多**」**按需加载**；首页首次加载**错峰**，降低启动并发。
- 修复展开「更多」后**私人雷达 / 每日推荐被清空**的问题（子集刷新改为合并保留）。
- **全列表去掉 `1/2/3` 序号**；封面左移后回调 **`16dp`** 左边距。
- 歌曲行副标题**只显示歌手**，不再拼接专辑名。
- 移除底部**红色脱机横幅**；网络失败文案改为「**网络不佳**」。

### 3.4 探索页

- 搜索源改为**右侧下拉**，关闭左右滑动切源；类型与来源**整组居中**。
- 上滑**保留**类型图标栏；「**新发现**」为独立全屏二级页，进入探索时重置状态。
- 精选歌单封面冷启动**随机一张**（不再自动轮播）。
- 搜索查找与 `cookie` **重复加载**等性能问题已修。

### 3.5 媒体库与本地歌单

- 本地页「歌单 / 歌手」与「**+ 新建**」同行，**新建靠右**。
- 媒体库 **Tab** 支持**自定义排序**；排序 / 刷新在**顶栏右侧**。
- 本地歌单**自定义排序**（含「**我喜欢的音乐**」）：
  - 拖动排序、拖到底部**自动滚屏**
  - 按**手指位置**判断是否继续上滚
  - **收藏**可保留列表中的位置；「**本地文件**」仍固定末尾

### 3.6 播放页与设置 UI

- **Apple Music 风格**：直线进度条、**大号三键**、底部 **Docker**（播放顺序与常用菜单）。
- 主控圆润图标与三角**光学居中**；封面保留**来源角标**（含「在线音源」）。
- 主 Tab **紧凑大标题**、**常驻合成**、动画缩短；设置重入**回到一级页**。
- 关于页：「**原作者声明**」单行摘要 + **圆角详情弹窗**；「**GitHub**」指向本仓库并可跳转。
- 初次设置去掉第 **5、6** 步（播放控件 / 歌词页）。

### 3.7 包名、发行与更新

| 项 | 说明 |
|----|------|
| `applicationId` | `moe.ouom.coolmusic`，可与 NeriPlayer **共存** |
| 应用内更新 | `设置 → 关于 → 检查更新`，对接 **GitHub Releases** 并覆盖安装 |
| `versionCode` | **分钟级**编码；Release 正文须写 `versionCode=数字` |
| 签名 | 固定 `app/neri.jks`；修复 vivo「无签名 / 软件包无效」 |
| debug 签名 | 密钥就绪时 debug **复用 release 签名**，便于 ADB 覆盖安装 |
| 安装包 | 本地 `dist/apk/`；对外以 GitHub Release 为准（如 [`v0.2.0`](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0)） |
| CI | 已**移除** PR 上的重型 Android / Native CI 工作流 |
| 同步品牌 | GitHub / WebDAV 默认仓库与文件名改为 Cool Music，**兼容旧备份** |
| 调试 | `scripts/install-debug.ps1` 一键 ADB 安装；约定见 [`开发规则.md`](./开发规则.md) |

---

## 四、原项目保留功能（沿用上游）

下列能力来自上游 **NeriPlayer**，本衍生版**默认保留**。完整说明见：[上游 README](https://github.com/cwuom/NeriPlayer/blob/master/README.md)。

### 4.1 定位与技术架构

- 原生 Android（**Compose + Media3**），要求 **Android 9（API 28）+**。
- **账号即能力**：通过第三方平台授权启用搜索、播放、歌单与收藏访问。
- **本地优先**：播放缓存、下载文件、歌单、历史、设置与授权默认保存在设备本地。
- **可选同步**：元数据同步到**用户自己的** GitHub / WebDAV，而不是开发者中心化服务。
- **单 Activity + Compose**：`MainActivity` 为唯一对外入口；`NavHost` + 底栏 + Mini Player + Now Playing 覆盖层。
- **启动与恢复**：`Loading → Disclaimer → Onboarding → Main`；上次崩溃或 ANR 时优先进入**安全模式**。

### 4.2 多源探索与播放

- **平台入口**：网易云音乐、Bilibili、YouTube Music，以及**本地音频**；媒体库可按平台分类浏览。
- **分层搜索**：探索页**按平台独立搜索**；支持链接识别（网易云歌曲/歌单/歌手、B 站视频/收藏夹/合集/UP 主、YouTube 视频/歌单/频道等）。
- **Media3 播放核心**：`PlayerManager` 统一管理**音源解析、队列、随机/循环、状态恢复、失败重试**与链接刷新。
- **网易云**：登录、搜索、歌单/专辑、播放、下载、歌词；无权限时可**降级音质**，并可选 B 站 / 本地兜底（默认关闭）。
- **Bilibili**：Web / 二维码登录、视频与收藏夹/合集、**分 P 转音频**播放与下载；可选 SponsorBlock 与自定义跳过区间。
- **YouTube Music**：登录、首页/媒体库浏览、歌单与搜索播放兼容；含 `Cookie`、`player.js`、`PoToken` 与 **EJS/HLS** 多级回退（受平台规则与账号权限约束）。
- **QQ 音乐**：主要用于播放页**元数据与歌词补全**（无完整登录/库页）。
- **本地音频**：外部分享/打开导入、设备扫描、授权文件夹扫描；识别附近**歌词与封面**；本地歌手聚合与本地歌单管理。
- **失败兜底**：播放异常先**刷新链接**；B 站 `DASH` / 渐进流回退；连续失败跳过或停止，避免卡死。

### 4.3 本地优先数据与脱机

- 播放缓存（默认约 **1GB** `SimpleCache + LRU`）、下载、歌单、历史、播放统计、设置默认在本地。
- **脱机模式**：感知系统网络状态；脱机时停用在线探索与远程图片，仍可使用**本地文件、已下载、缓存封面、本地歌单**。
- **下载链路**：自研下载（非系统 `DownloadManager`）：可调并发、队列持久化、**HTTP / 分块 Range / HLS 续传**、标签与歌词/封面 sidecar；取消会清理半成品。
- **下载目录**：默认应用管理目录，可经 **SAF** 迁移；支持文件名模板。
- **存储分析**：缓存、下载、日志等**分组统计**；清缓存不会误删用户主动下载的歌曲。
- **备份**：歌单 **JSON** 与**完整配置**导入/导出（设置、授权、同步配置可迁移）。

### 4.4 歌词与外链能力

- **仿 Apple Music 歌词**：逐行 / 逐词（`LRC`、`YRC`/`TTML` 等）、翻译、音译、偏移、点击跳转、长按分享与**歌词卡片**。
- 歌词编辑器可**多平台匹配**（酷狗 / 网易云 / QQ / LRCLIB 等，视上游实现）。
- **悬浮歌词**、**状态栏歌词**（部分机型）、`SuperLyric`、`Lyricon`、**蓝牙歌词**等外部联动。

### 4.5 播放音效与硬件

- 倍速、音调、**均衡器**预设/手动频段、响度增强、声道平衡、**淡入淡出 / 交叉淡入淡出**、蓝牙断连暂停、音频焦点策略等。
- **USB 独占播放**：面向 **UAC1.0** 与兼容 **UAC2.0 Type I PCM** 设备；采样率/位深/缓冲、32-bit、**比特完美音量**（软件 `0 dB`）、失败回退系统播放等（详见上游与 `CONTRIBUTING`）。

### 4.6 桌面与系统集成

- **桌面小组件**（如 `4x2` / `2x2` 播放卡片）、**启动器快捷方式**（继续播放、探索、媒体库、随机喜欢等）。
- **Mini Player** 横向滑动切歌；播放页**长按封面**大图预览。
- **动态取色**、主题、UI 缩放、自定义背景、**进阶玻璃模糊**（Android 13+）等个性化项。

### 4.7 可选同步与统计

- **GitHub / WebDAV**：同步本地歌单、收藏、最近播放、删除记录与播放统计到**用户自有远端**；`WorkManager` 延迟/周期任务；数据默认**不上传**到开发者服务器。
- **播放统计**：按歌曲稳定身份记录**次数、时长、日桶**；周/月热点等（实现细节以上游代码为准）。
- **流量统计**：播放/下载流量与网络类型分布，**高风险网络**下载可提示。

### 4.8 一起听（Listen Together）

- 创建/加入房间，**WebSocket** 同时同步播放状态、队列、循环/随机等；可**自建** Cloudflare Workers 服务端。
- 部署与协议见上游 README 与 `np-submodule/NeriPlayer-LTW`。

### 4.9 稳定性与诊断

- **JVM / Native** 崩溃日志、系统 **ANR** 记录、**安全模式**预览与导出。
- 开发者模式：设置页连点**版本号 7 次**后出现 `Debug` 页（探针、日志等）。
- 上游相关测试护栏（下载、同步、歌词、播放策略等）随代码一并保留。

---

## 五、快速开始

### 5.1 下载安装

1. 打开 [Releases](https://github.com/octagoningly/Cool-Music/releases)，下载最新 `CoolMusic-*-arm64*.apk`（当前示例：[`v0.2.0`](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0)）。
2. 使用**同一签名覆盖安装**即可，**无需卸载**（保留登录与本地数据）。
3. 也可在应用内：`设置 → 关于 → 检查更新`。

| 设备 | 选择 |
|------|------|
| 多数手机 | `arm64-v8a` |
| 32 位老旧设备 | `armeabi-v7a` |
| 模拟器 / x86 | 对应 x86 包 |

> 不要把 `*.jks`、密码或 `local.properties` 提交进仓库。

### 5.2 本地构建

```bash
git clone --recursive https://github.com/octagoningly/Cool-Music.git
cd Cool-Music
```

1. 用 **Android Studio** 打开并同步依赖。
2. 配置环境：`JAVA_HOME` → **JDK 17+**，`ANDROID_HOME` → Android SDK。
3. Debug 安装（推荐脚本）：

```powershell
.\scripts\install-debug.ps1
# 无线： .\scripts\install-debug.ps1 -Wireless 192.168.1.100:5555
# 或： .\gradlew.bat :app:installDebug
```

4. Release（需签名，见 [`开发规则.md`](./开发规则.md)）：

```powershell
# 配置 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD 后
.\gradlew.bat :app:assembleRelease
```

5. 首次启动：阅读免责声明并完成引导。设置页连点**版本号 7 次**开启开发者模式。

### 5.3 在线音源快速上手

1. `设置 → 第三方平台登录 → 在线音乐源`
2. 点击「**获取**」读取 GitHub 每日验证清单，可查看、复制或直接导入地址
3. 也可以手动导入 **LX JSON / JS** 与你的服务地址
4. 按需开启**优先播放**，可用**通道自检**
5. 播放时查看封面角标是否为「**在线音源**」

每日清单只记录通过通道测试的音源配置地址与检测结果，不保存音频内容、账号凭据或 Cookie。自动发现会同时使用 GitHub 代码搜索和无需额外令牌的公开仓库扫描；维护者也可以在 `tools_pub/lx_source_candidates.txt` 或仓库变量 `LX_SOURCE_CANDIDATE_URLS` 中添加待验证地址。

---

## 六、模块与代码入口

| 路径 | 用途 |
|------|------|
| `app/` | 主 Android 应用 |
| `app/.../data/source/lxmusic/` | LX 音源导入、解析、QuickJS |
| `app/.../core/player/resolver/lxmusic/` | 在线音源解析、跨平台兜底、歌词 |
| `app/.../ui/screen/tab/HomeScreen.kt` | 首页板块与歌曲行 |
| `app/.../ui/screen/tab/settings/auth/LxMusicSourceSettings.kt` | 在线音源设置 UI |
| `scripts/install-debug.ps1` | ADB 一键安装调试 |
| `开发规则.md` | 发行、签名、`versionCode`、Release、ADB |
| `更新记录.md` | 本仓库功能演进日志 |
| `np-submodule/` | 上游子模块（歌词组件、一起听服务端等） |

技术基线：**Jetpack Compose + Media3**，`minSdk 28`（Android 9+），**Java/Kotlin 17**，`applicationId = moe.ouom.coolmusic`。具体以 `gradle.properties` 与 `app` 模块配置为准。

---

## 隐私与数据

- 设置、授权、下载、统计默认在设备本地；同步写入**你自己配置**的远端。
- 在线音源请求发往**你配置的**接口 / 脚本，本项目不提供公共曲库。

---

## 上游与许可证

代码来源于 [cwuom/NeriPlayer](https://github.com/cwuom/NeriPlayer)，遵循 **GNU GPL v3**。

- 分发 APK 须同时提供源码或有效获取方式，并保留版权与许可证信息
- 许可见 [`LICENSE`](./LICENSE)
- 提交信息风格：`修复xx功能_n` / `新增xx功能_n`；完成后更新 [`更新记录.md`](./更新记录.md)

---

## 源码仓库

| 项目 | 地址 |
|------|------|
| Cool Music（本衍生版） | <https://github.com/octagoningly/Cool-Music> |
| NeriPlayer（上游） | <https://github.com/cwuom/NeriPlayer> |
| 最新发行 | [v0.2.0](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0) |
