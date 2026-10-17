# MasjidSync

Offline-first Android app that detects when you arrive at a mosque, silences your phone on entry,
restores it on exit, and logs your attendance. No account, no server. Detection, storage and
attendance logging are entirely on-device; only OpenStreetMap tiles and place-name search touch
the network (both free, no API keys).

This repository is the **runnable MVP**. It builds, unit tests pass, and the debug and release
builds are green. Device-only behaviours are marked with `TODO(device testing)`.

---

## Screens

| # | Screen | Where | Purpose |
|---|--------|-------|---------|
| 1 | **Home** | `ui/home` | Mosque map with Inside/Outside pill, gold "Save Current Position as Mosque" button and the zone picker with its editor sheet (name + 20–100 m radius slider + Save & Activate). Touch-and-hold drops a pin anywhere. |
| 2 | **History** | `ui/history` | Stats card (total / this week / day streak / per-mosque), CSV export, attendance grouped by day with entry/exit times, duration and the detection method used. |
| 3 | **Qibla** | `ui/qibla` | Offline compass to Makkah, prayer times (live Aladhan API when online, cached day or solar math offline) with next-prayer countdown, and the prayer silence buffer toggle. |
| 4 | **Settings** | `ui/settings` | **"My Routine" editor** — one switch per phone action for *If / Then / When it ends*, with the access each one needs and a "not possible on Android" list — plus per-mosque tracking toggles + arrival mode (full silent / notify only), "Search via Map" (disabled offline) + "Use Current Location", monitoring and auto-silent switches, permission banners. |

Bottom navigation (`ui/MainScreen.kt`) switches between the four tabs. The map's app bar has
*show all mosques* and *centre on my location* actions; the camera follows your fix until you pan,
then it is yours. Settings hands new mosques to Home through a one-shot
`MosquePickRequest`: map-centre pick, current-fix pick, or a searched place the camera flies to.
A Quick Settings tile (`service/MonitoringTileService.kt`) toggles background detection without
opening the app.

---

## The routine

The Settings card is the app's *If / Then / When it ends*. **If** is the list of tracked mosques,
**Then** and **When it ends** are one switch per phone action, chosen from the full catalogue in
`model/RoutineAction.kt`. The master switch is *Run the routine on arrival*; each mosque's own
arrival mode can still opt out entirely (*notify only*).

| Group | On arrival | Needs |
|-------|-----------|-------|
| Sound | Silent ringer, Vibrate instead (silent wins), Mute media, Mute alarm volume, Mute notification volume, Mute system sounds | DND access for the ring modes |
| Focus | Do Not Disturb — total silence, Do Not Disturb — alarms only | DND access |
| Device | Flashlight on, Vibrate once, Dim the screen, Shorten screen timeout, Lock rotation off | `WRITE_SETTINGS` for the display rows |
| Notice | Arrival notice | — |
| On exit | Restore sound, Restore device, Exit notice | — |

`routine/RoutineEngine.kt` captures the pre-arrival state **once** — a repeated `ENTER` while you are
still inside never overwrites the real originals with already-modified values — persists it as JSON in
DataStore, and puts back exactly what it changed. Restoring is per-part: turning *Restore sound* off
leaves the audio part pending instead of silently dropping it, and Settings offers **Restore now** as
a safety valve. Nothing is restored to a value the app never set, so a missing permission or a device
without a flash unit just means that action is skipped.

> **Platform note.** On some builds `STREAM_SYSTEM` is one of the streams the ring mode governs
> (`adb shell dumpsys audio` → `ringer mode affected streams = 0x1a6` includes `STREAM_SYSTEM`).
> Muting it then zeroes the ring stream and lets AudioService re-derive the ring mode as VIBRATE a
> moment later, undoing a silent ringer. The engine therefore skips that write whenever a ring-mode
> action is armed — the ring mode already mutes those streams — re-asserts the ring mode last, and
> the Settings row says "already handled by the ring mode".

Actions Android reserves for the system are listed in `RoutineAction.BLOCKED_ACTIONS` and shown in
Settings so the full menu is visible with the reason: Wi-Fi/Bluetooth toggling, battery saver, mobile
data, airplane mode, locking the screen now, and restricting background data.

The card also guards the easiest mistake in the whole app: muting every stream while leaving the
ringer alone, which produces a phone that still rings inside the mosque. When no ringer action is
armed but sound actions are, it says *"No ringer action is armed, so calls can still ring"* with a
**Set silent** button.

