<#
.SYNOPSIS
    Drives the emulator across a mosque's boundary and checks that the app calls the zone inside
    exactly where the drawn circle says it should.

.DESCRIPTION
    "Am I inside the mosque's circle?" is decided in one place (DetectionPipeline.isInsideZone) and
    the rule is centre-based: the reported position is compared with the circle you drew, and the
    fix's accuracy is not part of the comparison. ZoneBoundaryTest pins that rule down in a JVM test
    - including the case people usually expect the other way round, a fix whose error circle
    overlaps the mosque circle while its centre sits outside.

    This script covers the half a JVM test cannot reach: the wiring on a real device.

        mock position -> location flow -> Home pill          (what the user sees)
        mock position -> Play Services geofence -> worker     (the arrival that runs the routine)

    Point tests: it parks the mock position at a handful of exact distances either side of the
    drawn radius and reads the app's own Inside/Outside pill. Expected: inside up to and including
    the radius, outside beyond it. The pill is driven by the same location flow and the same
    function as the arrival check, so it is the honest instrument for the rule; it also updates on
    the next fix, which keeps each point to a few seconds.

    Arrival watch (-WatchArrival): the geofence path is slower and jittery (Play Services decides
    the transition, then a worker verifies it), so this part leaves the fence completely, settles,
    steps inside and then polls the app's attendance table for a new row.

    Nothing here can test an accuracy circle that merely touches the mosque circle: the emulator's
    injected fixes carry the emulator's own accuracy, not one you choose, and Play Services only
    raises an ENTER once the reported position itself crosses the fence. That case lives in the
    unit tests by necessity, not by preference.

.PARAMETER Serial
    adb device serial. Must be an emulator: mock coordinates cannot be injected over adb into a
    physical phone.

.PARAMETER Mosque
    Name (or part of it) of the mosque to test. Defaults to the first tracked mosque.

.PARAMETER Lat / Lon / Radius
    Test these coordinates instead of reading a saved mosque. -Radius is the drawn radius in metres.

.PARAMETER StepMeters
    Distance between the test points near the boundary. Default 5.

.PARAMETER SettleSeconds
    How long to wait for the app to react to a new mock position. Default 8.

.PARAMETER WatchArrival
    Also verify that a real arrival is recorded when the position enters the zone, and that none is
    recorded outside it. Slow: each half needs the fence to be left and to settle.

.PARAMETER ArrivalTimeoutSeconds
    How long to wait for an attendance row to appear during the arrival watch. Default 120.

.PARAMETER ExitSettleSeconds
    Extra wait after the pill reports Outside, so Play Services has time to settle the EXIT before
    an ENTER can be expected. Default 30.

.PARAMETER List
    Print the saved mosques (name, coordinates, radius) and exit.

.EXAMPLE
    .\tools\zone-boundary-sweep.ps1 -List
    .\tools\zone-boundary-sweep.ps1 -Mosque Al-Noor
    .\tools\zone-boundary-sweep.ps1 -Mosque Al-Noor -WatchArrival
    .\tools\zone-boundary-sweep.ps1 -Lat 37.422 -Lon -122.084 -Radius 100 -StepMeters 2

.NOTES
    Needs a debug build, because the saved mosque and the attendance table are read through run-as.
    The app has to be on the Home tab: that is where the pill lives.
#>
[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5554',
    [string]$Package = 'com.mosquetracker.app.debug',
    [string]$Mosque,
    [double]$Lat,
    [double]$Lon,
    [int]$Radius,
    [int]$StepMeters = 5,
    [int]$SettleSeconds = 8,
    [switch]$WatchArrival,
    [int]$ArrivalTimeoutSeconds = 120,
    [int]$ExitSettleSeconds = 30,
    [switch]$List
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ------------------------------------------------------------------ prerequisites

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
    throw 'adb not found. Set ANDROID_HOME or put platform-tools on PATH.'
}

$adb = Resolve-Adb

if (-not $Serial.StartsWith('emulator-') -and -not $List) {
    throw "'$Serial' is not an emulator. Mock coordinates can only be injected with 'emu geo fix', so this needs an emulator; a physical phone has to be walked by hand. (-List only reads the database, so it works on a phone.)"
}

# Metres per degree of latitude over Haversine's spherical Earth. The positions are approached due
# north, which keeps the geometry one-dimensional: a step of N metres is a step of N/this degrees.
$metresPerDegreeLatitude = 111195.08
$deviceDump = '/sdcard/zone-sweep.xml'
$dbName = 'mosque_tracker.db'

