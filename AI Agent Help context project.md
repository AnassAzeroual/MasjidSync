# AI Agent Help — Project Context & Master Guide

**Project:** **Mosque Tracker** — an **offline-first Android app** that detects when you arrive at a
mosque, silences the phone on entry, restores it on exit, and logs your attendance. No account, no
server, no network required.

Read this file end-to-end before touching the code. It covers architecture, the detection pipeline,
engineering decisions and quirks, versioning, signing, build/verify commands, and the git workflow.

> **MAINTENANCE CONTRACT (mandatory):** This file is the project's living context. Every time the
> project changes — code, scripts, commands, versioning, signing, assets, structure, tooling,
> workflows, or any quirk/decision — update this file in the same pass (add, modify, or delete
> sections to match reality). Never let it go stale. **Never delete this file.**

---

## 1. LIVE SNAPSHOT (always re-verify — it drifts)

Run these first; they override anything below:

```powershell
cd C:\Users\devtips\Documents\MosqueTracker
git status --short
Select-String gradle\libs.versions.toml -Pattern 'agp|kotlin|ksp'
Select-String app\build.gradle.kts -Pattern 'versionCode|versionName|compileSdk|minSdk|targetSdk'
```

Snapshot at time of writing:

- Version: **`versionCode 1` / `versionName 0.1.0`**
- App id / namespace: **`com.mosquetracker.app`** (debug builds append `.debug`)
- SDK: `minSdk 26`, `compileSdk 37`, `targetSdk 37`
- Toolchain: AGP **9.4.1**, Gradle **9.6.0**, Kotlin (built-in via AGP) **2.2.10**, KSP **2.3.12**,
  Compose BOM **2026.09.00**
- Status: debug + release builds green, 9 unit tests pass, `lintDebug` exits 0.
- Emulator: **working** (after the Media Foundation workaround, §8) — app installs and launches on
  `Medium_Phone`; the manual on-device checklist is still outstanding.

---

## 2. REPOSITORY

- Repo root / only module host: `C:\Users\devtips\Documents\MosqueTracker` (its **own** git repo,
  branch `main`). It is **not** inside and **not** related to the sibling `Gharib_Qoran_App` repo.
- Single Gradle module: `:app`. Version catalog: `gradle/libs.versions.toml`.
- No CI, no remote configured yet.

---

## 3. ENVIRONMENT (Windows, PowerShell)

| Tool | Path | Notes |
|---|---|---|
| JDK | `C:\Program Files\Android\Android Studio\jbr` | **not on PATH** — set `JAVA_HOME` before every Gradle call |
| Gradle | `.\gradlew.bat` | wrapper; Gradle 9.6.0 distribution is cached locally |
| Android SDK | `C:\Users\devtips\AppData\Local\Android\Sdk` | referenced from `local.properties` (gitignored) |
| build-tools | `...\build-tools\36.0.0\` | `aapt2`, `apksigner` live here |
| adb | `...\platform-tools\adb.exe` | |
| Emulator | `...\emulator\emulator.exe` | works **after** the Media Foundation workaround — see §8 |
| keytool | `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe` | ships with the Studio JBR |

---

## 4. ARCHITECTURE

Single-activity Jetpack Compose app, MVVM + Repository, Hilt DI, Room persistence, Coroutines/Flow.

```
MainActivity → MainScreen (bottom nav) → Dashboard / Sensors / Override Center / History
                                              └── AddMosqueDialog (shared)
