# MasjidSync

Offline-first Android app that detects when you arrive at a mosque, silences your phone on entry,
restores it on exit, and logs your attendance. No account, no server. Detection, storage and
attendance logging are entirely on-device; only map tiles (OpenStreetMap streets, Esri satellite),
place-name search and the Aladhan prayer-time API touch the network (all free, no API keys), and
everything keeps working without them.

This repository is the **runnable MVP**. It builds, unit tests pass, and the debug and release
builds are green. Device-only behaviours are marked with `TODO(device testing)`.

---

## Features

**Arrival and departure detection**
- GPS only, in two steps: an Android geofence wakes the app, then the straight-line distance to the
  mosque decides. *Inside* means the reported position is within the circle you drew — nothing else.
- A software watch runs the same inside-test on every live fix, so an arrival the geofence missed
  or got wrong is still caught. Its pace adapts to how far away you are: every 10 s near a circle's
  edge, 20 s inside a visit, 60 s within 2 km, and 3 min on low-power Wi-Fi/cell farther away.
- Weak readings are ignored: a fix only counts when it is accurate to 100 m and under 2 minutes old.
- Leaving is debounced. A fix clearly outside (25 m past the circle, or past the fix's own error if
  that is larger) closes the visit after **20 seconds**; one that never got clear of the circle — a
  phone parked on the rim, which is what the map shows — closes it after **a full minute** outside.
  Either way one stray reading cannot un-mute you mid-prayer. A geofence `EXIT` that a fresh fix
  confirms closes the visit at once.
- Detection keeps running in the background through a foreground service. It restarts after a
  reboot or app update, a 15-minute worker re-arms the geofences, and a **Quick Settings tile** turns
  detection on and off without opening the app.
