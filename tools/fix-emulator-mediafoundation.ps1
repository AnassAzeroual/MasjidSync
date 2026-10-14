<#
.SYNOPSIS
    Makes the Android Emulator start on Windows "N" editions by supplying MFPlat.DLL.

.DESCRIPTION
    Windows "N" editions ship WITHOUT the Windows Media components, so
    C:\Windows\System32\MFPlat.dll does not exist. Both emulator.exe and its qemu
    backend statically import MFPlat.DLL, so the Windows loader aborts them with
    0xC0000135 (STATUS_DLL_NOT_FOUND) before a single instruction runs. The user
    -visible symptom in Android Studio is:

        Error running 'app': The emulator process for AVD <name> has terminated.

    The supported fix is the Media Feature Pack:

        Add-WindowsCapability -Online -Name Media.MediaFeaturePack~~~~0.0.1.0

    On some machines that fails with DISM error 14107
    (ERROR_SXS_TRANSACTION_CLOSURE_INCOMPLETE) because the component store's
    capability payload is incomplete, and repairing it needs a matching
    "Features on Demand" source. See "AI Agent Help context project.md" section 8.

    This script applies a local, reversible workaround instead. The payload is
    already present in the component store (WinSxS); the script copies it into
    the three directories from which the emulator loads DLLs:

        <emulator>\                      emulator.exe's own directory
        <emulator>\lib64                 added to the backend's search path by the launcher
        <emulator>\qemu\windows-x86_64   the backend executable's own directory

    It does NOT modify System32, the registry, or any servicing state, so it
    cannot corrupt Windows. Every copied file is listed, and -Undo removes them.

    IMPORTANT: re-run this script after every Android Emulator SDK update, since
    updating the package replaces those directories and deletes the copies.

.PARAMETER Undo
    Remove the copies this script added, restoring the original state.

.EXAMPLE
    .\tools\fix-emulator-mediafoundation.ps1

.EXAMPLE
    .\tools\fix-emulator-mediafoundation.ps1 -Undo
#>
[CmdletBinding()]
param(
    [switch]$Undo
)

$ErrorActionPreference = 'Stop'

$relativeTargets = @(
    '',
    'lib64',
    'qemu\windows-x86_64'
)
$dllName = 'mfplat.dll'

function Find-EmulatorDir {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'emulator\emulator.exe'))) {
            return (Join-Path $candidate 'emulator')
        }
    }
    throw 'Could not find the Android Emulator. Set ANDROID_HOME/ANDROID_SDK_ROOT, or install the emulator SDK package.'
}

function Get-NewestStagedDll {
    param([string]$FileName)
    $components = Get-ChildItem 'C:\Windows\WinSxS' -Directory -Filter "amd64_microsoft-windows-*_*_none_*" -ErrorAction SilentlyContinue
    $best = $null
    $bestVersion = $null
    foreach ($c in $components) {
        $file = Join-Path $c.FullName $FileName
        if (-not (Test-Path -LiteralPath $file)) { continue }
        # Prefer the non-forwarder copy (the 'r\' subdirectories only contain forwarders).
        if ($c.FullName -match '\\r$') { continue }
        $m = [regex]::Match($c.Name, '_(\d+\.\d+\.\d+\.\d+)_')
        $version = if ($m.Success) { [version]$m.Groups[1].Value } else { [version]'0.0.0.0' }
        if ($null -eq $bestVersion -or $version -gt $bestVersion) {
            $bestVersion = $version
            $best = $file
        }
    }
    if ($best) { return [pscustomobject]@{ Path = $best; Version = $bestVersion } }
    return $null
}

$emulatorDir = Find-EmulatorDir
Write-Host "Emulator : $emulatorDir"

$targets = foreach ($rel in $relativeTargets) {
    $dir = if ($rel) { Join-Path $emulatorDir $rel } else { $emulatorDir }
    if (Test-Path $dir) { Join-Path $dir $dllName }
}
$targets = @($targets)

if ($Undo) {
    Write-Host ''
    Write-Host 'Removing copied DLLs...'
    $removed = 0
    foreach ($t in $targets) {
        if (Test-Path -LiteralPath $t) {
            Remove-Item -LiteralPath $t -Force
            Write-Host "  deleted $t"
            $removed++
        }
    }
    Write-Host "Done. $removed file(s) removed."
    exit 0
}

if (Test-Path 'C:\Windows\System32\MFPlat.dll') {
    Write-Host ''
    Write-Host 'Media Foundation is already present in System32 (this is not a broken N-edition setup).'
    Write-Host 'No workaround is required -- the emulator should start on its own.'
    exit 0
}

Write-Host 'Media Foundation: NOT in System32 (expected on this N edition).'

$staged = Get-NewestStagedDll -FileName $dllName
if (-not $staged) {
    throw "Could not find a staged $dllName in C:\Windows\WinSxS. Install the Media Feature Pack instead."
}
Write-Host "Staged payload : $($staged.Path)  (v$($staged.Version))"
Write-Host ''

$copied = 0
foreach ($t in $targets) {
    $same = $false
    if (Test-Path -LiteralPath $t) {
        $same = (Get-FileHash -LiteralPath $t).Hash -eq (Get-FileHash -LiteralPath $staged.Path).Hash
    }
    if ($same) {
        Write-Host "  up to date  $t"
    }
    else {
        Copy-Item -LiteralPath $staged.Path -Destination $t -Force
        Write-Host "  copied      $t"
        $copied++
    }
}

Write-Host ''
Write-Host "Done. $copied file(s) written."
Write-Host ''

# Functional check: the emulator can only list AVDs when the loader succeeds.
$emulatorExe = Join-Path $emulatorDir 'emulator.exe'
Write-Host 'Verifying: emulator.exe -list-avds ...'
$avds = & $emulatorExe -list-avds 2>&1
$exit = $LASTEXITCODE
if ($exit -eq 0 -and $avds) {
    Write-Host '  OK - emulator starts and reports AVDs:' -ForegroundColor Green
    $avds | ForEach-Object { Write-Host "    $_" }
    Write-Host ''
    Write-Host 'You can now run the app from Android Studio (or: emulator -avd <name>).' -ForegroundColor Green
}
else {
    Write-Host "  emulator.exe still fails (exit code $exit)." -ForegroundColor Red
    Write-Host '  Run .\tools\diagnose-emulator.ps1 for a full report.'
    exit 1
}