```

### Screens

| # | Screen | Package |
|---|--------|---------|
| 1 | Dashboard (status, nearest mosque, toggles, permission banners) | `ui/dashboard` |
| 2 | Coordinates & sensor dashboard (GPS fix, Wi-Fi BSSID, nearby APs, BLE beacons) | `ui/sensors` |
| 3 | "+ Add current location as mosque" (dialog) | `ui/addmosque` |
| 4 | Detection Override Center (bind Wi-Fi/BLE per mosque) | `ui/mosques` |
| 5 | History / attendance logs (grouped by day) | `ui/history` |

### Three-tier detection pipeline

| Tier | Mechanism | Code |
|------|-----------|------|
| **1** | OS geofence `ENTER`/`EXIT` | `detection/GeofenceManager.kt`, `detection/GeofenceBroadcastReceiver.kt` |
| **2a** | Wi-Fi BSSID (connected first, then cached scan results) | `detection/WifiScanner.kt`, `detection/DetectionPipeline.kt` |
| **2b** | BLE beacon (address / service UUID / name) | `detection/BleScanner.kt`, `detection/DetectionPipeline.kt` |
| **3** | Haversine distance ≤ 50 m | `detection/Haversine.kt`, `detection/GeoUtils.kt` |

Flow: geofence transition → `GeofenceBroadcastReceiver` (trivial) → `VerificationWorker` runs the
cascade → `AttendanceRepository` writes the row → `Notifier` posts → `RingerController` silences /
restores. `monitoring/MonitoringController.kt` owns the foreground service, geofence registration, and
the 15-minute `GeofenceRefreshWorker`. `detection/BootReceiver.kt` restarts monitoring after reboot /
app update.

### Database (Room, schema exported to `app/schemas/`)

- **`mosques`**: `id`, `name`, `latitude`, `longitude`, `geofence_radius` (default 100), `wifi_bssid`,
  `ble_uuid`, `created_at`.
- **`attendance_logs`**: `id`, `mosque_id` (FK → `mosques.id`, `ON DELETE CASCADE`, indexed),
  `entry_time`, `exit_time` (nullable), `detection_method`
  (`GEOFENCE` | `WIFI` | `BLE` | `MANUAL`).

Column names intentionally mirror the original spec so the SQLite file is inspectable with `sqlite3`.

---

## 5. ENGINEERING DECISIONS & QUIRKS (read before editing)

1. **AGP 9 has built-in Kotlin.** Applying `org.jetbrains.kotlin.android` is an **error** — it is
   intentionally absent from the root build, `:app`, and the version catalog. The `kotlin = "2.2.10"`
   catalog entry pins the Compose compiler plugin and matches AGP's built-in KGP. **Do not re-add it.**
2. **AGP 9.4.1 + Gradle 9.6.0** are the pinned pair — AGP 9.4.0+ is the first to officially support
   `compileSdk 37`, and Gradle 9.6.0 is its minimum. Bumping one means bumping the other.
3. **Haversine runs in Kotlin, not SQLite** (documented deviation). Android's bundled SQLite has no
   `sin`/`cos`/`asin`, so `Haversine.kt` does the maths and `GeoUtils.kt` supplies a lat/lng
   bounding-box prefilter to keep candidate sets small.
4. **Icons are local `ImageVector`s** in `ui/common/AppIcons.kt` (built with `addPathNodes` from
   Material path data). The official `material-icons-core` / `-extended` artifacts are frozen at
   1.7.8 and unmaintained — do not add them. Small XML drawables (`ic_wifi`, `ic_bluetooth`,
   `ic_mosque`, `ic_stat_mosque`) also exist for notifications/launcher.
5. **`WifiManager.startScan()` is deprecated / throttled**, so `WifiScanner.scanNearby()` reads the
   framework's cached `scanResults` and prefers `ScanResult.wifiSsid` on API 33+ with a
   `@Suppress("DEPRECATION")` `SSID` fallback below 33. Do not reintroduce `startScan()`.
6. **AOSP has no first-party geofencing API**, so Tier 1 uses Play Services' `GeofencingClient`.
7. **Hilt Compose entry point** is `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
   (`hilt-lifecycle-viewmodel-compose`), not the older `hilt-navigation-compose` path.
8. **`-Xannotation-default-target=param-property`** is set in `:app`'s Kotlin options to adopt the
   future default for constructor-property annotations (clears KT-73255 warnings). Keep it.
9. **Lint suppressions are deliberate and commented:**
   - `@SuppressLint("MissingPermission")` on `Notifier.updateForeground` / `Notifier.notify` — every
     `manager.notify(...)` call is gated by the private `canPostNotifications()` check, which lint
     cannot see through.
   - `@SuppressLint("InlinedApi")` on `DetectionForegroundService.onStartCommand` —
     `FOREGROUND_SERVICE_TYPE_LOCATION` is inlined from API 29; `ServiceCompat.startForeground`
     swallows the type below 29, so it is safe on minSdk 26.
