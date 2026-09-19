# Sign an unsigned NeriPlayer/Cool Music release APK for local install (e.g. vivo).
# Usage:
#   .\scripts\sign-release.ps1
#   .\scripts\sign-release.ps1 -InPath app\build\outputs\apk\release\NeriPlayer-xxx.apk
#   .\scripts\sign-release.ps1 -InPath .\CoolMusic-v0.1.0-arm64-release.apk -OutPath .\CoolMusic-signed-install.apk

param(
    [string]$InPath = "",
    [string]$OutPath = "",
    [string]$Keystore = "",
    [string]$StorePass = "",
    [string]$KeyAlias = "key0",
    [string]$KeyPass = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PsscriptRoot
if (-not $Keystore) { $Keystore = Join-Path $Root "app\neri.jks" }
if (-not (Test-Path $Keystore)) { throw "Keystore not found: $Keystore" }

if (-not $StorePass) {
    $gradleProps = "C:\Users\lrq_0\.gradle\gradle.properties"
    if (Test-Path $gradleProps) {
        $map = @{}
        Get-Content $gradleProps | ForEach-Object {
            if ($_ -match '^(KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD|KEYSTORE_FILE)=(.*)$') {
                $map[$Matches[1]] = $Matches[2].Trim()
            }
        }
        if ($map["KEYSTORE_PASSWORD"]) { $StorePass = $map["KEYSTORE_PASSWORD"] }
        if ($map["KEY_PASSWORD"]) { $KeyPass = $map["KEY_PASSWORD"] }
        if ($map["KEY_ALIAS"]) { $KeyAlias = $map["KEY_ALIAS"] }
    }
}
if (-not $StorePass) { throw "KEYSTORE_PASSWORD missing (pass -StorePass or set ~/.gradle/gradle.properties)" }
if (-not $KeyPass) { $KeyPass = $StorePass }

if (-not $InPath) {
    $candidates = @(
        (Join-Path $Root "CoolMusic-v0.1.0-arm64-release.apk"),
        (Get-ChildItem (Join-Path $Root "app\build\outputs\apk\release\*.apk") -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName)
    ) | Where-Object { $_ -and (Test-Path $_) }
    if (-not $candidates) { throw "No release APK found. Pass -InPath." }
    $InPath = $candidates[0]
}
if (-not $OutPath) {
    $dir = Split-Path -Parent $InPath
    $name = [IO.Path]::GetFileNameWithoutExtension($InPath)
    if ($name -notmatch 'signed') { $name = "$name-signed" }
    $OutPath = Join-Path $dir "$name.apk"
}

$env:JAVA_HOME = "C:\Users\lrq_0\DevTools\jdk17\jdk17"
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $fallback = "C:\Users\lrq_0\scoop\apps\temurin17-jdk\current"
    if (Test-Path "$fallback\bin\java.exe") { $env:JAVA_HOME = $fallback }
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "C:\Users\lrq_0\AndroidSDK" }
$apksigner = Get-ChildItem "$sdk\build-tools\*\apksigner.bat" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $apksigner) { throw "apksigner.bat not found under $sdk\build-tools" }

Write-Host "Signing $InPath"
Write-Host "  keystore: $Keystore"
Write-Host "  output:   $OutPath"
& $apksigner sign --ks $Keystore --ks-key-alias $KeyAlias `
    --ks-pass "pass:$StorePass" --key-pass "pass:$KeyPass" `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $OutPath $InPath
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

& $apksigner verify --verbose --print-certs $OutPath
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }
Write-Host "OK: $OutPath"