- Up to 100 mosques are armed (Android's limit); past that, the nearest ones are chosen. Settings
  says whether the zones are armed and, if not, why (no mosques, no precise location, no
  *Allow all the time*, or a failure).

**The routine: silence on arrival, restore on exit**
- An *If / Then / When it ends* editor with one switch per action: ring modes, mute media, alarm,
  notification and system volumes, two Do Not Disturb levels, flashlight, vibrate once, dim screen,
  shorter screen timeout, rotation lock, and arrival and exit notices.
- The phone's state before arrival is captured once and put back exactly. Only what the app changed
  is restored, one part at a time, and **Restore now** is there as a safety valve.
- Without Do Not Disturb access, *Silent ringer* falls back to vibrate, so calls still stop ringing.
- Each mosque has its own arrival mode: *full silent* runs the routine, *notify only* just logs the
  visit. Each mosque can also stop being tracked without being deleted.
- A guard warns when sound is muted but no ringer action is armed (*calls can still ring*), with a
  one-tap **Set silent** fix.
- The editor only offers switches Android actually allows the app to flip, so nothing you can arm
  silently fails to run.

**Map (Home)**
- **Streets** (OpenStreetMap) or **Satellite** (Esri World Imagery) basemaps, with the choice
  remembered and the credit kept as a tiny corner label.
- A blue **you-are-here** dot, with an accuracy halo when the fix is loose. The camera follows you
  until you pan; there are *show all mosques* and *centre on me* buttons.
- The zone that contains you is drawn **red**, using the same rule as the arrival check.
- A presence pill reads *Outside Zone* or *Inside Zone*, or *confirming…*, *GPS too weak to
  confirm*, *detection off* or *Leaving zone* while the app has not acted yet.
- One status banner at a time: a missing location grant, detection off while you are inside, Do Not
  Disturb access needed, or notifications blocked.
- The gold round **+** button at the bottom right saves wherever you are standing as a mosque;
  touch-and-hold anywhere drops a pin instead. The editor sheet
  sizes the circle from 20–100 m, then *Save & Activate*. New zones are numbered — *Mosque 1*,
  *Mosque 2*, … — so drawing one is a single step.
- **Hold the radius slider and the sheet fades away** — the card drops to 5% opacity and only the
  slider stays, at 50% — so you watch the circle on the map grow and shrink instead of guessing
  behind a panel. Everything returns the moment you let go.
- **Press and hold a saved pin to drag it.** A live chip shows the distance and whether you are
  inside, and dragging a zone onto or off yourself runs a real arrival or departure, so you can test
  without walking.

**Adding mosques (Settings)**
- *Search via Map* finds a place by name with the platform Geocoder; the camera flies there. It is
  disabled offline.
- *Use Current Location* drops the new mosque on your fix.
- Each saved mosque has a **rename** button. The map numbers new zones instead of asking for a
  name, so this is where the real name is given.

**History**
- Prominent **Today** and **This week** visit totals, a seven-day activity chart, all-time total,
  and day streak in a green-and-gold dashboard that supports light and dark mode. Large text sizes
  use a readable daily list instead of cramped chart labels.
- **Today / This week / All time** filters show how many visits belong to each mosque. Tap a mosque
  to open its own day-grouped visit timeline; mosque IDs distinguish places with identical names.
- Visit cards show the mosque name, entry/exit times, duration and GPS/manual method; open visits
  are marked **Ongoing**. Overnight exits include the exit date.
- Counts use the local **entry date**, including open visits once. Weeks run Monday–Sunday, and
  the calendar refreshes while History is open, so dates and totals follow midnight/timezone changes.
- **Export** always saves **all visits**, regardless of the filter, to a CSV file you pick. Formula-like
  cells are defused; a message confirms success or explains a write failure.

**Qibla and prayer times**
- An offline compass to Makkah showing the bearing and the distance. If the phone has no compass
  sensor, the screen says so.
- Prayer times: live from the Aladhan API (Moroccan method) when online. Offline, the app uses the
  cached day or calculates them on the phone (Muslim World League). They roll over at midnight and
  are recalculated after you move about 1 km.
- A countdown to the next prayer; after Isha it shows tomorrow's Fajr.
- **Prayer silence** applies the same sound profile as a mosque arrival for ±5, 15 or 30 minutes
  around each prayer, even when you are not at a mosque. It runs from scheduled one-shot workers,
  so it fires with the app closed, and it restores the sound when the window ends.

**Permissions**
- Each grant is asked for separately: precise location, *Allow all the time* (on Android 11+ this
  opens the system settings page directly), notifications, Do Not Disturb access, and system
  settings write access. Write access is only offered when an armed action needs it.
- Returning from a permission screen re-checks everything and re-arms the zones.

**Privacy**
- No account and no server. Mosques, visits and settings stay on the phone.
- Data is excluded from cloud backup and device-to-device transfer.
- Each mosque has one notification slot, so its exit notice replaces its arrival notice instead of
  piling up.

---

## Screens

| # | Screen | Where | Purpose |
|---|--------|-------|---------|
| 1 | **Home** | `ui/home` | Mosque map with a blue **you-are-here** dot, a presence pill (*Outside Zone*, *Inside Zone* once the visit is logged, or *confirming…* / *GPS too weak to confirm* / *detection off* / *Leaving zone* while the app has not acted yet), one status banner at a time (missing location grant, detection off while inside, Do Not Disturb access needed, notifications), a gold round **+** button at the bottom right, and the zone picker with its editor sheet (20–100 m radius slider + Save & Activate; new zones are numbered, not named). Touch-and-hold drops a pin anywhere, and **press-and-hold a saved pin to drag that mosque elsewhere** — the zone turns **red** the moment it covers you. Nothing on the map carries a name — pins, the details card and the drag read-out are nameless; the real name lives in Settings. The app bar switches the basemap between **Streets** and **Satellite**. |
| 2 | **History** | `ui/history` | Today/week totals, seven-day activity chart, all-time total and streak. Today / This week / All time filters, per-mosque counts with tap-to-open detail sheets, and a day-grouped visit timeline (times, duration, ongoing status, detection method). Export always includes all visits. |
| 3 | **Qibla** | `ui/qibla` | Offline compass to Makkah, prayer times (live Aladhan API, Moroccan method, when online; cached day or Muslim World League solar math offline; rolls over at midnight) with next-prayer countdown (tomorrow's Fajr after Isha), and the prayer silence buffer toggle. |
| 4 | **Settings** | `ui/settings` | **"My Routine" editor** — one switch per phone action for *If / Then / When it ends*, with the access each one needs — plus per-mosque tracking toggles + arrival mode (full silent / notify only) + rename, "Search via Map" (disabled offline) + "Use Current Location", monitoring and auto-silent switches, permission banners. |

Bottom navigation (`ui/MainScreen.kt`) switches between the four tabs. The map's app bar has
*map style*, *show all mosques* and *centre on my location* actions; the camera follows your fix
until you pan, then it is yours. Settings hands new mosques to Home through a one-shot
`MosquePickRequest`: map-centre pick, current-fix pick, or a searched place the camera flies to.
A Quick Settings tile (`service/MonitoringTileService.kt`) toggles background detection without
opening the app.

### Map styles

The app bar's layers button switches the basemap, and the choice is remembered. Both layers are
keyless, so nothing here is billed and nothing needs an account:

| Layer | Tiles | Credit (tiny, bottom-left corner) |
|-------|-------|----------------------------|
| **Streets** (default) | OpenStreetMap standard, the one osmdroid ships with | © OpenStreetMap contributors |
| **Satellite** | Esri World Imagery (`model/MapLayer.kt` builds the `{z}/{y}/{x}` URL by hand — osmdroid's own sources all emit `{z}/{x}/{y}`) | Imagery © Esri, Maxar, Earthstar Geographics |

Both providers hand out tiles on condition that the credit stays visible, so it stays on the map —
as a small corner label with a faint shadow (the way other map apps do it) rather than a pill or a
Settings page. Switching styles changes only the tiles: pins, zone circles
and the picker are overlays, so they look identical on either, and each source caches under its own
name so the two never mix.

### You are here

The live fix is marked with a blue dot (`drawable/current_location_dot.xml`), drawn over the pins so
it is never hidden. It appears only once there is a fix, and it follows every update — pan the map
away from yourself and the dot stays where you actually are.

Around it, a pale blue circle shows the fix's **accuracy**, and it is deliberately drawn only when
that accuracy is looser than the dot itself (under ~30 m the dot would cover it anyway). So at a
normal GPS fix you see just the dot, and when the phone is struggling — indoors, or on a network
fix — you get a visible halo saying *somewhere in here*. Above 200 m the halo is dropped entirely,
since a circle that size would wash out every detection zone on screen while telling you nothing.

### Which zone has you

Every tracked mosque is drawn as a faint green wash, and the one whose circle covers the current fix
is drawn **red** — same rule as the pill and the arrival check, so the map cannot disagree with the
badge above it. That is the "this is the circle that just silenced my phone" signal, and it is
readable from across the room.

### Moving a mosque (and testing a zone without walking)

**Press and hold a saved pin, then drag it.** The circle follows the finger, and a chip at the top
reads out the live distance and whether you are inside — hold the pin until that chip flips and you
are holding it exactly on the boundary. On release the new coordinates are saved and the geofence is
re-registered at the new place.

That makes the arrival test work without moving an inch: drag a mosque onto your dot and the app
records a real arrival and runs the routine; drag it off again and the visit closes and the routine
is restored. The second half has to be produced by the app rather than the geofence, because a
re-registered fence only ever raises an `ENTER` (`INITIAL_TRIGGER_ENTER`) — full reasoning in
`DetectionPipeline.moveTransition`, including why the `EXIT` is only synthesised when that mosque
actually has an open visit.

Three osmdroid details are worth knowing before touching this code: a marker drag starts from a
**long press** (`Marker.onLongPress`, not `onTouchEvent`); osmdroid snaps the marker's anchor onto
the finger when the drag begins, and the app cancels that jump with `Marker.setDragOffset`, otherwise
every move would be saved off by however far from the tip you grabbed the pin; and a long press is
handed to the **top-most** overlay that reports a hit, so the you-are-here dot — drawn last, on
purpose — declines long presses (`HomeScreen.LocationDotMarker`) and lets a pin sitting on your
position stay draggable.

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

**Who owns the routine.** Every event — arrival, exit, untrack, delete, alert-mode change, toggle,
prayer window start/end, monitoring off — goes through one door, `routine/RoutineCoordinator.kt`,
which asks the pure `routine/RoutinePlanner.kt` what the phone should be doing *now*. Two things can
own the routine: an open visit at a tracked *full silent* mosque, and an open prayer-silence window.
So a visit ending inside a prayer window lifts only the device half and keeps the phone quiet, a
notify-only exit never touches another mosque's silence, a repeated `ENTER` never re-mutes volumes
you raised by hand, and granting DND access mid-visit makes the next pass finish the job. *Restore
now* while still inside holds the routine off until a new visit starts or every owner is gone.

**Without Do Not Disturb access** Android refuses any change into or out of silent ring mode, but
still allows normal ↔ vibrate. *Silent ringer* therefore falls back to **vibrate** — calls stop
ringing instead of the arrival doing nothing to the ringer — and a phone that is already silent is
left alone. Grant DND access for a fully silent ringer (`RoutineEngine.ringTarget`).

> **Platform note.** On some builds `STREAM_SYSTEM` is one of the streams the ring mode governs
> (`adb shell dumpsys audio` → `ringer mode affected streams = 0x1a6` includes `STREAM_SYSTEM`).
> Muting it then zeroes the ring stream and lets AudioService re-derive the ring mode as VIBRATE a
> moment later, undoing a silent ringer. The engine therefore skips that write whenever a ring-mode
> action is armed — the ring mode already mutes those streams — re-asserts the ring mode last, and
> the Settings row says "already handled by the ring mode".

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
`VerificationWorker` — one unique chain per mosque (`APPEND_OR_REPLACE`), so the fence and the watch
can never run two decisions for the same mosque at once. The worker hands the decision to
`detection/PresenceEngine.kt`, which (serialized) runs the distance check, opens or closes the visit,
posts the notice and asks `RoutineCoordinator` to bring the routine in line. Saving a mosque
runs the same check immediately, so you get an instant answer instead of waiting for the OS
round-trip — and every live fix, from the background service and from Home while it is open, is
run past the saved mosques the same way (`detection/ZoneWatch.kt`), because a fence event on its
own is one reading taken at one moment (see *What counts as inside*).

```
GeofencingClient ──ENTER/EXIT──▶ GeofenceBroadcastReceiver ──▶ VerificationWorker (unique per mosque)
Service + Home live fixes ─▶ ZoneWatch (inside-test on every fix) ─▶ │
                                                                     ▼
                                     PresenceEngine ── DetectionPipeline (distance vs drawn circle)
                                                                     │
                                         AttendanceRepository · Notifier
                                                                     │
                                     RoutineCoordinator ── RoutinePlanner ──▶ RoutineEngine
Prayer window start/end (PrayerWorkers) ──────────────────▶ RoutineCoordinator
```

Switching detection off closes every open visit and lifts the routine — nothing would ever end
those visits otherwise, and the phone would stay silent.

Background machinery is coordinated by `monitoring/MonitoringController.kt`:

- **Foreground service** (`service/DetectionForegroundService.kt`) — shows the "detection active"
  notification, arms the geofences and runs the watch over its own location stream, so arrivals and
  departures are noticed with the screen off. Its pace adapts (`detection/WatchCadence.kt`):

  | Situation | Fix every | Source |
  |-----------|-----------|--------|
  | Inside a visit | 20 s | GPS |
  | Within 300 m of a circle's edge | 10 s | GPS |
  | Within 2 km | 60 s | GPS |
  | Farther, or nothing tracked | 3 min | Wi-Fi/cell (balanced power) |

- **Geofences** (`detection/GeofenceManager.kt`) — the old set is removed before the new one is
  added, at most **100** (the Play Services per-app limit; past that the nearest to the last fix
  win), and each registration reports a status: active, no mosques, no precise location, no
  *Allow all the time* (Android 10+ refuses geofences without it), or failed. Settings shows that
  status under *Background detection* (in red when zones are not armed), and opening the app or
  returning from a permission screen re-arms them.
- **15-minute `GeofenceRefreshWorker`** (`work/MonitoringScheduler.kt`) — re-registers the fences so
  they survive OS cleanup and revives the service if the system stopped it. With detection off it
  cancels itself.
- **Restarts** — `detection/BootReceiver.kt` (reboot/app update), the Quick Settings tile and the
  refresh worker start the service only when *Allow all the time* is granted: Android 14 will not
  let a location service go foreground from the background without it, and a started service
  that never goes foreground crashes the app. Android 12+ may refuse such a start outright; that
  is caught. Opening the app always brings the service back (`MainActivity.onStart`).

The map's and the Qibla tab's own 3-second GPS streams run only while those screens are visible.

### What counts as inside

Inside means exactly one thing: **the reported position lies within the circle you drew**. The fix's
accuracy is not part of the comparison, so a fix whose error circle overlaps the mosque circle while
its centre sits outside is scored as **outside**. One function decides this
(`DetectionPipeline.isInsideZone`) and both the Home pill and the arrival check call it, so the two
cannot drift apart — the map paints the same answer, in red, on the zone that has you.

That also decides how far the app can be woken: the OS geofence is registered with the same radius,
so Play Services raises an `ENTER` only once the reported position itself crosses the circle. Making
a touching accuracy circle count as inside would mean widening the registered geofence by the same
margin — otherwise the app would never be told about the positions it is supposed to accept.

### Arrival is watched, not just pushed

A geofence reports the crossings its platform believes in, and every report is verified against a
single fix — so a phone that lands on the boundary at the moment of its arrival can have that
arrival rejected once and then never hear about that mosque again for the whole visit. The app
therefore runs the same inside-test over every live fix — the background service's and, while it
is open, Home's (`detection/ZoneWatch.kt`, the decision in `DetectionPipeline.watchTransition`),
and every trigger
carries the fix it was decided on into `VerificationWorker`, so the reading that decides an arrival
is the reading that records it. Fence events, which carry no fix, are checked against the cached
reading and then against one requested freshly.

Only **usable** fixes decide a visit (`detection/PresenceRules.kt`): accuracy ≤ 100 m and no older
than 2 minutes. The watch releases an open visit only once a departure has *held*: a fix clearly
outside — 25 m past the circle, or past the fix's own error when that is larger — needs **20
seconds**, while one that never got clear of the drawn circle needs **a full minute** of the same
answer, so a phone parked on the rim is not muted forever just because its readings stay ambiguous.
The clock runs from the circle the map draws, and a single fix back inside it restarts it, so one
stray reading cannot un-mute the phone mid-prayer. A fence `EXIT` is
re-checked against a freshly requested fix: only a usable fix that puts the phone back inside
overrules it (no fix → the exit stands, because a phone stuck on silent is worse than a visit logged
a little short). Pin moves stay immediate. Standing right on the rim is the honest edge case — the
paint may flicker red/grey while the routine holds, for at most that minute. A fix or fence event
still in flight when detection is switched off opens nothing.

```powershell
.\gradlew.bat :app:testDebugUnitTest   # 131 tests: ZoneBoundaryTest, PresenceRulesTest, RoutinePlannerTest, WatchCadenceTest, GeofenceSelectionTest, ZonePresenceTest, DetectionSummaryTest, NextPrayerTest, CsvTest, MosqueAutoNameTest, HistoryStatsTest, ...
.\tools\zone-boundary-sweep.ps1 -Mosque "Al-Noor"                 # emulator: real positions vs the drawn circle
.\tools\zone-boundary-sweep.ps1 -Mosque "Al-Noor" -WatchArrival   # ... and a real geofence-driven arrival
```

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
| Map | OpenStreetMap (Streets) + Esri World Imagery (Satellite) via `osmdroid-android` **6.1.20** — free tiles, no API key; blank basemap offline, pins and picker still work |
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
├── MainActivity.kt            # single activity, splash + theme + MainScreen; revives detection on start
├── MosqueTrackerApp.kt        # @HiltAndroidApp, supplies HiltWorkerFactory to WorkManager
├── routine/
│   ├── RoutineEngine.kt       # applies/restores the routine's actions (snapshot, per-part restore)
│   ├── RoutineCoordinator.kt  # the single door: reconciles the routine with its current owners
│   └── RoutinePlanner.kt      # pure owner-based plan (visit / prayer window / suppression)
├── data/
│   ├── local/                 # Room database, DAOs, entities
│   ├── prefs/SettingsStore.kt # DataStore-backed settings + routine actions & snapshot
│   └── repository/            # MosqueRepository, AttendanceRepository, PrayerRepository (Aladhan → cache → solar math)
├── detection/
│   ├── DetectionPipeline.kt   # GPS distance check against the drawn circle
│   ├── PresenceEngine.kt      # serialized enter/exit decisions → visit rows + routine
│   ├── PresenceRules.kt       # usable-fix, release-distance, fence-exit and departure-dwell rules
│   ├── ZoneWatch.kt           # software watch over live fixes; returns the next WatchCadence
│   ├── WatchCadence.kt        # background watch pace from the distance to the nearest circle
│   ├── GeofenceManager.kt     # replaces OS geofences (tracked, nearest 100) and reports FenceStatus
│   ├── GeofenceBroadcastReceiver.kt, BootReceiver.kt
│   ├── Haversine.kt, GeoUtils.kt
│   └── Qibla.kt, PrayerTimes.kt   # bearing/distance to Makkah, offline solar prayer times
├── di/AppModule.kt            # Hilt bindings/providers
├── location/                  # LocationProvider (fused fixes), LocationPermissions (fg/bg/fine checks), CompassProvider
├── model/Models.kt            # LocationSample, DetectionMethod, DetectionOutcome
├── model/RoutineAction.kt     # the routine's action catalogue (+ what Android refuses)
├── model/MapLayer.kt          # Streets / Satellite tile sources
├── monitoring/MonitoringController.kt  # detection on/off, ensureRunning, recheck after grants
├── notifications/Notifier.kt  # channels + foreground notification + one entry/exit slot per mosque
├── service/
│   ├── DetectionForegroundService.kt  # notification + fences + adaptive background watch
│   └── MonitoringTileService.kt       # Quick Settings tile
├── ui/
│   ├── MainScreen.kt          # bottom navigation
│   ├── home/                  # map, HomeViewModel, ZonePresence (pill state)
│   ├── history/               # day/week dashboard, mosque drilldown, visit timeline, CSV export
│   ├── qibla/                 # compass, prayer times, next prayer, prayer silence
│   ├── settings/              # routine editor, mosques, DetectionSummary (fence status line)
│   ├── common/                # Permissions, SystemSettings deep links, banners, MosquePickRequest
│   └── theme/
├── util/                      # TimeFormat, PlaceSearch (Geocoder), PrayerApi (Aladhan), Csv, DetectionMethodExt
└── work/                      # VerificationWorker, PrayerWorkers + PrayerScheduler, GeofenceRefreshWorker + scheduler
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
| `INTERNET`, `ACCESS_NETWORK_STATE` | Map tiles (OpenStreetMap, Esri satellite), place-name search and Aladhan prayer times (detection itself stays offline) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Persistent detection service |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after reboot |
| `ACCESS_NOTIFICATION_POLICY` | The routine's ring-mode and Do Not Disturb actions |
| `VIBRATE` | The routine's *vibrate instead* and *vibrate once* actions |
| `WRITE_SETTINGS` (special access) | The routine's *dim the screen*, *shorten screen timeout* and *lock rotation off* actions — Android grants it from its own "Modify system settings" screen, not a runtime dialog |

Each missing grant gets its own banner — location, precise location, "Allow all the time"
background location, notifications — so denying notifications no longer hides the location steps.
On Android 11+ "Allow all the time" can only be chosen on the app's settings page, so that banner
opens it; every banner also offers "App settings" for a permission that was denied for good. Home
adds a Do Not Disturb banner when an armed arrival action needs that access.
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

**Nothing is backed up.** `allowBackup="false"` plus `res/xml/data_extraction_rules.xml` (Android
12+) and `res/xml/backup_rules.xml` (Android 8–11) keep the
visit log and mosque pins out of Google cloud backup and device-to-device transfer: they are a
precise record of where and when you pray. The flip side is that a new phone starts empty — export
the CSV from History first. The export defuses spreadsheet formulas (a cell starting with `=`, `+`,
`-`, `@`, tab or CR gets a leading `'`), so a mosque named `=HYPERLINK(…)` stays text in Excel.

Entry and exit notices share one notification slot per mosque, so leaving replaces that mosque's
"You're inside" notice instead of stacking a second one.

---

## Documented deviations from the original spec

1. **Haversine is computed in Kotlin, not inside SQLite.** Android's bundled SQLite has no
   `sin`/`cos`/`asin`, so `Haversine.kt` does the maths and `GeoUtils.kt` provides a lat/lng
   bounding-box prefilter to keep candidate sets small.
2. **Icons are local `ImageVector`s** (`ui/common/AppIcons.kt`). The official
   `material-icons-core` / `-extended` artifacts are frozen (last release 1.7.8) and unmaintained.
3. **Deviation from `kotlin-android`:** with AGP 9's built-in Kotlin, applying
   `org.jetbrains.kotlin.android` is an error — it is intentionally absent.
4. **AOSP has no first-party geofencing API**, so the geofence step uses Play Services' `GeofencingClient`
   (the platform-recommended geofence API).
5. **Below API 33 the callback `Geocoder` API does not exist**, so place search uses the blocking
   `getFromLocationName` on `Dispatchers.IO` under `@Suppress("DEPRECATION")` there (API 33+ uses
   the listener version).
6. **The editor sheet has no mini-map**: the green circle drawn on the main map behind the sheet
   *is* the live radius preview (and the exact geofence that gets armed).
7. **Prayer times come from the Aladhan API when online** (method 21, the Moroccan convention that
   matches local mosque timetables), from the same day's cache within 50 km, and otherwise from
   offline solar math (MWL convention), which lands within ~10 minutes of published timetables —
   fine for a ±15 min silence buffer, not authoritative. Only today is fetched, so after Isha the
   "tomorrow's Fajr" countdown is always the offline figure. Where twilight never gets deep enough
   (high-latitude summer), the offline Fajr/Isha fall back to the seventh-of-night rule.
8. **The satellite layer builds its own tile URL.** esri's service wants `{z}/{y}/{x}` (row before
   column) while every tile source osmdroid ships emits `{base}/{z}/{x}/{y}`, so `MapLayer.kt`
   subclasses `OnlineTileSourceBase` and assembles the URL itself. Its zoom stops at 19, where
   this imagery ends.

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
- `tools/zone-boundary-sweep.ps1` — drives an emulator across a mosque's boundary and checks the
  Inside/Outside pill plus a real geofence-driven arrival. Its `-List` only reads the database, so it
  works on a physical phone too (handy for checking what the app has saved).
- `tools/diagnose-emulator.ps1` — why the emulator won't start (edition, Media Foundation, functional test).
- `tools/fix-emulator-mediafoundation.ps1` — local, reversible fix for Windows *N* editions (`-Undo` reverts).
