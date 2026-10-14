# Mosque Tracker

Offline-first Android app that detects when you arrive at a mosque, silences your phone on entry,
restores it on exit, and logs your attendance. Everything runs on-device — no account, no server,
no network required.

This repository is the **runnable MVP scaffold**. It builds, unit tests pass, and the debug and
release builds are green. Device-only behaviours are marked with `TODO(device testing)`.

---

## Screens

| # | Screen | Where | Purpose |
|---|--------|-------|---------|
| 1 | **Dashboard** | `ui/dashboard` | Inside/outside status, nearest mosque + distance, monitoring toggles, permission banners, last detection outcome. |
| 2 | **Sensors** (coordinates & sensor dashboard) | `ui/sensors` | Live GPS fix/accuracy, connected Wi-Fi BSSID, nearby access points, discoverable BLE beacons. |
| 3 | **Add current location as mosque** | `ui/addmosque` | Dialog launched from the dashboard / mosque list FAB. Captures the current fix and saves it as a new geofence. |
| 4 | **Detection Override Center** | `ui/mosques` | List of saved mosques; bind/unbind a Wi-Fi BSSID or BLE beacon per mosque; delete. |
| 5 | **History / attendance logs** | `ui/history` | Attendance grouped by day with entry/exit times, duration and the detection method used. |

Bottom navigation (`ui/MainScreen.kt`) switches between the four tabs; the add-mosque dialog is
shared by Dashboard and the Override Center.

---

## Detection pipeline

A three-tier cascade. Each tier is cheaper/more reliable than the one below it.

| Tier | Mechanism | Code |
|------|-----------|------|
| **1** | OS geofence (`GeofencingClient`) fires `ENTER`/`EXIT` | `detection/GeofenceManager.kt`, `detection/GeofenceBroadcastReceiver.kt` |
| **2a** | Wi-Fi BSSID — connected network first, then nearby scan results | `detection/WifiScanner.kt`, `detection/DetectionPipeline.kt` |
| **2b** | BLE beacon (address / service UUID / name match) | `detection/BleScanner.kt`, `detection/DetectionPipeline.kt` |
| **3** | Haversine straight-line distance ≤ 50 m | `detection/Haversine.kt`, `detection/GeoUtils.kt` |

Flow: the geofence transition wakes `GeofenceBroadcastReceiver` (kept trivial), which enqueues a
`VerificationWorker`. The worker runs the cascade, writes the attendance row, posts a notification,
and silences/restores the ringer.

```
GeofencingClient ──ENTER/EXIT──▶ GeofenceBroadcastReceiver ──▶ VerificationWorker
                                                                    │
                                            DetectionPipeline (Tier 2 → Tier 3)
                                                                    │
                                      AttendanceRepository · Notifier · RingerController
```

Background machinery is coordinated by `monitoring/MonitoringController.kt`:
foreground service (`service/DetectionForegroundService.kt`) + registered geofences +
a 15-minute `GeofenceRefreshWorker` (`work/MonitoringScheduler.kt`) that re-registers fences so they
survive OS cleanup. `detection/BootReceiver.kt` restarts monitoring after reboot/app update.

---

## Tech stack

Kotlin + Jetpack Compose, single-activity, MVVM. Versions live in `gradle/libs.versions.toml`.

| Area | Choice |
|------|--------|
| Language | Kotlin (built-in Kotlin via AGP — the separate `kotlin-android` plugin is no longer used) |
| Build | AGP **9.4.1**, Gradle **9.6.0**, version catalog + Kotlin DSL |
| UI | Jetpack Compose (BOM **2026.09.00**), Material 3, Navigation Compose, edge-to-edge, SplashScreen |
| DI | Hilt **2.60.1** (KSP) |
| Persistence | Room **2.8.5** (KSP, schema exported) + DataStore Preferences |
| Async | Coroutines / Flow |
| Background | WorkManager **2.12.0** + foreground service (`foregroundServiceType="location"`) |
| Location | Play Services `play-services-location` **21.4.0** (`GeofencingClient` + fused location) |
| SDK | `minSdk 26`, `compileSdk 37`, `targetSdk 37` |
| App id | `com.mosquetracker.app` (debug builds use the `.debug` suffix) |

Unit tests: JUnit 4 + `kotlinx-coroutines-test`.

> Note on Kotlin: AGP 9 has **built-in Kotlin support**, so `org.jetbrains.kotlin.android` must not be
> applied. The `kotlin` entry in the version catalog (2.2.10) pins the Compose compiler plugin and
> matches the Kotlin Gradle plugin that AGP's built-in Kotlin uses.

---

## Project layout

```
app/src/main/java/com/mosquetracker/app/
├── MainActivity.kt            # single activity, splash + theme + MainScreen
├── MosqueTrackerApp.kt        # @HiltAndroidApp, supplies HiltWorkerFactory to WorkManager
├── audio/RingerController.kt  # silent-on-entry / restore-on-exit (needs DND access)
├── data/
│   ├── local/                 # Room database, DAOs, entities
│   ├── prefs/SettingsStore.kt # DataStore-backed settings + previous ringer mode
│   └── repository/            # MosqueRepository, AttendanceRepository
├── detection/
│   ├── DetectionPipeline.kt   # the 3-tier cascade
│   ├── GeofenceManager.kt     # register/unregister OS geofences
│   ├── GeofenceBroadcastReceiver.kt, BootReceiver.kt
│   ├── WifiScanner.kt, BleScanner.kt
│   └── Haversine.kt, GeoUtils.kt
├── di/AppModule.kt            # Hilt bindings/providers
├── location/LocationProvider.kt
├── model/Models.kt            # LocationSample, WifiAccessPoint, BleBeacon, DetectionOutcome
├── monitoring/MonitoringController.kt
├── notifications/Notifier.kt  # channels + foreground/entry/exit notifications
├── service/DetectionForegroundService.kt
├── ui/                        # theme, MainScreen, the 5 screens, shared components
├── util/
└── work/                      # VerificationWorker, GeofenceRefreshWorker + scheduler
```

