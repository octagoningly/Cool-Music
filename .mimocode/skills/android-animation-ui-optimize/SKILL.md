---
name: android-animation-ui-optimize
description: Android Compose 动画与 UI 性能优化。当用户要求优化动画、修卡顿/jank、做页面转场、封面/歌词/迷你播放器动效、毛玻璃/模糊性能、帧率或「看起来不够顺」时使用。贴合 NeriPlayer（Compose 音乐播放器 + AdvancedGlass）。
---

# Android 动画 UI 优化（NeriPlayer / Compose）

目标：动画**顺、稳、可预测**——不掉帧、不抢手势、不在不可见时烧 GPU。

## 何时触发

- 「优化动画 / 转场 / 卡顿 / jank / 掉帧 / 不丝滑」
- 封面展开、MiniPlayer ↔ NowPlaying、Tab 切换、歌词滚动、Sheet 手势
- AdvancedGlass / 毛玻璃 / RenderEffect / blur 发热或掉帧
- 列表滑动、过度滚动、弹簧回弹异常
- 新增动效前的规范审查

## 本项目关键路径

| 场景 | 主要文件 |
|------|----------|
| Tab 转场 | `ui/MainTabTransitionController.kt`, `ui/MainTabLayerHost.kt` |
| 导航转场 | `ui/screen/host/*Transition*`, `ui/effect/glass/AdvancedGlassNavigationTransition.kt` |
| MiniPlayer | `ui/component/playback/NeriMiniPlayer.kt` |
| NowPlaying 控件 | `ui/component/playback/*`（`WaveformSlider`, `PlaybackControl*`, `NowPlayingCover*`） |
| 歌词 | `ui/component/lyrics/*`, `np-submodule/accompanist-lyrics-ui` |
| 毛玻璃 | `ui/effect/glass/*`（尤其 `AdvancedGlassPerformanceGate` / `RenderEffect` / `LocalBlur*`） |
| 主题揭幕 | `ui/component/common/ThemeRevealOverlay.kt` |
| 导航条/底栏 | `ui/component/navigation/NeriBottomBar.kt` |

项目已有的降级开关（优先复用，不要另起炉灶）：

- 歌词：`lowPowerRendering`, `useAdditiveBlend`, `lyricBlurEnabled`
- 毛玻璃：`AdvancedGlassPerformanceGate` / RenderProfile / RegionRegistry
- 触摸屏蔽：`ui/component/common/TouchInputBlockers.kt`（转场中防误触）

## 优化决策树

```
用户反馈「动画有问题」
├─ 1. 是「观感」还是「性能」？
│    ├─ 观感（时长/曲线/层级/遮挡）→ 改 AnimationSpec / 布局，不必先上 profiler
│    └─ 性能（卡顿/掉帧/发热）→ 走下面测量路径
├─ 2. 复现场景定位
│    ├─ 仅转场瞬间 → 查是否同时动 layout/blur/大图
│    ├─ 持续播放时 → 查歌词/波形/封面无限动画 + 每帧重组
│    └─ 列表滚动 → 查 item 动画、玻璃区域随滚动重算
├─ 3. 常见根因（按概率）
│    ├─ 非必要的 layout 动画（size/offset 驱动整棵子树 remeasure）
│    ├─ graphicsLayer 参数在组合阶段每帧变化，导致 layer 失效
│    ├─ blur/RenderEffect 作用在过大区域或每帧全屏重算
│    ├─ 长重组：不稳定 lambda/data class 进入子组件
│    ├─ 主线程 IO/JSON/图片解码与动画同帧
│    └─ 转场中多层玻璃 + 阴影 + 大图同时出现
└─ 4. 修复优先级
     P0 可见性/手势正确性
     P1 掉帧与发热（帧时间、GPU）
     P2 曲线与时长统一
     P3 花活（粒子、多重模糊等）——默认不做
```

## Compose 动画硬规则

### 1. 优先动这些（便宜）

