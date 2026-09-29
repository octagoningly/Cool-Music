# QQ 音乐链路调研笔记（M0）

> 状态：**✅ M0 完成**（2026-09-28）——取流直链实测打通，规格全验证
> 方法：本地脚本解读 + HTTP 实测 + GitHub 开源研读 + **Playwright 真实浏览器登录态抓包复刻**
> 对应计划：`docs/QQ音乐原生接入计划.md` §2

---

## 0. ★ 核心结论

**取流接口：`POST https://u.y.qq.com/cgi-bin/musicu.fcg`（`t.y.qq.com` 亦可），需登录 Cookie + 登录态 g_tk + 特定 body 结构。**

三要素缺一不可（少任何一个 → `result=104003`、`purl` 空）：

1. **有效登录 Cookie**（`uin` + `qm_keyst`/`qqmusic_key`，二者同值）
2. **`g_tk` = 登录态计算值**（实测值 `208401460`，随 key 变化；**不是**社区通用的 5381）
3. **body 结构 `{comm, req}`**（POST + application/json；不是开源资料里 GET query 的 `{req_0, comm}` 结构）

> 104003 的三种语义（本笔记 §4 边界表）：匿名无登录态 / 该音质文件不存在 / 账号无权限（会员墙、版权墙）。

---

## 1. 成功请求规格（实测复刻通过，可直接照抄）

### 请求

```
POST https://u.y.qq.com/cgi-bin/musicu.fcg        # t.y.qq.com 同样可用
Content-Type: application/json;charset=utf-8
Referer: https://y.qq.com/
User-Agent: <浏览器 UA>
Cookie: uin=<QQ号>; qm_keyst=<key>; qqmusic_key=<key>   # 二者同值
```

Body（**`{comm, req}` 顶层结构**）：

```json
{
  "comm": { "g_tk": 208401460, "platform": "yqq", "ct": 24, "cv": 0 },
  "req": {
    "module": "vkey.GetVkeyServer",
    "method": "CgiGetVkey",
    "param": {
      "filename": ["M500<songmid>.mp3"],
      "guid": "4551078725",
      "songmid": ["<songmid>"],
      "songtype": [0],
      "uin": "<QQ号>",
      "loginflag": 1,
      "platform": "20"
    }
  }
}
```

- `g_tk`：登录态值，网页侧对 Cookie 计算（登录后实测 `208401460`；实现时需按登录 key 动态计算，算法为经典 `hash=5381; hash=((hash<<5)+c)` 32 位变体，输入为 `qm_keyst` 等——**待写代码时用已知 (key,g_tk) 对验证**）
- `filename` **中段就是 songmid**（`M500{songmid}.mp3`），实测有效；无需 media_mid 拼接
- `guid`：随机数字串即可（网页用 `4551078725`，历史样本 `2796982635` 亦可）

### 响应与拼链

```json
{ "req": { "data": {
    "sip": ["http://aqqmusic.tc.qq.com/", "http://sjy6.stream.qqmusic.qq.com/"],
    "midurlinfo": [{ "purl": "M500xxx.m4a?guid=..&vkey=..&uin=&fromtag=120032&src=..", "result": 0 }],
    "expiration": 80400
}}}
```

- **最终 URL = `sip[1]`（或任一非 ws 域名） + `purl`**
- 实测验证：`GET` 直链 → `206` / `Content-Type: audio/mp4` / 前 12 字节 `00-00-00-20-66-74-79-70-6D-70-34-32`（合法 ftyp mp42 头）
- Range 请求正常（ExoPlayer 分段没问题）；`fromtag=120032` 由服务端写在 purl 里
- `expiration: 80400` 秒（约 22.3h，服务端声明值；实际有效期以播放失败重取为准）

### 音质文件名与权限边界（实测）

| 文件名 | 音质 | 免费账号结果 |
|--------|------|--------------|
| `C400{songmid}.m4a` | 试听 m4a | ✅ OK（普通歌） |
| `M500{songmid}.mp3` | 128k | ✅ OK |
| `M800{songmid}.mp3` | 320k | ❌ 104003（**非会员墙**） |
| `F000{songmid}.flac` | FLAC | ❌ 104003（无文件或会员墙） |

