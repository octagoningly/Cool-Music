[中文](./README.md) | [English upstream](./README_EN.md)

# Cool Music

> 基于 **[NeriPlayer](https://github.com/cwuom/NeriPlayer)**（GPL-3.0）二次开发的 Android 音乐播放器衍生版。

### 在上游「多源在线 / 本地优先 / 歌词体验 / 可选同步」的基础上，强化了**第三方在线音源取流、歌词补全、播放页操作与启动性能**

- 仓库：<https://github.com/octagoningly/Cool-Music>
- 上游项目：<https://github.com/cwuom/NeriPlayer>
- 应用包名（applicationId）：`moe.ouom.coolmusic`（与上游 NeriPlayer 的 `moe.ouom.neriplayer` 独立，可共存安装）
- 许可证：GPL-3.0（与上游一致，分发时须保留版权与许可证，并提供对应源码获取方式）
- 声明：Cool Music 与原作者无关；详细能力以本仓库代码与 `更新记录.md` 为准

> [!WARNING]
> 本项目仅供学习与研究使用，请勿用于任何非法用途。
> 请只在你拥有权利、授权或第三方平台规则允许的范围内访问、播放或保存内容。
> 本项目不提供媒体内容、密钥，也不提供规避付费 / DRM / 地区限制的方案。
> 在线音源依赖你自己配置的第三方接口 / 脚本，账号与内容权限仍受原平台约束。

---

## 快速定位

- 只想了解本衍生版改了什么 → [相对上游的功能性改动](#相对上游的功能性改动)
- 想体验安装或本地构建 → [快速开始](#快速开始)
- 想看上游完整能力（多源探索、本地库、同步、一起听等） → [上游核心能力](#上游核心能力沿用) 或直接阅读 [上游 README](https://github.com/cwuom/NeriPlayer/blob/master/README.md)
- 实现演进明细 → 根目录 [`更新记录.md`](./更新记录.md)

```text
Cool Music（本仓库）
├── 在线 LX 音源：JSON / JS 脚本导入、优先播放、跨平台兜底
├── 歌词补全：无歌词时从在线音源平台同步，并识别网易云占位歌词
├── 播放体验：Apple Music 风格播放页、大号三键、底部 Docker 菜单
├── 启动与导航：首页按需加载、主 Tab 常驻合成、设置重入回到一级
└── 上游能力沿用：Media3 多源播放、本地库、歌词、下载、可选 GitHub/WebDAV 同步
```

---

## 相对上游的功能性改动

以下均来自本仓库实际代码与 `更新记录.md`，按功能域整理；纯品牌 / 图标替换不在此展开。

### 1. 在线 LX 音源（核心新能力）

上游播放链路以网易云 / Bilibili / YouTube Music 与本地文件为主。本衍生版新增 **LX Music 风格在线音源**，接入用户自己的第三方音源服务，在**已登录的第三方平台曲目**上优先解析并播放。

| 能力 | 说明 |
|------|------|
| 音源导入 | 支持 LX Music **JSON** 与 **JS 脚本** 两种格式导入；JS 音源由内置 **QuickJS** 执行 |
| 优先播放 | 设置 → 第三方平台登录 → 在线音乐源：导入后可开启优先级，解析时优先走在线音源 |
| 源角标 | 实际走在线音源时，播放页封面区显示「在线音源」角标，便于确认当前取流来源 |
| 匹配与回落 | 按歌名 / 歌手 / 时长打分匹配候选，过滤伴奏 / 翻唱 / 铃声等同名异版；`songmid` 等 ID 错配已修复，失败时按配置顺序回落 |
| 跨平台兜底 | 主通道不可用时，可依次尝试 **酷我 → 酷狗 → QQ** 等落雪协议平台 ID 搜索原版曲目并取流（例如网易云通道故障时用酷我 ID 搜原版） |
| 通道自检 | 设置侧提供音源通道可用性检查，便于排查脚本 / API 是否失效 |
| 脚本运行时 | QuickJS 侧补齐 `console` 与 **MD5** 等能力，便于通过独家音源常见 API 校验；播放热路径也做了调用优化 |
| 防抖 | 同一首歌在短时间内不会对同一解析链路重复等待在线音源，避免无谓重试拖慢播放 |

相关代码大致位于：

- `app/src/main/java/moe/ouom/neriplayer/data/source/lxmusic/`（导入、解析、HTTP 客户端、JS 运行时）
- `app/src/main/java/moe/ouom/neriplayer/core/player/resolver/lxmusic/`（播放解析、跨平台兜底、歌词同步）
- `app/src/main/java/moe/ouom/neriplayer/ui/screen/tab/settings/auth/LxMusicSourceSettings.kt`（设置入口）

> [!NOTE]
> 在线音源**不会**绕过平台账号权限或版权限制；能否播放取决于你配置的接口、脚本以及对应平台的账号能力。
> 本仓库不内置、不维护任何公共曲库或第三方音源地址。

### 2. 在线音源歌词同步

上游在无歌词时依赖平台内歌词或 LRCLIB 等来源。本衍生版补充了一条链路：

- 当前曲目**没有有效歌词**时，从在线音源对应的平台接口同步歌词（可复用跨平台取流时已拿到的曲目 ID）。
- 会识别网易云**占位歌词**（空壳 / 无实质内容），避免「有接口返回但其实没歌词」，使同步真正生效。
- 歌词结果按歌曲稳定身份做本地缓存，减少重复请求。

实现入口：`LxMusicLyricsSource.kt`，并接入播放页 `PlayerLyricsProvider` 数据链路。

### 3. 播放页 Apple Music 风格操作

对 Now Playing 做了以可点性与一眼可读为目标的改版（UI 结构，而非主题换色）：

- **直线进度条**：改为更粗的直线样式（约加宽 1.5 倍），拖动与观察进度更直观。
- **大号三键**：上一首 / 播放暂停 / 下一首放大（播放键约 1.2×），主控使用实心圆润几何图标，并修正三角形歪斜问题。
- **底部 Docker 菜单**：播放顺序、循环模式与其他常用操作收进底部 Docker 区，减少必须展开全屏才能改状态的情况。
- 封面区保留**来源角标**（网易云 / B 站 / YouTube Music / 本地 / 在线音源），播放来源可辨。

### 4. 首页 / 探索 / 设置的交互与性能

围绕「打开更快、少误触、少卡顿」做了一批可感知改动：

**首页加载策略**

- 首次加载**错峰**执行，避免启动瞬间多个请求抢主线程导致掉帧或假死。
- 次级板块（含**私人 FM** 等）收进「更多」按钮，**按需加载**，降低首页首屏并发。

**探索页**

- 搜索结果查找从 O(n²) 路径优化为更高效的查询，减少列表数据量上来后的卡顿。
- 修复 cookie 变化时**重复触发加载**的问题。
- 搜索音源改为**右侧下拉**选择，关闭左右滑动切源，降低误触；类型栏可仅图标展示，上滑时保留类型入口。
- 「新发现」改为**独立全屏二级页**（风格化歌单浏览），进入探索 Tab 时重置状态，避免残留上次页面。

**导航与设置**

- 四个主 Tab 顶栏改为**紧凑大标题**，减少顶部留白；移除遗留的 `nestedScroll` 拦截，修复页面滑动卡顿。
- 主 Tab 切换：缩短动画时长、关闭玻璃 handoff，并**常驻合成**子页面，避免切换时重复 Compose 构建造成的卡顿。
- 从其他 Tab 再次进入设置时**重置到一级设置页**，避免停留在上次的二级页产生闪烁或「找不到回退」。
- 设置二级页去掉大标题并上移内容，降低顶部空白。

> 中间曾尝试「探索搜索历史 + 全高毛玻璃弹层」等方案，后评估为收益不稳或影响滑动，已回退为普通搜索；当前以稳定性优先。

### 5. 开发与真机调试

- 提供 **ADB 真机一键安装调试脚本**：`scripts/install-debug.ps1`
  - 默认构建并安装 Debug 包到已授权设备
  - 支持 `-Wireless IP:PORT` 无线调试、`-SkipBuild` 只装包
- 项目内沉淀了动画 / UI 性能优化约定（`android-animation-ui-optimize` skill），便于后续同类改动沿用同一套检查思路。

---

## 上游核心能力（沿用）

下列能力来自上游 NeriPlayer，本衍生版默认保留；细节与限制请以 [上游文档](https://github.com/cwuom/NeriPlayer/blob/master/README.md) 与当前代码为准。

- **多源探索与播放**：网易云、Bilibili、YouTube Music、本地音频；Media3 `PlayerManager` 统一队列、恢复与失败处理
- **本地优先**：下载、缓存、歌单、历史、播放统计默认落在设备本地；脱机模式下仍可使用已下载与缓存内容
- **歌词体验**：逐行 / 逐词歌词、翻译、编辑与分享、悬浮 / 状态栏歌词等链路
- **下载与本地管理**：应用内下载队列、断点续传、本地导入扫描、歌手分类、歌单收藏
- **可选同步**：GitHub / WebDAV 元数据同步（用户自有远端，非开发者托管曲库）
- **播放音效与硬件**：均衡器、响度、USB 独占（UAC 等）等进阶链路
- **稳定性**：崩溃 / ANR 日志、安全模式启动恢复

平台现状、一起听部署、同步协议等**未在本 README 重复展开**，需要时请看上游 README 与 `np-submodule/` 下各子项目文档。

---

## 快速开始

### 下载

可从本仓库 [Releases](https://github.com/octagoningly/Cool-Music/releases) 获取已构建的 APK（如仓库根目录示例 `CoolMusic-*-arm64-release.apk` 所对应的发布流程）。

- 大多数手机选择 `arm64-v8a`
- 32 位老旧设备选择 `armeabi-v7a`
- 模拟器 / x86 设备选择对应 x86 包

Release 与签名流程请自行配置密钥；**不要**将签名文件或 `local.properties` 提交进仓库。

### 本地构建

1. 克隆本仓库并初始化子模块：

   ```bash
   git clone --recursive https://github.com/octagoningly/Cool-Music.git
   cd Cool-Music
   ```

2. 使用 Android Studio 打开工程并同步依赖。
3. 配置环境（Windows 示例）：
   - `JAVA_HOME` 指向 JDK 17+
   - `ANDROID_HOME` 指向 Android SDK（与工程 `compileSdk` 对齐）
4. 构建并安装 Debug 包：

   ```powershell
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:installDebug
   ```

   或使用真机脚本：

   ```powershell
   .\scripts\install-debug.ps1
   .\scripts\install-debug.ps1 -Wireless 192.168.1.100:5555
   ```

5. Release（需自备签名）：

   ```text
   在 gradle.properties / 环境中配置 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
   .\gradlew.bat :app:assembleRelease
   ```

6. 首次启动：阅读免责声明并完成引导。设置页连续点击**版本号 7 次**可开启开发者模式（Debug 页）。

### 在线音源使用提示

1. 进入 **设置 → 第三方平台登录 → 在线音乐源**
2. 导入 LX JSON 或 JS 音源（以及你自己的服务端地址）
3. 按需开启**优先播放**，可用通道自检确认接口可用
4. 播放时观察封面区角标，确认是否走「在线音源」
5. 权限、会员与地区限制仍由对应平台与你的账号决定；本应用只负责在用户配置的接口下尝试解析

---

## 模块与代码入口（便于开发）

| 路径 | 用途 |
|------|------|
| `app/` | 主 Android 应用 |
| `app/.../data/source/lxmusic/` | LX 音源导入、解析、JS 运行时 |
| `app/.../core/player/resolver/lxmusic/` | 在线音源播放解析、跨平台兜底、歌词 |
| `app/.../ui/screen/tab/settings/auth/LxMusicSourceSettings.kt` | 在线音源设置 UI |
| `scripts/install-debug.ps1` | ADB 一键安装调试 |
| `np-submodule/` | 上游子模块（歌词组件、一起听服务端等） |
| `更新记录.md` | 本仓库功能演进日志 |

技术基线大体与上游一致：Jetpack Compose + Media3，`minSdk 28`（Android 9+），Java/Kotlin 17，具体以 `gradle.properties` 与 `app` 模块配置为准。

---

## 隐私与数据边界

- 设置、授权 Cookie、下载与播放统计等**默认保存在设备本地**；可选同步写入**你自己配置**的 GitHub / WebDAV 远端。
- 在线音源请求会发往**你配置的**第三方接口 / 脚本地址，不会由本项目提供公共曲库。
- 仓库不应包含：签名密钥、`local.properties`、账号 Cookie/Token、设备日志等个人数据。
- `.gitignore` 已忽略常见本地产物；提交前请自行检查 diff。

---

## 上游与许可证

本仓库代码来源于 [cwuom/NeriPlayer](https://github.com/cwuom/NeriPlayer)，遵循 **GNU GPL v3**。

- 分发 APK 时须同时提供对应源码或有效获取方式，并保留版权与许可证信息
- 完整许可见仓库内 [`LICENSE`](./LICENSE)
- 上游 CONTRIBUTING、CODE_OF_CONDUCT 等仍适用于向本仓库提交的改动

贡献代码前建议：

1. 从 `main` 拉出功能分支，不要在 `main` 上直接开发
2. 与远程同步时：`main` 先 pull，再对工作分支 rebase；冲突优先同时保留双方意图，拿不准的冲突先问再定
3. 每完成一项功能，在 [`更新记录.md`](./更新记录.md) 追加一行：`lrq--<功能说明>--<日期>`

---

## 源码仓库

| 项目 | 地址 |
|------|------|
| Cool Music（本衍生版） | <https://github.com/octagoningly/Cool-Music> |
| NeriPlayer（上游） | <https://github.com/cwuom/NeriPlayer> |