```kotlin
// Good: 只动 layer 属性，不 remeasure
Modifier.graphicsLayer {
    alpha = animatedAlpha
    translationY = animatedY
    scaleX = animatedScale
    scaleY = animatedScale
}

// Bad: 每帧改 size/offset 触发 layout
Modifier.offset { IntOffset(0, animatedY.roundToInt()) }
Modifier.height(animatedDp)
```

`alpha` / `translation` / `scale` / `rotation` / `clip` → `graphicsLayer`。
只有布局语义必须变时才动 `offset`/`size`。

### 2. 状态与驱动

- 单一驱动源：手势 → 一个 `Animatable`/`offsetFraction` → UI 读取。
- 参考 `MainTabTransitionController`：`offsetFraction` + `isRunning` + generation 防竞态。
- 转场中取消旧 Job；新请求打断旧行为，不要叠两套动画。
- 播放进度、歌词时间**不要**用 `withFrameNanos` 往 State 里狂写除非必要；高频源抽稀或只在可见时订阅。

### 3. 重组稳定性

- 进入动画子树的回调用 `rememberUpdatedState`，避免动画中闭包过期。
- 大列表 item 不要传入每次新建的 data class/lambda 而不做 `key`。
- 动画相关局部状态 `remember` + 稳定 key；配置变化要按产品语义决定恢复与否。
- `@Stable`/`@Immutable` 用在跨层传递的 UI 模型上（项目里 Controller 已是 `@Stable`）。

### 4. 手势与动画协作

- 可拖区域与父子嵌套滚动要显式：参考 `BottomSheetGestureGuards`、MiniPlayer `detectHorizontalDragGiles`（注意实际是 `detectHorizontalDragGestures`）。
- 转场进行中用 `TouchInputBlockers` 挡误触，而不是事后修点击穿透。
- 手势松手后的 settling 用 spring（跟手后自然回弹），纯自动转场可用 `tween(FastOutSlowInEasing)`。

### 5. 时长与曲线（本项目 Apple Music 风）

| 场景 | 建议 |
|------|------|
| 控件微反馈（按压/切换） | 80–150ms |
| Sheet / 面板展开 | 250–400ms |
| Tab / 页级转场 | 与 `advancedGlassMainTabEnter/ExitSpec` 对齐，勿另写一套 |
| 封面 ↔ 全屏播放 | 连续驱动优于两段硬切；跟手势优先 |
| 歌词自动滚动 | 平滑优先，避免每句硬 jump；用户拖动时暂停自动滚 |

曲线：控件 `FastOutSlowIn` 或项目既有 spec；跟手手势用 `spring`。**禁止**同一产品线里三种不同 Tab 时长。

## 毛玻璃 / 模糊（NeriPlayer 重灾区）

检查清单：

1. **范围**：只 blur 可见 region，用 `AdvancedGlassRegionRegistry` / LocalBlurPlan，禁止无脑全屏 RenderEffect。
2. **频率**：静态背景不要每帧重采样；滚动/转场时评估是否降采样或缓存。
3. **层级**：转场中避免「旧页玻璃 + 新页玻璃 + 遮罩」同时满载；退出时尽早摘掉 blur。
4. **降级**：低端机/省电走 `lowPowerRendering` / PerformanceGate 关路径。
5. **像素对齐**：region 边界 round 到物理像素（见 `AdvancedGlassPerformanceGate`），减少半透明缝与额外 overdraw。
6. **不要**在歌词每一行叠 blur；只对当前焦点行或外层容器做一次。

## 歌词动效

- 自动滚动与用户拖动互斥；拖动结束后再恢复对齐。
- 模糊/缩放/透明度尽量作用在**焦点行容器**，而不是每字每帧 shader。
- Karaoke 逐字动画：跟播放时钟，不要为每字开协程；离屏行停动画。
- `CompositingStrategy` / `BlendMode` 仅在需要时开；低性能路径关掉 additive blend。

## MiniPlayer / NowPlaying

