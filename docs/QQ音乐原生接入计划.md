# QQ 音乐原生接入计划

> 状态：开发中 · M2–M5 已完成，M6 周边功能进行中
> 目标：把 QQ 音乐做成与网易云 / B 站 / YouTube Music 同级的**原生播放渠道**（独立搜索、独立取流、独立音质、可下载），而不是仅靠 LX 脚本兜底。
> 约束：遵循 `开发规则.md`（冲突时最高优先）、`CONTRIBUTING.md` 扩展路径；只在一个工作分支上改；禁止 force-push `main`。

---

## 0. 现状盘点（写代码前的已知事实）

### 已有，可直接复用

| 资产 | 位置 | 说明 |
|------|------|------|
| 搜索 + 详情 + 歌词 API | `core/api/search/QQMusicSearchApi.kt` | 已实现 `SearchApi` 接口（搜索 `c.y.qq.com/soso/fcgi-bin/client_search_cp`、详情 `u.y.qq.com/cgi-bin/musicu.fcg`、歌词 Base64 解码 + AMLL 逐字），目前只被歌词匹配链路使用 |
| LX 跨平台搜索里的 QQ 通道 | `core/player/resolver/lxmusic/LxMusicCrossPlatformSource.kt:464` | `fetchLxQqHits` / `LX_QQ_PLATFORM_ID = "tx"`，可用作搜索响应解析参考，**不能当播放取流用** |
| 歌词偏移设置 | `qq_music_lyric_default_offset_ms`（`AutoSettingsSchema.kt:1270`） | 歌词侧设置已存在 |
| UI 占位 | `LibraryScreen.kt:241` `LibraryTab.QQMUSIC`、设置页 `ic_qq_music` 入口 | 目前显示 `library_qqmusic_coming`，可挂真实内容 |
| 通用 `SearchApi` / `SongSearchInfo` / `MusicPlatform.QQ_MUSIC` | `core/api/search/SearchApi.kt` | 模型层已把 QQ 列为平台枚举 |

### 缺失（本计划要补的）

| 缺口 | 说明 |
|------|------|
| **取流（播放 URL）** | 整个计划的核心难点。没有任何 `getPlayUrl` / vkey / 签名相关代码 |
| 原生 `QQMusicClient` | 现有 `QQMusicSearchApi` 是搜索客户端，不含播放；也没有 Cookie 仓库 / 登录 |
| 播放解析链路 | `PlayerManagerUrlExtensions.kt:384` 的 `when` 分支只有 YouTube / Bili / Netease 三路 |
| `isQQMusicTrack` 识别 | `PlayerManager.kt:1548` 只有 `isYouTubeMusicTrack` / `isBiliTrack`；`SongIdentity.kt` 未定义 qq 渠道身份 |
| 音质体系 | `PlaybackAudioSource` 枚举无 `QQ_MUSIC`；`PlayerUrlResolver.kt` 无 QQ 音质映射 |
| 登录 | 无 `activity/auth/QQ*`；无 `data/auth/qq/` |
| 下载 | `AudioDownloadManager.kt:1148` 分支无 QQ |
| Explore 搜索源 | `ExploreViewModel.kt:105` `SearchSource` 无 QQ |
| 一起听 / 同步 | `ListenTogetherChannels` 无 QQ；可后置 |

---

## 1. 总体策略与里程碑

按风险排序：**先打通「匿名/试听也能播」的最小取流链路，再补登录高音质，最后铺 UI 和周边**。

> **M0 调研后修订（重要）**：QQ 取流**必须登录态**（`comm.authst = qqmusic_key`，匿名恒 104003，详见 `docs/qq-music-link-notes.md` §0）。因此**登录从 M5 提前到 M2**，成为取流原型的前置；原 M5 的「高音质/自动续期」保留为 M5。

