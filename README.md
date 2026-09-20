[中文](./README.md) | [English upstream](./README_EN.md)

# Cool Music

> 基于 **[NeriPlayer](https://github.com/cwuom/NeriPlayer)**（GPL-3.0）二次开发的 Android 音乐播放器衍生版。

### 在上游「多源在线 / 本地优先 / 歌词体验 / 可选同步」的基础上，强化了**在线音源取流、歌词补全、首页与歌单交互、播放页操作、发行与更新**

- 仓库：<https://github.com/octagoningly/Cool-Music>
- 上游项目：<https://github.com/cwuom/NeriPlayer>
- 应用包名（applicationId）：`moe.ouom.coolmusic`（与上游 `moe.ouom.neriplayer` 独立，可共存安装）
- 许可证：GPL-3.0（分发时须保留版权与许可证，并提供对应源码获取方式）
- 声明：Cool Music 与原作者无关；能力以本仓库代码与 [`更新记录.md`](./更新记录.md) 为准

> [!WARNING]
> 本项目仅供学习与研究使用，请勿用于任何非法用途。
> 请只在你拥有权利、授权或第三方平台规则允许的范围内访问、播放或保存内容。
> 本项目不提供媒体内容、密钥，也不提供规避付费 / DRM / 地区限制的方案。
> 在线音源依赖你自己配置的第三方接口 / 脚本，账号与内容权限仍受原平台约束。

---

## 快速定位

- 只想了解本衍生版改了什么 → [相对上游的功能性改动](#相对上游的功能性改动)
- 想安装或本地构建 → [快速开始](#快速开始)
- 想看上游完整能力 → [上游核心能力](#上游核心能力沿用) 或 [上游 README](https://github.com/cwuom/NeriPlayer/blob/master/README.md)
- 功能演进明细 → [`更新记录.md`](./更新记录.md)
- 发行 / 签名 / ADB 约定 → [`开发规则.md`](./开发规则.md)
- 当前发行版 → [Releases · v0.2.0](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0)

```text
Cool Music（本仓库）
├── 在线 LX 音源：JSON / JS 导入、优先播放、跨平台兜底、通道自检
├── 歌词补全：无歌词时从在线音源 / QQ / 酷狗匹配，并识别占位歌词
├── 首页：私人雷达 → 每日推荐 → 雷达歌单 →「更多」；列表无序号
├── 播放页：Apple Music 风格进度条 / 大号三键 / 底部 Docker
├── 媒体库与歌单：自定义排序、Tab 自定义、本地歌单与收藏排序
├── 发行：包名独立、GitHub 应用内更新、versionCode 分钟级、同签名覆盖安装
└── 上游沿用：Media3 多源播放、本地库、下载、GitHub/WebDAV 同步等
```

---

## 相对上游的功能性改动

以下来自 `更新记录.md` 与实际代码；纯改名 / 换图标不在此展开。

### 1. 在线 LX 音源（核心）

| 能力 | 说明 |
|------|------|
| 音源导入 | LX Music **JSON / JS**；JS 由内置 **QuickJS** 执行（含 `console`、MD5，便于过常见 API 校验） |
| 优先播放 | 设置 → 第三方平台登录 → 在线音乐源；封面区显示「在线音源」角标 |
| 匹配与回落 | 按歌名 / 歌手 / 时长打分，过滤伴奏 / 翻唱等同名异版；修复 `songmid` 错配与回落顺序 |
| 跨平台兜底 | 主通道不可用时按 **酷我 → 酷狗 → QQ** 等搜原版取流（如网易云故障用酷我 ID） |
| 通道自检 | 设置内检查脚本 / API 是否可用 |
| 导入体验 | 支持失败清空、点击查看完整地址；压缩设置弹窗过高与重复探测问题 |
| 性能 | 播放热路径优化；同一解析链路内避免重复等待同一音源 |

代码入口：`data/source/lxmusic/`、`core/player/resolver/lxmusic/`、`ui/.../LxMusicSourceSettings.kt`

> [!NOTE]
> 在线音源不会绕过平台账号权限或版权限制；本仓库不内置公共曲库或第三方音源地址。

### 2. 歌词补全

- 无歌词时从**在线音源平台**同步歌词；无歌词时也可走 **QQ / 酷狗公开接口** 匹配。
- 识别网易云**占位歌词**，避免「有返回但无内容」挡住真正歌词。
- 修复播放页占位歌词压过音源 LRC 的问题；兼容 QQ 新搜索字段。
- 歌词按歌曲身份本地缓存，减少重复请求。

### 3. 首页与探索

**首页信息架构**

```text
私人雷达
每日推荐
雷达歌单
更多  ← 展开后：私人 FM / 新歌榜等 / 推荐歌单
```

- 次级板块收进「更多」**按需加载**；首次加载**错峰**，降低启动并发。
- 私人 FM 在「更多」内排在新歌榜之后。
- 修复展开「更多」后私人雷达 / 每日推荐被清空的问题（子集刷新改为合并保留）。
- 大标题下间距收紧；移除底部红色脱机横幅，网络失败文案改为「网络不佳」。

**歌曲列表（首页及各歌单详情等）**

- **不再显示 1/2/3 序号**；去掉序号后封面左移，并回调 **16dp** 左边距。
- 歌曲行副标题**只显示歌手**，不再拼接专辑名。

**探索页**

- 搜索源改为**右侧下拉**，关闭左右滑动切源；类型与来源选择整组居中。
- 上滑时保留类型图标栏；「新发现」为独立全屏二级页，进入探索时重置状态。
- 精选歌单封面：冷启动**随机一张**（不再自动轮播）。
- 搜索结果查找与 cookie 重复加载等性能问题已修。

### 4. 媒体库与本地歌单

- 本地页「歌单 / 歌手」与「+ 新建」同行，**新建靠右**。
- 媒体库 Tab 栏上移，支持**自定义排序**；排序与刷新图标在顶栏右侧。
- 本地歌单**自定义排序**（含「我喜欢的音乐」）：拖动排序、拖到底部自动滚屏、按手指位置判断上滚；收藏可在列表中保留位置，「本地文件」仍固定末尾。
- 排序按钮为图标 + 文字，修复拖动闪烁、底部遮挡等问题。

### 5. 播放页

- Apple Music 风格：直线进度条（约加宽 1.5 倍）、大号三键、底部 Docker 管播放顺序与其它菜单。
- 主控实心圆润图标，三角形几何修正；播放三角在圆角按钮内**光学居中**。
- 封面区来源角标（含「在线音源」）。

### 6. 导航与设置 UI

- 主 Tab 顶栏紧凑大标题；主 Tab **常驻合成**、动画缩短，减少切换卡顿。
- 设置重入回到一级页；二级页去大标题、内容上移。
- 关于页：**原作者声明**为单行摘要，点击弹出**圆角详情弹窗**（含上游项目与 GPL 说明）；**GitHub** 行指向 `github.com/octagoningly/Cool-Music` 且可跳转。
- 初次设置去掉第 **5** 步（播放控件）与第 **6** 步（歌词页）。

### 7. 包名、签名与发行

| 项 | 说明 |
|----|------|
| applicationId | `moe.ouom.coolmusic`，与 NeriPlayer 共存，不冲突 |
| 应用内更新 | 设置 → 关于 → **检查更新**：对接 GitHub Releases 下载并覆盖安装 |
| versionCode | **分钟级**编码，避免同日多版冲突；Release 正文须写 `versionCode=数字` |
| 签名 | 始终使用同一把 `app/neri.jks`；修复 vivo「软件包无效 / 无签名」问题（Release 补签 v1+v2+v3） |
| debug 签名 | 本地密钥就绪时 **debug 复用 release 签名**，便于 ADB 覆盖安装且不丢数据 |
| 安装包目录 | 本地包统一 `dist/apk/`；对外以 GitHub Release 为准 |
| CI | 已移除 PR 上的 NeriPlayer Android CI / Native CI 工作流，避免衍生仓库被重型检查拖住 |
| 发行示例 | [v0.2.0](https://github.com/octagoningly/Cool-Music/releases/tag/v0.2.0)，`versionCode=609200119`，`versionName=60df2b47.09200119` |

同步侧：GitHub / WebDAV 默认仓库与文件名改为 Cool Music 品牌，并兼容旧备份。

### 8. 开发与真机调试

- `scripts/install-debug.ps1`：构建并安装到已授权设备；支持 `-Wireless`、`-SkipBuild`。
- 详细约定见 [`开发规则.md`](./开发规则.md)（签名、versionCode、Release 清单、ADB 说明）。

---

## 上游核心能力（沿用）

- 多源探索与播放：网易云 / Bilibili / YouTube Music / 本地；Media3 统一队列与恢复
- 本地优先：下载、缓存、歌单、历史、统计；脱机仍可使用已下载内容
- 歌词、下载管理、可选 GitHub/WebDAV 同步、USB 独占、崩溃 / ANR / 安全模式等

平台细节、一起听部署等见上游 README 与 `np-submodule/`。

---

## 快速开始

### 下载安装

1. 打开 [Releases](https://github.com/octagoningly/Cool-Music/releases)，下载最新 `CoolMusic-*-arm64*.apk`
2. **同签名覆盖安装**即可，无需卸载（会保留登录与本地数据）
3. 也可在应用内：设置 → 关于 → 检查更新

- 多数手机：`arm64-v8a`
- 32 位设备：`armeabi-v7a`
- 模拟器 / x86：对应 x86 包

**不要**把 `*.jks`、密码、`local.properties` 提交进仓库。

### 本地构建

```bash
git clone --recursive https://github.com/octagoningly/Cool-Music.git
cd Cool-Music
```

Android Studio 打开后配置 `JAVA_HOME`（JDK 17+）与 `ANDROID_HOME`。

```powershell
# Debug 调试包（本地密钥就绪时与正式包同签名）
.\scripts\install-debug.ps1
# 或
.\gradlew.bat :app:installDebug

# Release（需配置 KEYSTORE_* 见开发规则.md）
.\gradlew.bat :app:assembleRelease
```

首次启动完成免责声明与引导；设置页连点**版本号 7 次**开启开发者模式。

### 在线音源

1. 设置 → 第三方平台登录 → **在线音乐源**
2. 导入 LX JSON / JS 与你的服务地址
3. 开启优先播放，可用通道自检
4. 播放时看封面角标是否为「在线音源」

---

## 模块与代码入口

| 路径 | 用途 |
|------|------|
| `app/` | 主应用 |
| `app/.../data/source/lxmusic/` | LX 音源导入 / QuickJS |
| `app/.../core/player/resolver/lxmusic/` | 在线音源解析、跨平台、歌词 |
| `app/.../ui/screen/tab/HomeScreen.kt` | 首页板块与歌曲行 |
| `scripts/install-debug.ps1` | ADB 一键安装 |
| `开发规则.md` | 发行、签名、versionCode、ADB |
| `更新记录.md` | 功能演进 |
| `np-submodule/` | 上游子模块 |

技术基线：Jetpack Compose + Media3，`minSdk 28`，`applicationId = moe.ouom.coolmusic`。

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
