# NeriPlayer 一键安装到真机（Debug）
# 用法：
#   .\scripts\install-debug.ps1
#   .\scripts\install-debug.ps1 -Wireless 192.168.1.100:5555
#   .\scripts\install-debug.ps1 -SkipBuild   # 已有 APK 时只安装

param(
    [string]$Wireless = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Adb = "C:\Users\lrq_0\AndroidSDK\platform-tools\adb.exe"
if (-not (Test-Path $Adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $Adb = $cmd.Source } else { throw "未找到 adb，请检查 Android SDK platform-tools" }
}

$env:JAVA_HOME = "C:\Users\lrq_0\DevTools\jdk17\jdk17"
$env:ANDROID_HOME = "C:\Users\lrq_0\AndroidSDK"
$env:ANDROID_SDK_ROOT = "C:\Users\lrq_0\AndroidSDK"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if ($Wireless) {
    Write-Host "连接无线设备 $Wireless ..." -ForegroundColor Cyan
    & $Adb connect $Wireless
}

Write-Host "检查设备..." -ForegroundColor Cyan
& $Adb devices -l
$ready = & $Adb devices | Select-String -Pattern "device$"
if (-not $ready) {
    Write-Host ""
    Write-Host "没有检测到已授权设备。请：" -ForegroundColor Yellow
    Write-Host "  1. 手机打开「开发者选项 → USB 调试」"
    Write-Host "  2. USB 连接电脑，手机上点「允许调试」"
    Write-Host "  3. 或无线：手机「无线调试」开好后执行本脚本 -Wireless <ip:port>"
    Write-Host "  4. 也可先运行：  & `"$Adb`" devices -l"
    exit 1
}

if (-not $SkipBuild) {
    Write-Host "编译并安装 Debug..." -ForegroundColor Cyan
    Push-Location $Root
    try {
        & .\gradlew.bat :app:installDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradlew installDebug 失败" }
    } finally {
        Pop-Location
    }
} else {
    $apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "未找到 APK: $apk（去掉 -SkipBuild 先构建）" }
    Write-Host "安装已有 APK: $apk" -ForegroundColor Cyan
    & $Adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "adb install 失败" }
}

Write-Host ""
Write-Host "完成。可选：" -ForegroundColor Green
Write-Host "  启动应用:  & `"$Adb`" shell am start -n moe.ouom.neriplayer/.activity.MainActivity"
Write-Host "  看日志:    & `"$Adb`" logcat -s NERI-LxMusicSource NERI-PlayerManager"
Write-Host "  卸载:      & `"$Adb`" uninstall moe.ouom.neriplayer"