- MiniPlayer 手势位移用 `graphicsLayer.translation`，松手后再决定 dismiss/expand。
- 进度条/波形：每帧 path 变化要有上限（采样点、只画可见段）。
- 封面：列表用 Coil 尺寸约束；转场共享元素或 preview transform 不要解码 2× 全图。
- 无限动画（旋转封面、呼吸灯）：`isPlaying && isVisible` 时才跑；不可见 `pause`。

## 列表性能

- `LazyColumn/LazyRow`：稳定 `key`；item 动画不要用全量 `animateItem` 打在超长列表上而不测。
- 探索页横向卡片：预取有限，边缘 fade 不要每个 item 一层重 blur。
- 尽量避免 nested scroll 双重拦截（更新记录里已有「移除 nestedScroll 拦截修滑动卡顿」教训）。

## 测量与验收

### 必做

1. **真机**（或至少 `adb shell dumpsys gfxinfo <pkg>`）看转场窗口 janky frames。
2. Android Studio **Layout Inspector / Recomposition Count** 看转场与歌词是否每帧重组。
3. GPU 过度绘制：转场峰值区域不超过 2–3 层。
4. 长场景（播放页挂 3–5 分钟）观察是否发热——模糊常是元凶。
5. 相关测试不回归：  
   `MiniPlayerPlaybackTransitionTest`, `NeriAppNavigationTransitionTest`, `HostNavigationTransitionGeometry*`, `AdvancedGlass*RenderTest`, `Explore*TransitionTest`

### 预算（软目标）

- 60Hz：一帧 ~16.6ms；动画期间尽量 P99 < 16ms，允许偶发，不允许连续掉。
- 转场峰值避免 >3 层全屏模糊。
- 动画相关主线程工作 >8ms 的同步段要移出帧或缓存。

### 命令参考

```powershell
# 帧统计
adb shell dumpsys gfxinfo moe.ouom.neriplayer framestats

# 仅跑动画相关单测（示例，按模块调整）
.\gradlew :app:testDebugUnitTest --tests "*Transition*"
.\gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=moe.ouom.neriplayer.ui.MiniPlayerPlaybackTransitionTest
```

## 新增动效的流程

1. 说清用户故事：谁在什么状态看到什么变化。
2. 选驱动：手势驱动 / 状态驱动 / 时钟驱动；禁止三者叠在同一元素上无优先级。
3. 选 API：`graphicsLayer` → `animate*AsState`/`Animatable` → `Transition` → 自定义 Canvas；玻璃必须走 AdvancedGlass API。
4. 接降级：不可见暂停、低性能路径、取消与打断。
5. 对齐既有 spec（Tab 用 MainTab/Glass 的 enter/exit）。
6. 真机看帧 + 跑相关测试。
7. 更新 `更新记录.md`（格式：`lrq--xx功能--日期`）。

## 反模式（Code Review 直接打回）

- 在 `drawBehind`/`graphicsLayer` 每帧分配对象、创建 Path/Shader 而不缓存。
- `while(true) { delay(16); progress++ }` 驱动 UI 而不用动画/时钟状态。
- 全屏 `blur` + `shadow` + 大圆角同时出现在转场每一帧。
- 用 `LaunchedEffect(Unit) { animate(...) }` 写在可重组热点里且 key 不稳定。
- 为「更炫」在低端路径保留重效果。
- 动画结束不清理：玻璃 region 残留、触摸拦截不撤、Job 泄漏。

## 外部参考（调研用，不直接拷代码）

- `skydoves/compose-performance` — Compose 重组与性能
- `wasabeef/awesome-android-ui` — UI/动画库索引
- `nikhilpanju/FabFilter` — 复杂转场示范
- `airbnb/lottie-android` — 需要设计师资源时的动效播放（评估包体与线程）
- 官方 `android/compose-samples` — MotionLayout/Compose 转场写法

## 输出建议格式（给用户的优化方案）

1. **现象**：卡在哪、可复现步骤  
2. **根因**：重组/layout/blur/手势/主线程  
3. **改法**：文件级 + 关键 diff 思路（优先 P0/P1）  
4. **降级**：低端机怎么表现  
5. **验证**：跑哪些测试 / 看哪些指标  
6. **风险**：手势、可访问性、横竖屏
