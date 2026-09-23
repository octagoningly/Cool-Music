from pathlib import Path

path = Path(r"D:\study\AI\NeriPlayer\开发规则.md")
text = path.read_text(encoding="utf-8")

old = """### 透明材质（高级模糊）

- 同一透明/毛玻璃控件必须走 `AdvancedGlassSurface` + 合适的 `AdvancedGlassRole`，**不要**另写一套 blur。
- 开关：设置 → 动效 → **高级模糊效果**（`advancedBlurEnabled` / 增强 `enhancedAdvancedBlurEnabled`）。
- 模糊强度：设置里的 **模糊度**（`enhancedAdvancedBlurRadiusDp`），经 `AdvancedGlassController.normalizedBlurAmountDp` 生效。
- 弹窗/下拉：圆角 + 半透明底 + 细边；模糊开时贴近 MiniPlayer 质感，关时用 `fallbackColor` 可读底。
- 参考实现：`NeriMiniPlayer`（`AdvancedGlassRole.MiniPlayer`）、`NeriBottomBar`（`BottomNavigation`）、`ui/component/playlist/GlassDropdownMenu.kt`。"""

new = """### 透明材质（高级模糊）

- 同一透明/毛玻璃控件必须走 `AdvancedGlassSurface` + 合适的 `AdvancedGlassRole`，**不要**另写一套 blur。
- 开关：设置 → 动效 → **高级模糊效果**（`advancedBlurEnabled` / 增强 `enhancedAdvancedBlurEnabled`）。
- 模糊强度：设置里的 **模糊度**（`enhancedAdvancedBlurRadiusDp`），经 `AdvancedGlassController.normalizedBlurAmountDp` 生效。
- 弹窗/下拉：圆角 + 半透明底 + 细边；模糊开时贴近 MiniPlayer 质感，关时用 `fallbackColor` 可读底。
- 参考实现：`NeriMiniPlayer`（`AdvancedGlassRole.MiniPlayer`）、`NeriBottomBar`（`BottomNavigation`）、`ui/component/playlist/GlassDropdownMenu.kt`。

### 弹窗美化复用配方（圆角 + 高级透明模糊）

> 口令：**「使用这里面的记录来美化弹窗」** → 直接按本节复用 `GlassDropdownMenu`，不要另起炉灶。
> 标准参考实现：`ui/component/playlist/GlassDropdownMenu.kt`。

#### 原理（两层，且必须同坐标系）

```text
主窗口（同一 Window）
├─ 底层 content 捕获层（captureAdvancedGlassBackdrop）
│    在「玻璃区域」内做模糊（RenderEffect / LocalBlur）
└─ 上层玻璃面（AdvancedGlassSurface）
     只画 tint / 边缘，背后透出模糊
     注册 boundsInWindow 进 regionRegistry
```

- 对齐公式：`模糊区域 = 玻璃面.boundsInWindow − 捕获层.positionInWindow`
- 两层都在**主窗口** `boundsInWindow` 体系里；弹窗若在独立 Window，必须把区域换算到主窗口再注册。

#### 弹窗步骤（照抄）

1. **定位**：自定义 `PopupPositionProvider` + `androidx.compose.ui.window.Popup`。
   `calculatePosition()` 的返回值就是弹窗在**父窗口（主窗口）** 的坐标，直接作为 `regionBoundsOverride`，**禁止**用弹窗内 `boundsInWindow` 或屏幕坐标硬换算。
2. **玻璃面**：内容外包 `AdvancedGlassSurface(role = AdvancedGlassRole.PopupMenu, shape = RoundedCornerShape(20.dp), fallbackColor = …, tintColor = surfaceContainerHigh, regionBoundsOverride = 主窗口 Rect)`。
3. **宽度**：外层 `Modifier.width(IntrinsicSize.Max)`，把 `DropdownMenuItem` 的 `fillMaxWidth` 收成「最宽一项」；`Popup` 无限宽时不加会变成屏宽。
4. **高度**：`heightIn(max = 360.dp)` 即可。**禁止** `IntrinsicSize.Max` + `verticalScroll` 同层（无限高度会闪退）；列表滚动交给 Material 菜单自身。
5. **注册时机**：override 就绪后要用 `LaunchedEffect` 补注册；只靠 `onGloballyPositioned` 会在位置不再变化时漏注册。
6. **关闭模糊**：`LocalAdvancedGlassController.isBaseBlurEnabled == false` 时退 `fallbackColor` 实底，保证可读。
7. **调用方**：放在锚点同级 `Box` 内（`BoxScope`），锚点在前、菜单在后。

#### 踩坑清单

| 坑 | 现象 | 正确做法 |
|----|------|----------|
| 弹窗 `boundsInWindow` 当主窗口坐标 | 模糊偏到屏幕左上 | 用 `PopupPositionProvider` 返回值 |
| `popupView.rootView` 当主窗口原点 | 仍偏移 | 根本不要自己换算 |
| `fillMaxWidth` / 不设宽 | 菜单变成屏宽 | `width(IntrinsicSize.Max)` |
| `IntrinsicSize.Max` + `verticalScroll` | 点开闪退 | 只收宽度，不加自建滚动 |
| 只在 `onGloballyPositioned` 注册 | 模糊不出现/错位 | override 变化时 `LaunchedEffect` 补注册 |
| 品牌图标跟改圆角 | Logo 失真 | 品牌图标不动（见上文图标节） |"""

if old not in text:
    raise SystemExit("anchor section not found")
text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("popup recipe written")