- **VIP 付费歌（如《晴天》payplay=1）：所有音质含 C400 均 104003**（版权墙，账号无该曲权限）
- 无损文件不存在时（搜索 `sizeflac=0`）也回 104003 → 与权限墙同码，**调用方需先用搜索/详情的 size 字段预判**
- 结论：**免费账号可完整听普通歌 128k + 试听；320k+/FLAC 需绿钻会员，VIP 歌需 VIP**——与官方规则一致，属预期语义而非缺陷

---

## 2. 匿名为何全军覆没（对照表）

| 实验 | 结果 | 原因 |
|------|------|------|
| GET query + `{req_0, comm}` 结构（jsososo 老规格） | 104003 | 网页现行路径是 **POST + `{comm, req}`**；老结构服务端仍解析但缺登录态即拒 |
| `comm.authst = qm_keyst`（jsososo song.js 规格） | 104003 | `authst` 不是决定项；决定项是 **Cookie 头 + g_tk 登录值** |
| g_tk=5381（社区通用值） | 104003 | 登录态必须用 **按 key 算出的 g_tk**（实测 208401460） |
| 匿名 Cookie | 104003 | 无登录态一律拒 |

**关键帧来源**：Playwright headed Chrome 扫码登录 → 榜单播放 → 截获 `#147 POST musicu.fcg` body（`{comm:{g_tk:5381,platform:"yqq",ct:24,cv:0},req:{module:"QQConnectLogin.LoginServer",...}}`）→ 发现 `{comm,req}` 结构与登录 g_tk → 按此复刻成功。

---

## 3. 另一条链路：`musics.fcg` 加密网关（记录备用，暂不走）

网页播放器（QMA 2.0.1）实际播放时还大量使用：

```
POST https://u6.y.qq.com/cgi-bin/musics.fcg?_=<ts>&encoding=ag-1&sign=zzc...
Content-Type: text/plain
body: <base64 加密二进制>     ← 请求体加密
resp: <加密二进制>            ← 响应同样加密（含 vkey/purl）
```

- `sign=zzc...` 即 jsososo/QQMusicApi `util/sign.js` 的 `__getSecuritySign` 输出格式（GPL-3.0 可复用）
- `encoding=ag-1` 为请求/响应加密方案，逆向成本高——**暂不走**，`musicu.fcg` 明文路径已足够
- 音频流格式（真实播放观察）：
  `http://ws6.stream.qqmusic.qq.com/C400{songmid}.m4a?guid=<g>&vkey=<V>&uin=<uin>&fromtag=120032&src=<mediaMid>.m4a`
  VIP 曲另有 `RS02...` 前缀流（试听/特殊流），`fromtag=120052`；Range 206 分段正常

---

## 4. 登录方案（已实战验证）

| 项 | 结论 |
|----|------|
| 核心 Cookie | `uin`（QQ号）+ `qm_keyst`（= `qqmusic_key`，同值）；domain `.qq.com`，均非 HttpOnly |
| **扫码登录（首选）** | Playwright/WebView 打开 y.qq.com → 登录弹窗内嵌 `graph.qq.com` OAuth iframe → 手机 QQ 扫码 → 一键授权（**已实测跑通**，登录后 `g_tk` 变为登录值、请求带 `uin`） |
| 登录初始化 | `POST musicu.fcg` body `{comm:{g_tk:5381,platform:"yqq",ct:24,cv:0}, req:{module:"QQConnectLogin.LoginServer",method:"QQLogin",param:{code:"<授权code>"}}}` |
| key 刷新 | `u6.y.qq.com/cgi-bin/musics.fcg?sign=<securitySign>&data={req1:{module:"QQConnectLogin.LoginServer",method:"QQLogin",param:{musicid,musickey,expired_in}}}`（需 sign.js 算法） |
| 失效检测 | `c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg` 返回 `code=1000` = 未登录/失效；或取流 104003 且歌曲有权限 |
| Cookie 有效期观察 | 用户首份 `qm_keyst` 数小时后失效（`code=1000`）；扫码新取的 key 即刻有效——**App 端必须做续期与过期引导** |
| 手动 Cookie 兜底 | jsososo 同款：用户在 y.qq.com 登录后复制 Cookie 粘贴 |