---

## Database

Room, exported schema at `app/schemas/`. Column names follow the original spec so the SQLite file is
easy to inspect with `sqlite3`.

**`mosques`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK | autoincrement |
| `name` | TEXT | |
| `latitude`, `longitude` | REAL | |
| `geofence_radius` | INTEGER | default 100 m |
| `wifi_bssid` | TEXT NULL | Tier 2 Wi-Fi binding |
| `ble_uuid` | TEXT NULL | Tier 2 BLE binding |
| `created_at` | INTEGER | epoch millis |

**`attendance_logs`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK | autoincrement |
| `mosque_id` | INTEGER FK → `mosques.id` | `ON DELETE CASCADE`, indexed |
| `entry_time` | INTEGER | epoch millis |
| `exit_time` | INTEGER NULL | set when the exit transition fires |
| `detection_method` | TEXT | `GEOFENCE` \| `WIFI` \| `BLE` \| `MANUAL` |

---

## Build & run

The JDK bundled with Android Studio is used (no separate JDK install required).

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleDebug        # debug APK
.\gradlew.bat :app:testDebugUnitTest    # unit tests
.\gradlew.bat :app:assembleRelease      # R8 + resource shrinking
.\gradlew.bat :app:lintDebug            # Android Lint (exits 0)
```

Install the debug build on a connected device/emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or run the whole smoke test (build → install → launch → crash-watch logcat → screenshot →
checklist) with:

```powershell
.\tools\verify-on-device.ps1
```

`local.properties` (gitignored) must point at your SDK:

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

---

## Permissions

Requested at runtime via `ui/common/Permissions.kt` and declared in `AndroidManifest.xml`.

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Geofences + Tier 3 distance |
| `ACCESS_BACKGROUND_LOCATION` | Detection while the app is closed |
| `POST_NOTIFICATIONS` | Entry/exit + foreground-service notifications |
| `NEARBY_WIFI_DEVICES` (33+) | Wi-Fi BSSID (declared `neverForLocation`) |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (31+) | BLE beacon verification |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (≤30) | Legacy BLE |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Persistent detection service |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `ACCESS_NOTIFICATION_POLICY` | Auto-silent (Do Not Disturb access) |

The dashboard walks the user through foreground permissions, then "Allow all the time" background
location, then DND access — with a button that opens the relevant settings screen.

---

## Known limitations / `TODO(device testing)`

These need a real device (and a real mosque/router/beacon) to validate:

- Geofence `ENTER`/`EXIT` timing and the accuracy of `dwell`/`loiteringDelay` tuning.
- Whether cached Wi-Fi scan results are fresh enough for Tier 2a. App-triggered scans
  (`WifiManager.startScan`) are deprecated and throttled by the platform, so the code reads the
  framework's cached results instead of requesting a scan. If that proves too stale on hardware, a
  supported scan-request path (or a longer geofence dwell) is the fix.
- BLE scan duty cycle / battery impact while monitoring is on.
- Auto-silent behaviour with Do Not Disturb (some OEMs restrict third-party ringer control).
- Behaviour under battery optimisation / OEM process killers — the app may need to be exempted.

Network sync of attendance is intentionally **out of scope** for the MVP; the schema and repositories
are local-only, and a sync layer can be added later without changing the tables.

---

## Documented deviations from the original spec

1. **Haversine is computed in Kotlin, not inside SQLite.** Android's bundled SQLite has no
   `sin`/`cos`/`asin`, so `Haversine.kt` does the maths and `GeoUtils.kt` provides a lat/lng
   bounding-box prefilter to keep candidate sets small.
2. **Icons are local `ImageVector`s** (`ui/common/AppIcons.kt`). The official
   `material-icons-core` / `-extended` artifacts are frozen (last release 1.7.8) and unmaintained.
3. **Deviation from `kotlin-android`:** with AGP 9's built-in Kotlin, applying
   `org.jetbrains.kotlin.android` is an error — it is intentionally absent.
4. **AOSP has no first-party geofencing API**, so Tier 1 uses Play Services' `GeofencingClient`
   (the platform-recommended geofence API).

---

## Versioning

`versionCode 1` / `versionName 0.1.0` in `app/build.gradle.kts`. Release builds are signed from
`mosquetracker-release.jks` via `keystore.properties` (both gitignored); when those files are absent,
`assembleRelease` falls back to debug signing so a fresh clone still builds.

---

## Docs & tooling

- `AGENTS.md` — standing rules for agents working in this repo.
- `AI Agent Help context project.md` — the living master context (architecture, quirks, signing,
  environment, device-verification status). **Keep it up to date with every change.**
- `tools/verify-on-device.ps1` — build → install → launch → crash-watch → screenshot → checklist.
