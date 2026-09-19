# NeriPlayer install debug APK to a connected device.
# Usage:
#   .\scripts\install-debug.ps1
#   .\scripts\install-debug.ps1 -Wireless 192.168.1.100:5555
#   .\scripts\install-debug.ps1 -SkipBuild

param(
    [string]$Wireless = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Adb = "C:\Users\lrq_0\AndroidSDK\platform-tools\adb.exe"
if (-not (Test-Path $Adb)) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $Adb = $cmd.Source } else { throw "adb not found in Android SDK platform-tools" }
}

$env:JAVA_HOME = "C:\Users\lrq_0\DevTools\jdk17\jdk17"
$env:ANDROID_HOME = "C:\Users\lrq_0\AndroidSDK"
$env:ANDROID_SDK_ROOT = "C:\Users\lrq_0\AndroidSDK"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if ($Wireless) {
    Write-Host "Connecting wireless device $Wireless ..."
    & $Adb connect $Wireless
}

Write-Host "Checking devices..."
& $Adb devices -l
$ready = & $Adb devices | Select-String -Pattern "device$"
if (-not $ready) {
    Write-Host ""
    Write-Host "No authorized device found."
    Write-Host "1) Enable Developer options + USB debugging on phone"
    Write-Host "2) Plug USB and accept the debug prompt on phone"
    Write-Host "3) Wireless: enable wireless debugging, then run:"
    Write-Host "   .\scripts\install-debug.ps1 -Wireless IP:PORT"
    exit 1
}

if (-not $SkipBuild) {
    Write-Host "Building and installing debug..."
    Push-Location $Root
    try {
        & .\gradlew.bat :app:installDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradlew installDebug failed" }
    } finally {
        Pop-Location
    }
} else {
    $apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "APK not found: $apk (drop -SkipBuild to build first)" }
    Write-Host "Installing existing APK: $apk"
    & $Adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
}

Write-Host ""
Write-Host "Done. Optional next steps:"
Write-Host ("  Launch:  & '{0}' shell am start -n moe.ouom.coolmusic/moe.ouom.neriplayer.activity.MainActivity" -f $Adb)
Write-Host ("  Logs:    & '{0}' logcat -s NERI-LxMusicSource NERI-PlayerManager" -f $Adb)
Write-Host ("  Uninstall: & '{0}' uninstall moe.ouom.coolmusic" -f $Adb)
