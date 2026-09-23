from pathlib import Path

path = Path(r"D:\study\AI\NeriPlayer\开发规则.md")
text = path.read_text(encoding="utf-8")

section = """
## UI 风格

> 界面控件、弹窗、图标、字体的统一约定。改 UI 前先读本节；与单个页面临时样式冲突时，**以本节为准**。

### 圆角与形状

| 控件 | 规格 |
|------|------|
| 筛选 Chip / 胶囊按钮 | `RoundedCornerShape(50)`（全圆胶囊） |
| 探索搜索类型图标钮 | 统一 **48×48**，圆角 **18.dp**，图标 20.dp |
| 下拉菜单 / 弹窗面板 | 圆角 **20–28.dp**（菜单 20，对话框/面板 28），禁止直角 |
| 歌手/选项下拉 | 宽度自适应内容，`widthIn(max = 240.dp)`，长名省略号 |
| 下拉菜单文字 | **居中**（`textAlign = TextAlign.Center`） |
| 搜索条 / 次要圆角 | 16–24.dp，同一屏内保持一致 |

### 图标

- 目标：**简洁、圆角、加粗**。线性图标描边约 **2.2–3.4**，`stroke-linecap/linejoin = round`。
- 播放/上一曲/下一曲/暂停优先 **Apple Music 风格**（圆角三角、胶囊竖条），见 `AppleMusicPlaybackIcons.kt`。
- 品牌图标（B 站/网易/QQ 等）不改圆角与粗细。
- 选中态可用实心，未选中用加粗描边，语义一一对应。

### 字体

- 大标题（首页 Cool Music、探索、媒体库、设置及底栏文案）：**FontFamily.Serif + FontWeight.Bold**。
- 正文保持系统默认，避免整页衬线。

### 交互

- 底栏「首页 / 探索 / 媒体库」**再次点击**当前 Tab：关闭详情并回到列表顶部（设置 Tab 保持原逻辑）。
- 探索页搜索类型按钮层：**向下滚动时收起**，回到顶部展开。
- 歌曲「…」菜单结构统一：播放（保留队列）→ 接下来播放 → 加入队列 → 添加到歌单 → 收藏 →（下载）→ 复制信息。
- 加入歌单需反馈：**「已添加到『歌单名』」** / **「已存在，未重复添加」**。

### 透明材质（高级模糊）

- 同一透明/毛玻璃控件必须走 `AdvancedGlassSurface` + 合适的 `AdvancedGlassRole`，**不要**另写一套 blur。
- 开关：设置 → 动效 → **高级模糊效果**（`advancedBlurEnabled` / 增强 `enhancedAdvancedBlurEnabled`）。
- 模糊强度：设置里的 **模糊度**（`enhancedAdvancedBlurRadiusDp`），经 `AdvancedGlassController.normalizedBlurAmountDp` 生效。
- 弹窗/下拉：圆角 + 半透明底 + 细边；模糊开时贴近 MiniPlayer 质感，关时用 `fallbackColor` 可读底。
- 参考实现：`NeriMiniPlayer`（`AdvancedGlassRole.MiniPlayer`）、`NeriBottomBar`（`BottomNavigation`）、`ui/component/playlist/GlassDropdownMenu.kt`。

"""

anchor = "## 按任务速查"
if "## UI 风格" in text:
    print("UI section already exists")
else:
    if anchor not in text:
        # insert before 身份信息
        anchor2 = "## 身份信息"
        if anchor2 not in text:
            raise SystemExit("no insertion anchor")
        text = text.replace(anchor2, section.strip() + "\n\n---\n\n" + anchor2, 1)
    else:
        text = text.replace(anchor, section.strip() + "\n\n---\n\n" + anchor, 1)
    path.write_text(text, encoding="utf-8")
    print("UI style section inserted")