---

## Detection pipeline

GPS-only, two steps. There is no Wi-Fi or Bluetooth tier.

| Step | Mechanism | Code |
|------|-----------|------|
| **1** | OS geofence (`GeofencingClient`) fires `ENTER`/`EXIT` | `detection/GeofenceManager.kt`, `detection/GeofenceBroadcastReceiver.kt` |
| **2** | Haversine straight-line distance ≤ the mosque's own drawn circle | `detection/DetectionPipeline.kt`, `detection/Haversine.kt` |

Flow: the geofence transition wakes `GeofenceBroadcastReceiver` (kept trivial), which enqueues a
`VerificationWorker`. The worker runs the distance check, writes the attendance row, posts a
notification, and applies/restores the user's routine (`routine/RoutineEngine.kt`). Saving a mosque
runs the same check immediately, so you get an instant answer instead of waiting for the OS
round-trip.

```
GeofencingClient ──ENTER/EXIT──▶ GeofenceBroadcastReceiver ──▶ VerificationWorker
                                                                    │
                                            DetectionPipeline (Tier 2 → Tier 3)
                                                                    │
                                      AttendanceRepository · Notifier · RoutineEngine
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
| Map | OpenStreetMap via `osmdroid-android` **6.1.20** — free tiles, no API key; blank basemap offline, pins and picker still work |
| Search | Platform `Geocoder` (no key, no dependency); listener API on 33+, blocking call on `Dispatchers.IO` below |
| SDK | `minSdk 26`, `compileSdk 37`, `targetSdk 37` |
| App id | `com.mosquetracker.app` (debug builds use the `.debug` suffix) |

Unit tests: JUnit 4 + `kotlinx-coroutines-test`.

> Note on Kotlin: AGP 9 has **built-in Kotlin support**, so `org.jetbrains.kotlin.android` must not be
> applied. The `kotlin` entry in the version catalog (2.2.10) pins the Compose compiler plugin and
> matches the Kotlin Gradle plugin that AGP's built-in Kotlin uses.
>
> That 2.2.10 compiler can only read Kotlin class metadata up to **2.3.0**, which caps which
> dependencies are usable: for the same reason `:app` forces every `kotlin-stdlib` artifact down
> to the compiler's own version. Bumping either requires bumping AGP's Kotlin first.

---

## Project layout

```
app/src/main/java/com/mosquetracker/app/
├── MainActivity.kt            # single activity, splash + theme + MainScreen
├── MosqueTrackerApp.kt        # @HiltAndroidApp, supplies HiltWorkerFactory to WorkManager
├── routine/RoutineEngine.kt   # applies/restores the user's routine on entry & exit
├── data/
│   ├── local/                 # Room database, DAOs, entities
│   ├── prefs/SettingsStore.kt # DataStore-backed settings + routine actions & snapshot
│   └── repository/            # MosqueRepository, AttendanceRepository
├── detection/
│   ├── DetectionPipeline.kt   # GPS distance check against the drawn circle
│   ├── GeofenceManager.kt     # register/unregister OS geofences (tracked mosques only)
│   ├── GeofenceBroadcastReceiver.kt, BootReceiver.kt
│   └── Haversine.kt, GeoUtils.kt
├── di/AppModule.kt            # Hilt bindings/providers
├── location/LocationProvider.kt
├── model/Models.kt            # LocationSample, DetectionMethod, DetectionOutcome
├── model/RoutineAction.kt     # the routine's action catalogue (+ what Android refuses)
├── monitoring/MonitoringController.kt
├── notifications/Notifier.kt  # channels + foreground/entry/exit notifications
├── service/DetectionForegroundService.kt
├── ui/                        # theme, MainScreen, home/history/settings, shared components
├── util/                      # TimeFormat, PlaceSearch (Geocoder), DetectionMethodExt
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
| `geofence_radius` | INTEGER | the drawn circle in metres (editor 20–100, default 60) |
| `tracking_enabled` | INTEGER 0/1 | per-mosque switch from Settings (default on) |
| `alert_mode` | TEXT | arrival behaviour: `FULL_SILENT` or `NOTIFY_ONLY` (default full) |
| `created_at` | INTEGER | epoch millis |