| 里程碑 | 内容 | 完成标志 |
|--------|------|----------|
| **M0 · 链路调研** ✅ **已完成** | 弄清 QQ 音乐播放 URL 怎么拿（见 §2） | ✅ `docs/qq-music-link-notes.md`：取流直链实测打通（`POST musicu.fcg` + `{comm,req}` + 登录 g_tk + Cookie）、音质权限边界、登录方案已实战验证 |
| **M1 · 登录打通（原 M5 提前）** | WebView 登录 y.qq.com 捕获 Cookie（先例：`NeteaseWebLoginActivity` 等三套）+ 手动粘贴 Cookie 兜底 + key 刷新（`musics.fcg` + security sign，GPL 代码可移植） | 拿到可用的 `uin` + `qm_keyst`，刷新接口验证通过 |
| **M2 · 取流原型 + 客户端** ✅ **已完成** | 带登录态复现取流；`QQMusicClient` + 取流仓库 + 质量选择（仿 `BiliPlaybackRepository`） | ✅ `QQMusicClient`（`{comm,req}` 规格 + 104003 分类）+ `QQMusicPlaybackRepository`（降级链）+ `QQMusicQuality` 模型 + 单测 37 项全绿；真机播放闭环并入 M3 |
| **M3 · 播放接入** ✅ **代码完成**（真机手测待设备） | `resolveSongUrl` 分发、`isQQMusicTrack`、缓存 key、音质 UI | ✅ `SongIdentity` 渠道、`isQQMusicTrack`、`getQQMusicAudioUrl` 分发、`qualityLabelForQQMusic`、来源徽章、音质偏好/设置项/刷新任务全链路接线；QQ 单测绿；**真机播放手测待手机连接**（`adb devices` 为空） |
| **M4 · 搜索/探索** ✅ **搜索闭环完成** | Explore 搜索源 + Library QQ 标签 + 链接识别 | ✅ `SearchSource.QQ_MUSIC` + 分页搜索 + SongItem 映射（songmid→audioId）+ 平台标签 UI；**Library QQ 歌单标签未做**（数据层较重，可后置）；链接识别未做 |
| **M5 · 登录完善与高音质** ✅ **主体完成** | 自动续期、过期引导、VIP 音质联动、账号页 | ✅ 设置页 QQ 音质选择（flac/320k/128k/试听）+ `QQMusicAuthVerifier`（profile code=1000 过期判定）+ 取流失败刷新健康状态；**key 自动续期（security sign 移植）未做**——sign.js 为 VM 字节码实现，单列后续任务 |
| **M6 · 周边** | 下载、歌词完善、元数据补全、统计、一起听 | **下载与歌词已接入 QQ 原生链路**；元数据补全、统计、一起听仍待处理 |
| **M7 · 收尾** | 测试、文档、`更新记录.md`、发版检查 | 全量单测绿 |

**建议拆成多个 PR/提交**（`新增xx功能_n` 风格），每个里程碑一个，方便回滚。

---

## 2. M0 · 链路调研（第一步，必须先做完）

> 目的：在写任何生产代码之前，搞清楚「点一首歌 → 拿到音频直链」的完整 HTTP 链路。QQ 的难点全在这里。

### 2.1 调研问题清单

1. **取流接口是哪一个？**
   - 候选 A：`u.y.qq.com/cgi-bin/musics.fcg`（新版 musicu，多模块批量，常见 `GetCdnDispatch` / `GetPlayUrl` / `vkey.GetVkey` 类模块）
   - 候选 B：`u.y.qq.com/cgi-bin/musicu.fcg`（老接口，与现有详情接口同族）
   - 候选 C：`c.y.qq.com/base/fcgi-bin/fcg_music_express_mobile3.fcg`（历史 express 接口，可能已废弃）
   - 候选 D：网页/客户端播放器实际发出的接口（抓包为准，不猜）
2. **认证与签名**
   - 需要哪些 Cookie（`uin`、`qm_keyst`、`psrf_*`…）？匿名能不能拿到试听/低音质流？
   - 请求体是否需要 sign / guid / vkey 预取？参考开源实现（lx-music 的 `tx` 源脚本、QQMusicApi 类项目）时，**只借参数结构理解，不抄实现、不整包引入**。
3. **音质与文件名体系**
   - 音质前缀：`M500`(128k mp3) / `M800`(320k mp3) / `M4A` / `A000`/`RS01`/`F000`(flac 等) 之类与 level 的对应关系；`C400` m4a 试听。
   - `filename` 拼法（`M500{mid}.mp3` 之类）与 `media_mid` 的关系；现有搜索返回的是 `songMid`，取流还需要什么 id（songId / strMediaMid）？
