# Cool Music

基于开源项目 **[NeriPlayer](https://github.com/cwuom/NeriPlayer)**（GPLv3）二次开发的 Android 音乐播放器衍生版本。

- 应用名称：**Cool Music**
- 许可证：GPLv3（与上游一致）
- 原项目作者：NeriPlayer developers（cwuom）
- 声明：Cool Music 与原作者无关；修改版按 GPLv3 开源，使用时须遵守该协议并保留原作者与许可证声明。

## 相对上游的修改

| 模块 | 说明 |
|------|------|
| 品牌 | 应用名改为 Cool Music，更换启动图标（深色底 + 波形） |
| 关于页 | 增加「原作者声明」，指向 NeriPlayer 与 GPLv3 |
| 在线音源 | 设置 → 第三方平台登录 → 在线音乐源：支持 LX Music JSON / JS 音源导入与播放优先 |
| 播放角标 | 使用在线音源时封面区显示「在线音源」 |
| 探索页 | 首页精简为单封面 +「新发现」全屏二级页（风格歌单浏览） |
| 设置导航 | 从其他 Tab 再进入设置时回到一级页，避免二级页闪烁 |
| 构建 | 支持 ADB 覆盖安装调试（`scripts/install-debug.ps1`） |

详细实现说明见根目录 `更新记录.md`。

## 构建

```text
JAVA_HOME 指向 JDK17+
ANDROID_HOME 指向 Android SDK（compileSdk 37）
.\gradlew.bat :app:installDebug
```

Release（需签名）：

```text
gradle.properties 或环境配置 KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
.\gradlew.bat :app:assembleRelease
```

官方 Release 默认仅 `arm64-v8a` 并开启 R8/资源压缩；Debug 包体积更大属正常现象。

## 上游与许可证

本仓库代码来源于 NeriPlayer，遵循 **GNU GPL v3**。分发 APK 时须同时提供对应源码或有效获取方式，并保留版权与许可证信息。完整许可文本见 `LICENSE`。

上游项目：<https://github.com/cwuom/NeriPlayer>

## 隐私与数据

- 本仓库 **不应** 包含：签名密钥（`.jks`）、`local.properties`、账号 Cookie/Token、设备日志等个人数据。
- `.gitignore` 已忽略 `local.properties`、构建产物、IDE 文件等。
- 应用默认在设备本地存储设置；同步功能由用户自行配置。
