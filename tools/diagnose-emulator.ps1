<#
.SYNOPSIS
    Diagnoses why the Android Emulator will not start on this machine.

.DESCRIPTION
    Encodes the failure mode documented in "AI Agent Help context project.md" section 8:

      On Windows **N** editions, emulator.exe and its qemu backend statically import
      MFPlat.DLL (Media Foundation). N editions ship without it, so the Windows loader
      aborts them with 0xC0000135 (STATUS_DLL_NOT_FOUND). Android Studio reports:
      "The emulator process for AVD <name> has terminated."

    This script reports the edition, whether Media Foundation is available, whether the
    local workaround from tools\fix-emulator-mediafoundation.ps1 is in place, and runs a
    real functional test (emulator -list-avds). It prints a verdict and the exact fix.

.EXAMPLE
    .\tools\diagnose-emulator.ps1
#>
[CmdletBinding()]
param()

function Write-Section($t) {
    Write-Host ''
    Write-Host "===== $t =====" -ForegroundColor Cyan
}

$emulatorDir = $null
foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
    if ($candidate -and (Test-Path (Join-Path $candidate 'emulator\emulator.exe'))) {
        $emulatorDir = Join-Path $candidate 'emulator'
        break
    }
}

Write-Section 'Android emulator'
if (-not $emulatorDir) {
    Write-Host 'emulator.exe not found. Set ANDROID_HOME or install the emulator SDK package.' -ForegroundColor Red
    exit 1
}
$emulatorExe = Join-Path $emulatorDir 'emulator.exe'
Write-Host "emulator: $emulatorExe"

Write-Section 'Windows edition'
$cv = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
Write-Host "Product : $($cv.ProductName)"
Write-Host "Edition : $($cv.EditionID)"
Write-Host "Build   : $($cv.CurrentBuild).$($cv.UBR)"
$isN = $cv.EditionID -like '*N'
Write-Host "N edition (no Media Foundation by default): $isN" -ForegroundColor ($(if ($isN) { 'Yellow' } else { 'Green' }))

Write-Section 'Media Foundation'
$inSystem32 = Test-Path 'C:\Windows\System32\MFPlat.dll'
Write-Host "System32\MFPlat.dll : $inSystem32" -ForegroundColor ($(if ($inSystem32) { 'Green' } else { 'Red' }))

$workaroundPaths = @(
    (Join-Path $emulatorDir 'mfplat.dll'),
    (Join-Path $emulatorDir 'lib64\mfplat.dll'),
    (Join-Path $emulatorDir 'qemu\windows-x86_64\mfplat.dll')
)
$present = @($workaroundPaths | Where-Object { Test-Path -LiteralPath $_ })
Write-Host "local workaround copies : $($present.Count)/$($workaroundPaths.Count)"
foreach ($p in $workaroundPaths) {
    $mark = if (Test-Path -LiteralPath $p) { 'ok  ' } else { 'MISS' }
    Write-Host "  $mark $p"
}

Write-Section 'Functional test: emulator -list-avds'
$avds = & $emulatorExe -list-avds 2>&1
$exit = $LASTEXITCODE
if ($exit -eq 0 -and $avds) {
    Write-Host 'emulator responds:' -ForegroundColor Green
    $avds | ForEach-Object { Write-Host "  $_" }
}
else {
    Write-Host "emulator.exe exited $exit and listed no AVDs." -ForegroundColor Red
    if ($avds) { $avds | ForEach-Object { Write-Host "  $_" } }
}

Write-Section 'VERDICT'
if ($exit -eq 0 -and $avds) {
    if ($inSystem32) {
        Write-Host 'OK: Media Foundation is present in System32; nothing to do.' -ForegroundColor Green
    }
    else {
        Write-Host 'OK: the Media Foundation workaround is in place; the emulator starts.' -ForegroundColor Green
        Write-Host 'Reminder: re-run tools\fix-emulator-mediafoundation.ps1 after every' -ForegroundColor Yellow
        Write-Host 'Android Emulator SDK update, which replaces these directories.' -ForegroundColor Yellow
    }
}
else {
    Write-Host 'BLOCKED: the emulator cannot start.' -ForegroundColor Red
    if ($isN -and -not $inSystem32) {
        Write-Host 'Cause: Windows N edition without Media Foundation -> MFPlat.DLL missing -> 0xC0000135.'
        Write-Host ''
        Write-Host 'Quick fix (local, reversible, no admin):' -ForegroundColor Yellow
        Write-Host '  .\tools\fix-emulator-mediafoundation.ps1'
        Write-Host ''
        Write-Host 'Supported fix (Administrator PowerShell, then reboot):' -ForegroundColor Yellow
        Write-Host '  Add-WindowsCapability -Online -Name Media.MediaFeaturePack~~~~0.0.1.0'
        Write-Host '  # if that fails with DISM 14107 (ERROR_SXS_TRANSACTION_CLOSURE_INCOMPLETE):'
        Write-Host '  # repair with a matching Features-on-Demand source, or use the workaround above'
    }
    else {
        Write-Host 'Media Foundation is present, so the cause is elsewhere - inspect the component store.'
    }
    Write-Host ''
    Write-Host 'Alternative: use a physical device -> .\tools\verify-on-device.ps1'
}