4. **直链形态**
   - 域名：`*.qqmusic.qq.com` / `dlweb.music.qq.com` / `isure.stream.qqmusic.qq.com`… 哪些还能直接播。
   - 是否需要 `Referer: https://y.qq.com` / `User-Agent` / `Range` 头？（对照 `ConditionalHttpDataSourceFactory.kt:158` 给 B 站加 Referer 的先例）
   - 直链有效期多长？失效后表现是什么（403？过期提示）？是否要像 YouTube 一样支持 seek 前刷新。
5. **失败模式**
   - VIP 专享 / 版权下架 / 地域限制分别返回什么 code/msg？（对应网易云 `NO_PERMISSION` 的降级语义）
   - 未登录/试听截断（30 秒？60 秒？）如何识别（对照网易云 `PREVIEW_CLIP`）。
6. **登录方式**
   - 扫码登录的接口链路（`ptqrshow` / `xlogin` 之类 vs QQ 音乐自己的二维码）。
   - WebView 登录可行吗？（有 `NeteaseWebLoginActivity` / `BiliWebLoginActivity` 先例）
   - 登录态刷新与过期检测。
7. **可选但提前确认**
   - 歌单/收藏夹 API（Library QQ 标签要用）；歌词接口现有实现是否仍有效（`QQMusicSearchApi` 里的 lyric 调用）。
   - 歌曲搜索与取流的 id 是否一致（`songMid` vs `songId`），`SongItem.audioId` 该存哪个。

### 2.2 调研方法与产出

| 步骤 | 做法 | 产出 |
|------|------|------|
| a. 读开源实现建立假设 | 浏览 lx-music 用户脚本中 `tx` 源（`docs/online-sources.json` 里有多个 `healthyChannels: ["tx"]` 的脚本 URL，可下载阅读）、公开的 QQ 音乐 API 文档/逆向笔记 | 接口候选清单、参数表 |
| b. 实测验证 | 用脚本/HTTP 工具发请求（登录与未登录各一轮），对照 §2.1 逐条验证 | **实测截图/响应 JSON 片段**，区分「实测确认」与「仅文档声称」 |
| c. 对照本项目先例 | 把结论映射到 `BiliClient` / `NeteaseClient` 的结构（客户端-仓库-解析三件套） | 初步类设计草案 |
| d. 写笔记 | 落到 `docs/qq-music-link-notes.md` | M0 验收物 |

### 2.3 M0 验收标准

- [ ] 能用 curl/脚本 + 一个真实 songMid 拿到至少一条**可播放**直链（mp3 或 flac）
- [ ] 明确匿名 vs 登录各能拿什么音质；试听截断策略已知
- [ ] 请求头/签名要求已列全，失败码表已列
- [ ] 登录方案定型（扫码 / WebView / Cookie 导入，三选一或多选）
- [ ] 记录合法合规边界：仅个人学习用途，不实现破解 VIP 加密付费内容、不绕过付费墙的技术细节进仓库

---

## 3. M1–M2 · 取流原型与客户端

### 3.1 新增代码（预计）

```
core/api/qqmusic/
├── QQMusicClient.kt          # 网络客户端：搜索/详情已有逻辑迁入或委托，核心是 getPlayUrl
├── QQMusicCrypto.kt          # 若需要 sign/guid/vkey（调研后定）
├── QQMusicPlaybackRepository.kt  # 音质选择 + 失败降级（仿 BiliPlaybackRepository）
└── QQMusicQrLoginClient.kt   # M5 再做
data/platform/qqmusic/
└── QQMusicAudioStreamInfo.kt # 流描述模型（仿 data/platform/bili/BiliAudioStreamInfo）
```

### 3.2 音质模型

- 在 `PlaybackAudioSource` 加 `QQ_MUSIC`。
- 在 `PlayerUrlResolver.kt` 增加 `qualityLabelForQQ` / `buildQQQualityOptions` / `buildQQPlaybackAudioInfo`（完全仿 Bili 那三段）。
- 质量键建议与现有风格一致：`flac` / `320k` / `128k` / `m4a` 等，调研后按实际 level 命名。
- 设置项：`qqMusicAudioQualityFlow` 进 `SettingsRepository` / `AutoSettingsSchema`，UI 挂 `SettingsAudioQualitySection`（有 `ic_bilibili` 先例）。

### 3.3 原型验收

- [ ] 单元测试：响应解析（成功/无版权/VIP 限制/试听）全部覆盖
- [ ] 单元测试：`selectStreamByPreference` 类降级逻辑
- [ ] 真机 `./gradlew.bat :app:testDebugUnitTest` 全绿