$tempDir = Join-Path $env:TEMP "mosque-zone-sweep-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    # adb writes progress ("1 file pulled") and daemon chatter to stderr. Under the script's
    # ErrorActionPreference that shows up as a terminating error, so merge it here and let callers
    # decide what to do with the output.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $adb -s $Serial @Arguments 2>&1
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function Invoke-Python {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $python = Get-Command python -ErrorAction SilentlyContinue
    if (-not $python) {
        throw 'python is needed to read the app database. Pass -Lat/-Lon/-Radius to skip that.'
    }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        @(& $python.Source @Arguments)
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

function Get-RemoteFile {
    # Copies one file out of the app's private storage, byte for byte.
    #
    # Two traps are avoided here: 'run-as cat > /sdcard/x' fails because the app uid cannot write a
    # shell-opened file (SELinux), and PowerShell decodes native output as text, which corrupts a
    # SQLite database. exec-out plus a redirected base stream keeps the bytes intact.
    param([string]$RemotePath, [string]$Destination)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $adb
    $startInfo.Arguments = "-s $Serial exec-out run-as $Package cat `"$RemotePath`""
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = [System.Diagnostics.Process]::Start($startInfo)
    $target = [System.IO.File]::Create($Destination)
    try {
        $process.StandardOutput.BaseStream.CopyTo($target)
    }
    finally {
        $target.Dispose()
    }
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0 -or (Get-Item $Destination).Length -eq 0) {
        throw "Could not read $RemotePath from the app. $standardError"
    }
}

function Sync-AppDatabase {
    # Returns a local path to a fresh copy of the app database. A stale write-ahead log would poison
    # the read, so the previous copy is dropped first.
    Get-ChildItem -Path $tempDir -File | Remove-Item -Force

    $listing = (Invoke-Adb shell "run-as $Package ls databases/" | Out-String)
    if ($listing -notmatch [regex]::Escape($dbName)) {
        throw "Could not read databases/ through run-as. Is '$Package' a debug build on $Serial?"
    }
    $names = @(
        $listing -split "`r?`n" |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -like "$dbName*" }
    )

    foreach ($name in $names) {
        Get-RemoteFile -RemotePath "databases/$name" -Destination (Join-Path $tempDir $name)
    }

    return (Join-Path $tempDir $dbName)
}

function Get-DbRows {
    param([string]$Database, [string]$Query)
    $code = "import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); [print('|'.join('' if v is None else str(v) for v in r)) for r in c.execute(sys.argv[2])]"
    return @(Invoke-Python -c $code $Database $Query)
}

# ------------------------------------------------------------------ device interaction

function Get-UiXml {
    Invoke-Adb shell uiautomator dump $deviceDump | Out-Null
    $raw = (Invoke-Adb exec-out cat $deviceDump | Out-String)
    if ($raw -notmatch '<hierarchy') {
        throw 'Could not read the screen (uiautomator dump failed).'
    }
    return $raw
}

function Get-PillState {
    # The pill also reports what the app has done ("Inside Zone · confirming…", "Leaving zone ·
    # confirming…" while a visit is still open just past the rim); this script measures the circle.
    $xml = Get-UiXml
    if ($xml -match 'Inside Zone') { return 'Inside' }
    if ($xml -match 'Outside Zone|Leaving zone') { return 'Outside' }
    if ($xml -match 'Waiting for GPS') { return 'NoFix' }
    return 'NoPill'
}

function Tap-Text {
    param([string]$Text)
    $xml = Get-UiXml
    # Bounds may sit either side of the text attribute in the dumped node; try both orders.
    $match = [regex]::Match($xml, 'text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $match.Success) {
        $match = [regex]::Match($xml, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="' + [regex]::Escape($Text) + '"')
    }
    if (-not $match.Success) { return $false }

    $x = ([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2
    $y = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
    Invoke-Adb shell input tap $x $y | Out-Null
    return $true
}

function Set-Fix {
    param([double]$Latitude, [double]$Longitude)
    # 'emu geo fix' takes longitude first, and occasionally answers KO under load.
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $output = (Invoke-Adb emu geo fix $Longitude $Latitude | Out-String)
        if ($output -notmatch 'KO|error') { return }
        Start-Sleep -Seconds 1
    }
    throw "emu geo fix failed for lat $Latitude lon $Longitude."
}

function Wait-ForPill {
    param([string]$Wanted, [int]$Seconds)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $state = Get-PillState
        if ($state -eq 'NoPill') {
            throw 'The Home pill is not on screen. Open the app on the Home tab, cancel any zone picking, and run this again.'
        }
        if ($state -eq $Wanted) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

# ------------------------------------------------------------------ which circle to test

if ($List -or (-not $PSBoundParameters.ContainsKey('Lat'))) {
    $database = Sync-AppDatabase
    $rows = Get-DbRows -Database $database -Query 'SELECT id, name, latitude, longitude, geofence_radius, tracking_enabled FROM mosques ORDER BY id'

    # @(...) keeps a single mosque from collapsing into a scalar, which StrictMode would trip over.
    $saved = @(
        foreach ($row in $rows) {
            if (-not $row) { continue }
            $parts = $row -split '\|'
            [pscustomobject]@{
                Id        = [long]$parts[0]
                Name      = $parts[1]
                Latitude  = [double]$parts[2]
                Longitude = [double]$parts[3]
                Radius    = [int]$parts[4]
                Tracked   = ($parts[5] -eq '1' -or $parts[5] -eq 'True')
            }
        }
    )
    if ($saved.Count -eq 0) { throw 'No mosques are saved in the app.' }

    Write-Host ''
    Write-Host 'Saved mosques' -ForegroundColor Cyan
    $invariant = [System.Globalization.CultureInfo]::InvariantCulture
    $saved | ForEach-Object {
        $tracked = if ($_.Tracked) { 'tracked' } else { 'untracked' }
        Write-Host ([string]::Format($invariant, '{0,4}  {1,-24} {2,11:F5} {3,12:F5}  {4,4} m  {5}', $_.Id, $_.Name, $_.Latitude, $_.Longitude, $_.Radius, $tracked))
    }
    Write-Host ''

    if ($List) { return }

    if ($Mosque) {
        $target = $saved | Where-Object { $_.Name -like "*$Mosque*" } | Select-Object -First 1
        if (-not $target) { throw "No saved mosque matches '$Mosque'." }
    }
    else {
        $target = $saved | Where-Object { $_.Tracked } | Select-Object -First 1
        if (-not $target) { throw 'No tracked mosque to test. Pass -Mosque or track one in Settings.' }
    }

    $Lat = $target.Latitude
    $Lon = $target.Longitude
    $Radius = $target.Radius
    $mosqueId = $target.Id
    Write-Host "Testing '$($target.Name)' (id $($target.Id))."
}
else {
    $mosqueId = $null
}

if ($Radius -le 0) {
    # Mirrors DetectionPipeline.DEFAULT_ZONE_RADIUS_METERS: a mosque without a radius uses 50 m.
    Write-Host 'Mosque has no usable radius; the app falls back to 50 m.' -ForegroundColor Yellow
    $Radius = 50
}

function MetresNorth {
    param([double]$Metres)
    return $Lat + ($Metres / $metresPerDegreeLatitude)
}

function Move-To {
    param([double]$MetresFromCentre)
    Set-Fix -Latitude (MetresNorth -Metres $MetresFromCentre) -Longitude $Lon
}

# ------------------------------------------------------------------ focus the app on Home

Write-Host "Bringing $Package to the front on Home ..."
Invoke-Adb shell am start -n "$Package/com.mosquetracker.app.MainActivity" | Out-Null
Start-Sleep -Seconds 6
if (Tap-Text 'Home') { Start-Sleep -Seconds 3 }
if ((Get-PillState) -eq 'NoFix') {
    Write-Host 'No GPS fix yet; giving the app a moment to see the injected position.' -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}

# ------------------------------------------------------------------ point tests

# Exactly on the radius is deliberately absent: an injected fix cannot be placed to the millimetre,
# so inclusivity of the boundary is asserted in ZoneBoundaryTest instead. One metre either side is
# far enough from the boundary to be unambiguous.
$offsets = @(
    [pscustomobject]@{ Offset = -(2 * $StepMeters); Expect = 'Inside' }
    [pscustomobject]@{ Offset = -$StepMeters;       Expect = 'Inside' }
    [pscustomobject]@{ Offset = -1;                  Expect = 'Inside' }
    [pscustomobject]@{ Offset = 1;                   Expect = 'Outside' }
    [pscustomobject]@{ Offset = $StepMeters;         Expect = 'Outside' }
    [pscustomobject]@{ Offset = (2 * $StepMeters);   Expect = 'Outside' }
)

Write-Host ''
Write-Host "Point tests around a ${Radius} m circle" -ForegroundColor Cyan
Write-Host ''

$failures = 0
foreach ($point in $offsets) {
    $distance = $Radius + $point.Offset
    Move-To -MetresFromCentre $distance
    Start-Sleep -Seconds $SettleSeconds

    $state = Get-PillState
    $passed = ($state -eq $point.Expect)
    if (-not $passed) { $failures++ }

    $label = if ($point.Offset -lt 0) { "$distance m out (inside the circle)" } else { "$distance m out (outside the circle)" }
    $verdict = if ($passed) { 'ok' } else { "FAIL, expected $($point.Expect)" }
    Write-Host ('{0,-42} {1,-8} {2}' -f $label, $state, $verdict) -ForegroundColor $(if ($passed) { 'Green' } else { 'Red' })
}

# ------------------------------------------------------------------ arrival watch

if ($WatchArrival) {
    Write-Host ''
    Write-Host 'Arrival watch: heading well outside first, then back in.' -ForegroundColor Cyan

    if ($null -eq $mosqueId) {
        Write-Host 'Skipped: no saved mosque, so there is nothing to count arrivals for.' -ForegroundColor Yellow
    }
    else {
        $farOutside = $Radius + 150
        $attendanceBefore = 0
        $database = Sync-AppDatabase
        $rows = @(Get-DbRows -Database $database -Query "SELECT COUNT(*) FROM attendance_logs WHERE mosque_id = $mosqueId")
        if ($rows.Count -gt 0 -and $rows[0]) { $attendanceBefore = [int]$rows[0] }

        Move-To -MetresFromCentre $farOutside
        if (-not (Wait-ForPill -Wanted 'Outside' -Seconds 60)) {
            Write-Host "The app never reported Outside at $farOutside m out; is monitoring on?" -ForegroundColor Red
            $failures++
        }
        else {
            Write-Host "Outside at $farOutside m out. Letting the fence settle for $ExitSettleSeconds s ..."
            Start-Sleep -Seconds $ExitSettleSeconds

            # Sitting outside must not produce an arrival. Re-baseline here so that anything counted
            # from now on really is the entry that follows.
            $database = Sync-AppDatabase
            $rows = @(Get-DbRows -Database $database -Query "SELECT COUNT(*) FROM attendance_logs WHERE mosque_id = $mosqueId")
            $attendanceOutside = 0
            if ($rows.Count -gt 0 -and $rows[0]) { $attendanceOutside = [int]$rows[0] }
            if ($attendanceOutside -gt $attendanceBefore) {
                Write-Host "An arrival was recorded while the position was outside the zone ($attendanceBefore -> $attendanceOutside)." -ForegroundColor Red
                $failures++
            }
            $attendanceBefore = $attendanceOutside

            $inside = [math]::Max(5, $Radius - (2 * $StepMeters))
            Move-To -MetresFromCentre $inside
            Write-Host "Moved to $inside m out. Waiting up to $ArrivalTimeoutSeconds s for an attendance row ..."

            $deadline = (Get-Date).AddSeconds($ArrivalTimeoutSeconds)
            $arrived = $false
            while ((Get-Date) -lt $deadline) {
                Start-Sleep -Seconds 8
                $database = Sync-AppDatabase
                $rows = @(Get-DbRows -Database $database -Query "SELECT COUNT(*) FROM attendance_logs WHERE mosque_id = $mosqueId")
                $count = 0
                if ($rows.Count -gt 0 -and $rows[0]) { $count = [int]$rows[0] }
                if ($count -gt $attendanceBefore) { $arrived = $true; break }
            }

            if ($arrived) {
                $database = Sync-AppDatabase
                $latest = @(Get-DbRows -Database $database -Query "SELECT detection_method, entry_time FROM attendance_logs WHERE mosque_id = $mosqueId ORDER BY id DESC LIMIT 1")
                Write-Host "Arrival recorded after $inside m out - a genuine zone entry." -ForegroundColor Green
                if ($latest.Count -gt 0 -and $latest[0]) {
                    $parts = $latest[0] -split '\|'
                    $when = [DateTimeOffset]::FromUnixTimeMilliseconds([long]$parts[1]).LocalDateTime
                    Write-Host ("Latest row: {0}, logged {1}" -f $parts[0], $when)
                }
            }
            else {
                Write-Host "No attendance row appeared within $ArrivalTimeoutSeconds s of being $inside m out." -ForegroundColor Red
                Write-Host 'Check that monitoring is on, the mosque is tracked, and the routine is armed.' -ForegroundColor DarkGray
                $failures++
            }
        }
    }
}

# ------------------------------------------------------------------ verdict

Write-Host ''
if ($failures -eq 0) {
    Write-Host 'All checks passed: the app treats the drawn circle as the zone.' -ForegroundColor Green
    Write-Host 'Reminder: only the reported position counts. A fix whose accuracy circle merely touches' -ForegroundColor DarkGray
    Write-Host 'the mosque circle is still outside - see ZoneBoundaryTest.' -ForegroundColor DarkGray
    exit 0
}

Write-Host "$failures check(s) failed." -ForegroundColor Red
exit 1