Migrations: v1 → v2 drops the retired `wifi_bssid`/`ble_uuid` columns; v2 → v3 adds
`tracking_enabled`; v3 → v4 adds `alert_mode`. All preserve existing rows.

**`attendance_logs`**

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER PK | autoincrement |
| `mosque_id` | INTEGER FK → `mosques.id` | `ON DELETE CASCADE`, indexed |
| `entry_time` | INTEGER | epoch millis |
| `exit_time` | INTEGER NULL | set when the exit transition fires |
| `detection_method` | TEXT | `GEOFENCE` \| `MANUAL` |

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

### Emulator on Windows *N* editions

Windows *N* editions ship without Media Foundation (`MFPlat.dll`), which both `emulator.exe` and its
qemu backend import at load time — so the emulator dies with `0xC0000135` and Android Studio reports
*"The emulator process for AVD … has terminated."* This machine hit exactly that. If it happens to you:

```powershell
.\tools\diagnose-emulator.ps1              # report + verdict
.\tools\fix-emulator-mediafoundation.ps1   # local, reversible workaround (no admin)
```

Full explanation in `AI Agent Help context project.md` §8. Re-run the fix script after every Android
Emulator SDK update.

---

## Permissions

Requested at runtime via `ui/common/Permissions.kt` and declared in `AndroidManifest.xml`.

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | GPS fix, geofences, distance check — the app's only sensor |
| `ACCESS_BACKGROUND_LOCATION` | Detection while the app is closed |
| `POST_NOTIFICATIONS` | Entry/exit + foreground-service notifications |
| `INTERNET`, `ACCESS_NETWORK_STATE` | OSM map tiles + place-name search (detection itself stays offline) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Persistent detection service |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `ACCESS_NOTIFICATION_POLICY` | The routine's ring-mode and Do Not Disturb actions |
| `VIBRATE` | The routine's *vibrate instead* and *vibrate once* actions |
| `WRITE_SETTINGS` (special access) | The routine's *dim the screen*, *shorten screen timeout* and *lock rotation off* actions — Android grants it from its own "Modify system settings" screen, not a runtime dialog |

Home and Settings walk the user through foreground permissions, then "Allow all the time"
background location, then DND access — with a button that opens the relevant settings screen.
`WRITE_SETTINGS` is asked for only when a display action is armed, and each switch says what it
needs when the access is missing. There are no Wi-Fi or Bluetooth permissions: the app has no such
sensors.

---

## Known limitations / `TODO(device testing)`

These need a real device to validate:

- Geofence `ENTER`/`EXIT` timing and GPS accuracy inside dense urban areas.
- Place search depends on the platform `Geocoder` backend, which varies by device/region; when it
  is absent the field explains so and map-pick still works.
- Auto-silent behaviour with Do Not Disturb (some OEMs restrict third-party ringer control), and
  whether a given OEM lets the routine's individual actions land.
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
5. **Below API 33 the callback `Geocoder` API does not exist**, so place search uses the blocking
   `getFromLocationName` on `Dispatchers.IO` under `@Suppress("DEPRECATION")` there (API 33+ uses
   the listener version).
6. **The editor sheet has no mini-map**: the green circle drawn on the main map behind the sheet
   *is* the live radius preview (and the exact geofence that gets armed).
7. **Prayer times are computed offline** (MWL convention) and land within ~10 minutes of published
   timetables — fine for a ±15 min silence buffer, not authoritative. Where twilight never gets
   deep enough (high-latitude summer), Fajr/Isha fall back to the seventh-of-night rule.

---

## Versioning

`versionCode 1` / `versionName 0.1.0` in `app/build.gradle.kts`. Release signing lives under
`.key_store/` (`mosquetracker-release.jks`, `keystore.properties`, both gitignored). If no signing
material is present, `assembleRelease` falls back to debug signing so a fresh clone still builds.

---

## Docs & tooling

- `AGENTS.md` — standing rules for agents working in this repo.
- `AI Agent Help context project.md` — the living master context (architecture, quirks, signing,
  environment, device-verification status). **Keep it up to date with every change.**
- `tools/verify-on-device.ps1` — build → install → launch → crash-watch → screenshot → checklist.
- `tools/diagnose-emulator.ps1` — why the emulator won't start (edition, Media Foundation, functional test).
- `tools/fix-emulator-mediafoundation.ps1` — local, reversible fix for Windows *N* editions (`-Undo` reverts).