---

## 4. M3 · 播放链路接入（关键一步）

按 `CONTRIBUTING.md`「新增在线播放平台」路径：

1. **曲目识别**
   - `PlayerManager.kt` 增加 `isQQMusicTrack(song)`：`channelId == "qqmusic"` 或（调研后定）`album` 前缀约定。
   - **`SongIdentity.kt`**：`normalizeChannelAlias` / `normalizedChannelId` 登记 `"qqmusic"` 渠道，决定 `audioId` 存 songMid 还是 songId（M0 结论）；保证 `stableKey` 稳定，否则同步/缓存会串歌。
2. **URL 解析分发**
   - `PlayerManagerUrlExtensions.kt:384` 的 `when` 增加 `isQQMusicTrack(song) -> getQQMusicAudioUrl(...)`。
   - 新建 `core/player/resolver/qqmusic/`：
     - `QQMusicPlaybackResponseParser.kt`（仿 `NeteasePlaybackResponseParser`：Success/Failure + 试听标记）
     - 必要时 `PlayerManagerQQAutoSourceSwitch.kt`（失败跨源兜底，可后置；注意 CONTRIBUTING 要求网易云兜底链路不要被污染）
3. **HTTP 头**
   - 若需 Referer/UA/Range：扩 `ConditionalHttpDataSourceFactory.kt`（B 站 Referer 先例在 158 行）。
   - `TrafficCountingHttpDataSource` 如有流量统计口径问题，同步检查 QQ 域名是否计费/计流量正确。
4. **缓存**
   - `computeCacheKey` / `CachedPlaybackDescriptor` 确认对 QQ 生效；`representationIdentity` 参照 `buildBiliRepresentationIdentity`。
   - 离线缓存歌单 `CachedSongsPlaylist.kt` 的渠道识别分支补 QQ。
5. **降级语义**
   - 试听截断 → `noticeMessage` + `isPreviewClip`（对标网易云）。
   - 无权限 → 明确错误文案（可新建 `R.string.player_qq_*`），考虑「切换平台/降音质」按钮（有 `player_netease_no_permission_switch_platform` 先例）。

### 4.1 M3 验收

- [ ] 点播任意 QQ 曲目：能起播、seek、切歌、通知栏信息正确
- [ ] 播放页来源角标显示 QQ（`PlaybackSourceBadge.kt` 补分支）
- [ ] 断网后重播走缓存；直链过期后能自动重解析
- [ ] 网易云/B 站/YouTube 三条老链路回归无破坏（单测 + 手测）

---

## 5. M4 · 搜索、Explore、Library

1. **Explore 搜索源**
   - `SearchSource` 枚举加 `QQ_MUSIC`；`ExploreViewModel` 加 `searchQQMusic()` / 分页（对照 `searchBilibili`）。
   - `ExploreScreen` 平台标签 + 结果 UI；必要时 `ExploreLinkRecognizer` 支持 `y.qq.com` / `c.y.qq.com` 分享链接（`ExploreHostScreen` 有 B 站先例）。
2. **QQ 歌单/收藏**
   - 模型仿 `BiliFavoriteFolderCacheRepository` / `CollectionSummaryModels.BiliFavoriteFolder`。
   - `LibraryScreen` 的 `QQMUSIC` 标签把 `library_qqmusic_coming` 换成真实列表；`LibraryViewModel.refreshQQMusic()`。
3. **歌词**
   - `QQMusicSearchApi` 歌词链路已有，接到播放页 `PlayerLyricsProvider` 的 QQ 分支；翻译/逐字（AMLL）走现有开关。
4. **封面**
   - 现有搜索已拼 `y.qq.com/music/photo_new/T002R800x800M000{mid}.jpg`；在取流/详情确认 mid 一致性。

---

## 6. M5 · 登录与高音质

1. **登录 UI**：`activity/auth/QQQrLoginActivity.kt`（仿 `BiliQrLoginActivity`）或 `QQWebLoginActivity`（仿 `NeteaseWebLoginActivity`）——以 M0 结论为准；注册到 AndroidManifest（独立次进程，见 CONTRIBUTING）。
2. **凭据存储**：`data/auth/qqmusic/QQMusicCookieRepository.kt`（仿 `BiliCookieRepository`，`preferencesDataStore("qqmusic_auth_store")`）；**禁止**写入仓库文件，口令类只走 `~/.gradle` 或系统凭据边界。
3. **设置页**：`SettingsCookieAuthDialogs` / `SettingsScreen` 的 QQ 入口从占位变可用；登录态过期自动刷新或引导重登。
4. **音质联动**：登录后解锁更高 level；`AutoSourceSwitch` 在试听截断时提示登录（可选）。