---

## 5. 搜索 / 详情（匿名可用，已有结论保留）

- 搜索：`c.y.qq.com/soso/fcgi-bin/search_for_qq_cp?...&new_json=1&g_tk=5381&uin=0` + Referer → `songmid`/`songid`/`size128/320/flac`/**`pay.payplay`**/**`preview.trybegin/tryend/trysize`**
- 详情：`musicu.fcg?data={"songinfo":{module:"music.pf_song_detail_svr",method:"get_song_detail_yqq",param:{song_mid}}}`（GET 明文）→ `id`、`file.media_mid`、各 `size_*`、`size_try`、`file.url`（恒空）
- **取流前预判**：`size320=0` → 别请求 M800；`payplay=1` 且账号无 VIP → 直接提示；`preview` 字段 → 试听截断提示（映射 `isPreviewClip`）
- 试听截断标记：详情 `size_try>0` + 搜索 `preview` 区间

### 复现命令（成功版）

```powershell
$uin='<QQ号>'; $key='<qm_keyst>'; $smid='001rR5MF2lZdOL'
$body=@{comm=@{g_tk=<登录g_tk>; platform='yqq'; ct=24; cv=0}
        req=@{module='vkey.GetVkeyServer'; method='CgiGetVkey'
              param=@{filename=@("M500$smid.mp3"); guid='4551078725'; songmid=@($smid)
                      songtype=@(0); uin=$uin; loginflag=1; platform='20'}}} | ConvertTo-Json -Depth 6 -Compress
$r=Invoke-WebRequest -Uri 'https://u.y.qq.com/cgi-bin/musicu.fcg' -Method Post -Body $body `
  -ContentType 'application/json;charset=utf-8' `
  -Headers @{Referer='https://y.qq.com/'; Cookie="uin=$uin; qm_keyst=$key; qqmusic_key=$key"}
$d=($r.Content | ConvertFrom-Json).req.data
$url=$d.sip[1] + $d.midurlinfo[0].purl    # → GET 即可播
```

---

## 6. M0 验收（计划 §2.3）✅ 全部达成

- [x] 真实 songMid 拿到可播放直链（206/audio/mp4/ftyp 合法）——**免费歌 128k+试听**
- [x] 匿名 vs 登录音质边界：匿名一律 104003；登录后按账号权限分层（见 §1 边界表）
- [x] 请求头/签名/失败码表齐全（§0–§4）
- [x] 登录方案定型：扫码为主（已实战）、手动 Cookie 兜底、key 刷新接口与 sign 算法已知
- [x] 合规边界：仅正常使用路径与账号自身权限内取流，不实现付费破解；**Cookie/账号值禁止写入任何仓库文件**

## 7. 移交 M1/M2 的要点

1. 实现 `gtk` 计算（本机已用真实 `(qm_keyst → 登录态 g_tk)` 对验证算法一致；**凭据与完整对值不入库**，单测使用合成样本与预计算期望值；注意 32 位溢出与整型差异）
2. App 登录 = WebView 打开 y.qq.com 登录页（三平台先例齐全），Cookie 捕获 `uin`+`qm_keyst`
3. 取流客户端按 §1 规格实现；错误映射：104003 → 区分「无权限/无文件/登录过期」（结合详情 size 预判 + 登录态检测）
4. `guid` 生成与缓存 key 稳定性、vkey 过期重取（`expiration` + 播放 403/404 触发刷新）
5. 引用来源：`jsososo/QQMusicApi`（GPL-3.0，sign.js 可移植）；Playwright 抓包样本见本文

**凭据落地时禁止提交进仓库；本文所有 `<key>` 占位符保持占位。**
