# 构建 AutoLedger 安卓 App。
# 环境（本机已具备，2026-10 打包留下）：
#   C:\Users\DNY\dev\jdk\jdk-17.0.2
#   C:\Users\DNY\dev\gradle\gradle-8.2
#   C:\Users\DNY\dev\android-sdk  (build-tools 34.0.0 / platforms android-34)
#
# 用法：powershell -ExecutionPolicy Bypass -File build-apk.ps1

$ErrorActionPreference = 'Stop'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$android = Join-Path $dir 'android'

$env:JAVA_HOME = 'C:\Users\DNY\dev\jdk\jdk-17.0.2'
$env:ANDROID_HOME = 'C:\Users\DNY\dev\android-sdk'
$env:ANDROID_SDK_ROOT = 'C:\Users\DNY\dev\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

$gradle = 'C:\Users\DNY\dev\gradle\gradle-8.2\bin\gradle.bat'
if (-not (Test-Path $gradle)) { throw "找不到 gradle: $gradle" }
if (-not (Test-Path $env:JAVA_HOME)) { throw "找不到 JDK: $env:JAVA_HOME" }

Write-Output '== 构建 release APK =='
& $gradle -p $android assembleRelease --no-daemon --console=plain

$built = Join-Path $android 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $built)) { throw "构建产物不存在: $built" }

$dest = Join-Path $dir 'AutoLedger.apk'
Copy-Item $built $dest -Force
Write-Output ''
Write-Output ('产物: ' + $dest + '  (' + [math]::Round((Get-Item $dest).Length / 1MB, 2) + ' MB)')