10. **Adaptive launcher icons live in `res/mipmap-anydpi/`** (not `-anydpi-v26`) because minSdk is 26
    and lint flags the redundant `-v26` qualifier. They reference `@drawable/ic_launcher_background`
    (which hardcodes `#0F766E`), so `@color/ic_launcher_background` was removed as unused.
11. Only two lint warnings remain, both informational version-currency notices
    (`AndroidGradlePluginVersion`, `NewerVersionAvailable`). Leave them unless deliberately upgrading.

---

## 6. BUILD, TEST & LINT

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleDebug        # debug APK  → app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:assembleRelease      # R8 + resource shrinking → app/build/outputs/apk/release/app-release.apk
.\gradlew.bat :app:testDebugUnitTest    # JUnit 4 + kotlinx-coroutines-test (9 tests)
.\gradlew.bat :app:lintDebug            # must exit 0
```

`local.properties` (gitignored) must contain `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.

`gradle/gradle-daemon-jvm.properties` (generated by Android Studio's *updateDaemonJvm*) pins the Gradle
daemon to **JDK 25** (`toolchainVersion=25`), matching Android Studio's bundled `jbr`. `gradle.properties`
also enables `org.gradle.tooling.parallel=true` for parallel IDE sync. Both came from Android Studio and
are committed so IDE and CLI builds behave identically.

Static APK verification (no device needed):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe" dump badging app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

---

## 7. SIGNING & VERSIONING

- Version source of truth: `versionCode` / `versionName` in `app/build.gradle.kts` (`0.1.0` / `1`).
- Release signing material: `mosquetracker-release.jks` + `keystore.properties` at the repo root —
  **both gitignored, never commit or print them**. `keystore.properties` keys: `storeFile`,
  `storePassword`, `keyAlias`, `keyPassword`.
- `app/build.gradle.kts` reads `keystore.properties` if present; if it is absent (fresh clone, CI) the
  release build **falls back to debug signing** instead of failing, so `assembleRelease` always works.
- Signing scheme: `enableV1Signing = false`, `enableV2/V3/V4Signing = true` (minSdk 26 makes JAR/v1
  signing unnecessary; v3 enables key rotation).
- Schema/version bump rule: bump `versionCode` every shipped build and `versionName` on user-visible
  changes.

---

## 8. DEVICE VERIFICATION (emulator RESOLVED via a local workaround; manual pass still TODO)

- **Status (2026-10-14):** the emulator now **boots and runs the app**. `emulator -list-avds` reports
  `Medium_Phone` and `Small_Phone`; a cold boot completed in ~38.7 s; `app-debug.apk` installed and
  `com.mosquetracker.app.debug/com.mosquetracker.app.MainActivity` became `topResumedActivity` with no
  crash. Evidence: `artifacts/emulator-dashboard.png` (gitignored).
- Still outstanding: the **manual checklist** (§9) — sensors, geofence enter/exit, Wi-Fi/BLE tier
  precedence, reboot and battery behaviour. On an emulator, `adb emu geo fix` can drive the geofence.

### Tools
- `tools/diagnose-emulator.ps1` — one-shot report (edition, Media Foundation, workaround copies) plus a
  real functional test (`emulator -list-avds`), then a verdict + fix.
- `tools/fix-emulator-mediafoundation.ps1` — applies/refreshes the workaround below (idempotent;
  `-Undo` removes it).
- `tools/verify-on-device.ps1` — build → install → launch → crash-watch logcat → screenshot → print the
  manual checklist. Works on a physical device **or** a running emulator.

### Symptom and root cause
- **Symptom:** Android Studio shows `Error running 'app': The emulator process for AVD Medium_Phone has
  terminated.` Gradle sync and `:app:assembleDebug` both succeed — only the emulator launch dies.
  `emulator.exe` exited `-1073741515` (`0xC0000135` = `STATUS_DLL_NOT_FOUND`) and `emulator -list-avds`
  printed nothing. Studio's `idea.log`: `Process finished with exit code -1073741515 (0xC0000135)`.
- **Root cause:** this machine is **Windows *N*** (`EditionID = ProfessionalN`), which ships **without
  Windows Media components**. `MFPlat.DLL` (Media Foundation Platform) is absent from `System32`, and
  **both `emulator.exe` and the qemu backend import it at load time** — so the loader aborts them before
  they run. (The `api-ms-win-crt-*.dll` entries a naive checker flags "missing" are a **false
  positive**: those are API-set contracts resolved by the loader, never real files on Win10+;
  `ucrtbase.dll` is present.)
- **Why the supported fix fails here:** the fix is the Media Feature Pack —
  ```powershell
  # Administrator PowerShell, then reboot
  Add-WindowsCapability -Online -Name Media.MediaFeaturePack~~~~0.0.1.0
  ```
  but on this machine it fails with **DISM error 14107 / `ERROR_SXS_TRANSACTION_CLOSURE_INCOMPLETE`
  (0x8007371b)**. `CBS.log` says `Plan: Populating already installed satellites for FOD:
  Media.MediaFeaturePack`: DISM believes the payload is present and never downloads it, while the
  closure is incomplete (`mfperfhelper`/`mfmediaengine` have **no** components in the store).
  `DISM /Cleanup-Image /RestoreHealth` ran for 12 min and did **not** fix it (RestoreHealth repairs
  corruption, not deliberately removed features). The supported route needs a matching
  **Features-on-Demand source** (the Windows 11 25H2 FoD ISO) via `Add-WindowsCapability -Source <ISO>`.

### The workaround that IS in place (local, reversible, no admin)
Media Foundation's DLLs are already *staged* in the component store, so they can be supplied locally.
`tools/fix-emulator-mediafoundation.ps1` copies the newest staged `mfplat.dll` into the three
directories the emulator loads from:
```
<emulator>\                       emulator.exe's own directory
<emulator>\lib64                  added to the backend's search path by the launcher
<emulator>\qemu\windows-x86_64    the qemu backend's own directory
```
- **Gotcha that cost the most time:** the launcher's verbose log prints `Adding library search path:`
  for `lib64`, `lib64\gles_swiftshader` and `lib64\vulkan` **only**. A copy in the emulator **root**
  fixes `emulator.exe` but the **child qemu never searches the root**, so `lib64` and the qemu dir are
  required too. The launcher **relays the child's exit code**, so qemu's `0xC0000135` masqueraded as
  the launcher's own failure.
- It touches **nothing** outside the emulator directory (no `System32`, no registry, no servicing).
  **Never** hand-copy `mfplat.dll` into `System32` — unsupported and breaks servicing.
- **Re-run the script after every Android Emulator SDK update**, which replaces those directories.
- Only `mfplat.dll` is needed; `mfcore.dll`/`mfperfhelper.dll` are not used at load time.
- Fallback: a **physical device** via `tools/verify-on-device.ps1` (the app needs no changes).

- This is a **machine/OS defect, unrelated to app code**. Do not "fix" it by changing app sources.
  Device-only behaviours stay marked `TODO(device testing)` (see `README.md`).

---

## 9. MANUAL CHECKLIST (run on real hardware)

1. Grant foreground location → "Allow all the time" → notifications → DND access from the dashboard.
2. Add a mosque at your current location; confirm it appears in the Override Center.
3. Walk/teleport across the geofence boundary; confirm the phone silences on entry and restores on exit.
4. Confirm an `attendance_logs` row appears in History with the right method.
5. Bind a Wi-Fi BSSID / BLE beacon and confirm Tier 2 wins over Tier 3 in the logged method.
6. Reboot the device; confirm monitoring restarts (boot receiver).
7. Battery: leave monitoring on for a few hours and check drain / OEM process killing.

---

## 10. DOCUMENTED DEVIATIONS FROM THE ORIGINAL SPEC

1. Haversine computed in Kotlin, not SQLite (§5.3).
2. Local `ImageVector` icon set instead of the frozen `material-icons-*` artifacts (§5.4).
3. `org.jetbrains.kotlin.android` intentionally not applied (AGP 9 built-in Kotlin) (§5.1).
4. Tier 1 uses Play Services `GeofencingClient` because AOSP has no first-party geofence API (§5.6).
5. Wi-Fi scanning reads cached results instead of calling the deprecated `startScan()` (§5.5).

Network sync of attendance is intentionally **out of scope** for the MVP; the schema and repositories
are local-only and a sync layer can be added later without changing the tables.
