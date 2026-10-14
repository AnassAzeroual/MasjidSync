<#
.SYNOPSIS
    Builds, installs, launches and smoke-tests Mosque Tracker on a connected device/emulator,
    then prints the manual verification checklist.

.DESCRIPTION
    Pipeline:
      1. build the debug APK (skippable with -SkipBuild)
      2. install it over the existing app
      3. force-stop, clear logcat, launch MainActivity
      4. watch logcat for a few seconds and fail on FATAL EXCEPTION / ANR / our own crash tag
      5. pull a screenshot into artifacts/
      6. print the manual checklist from the project context file

    Exits non-zero on build failure, install failure, no device, or a detected crash.

.EXAMPLE
    .\tools\verify-on-device.ps1
    .\tools\verify-on-device.ps1 -Serial R5CY42DCQAX -LogSeconds 30
    .\tools\verify-on-device.ps1 -SkipBuild

.NOTES
    The emulator on the dev machine is currently broken (emulator.exe exits 0xC0000135,
    STATUS_DLL_NOT_FOUND). Use a physical device until that is resolved. See
    "AI Agent Help context project.md" section 8.
#>
[CmdletBinding()]
param(
    # adb device serial. Defaults to the only connected device.
    [string]$Serial,

    # Seconds to watch logcat after launch.
    [int]$LogSeconds = 15,

    # Reuse an existing debug APK instead of rebuilding.
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$applicationId = 'com.mosquetracker.app.debug'
$launchComponent = "$applicationId/com.mosquetracker.app.MainActivity"
$apkPath = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'

# ---------------------------------------------------------------- prerequisites

function Resolve-Adb {
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    throw "adb not found. Install platform-tools or set ANDROID_HOME / ANDROID_SDK_ROOT."
}

function Resolve-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { return }
    $studio = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studio 'bin\java.exe')) {
        $env:JAVA_HOME = $studio
        Write-Host "JAVA_HOME set to $studio"
        return
    }
    throw "No JDK found. Set JAVA_HOME to a JDK 17+ install."
}

$adb = Resolve-Adb
Write-Host "adb: $adb"

# ---------------------------------------------------------------- device

$deviceLines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
$serials = @($deviceLines | ForEach-Object { ($_ -split "`t")[0] })

if (-not $Serial) {
    if ($serials.Count -eq 0) {
        Write-Error @"
No device/emulator is connected.

  * Plug in a phone with USB debugging enabled and run: adb devices
  * The bundled emulator is currently BROKEN on this machine (emulator.exe exits
    0xC0000135 STATUS_DLL_NOT_FOUND) - use a physical device.
"@
    }
    if ($serials.Count -gt 1) {
        throw "Multiple devices attached ($($serials -join ', ')). Re-run with -Serial <serial>."
    }
    $Serial = $serials[0]
}

if ($serials -notcontains $Serial) {
    throw "Device '$Serial' is not attached. Connected: $($serials -join ', ')."
}

Write-Host "Target device: $Serial"

# ---------------------------------------------------------------- build

if (-not $SkipBuild) {
    Resolve-JavaHome
    $gradlew = Join-Path $repoRoot 'gradlew.bat'
    Write-Host "Building debug APK..."
    & $gradlew :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }
}

if (-not (Test-Path $apkPath)) {
    throw "APK not found at $apkPath. Build it first (drop -SkipBuild)."
}

# ---------------------------------------------------------------- install

Write-Host "Installing $apkPath ..."
# `adb install` can print Success while the package manager rejects the update
# (e.g. signature mismatch). Parse the result instead of trusting the exit code.
$installOutput = & $adb -s $Serial install -r -g $apkPath 2>&1 | Out-String
Write-Host $installOutput.Trim()
if ($installOutput -notmatch 'Success') {
    throw "Install failed:`n$installOutput"
}

# ---------------------------------------------------------------- launch + watch

& $adb -s $Serial shell am force-stop $applicationId | Out-Null
& $adb -s $Serial logcat -c | Out-Null

Write-Host "Launching $launchComponent ..."
$startOutput = & $adb -s $Serial shell am start -W -n $launchComponent 2>&1 | Out-String
Write-Host $startOutput.Trim()
if ($startOutput -match 'Error' -or $startOutput -match 'does not exist') {
    throw "Failed to launch activity:`n$startOutput"
}

Write-Host "Watching logcat for $LogSeconds s ..."
$logFile = Join-Path $env:TEMP "mosquetracker-logcat-$([guid]::NewGuid().ToString('N')).txt"
& $adb -s $Serial logcat -d -v threadtime 2>&1 | Out-File -FilePath $logFile -Encoding utf8

$deadline = (Get-Date).AddSeconds($LogSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
}
& $adb -s $Serial logcat -d -v threadtime 2>&1 | Out-File -FilePath $logFile -Encoding utf8 -Append

$crashPatterns = @(
    'FATAL EXCEPTION',
    'ANR in',
    'E AndroidRuntime',
    'Shutting down VM',
    "Force finishing activity $applicationId"
)
$crashLines = Get-Content $logFile | Where-Object {
    $line = $_
    $crashPatterns | Where-Object { $line -like "*$_*" } | Select-Object -First 1
}

if ($crashLines) {
    Write-Host ''
    Write-Host '--- crash lines ---' -ForegroundColor Red
    $crashLines | Select-Object -Last 60 | ForEach-Object { Write-Host $_ }
    Write-Host "Full logcat: $logFile"
    throw 'Crash/ANR detected in logcat.'
}

Write-Host 'No crashes or ANRs detected.' -ForegroundColor Green

# Sanity: is the app process actually alive?
$pidLine = & $adb -s $Serial shell pidof $applicationId
if (-not $pidLine) {
    throw "App process is not running after launch. Check $logFile."
}
Write-Host "App process pid: $($pidLine.Trim())"

# ---------------------------------------------------------------- screenshot

$artifacts = Join-Path $repoRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifacts "screenshot-$stamp.png"
& $adb -s $Serial exec-out screencap -p > $screenshot
Write-Host "Screenshot: $screenshot"

# ---------------------------------------------------------------- checklist

$checklist = @'

================================================================
 MANUAL VERIFICATION CHECKLIST  (see context file section 9)
================================================================
 1. Grant foreground location -> "Allow all the time" -> notifications
    -> DND access from the Dashboard.
 2. Tap "+ Add Current Location as Mosque"; confirm it appears in the
    Override Center.
 3. Walk/teleport across the geofence boundary; phone must silence on
    entry and restore on exit.
 4. Confirm an attendance_logs row appears in History with the right
    detection method.
 5. Bind a Wi-Fi BSSID / BLE beacon; confirm Tier 2 wins over Tier 3 in
    the logged method.
 6. Reboot the device; confirm monitoring restarts (BootReceiver).
 7. Battery: leave monitoring on for a few hours; check drain and OEM
    process killing.
================================================================
'@
Write-Host $checklist -ForegroundColor Cyan

Write-Host "Automated pass complete for device $Serial."