---

## 7. M6 · 下载与周边

1. `AudioDownloadManager.kt:1148` 分支加 `isQQMusic -> resolveQQMusic(song)`；下载命名 `ManagedDownloadNaming.kt` 补渠道映射；失败重试策略对齐。
2. `PlaybackAudioSource` 的统计/上报如有 switch 分支，全库搜 `BILIBILI`/`YOUTUBE_MUSIC` 找漏网枚举分支补齐。
3. 一起听：`ListenTogetherChannels.QQMUSIC` + `PlayerManagerListenTogetherStreamExtensions`（可后置，单独立项）。
4. 同步到「网易云我喜欢」的映射（CONTRIBUTING 扩展路径 3.5）——**不阻塞 QQ 播放**，需要稳定的网易云 ID 映射，建议单独任务。

---

## 8. M7 · 测试、文档与验收

1. **单元测试**（`app/src/test/`）：响应解析、音质选择、身份/缓存 key、URL 分发分支、歌词解码；与播放/下载/歌词/设置策略相关的改动按项目惯例优先补同名 `*Test.kt`。
2. **提交前**：`.\gradlew.bat :app:testDebugUnitTest`；真机 `.\scripts\install-debug.ps1` 手测主链路（播放/下载/登录/缓存）。
3. **文档**：`CONTRIBUTING.md` 若扩展方式有变则同步；每完成一项在 `更新记录.md` 标题下插入 `lrq--简短说明--YYYY-MM-DD`。
4. **禁提交**：调试脚本里的 Cookie/Token、抓包原文含敏感信息的文件；调研笔记中凭据打码。
5. 发布按 `开发规则.md`（不在本计划展开；Debug APK 不上 Release）。

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 取流接口签名/加密复杂（类似 YouTube 的 EJS） | M1/M2 拖期 | M0 阶段尽早验证；必要时先只做「低音质匿名」MVP；参考但不照抄 lx 脚本的 `tx` 实现思路 |
| 接口随时变更/风控 | 403/502 | 失败码表 + 可配置 UA/Referer + 重试/换接口候选；健康探测可仿 `probeLxSourceChannels` |
| VIP/版权墙 | 只能试听 | 明确试听语义 + 登录引导；不实现任何形式的付费破解 |
| 身份 id 不稳定导致同步串歌 | 缓存/歌单错乱 | M3 把 `SongIdentity` 作为一等公民，补单测 |
| 破坏现有三渠道 | 回归风险 | `when` 分支只增不改；每里程碑跑全量单测 |
| 合规 | 法律风险 | 仅接入公开/半公开接口的正常使用路径；仓库不带凭据、不带破解 |

---

## 10. 建议执行顺序（任务拆分）

1. ~~**[M0]** 收集资料~~ ✅ 已完成：104003 根因、接口规格、登录方案见 `docs/qq-music-link-notes.md`
2. **[M0 收尾]** 登录态实测（需你提供测试 Cookie 或授权 WebView 登录）→ 验证 purl、filename 拼法、vkey 有效期
3. **[M1]** 登录链路落地（WebView + Cookie 存储 + key 刷新）
4. **[M2]** 带登录态取流脚本验证 → `QQMusicClient` + `QQMusicPlaybackRepository` + 单测
5. **[M3]** `SongIdentity` / `isQQMusicTrack` / `resolveSongUrl` 分发 / 音质设置
6. **[M3]** 真机播放闭环 + 三渠道回归
7. **[M4]** Explore 搜索源 + Library QQ 标签 + 链接识别
8. **[M5]** 登录完善（自动续期/过期引导）+ 高音质
9. **[M6]** 下载 + 歌词完善 + 徽章/统计（下载与 QQ 歌词已完成，剩余元数据/统计/一起听）
10. **[M7]** 全量测试 + `更新记录.md` + 文档同步

> 从 M2 起进入本仓库开发阶段，遵守「工作分支 → rebase → 合入」与提交信息格式 `新增xx功能_n`。
