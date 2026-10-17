# AI Agent Help — Project Context & Master Guide

**Project:** **MasjidSync** (display name; code namespace stays `com.mosquetracker.app`) — an
**offline-first Android app** that detects when you arrive at a mosque, silences the phone on
entry, restores it on exit, and logs your attendance. No account, no server. Detection, storage
and attendance logging are entirely on-device; only map tiles and place-name search touch the
network.

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
- Status: debug + release builds green, 31 unit tests pass, `lintDebug` exits 0
  (only the two informational version-currency warnings).
- Routine: **editable** (one switch per action, catalogue in `model/RoutineAction.kt`); a full
  armed entry→exit cycle is verified on-device, see §8.
- UI: **MasjidSync** template rebuild verified on-device (Home map + Inside pill + status card,
  Settings toggles + search + routine editor, editor sheet, full save flow) — see §8.
- Emulator: **working** (after the Media Foundation workaround, §8) — app installs and launches on
  `Medium_Phone`; core flows verified via `uiautomator` + screenshots, see §8.
- Map styles: **switchable** from the Home app bar — *Streets* (OpenStreetMap) and *Satellite*
  (Esri World Imagery), remembered across restarts (`map_layer` in DataStore). Verified on the
  emulator and on the S25; both layers' attribution is drawn under the map (§5.21).
- You-are-here: a **blue dot** at the live fix, added last to the overlay list so it draws over the
  pins; hidden until there is a fix and moved on every update. Its accuracy halo only appears when
  the fix is looser than the dot (~30 m) and is dropped above 200 m. Verified on the emulator
  (dot follows mock fixes, leaves and returns with them) and on the S25.
- Zone boundary: **centre-based rule** (accuracy does not widen the zone, §5.23). Unit-tested in
  `ZoneBoundaryTest` and measured on the emulator by `tools/zone-boundary-sweep.ps1`: a 100 m mosque
  reads *Inside* at 90/95/99 m, *Outside* at 101/105/110 m, and a `GEOFENCE` arrival row appeared on
  entry from 250 m out.
- Zone colour: the tracked zone whose circle covers the fix is drawn **red** (faint green wash
  otherwise) and the Home pill turns red with it, so the map, the badge and the arrival check all
  say the same thing. Same styling while placing a new zone. Verified on the S25 (red circle + red
  *Inside Zone* pill with the phone inside, green + grey *Outside Zone* after moving it away).
- Moving a mosque: **press and hold a saved pin, then drag it** — circle follows the finger, a chip
  reads the live distance / inside-outside, release saves the new position, re-registers the fence
  and runs the matching verification pass itself (§5.24). Verified on `emulator-5554` (grab offset
  exact: a 434 px drag landed within 9 m of the predicted distance, in both directions) and on the
  S25 (dragged onto the phone → red circle, arrival row, routine applied; dragged off → green,
  visit closed, routine restored; a pin dropped on the dot stays grabbable — the dot declines long
  presses, §5.26).
- Arrival is **watched** while the app runs: `detection/ZoneWatch.kt` runs
  `DetectionPipeline.watchTransition` over every fix Home collects, and every trigger carries the
  fix it was decided on into `VerificationWorker` (dragged pin, fresh save, watch) — the reading
  that decides an arrival is the reading that records it. Fence events carry none and are verified
  against `lastKnown()`, then against one freshly requested fix. A visit is released only 25 m past
  the circle; a software arrival re-registers the fences so the platform owns the matching `EXIT`
  (§5.25). Verified on `emulator-5554`: 91 m inside a 100 m mosque → *Inside Zone*, row opened,
  media/alarm/notification muted, arrival notice; 236 m out → row closed and audio restored;
  **99 m inside a 100 m zone** — the rim case the fence alone used to swallow on the S25 — → row
  opened and muted. Same build installed on the S25.

---

## 2. REPOSITORY

- Repo root / only module host: `C:\Users\devtips\Documents\MosqueTracker` (its **own** git repo,
  branch `main`). It is **not** inside and **not** related to the sibling `Gharib_Qoran_App` repo.
- Single Gradle module: `:app`. Version catalog: `gradle/libs.versions.toml`.
- Remote: **`origin` → `https://github.com/AnassAzeroual/MasjidSync.git`** (public, branch `main`).
  No CI configured. Pushing uses the Git Credential Manager credential for `AnassAzeroual`; `gh`
  CLI is **not** installed.

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
MainActivity → MainScreen (bottom nav: Home / History / Settings)
```

### Screens

| # | Screen | Package |
|---|--------|---------|
| 1 | Home — map (opens on the last cached fix, follows the live fix until you pan; my-location resumes), blue you-are-here dot + accuracy halo at the live fix, basemap switcher in the app bar (Streets / Satellite), Inside/Outside pill, gold save button, zone picker + editor sheet (tap pin, or touch-and-hold the map to drop one) | `ui/home` |
| 2 | History — stats card (total / week / streak / per-mosque), CSV export, day-grouped logs | `ui/history` |
| 3 | Qibla — offline compass, prayer times (live Aladhan API when online, cached day, else solar math) + next-prayer countdown, prayer silence buffer | `ui/qibla` |
| 4 | Settings — "My Routine" (editable If/Then/When-it-ends: pick the on-arrival and on-exit actions), per-mosque tracking toggles + arrival mode (full silent / notify only), place search, monitoring switches, "not possible on Android" list | `ui/settings` |

Shared: `ui/common` (`ScreenScaffold` green header, `MosquePickRequest` hand-off, `AppIcons`,
`Permissions`, `SectionCard`/`KeyValueRow`/`EmptyState`). The old `ui/dashboard` (list) and
`ui/mosques` packages were removed in the MasjidSync rebuild.

### GPS-only detection (two steps, no Wi-Fi/BLE)

| Step | Mechanism | Code |
|------|-----------|------|
| **1** | OS geofence `ENTER`/`EXIT` | `detection/GeofenceManager.kt`, `detection/GeofenceBroadcastReceiver.kt` |
| **2** | Haversine distance ≤ the mosque's own drawn circle | `detection/DetectionPipeline.kt`, `detection/Haversine.kt`, `detection/GeoUtils.kt` |

Flow: geofence transition → `GeofenceBroadcastReceiver` (trivial) → `VerificationWorker` runs the
check → `AttendanceRepository` writes the row → `Notifier` posts → `RoutineEngine` applies /
restores the user's routine. `monitoring/MonitoringController.kt` owns the foreground service, geofence registration
(**tracked mosques only**), and the 15-minute `GeofenceRefreshWorker`. `detection/BootReceiver.kt`
restarts monitoring after reboot / app update. Saving a mosque runs the same pipeline immediately
(`onMosqueAdded`), so the user gets an instant answer.

The zone itself is **centre-based**: `DetectionPipeline.isInsideZone(distance, radius)` compares the
reported position with the drawn circle and ignores the fix's accuracy, and the OS fence is registered
with that same radius — so an accuracy circle that merely *touches* the mosque circle is outside, and
raises no `ENTER` at all. Pinned by `ZoneBoundaryTest` (§6) and measurable on an emulator with
`tools/zone-boundary-sweep.ps1`.

The same rule paints the map: a tracked zone whose circle covers the fix is drawn **red** (green wash
otherwise), and the pill and the picker's circle follow it. Moving a saved pin (§5.24) runs the same
check by hand, because a re-registered fence only ever raises an `ENTER`:
`DetectionPipeline.moveTransition(distance, radius, hasOpenVisit)` decides which pass, if any, the
move needs.

What the fence cannot do is *keep* watching. Its reports are single readings taken at single moments,
so a phone sitting on the rim can lose an arrival to one disagreement and then never hear about that
mosque again for the visit. `detection/ZoneWatch.kt` therefore re-runs the same inside-test on every
fix Home collects and carries that fix into the worker, so the reading that decides an arrival is the
reading that records it; the release side of the watch waits for a 25 m margin. Full reasoning in
§5.25.

### The routine (editable If / Then / When-it-ends)

The Settings card is the single place that decides what the phone does on arrival. `If` is the
tracked-mosque list; `Then` and `When it ends` are one switch per action, stored in DataStore under
`routine_actions` and catalogued in `model/RoutineAction.kt`. "Run the routine on arrival" (the
`auto_silent` key) is the master switch, and the prayer-silence feature reuses the routine's
sound subset (`RoutineAction.SOUND_PROFILE`).

| Group | On arrival | Needs |
|---|---|---|
| Sound | Silent ringer, Vibrate instead (silent wins), Mute media, Mute alarm volume, Mute notification volume, Mute system sounds | DND access for the ring modes |
| Focus | Do Not Disturb (total silence), Do Not Disturb (alarms only) | DND access |
| Device | Flashlight on, Vibrate once, Dim the screen, Shorten screen timeout, Lock rotation off | WRITE_SETTINGS for the display rows |
| Notice | Arrival notice | — |

On exit: `Restore sound` (ring mode + volumes + DND), `Restore device` (brightness, timeout,
rotation, flashlight), `Exit notice`. Restoring is per-part, so turning one off leaves that part
pending rather than silently dropping it, and Settings offers **Restore now** as a safety valve.

`routine/RoutineEngine.kt` snapshots the pre-arrival state once (not per ENTER) into
`routine_snapshot` and puts back exactly what it changed. Two hard-won quirks are baked in:

1. **The ring mode is written last and re-asserted.** `STREAM_SYSTEM` is one of the streams the
   ring mode governs (`dumpsys audio` → `ringer mode affected streams = 0x1a6`), so muting it
   re-derives the ring mode as VIBRATE a moment later and undoes a silent ringer. The
   `MUTE_SYSTEM` write is therefore skipped whenever a ring-mode action is armed (it is redundant
   then) and `setRingModeAndSettle` re-checks after a short gap.
2. **Every action is best-effort.** Missing access, no flash unit, or an OEM refusal means the
   action is skipped, and the affected value is left out of the snapshot so nothing is restored to
   a value we never set.

Actions Android reserves for the system are listed in `BLOCKED_ACTIONS` and shown in Settings so
the user sees the full menu and the reason (Wi-Fi/Bluetooth toggling, battery saver, mobile data,
airplane mode, lock-now, background-data restrictions).

### Database (Room, schema exported to `app/schemas/`, currently **v4**)

- **`mosques`**: `id`, `name`, `latitude`, `longitude`, `geofence_radius` (editor range 20–100 m,
  default 60), `tracking_enabled` (default 1), `alert_mode` (`FULL_SILENT` | `NOTIFY_ONLY`,
  default `FULL_SILENT`), `created_at`.
- **`attendance_logs`**: `id`, `mosque_id` (FK → `mosques.id`, `ON DELETE CASCADE`, indexed),
  `entry_time`, `exit_time` (nullable), `detection_method` (`GEOFENCE` | `MANUAL`;
  legacy `WIFI`/`BLE` rows fall back to `GEOFENCE` in History).
- Migrations: v1 → v2 drops the dead `wifi_bssid`/`ble_uuid` columns (table rebuild, data kept);
  v2 → v3 adds `tracking_enabled` (`ADD COLUMN`, safe on all API levels);
  v3 → v4 adds `alert_mode` (same pattern). All verified on-device against real data (see §8).

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
   1.7.8 and unmaintained — do not add them. Map pins use the `mosque_pin.xml` vector drawable
   (green, on-brand); `ic_stat_mosque` serves notifications.
5. **AOSP has no first-party geofencing API**, so Tier 1 uses Play Services' `GeofencingClient`.
6. **Hilt Compose entry point** is `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`
   (`hilt-lifecycle-viewmodel-compose`), not the older `hilt-navigation-compose` path.
7. **`-Xannotation-default-target=param-property`** is set in `:app`'s Kotlin options to adopt the
   future default for constructor-property annotations (clears KT-73255 warnings). Keep it.
8. **Lint suppressions are deliberate and commented:**
   - `@SuppressLint("MissingPermission")` on `Notifier.updateForeground` / `Notifier.notify` — every
     `manager.notify(...)` call is gated by the private `canPostNotifications()` check, which lint
     cannot see through.
   - `@SuppressLint("InlinedApi")` on `DetectionForegroundService.onStartCommand` —
     `FOREGROUND_SERVICE_TYPE_LOCATION` is inlined from API 29; `ServiceCompat.startForeground`
     swallows the type below 29, so it is safe on minSdk 26.
9. **Adaptive launcher icons live in `res/mipmap-anydpi/`** (not `-anydpi-v26`) because minSdk is 26
    and lint flags the redundant `-v26` qualifier. They reference `@drawable/ic_launcher_background`
    (which hardcodes `#0F766E`), so `@color/ic_launcher_background` was removed as unused.
10. Only two lint warnings remain, both informational version-currency notices
    (`AndroidGradlePluginVersion`, `NewerVersionAvailable`). Leave them unless deliberately upgrading.
11. **Bottom content floats over the map; there are no FABs.** Home and Settings use full-width gold
    buttons instead of floating action buttons (the old `FabContentPadding` reservation was removed
    with them). The editor sheet is deliberately non-modal: a scrim would swallow the map taps the
    zone picker needs.
12. **osmdroid `Marker` needs an explicit app icon.** The shared default marker icon can go blank
    (and therefore untappable — hit-testing needs a drawn rect) after MapView detach/recycle cycles
    (`Marker.onDetach` nulls listeners and recycles the icon via `BitmapPool.asyncRecycle`). Every
    pin — picker and saved, tracked (full alpha) or not (dimmed) — therefore sets our own
    `mosque_pin` drawable in code at creation time. Never rely on the osmdroid default icon.
13. **Search needs the network; the toggle needs the DB.** `PlaceSearch` disables its field while
    `ConnectivityManager` reports no validated connection, and `Geocoder.isPresent()` gates the
    backend. Below API 33 the callback `Geocoder` API does not exist, so the blocking call runs on
    `Dispatchers.IO` under `@Suppress("DEPRECATION")` — a documented deviation (§10.6).
14. **Prayer silence is chained one-shots, not periodic.** `PrayerScheduler.scheduleNext` aims a
    silence + restore pair at the next ±buffer window (today, else tomorrow's Fajr); the restore
    worker chains the following window. Toggle-off cancels both. Refresh points: Qibla open,
    toggle/buffer change, boot. Overlap with a geofence-applied routine self-heals on the next
    ENTER (see `VerificationWorker` comment).
15. **The QS tile is a plain toggle.** `MonitoringTileService` (Hilt-injected `TileService`)
    flips monitoring and re-reads state every `onStartListening`; no unlock needed.
16. **CSV export uses the Storage Access Framework** (`CreateDocument`), so no storage permission
    is declared. Stats (streak included) are computed in Kotlin from the already-loaded history
    rows — no extra DAO queries.
17. **Prayer times prefer the Aladhan API, degrade gracefully.** `PrayerRepository` tries
    `timings/DD-MM-YYYY?latitude=..&longitude=..&method=21` (Morocco convention, matching local
    mosque timetables; plain HttpURLConnection + org.json, no new dependency), falls back to the
    same-day cache within 50 km, else to the local MWL solar math in `PrayerTimes` — so Qibla and
    the silence scheduler work fully offline. API times render in the API's own `meta.timezone`.
    Verified identical to the API on-device.
18. **The routine is data, not code.** `model/RoutineAction.kt` is the single catalogue (label,
    description, `ActionPhase`, `ActionGroup`, `ActionRequirement`, `defaultEnabled`). Adding an
    action is one enum entry plus one application site in `RoutineEngine`; the Settings editor, the
    "N of 14 armed" counter, the access hints and the blocked list all derive from the catalogue.
    Fresh-install defaults are in `RoutineAction.DEFAULT_ENABLED`, never in `SettingsStore`.
19. **The routine snapshot is captured once, restored per-part, and completed later.** `RoutineEngine.apply`
    reuses an existing `routine_snapshot` so a repeated ENTER cannot overwrite the true pre-arrival
    values, and `revert` only puts back the parts the enabled on-exit actions cover — what is left
    stays in the snapshot (and Settings keeps offering *Restore now*). Fields are `UNSET = -1` when
    not captured, so `restoreVolume`/`restoreDndFilter` no-op instead of writing a value we never
    set. Every apply then calls `RoutineSnapshot.fillGaps`, which fills still-unset fields from a
    fresh capture: unset only ever means the action was armed while its access was missing, so the
    live value is still the original one. Without it, granting DND or WRITE_SETTINGS *after* the
    first arrival would leave those fields unset for good — the routine would silence/dim/lock on
    every later arrival and never put any of it back.
20. **The ring mode is the last audio write and is re-asserted** (`setRingModeAndSettle`).
    `STREAM_SYSTEM` is one of the streams the ring mode governs on this build
    (`dumpsys audio` → `ringer mode affected streams = 0x1a6` includes `STREAM_SYSTEM`), so muting
    it zeroes the ring stream and makes AudioService re-derive the ring mode as VIBRATE a moment
    later — silently undoing "Silent ringer". `applyOnArrival` therefore skips the `MUTE_SYSTEM`
    write while a ring-mode action is armed (redundant in that case: the ring mode already mutes
    those streams), and the Settings row reads "already handled by the ring mode". Do not "fix" the
    skip without re-testing a full armed cycle on `MUTE_SYSTEM` + `SILENT_RINGER`.
21. **The satellite basemap needs a hand-built tile source.** `model/MapLayer.kt` is the catalogue
    (label, description, credit, `tileSource`); the stored value is the enum name (`map_layer`),
    decoded by `MapLayer.fromStorage`, which falls back to `STREETS` for anything unknown. Esri's
    service wants `{z}/{y}/{x}` — **row before column** — while every source osmdroid 6.1.20 ships
    (`XYTileSource` included) concatenates `{base}/{z}/{x}/{y}`, so the enum's private
    `OnlineTileSourceBase` subclass builds the URL itself from `MapTileIndex`. Max zoom is 19,
    which is where that imagery ends, and osmdroid clamps the map to the source's range. Both
    layers' licences require the credit to stay visible, so `MapCredit` draws it on the map, never
    in Settings. Switching only swaps tiles (`setTileSource` in `rememberMosqueMapView`): pins,
    zone polygons and the picker are overlays, and each source caches under its own name.
22. **The you-are-here dot is added to the overlay list unconditionally and toggled with
    `isEnabled`.** The list is cleared and rebuilt by the `LaunchedEffect(mosques, picking)` effect,
    so a dot that was added/removed on fix changes would race that rebuild (get dropped, or be
    re-added behind it). It is instead added in the rebuild (halo under the zones, dot last so it
    draws over the pins) and the fix effect only moves it, hides it while `location == null`, and
    resizes the halo. Disabled overlays are skipped by osmdroid for both drawing *and* tap routing,
    which is why the dot also swallows its own taps (`setOnMarkerClickListener { _, _ -> true }`) —
    otherwise a tap on it would open an info bubble or, while picking, place a zone under the dot.
23. **"Inside" is the reported position inside the drawn circle — accuracy is not part of it.** One
    function decides it, `DetectionPipeline.isInsideZone(distance, radius)`, and the Home pill, the
    arrival check and the immediate check on save all route through it, so they cannot disagree (the
    map's polygon uses `zoneRadiusMeters(radius)`, which only supplies the 50 m fallback for a mosque
    with no radius). Consequences to keep in mind before "improving" it: a fix whose error circle
    overlaps the mosque circle while its centre is outside is **outside**; the comparison is inclusive
    at the boundary (`distance == radius` is inside); and because `GeofenceManager` registers the fence
    with the *same* radius, Play Services only wakes the app once the position itself crosses it. Any
    move to the generous `distance <= radius + accuracy` rule must therefore widen the registered
    fence by the same margin, or the app never hears about the positions it meant to accept. Tested by
    `ZoneBoundaryTest` (rule, boundary, touch case, pipeline/pill agreement) and end-to-end by
    `tools/zone-boundary-sweep.ps1`.
24. **A saved mosque is moved by dragging its pin, and the move runs its own verification pass.**
    Saved pins are `setDraggable(true)`, and osmdroid starts a marker drag from a **long press**
    (`Marker.onLongPress` — a fast swipe across a pin does nothing, which is also why the pin has to
    consume the press: that press otherwise belongs to the map, "drop a new pin here"). Two
    consequences, both found the hard way:
    - osmdroid's `moveToEventPosition` **snaps the marker's anchor onto the finger** when the drag
      starts, so a pin grabbed by its head jumped ~35 px and the coordinates that got saved were off
      by that much. `HomeScreen.keepGrabOffsetOnDrag` cancels it through `Marker.setDragOffset`
      (osmdroid converts that field as *millimetres*, hence the `25.4f / xdpi` conversion), fed by
      the DOWN position the map's own touch listener records.
    - A re-registered fence only ever raises an `ENTER` (`INITIAL_TRIGGER_ENTER`), so dropping a pin
      off a phone that was inside it produced no `EXIT` at all and left the routine armed.
      `HomeViewModel.moveMosque` therefore enqueues the matching `VerificationWorker` pass:
      `DetectionPipeline.moveTransition(distance, radius, hasOpenVisit)` returns ENTER when the phone
      is inside the moved circle, EXIT **only** when that mosque has an open visit
      (`AttendanceRepository.hasOpenVisit`), and null otherwise — without that guard, dragging an
      unrelated pin would close a visit that never opened and lift a routine that belongs to another
      mosque. All three branches are pinned in `ZoneBoundaryTest`.
    - The zone follows the finger live (`zonePolygons[id].points` re-pointed in `onMarkerDrag`) and
      the drag chip replaces the pill for the duration, so a mid-drag read-out can never contradict
      the badge above it. `draggedZone` is cleared on drop; the row is the source of truth from then
      on, and the map rebuilds from it.
25. **Arrival is level-triggered off the live fixes, and a trigger carries the fix it decided on.**
    Found on the S25 the hard way: the phone sat 20 cm *inside* a 60 m zone (`gps
    33.510389,-7.832038`, hAcc ≈ 11 m, centre 59.8 m away), so the map flickered red/grey while
    nothing ever muted. The fence is why. It reports a crossing the platform believes in, the worker
    then verified that report against `locationProvider.lastKnown()` — a *different* sample — and a
    rejection there is silent and final: Play Services has already moved to "inside", so no second
    `ENTER` arrives for that visit, and no attendance row is written either. Three parts to the fix:
    - `detection/ZoneWatch.kt` runs `DetectionPipeline.watchTransition(distance, radius,
      hasOpenVisit)` on every fix Home collects (same master switch as the background machinery), so
      the arrival follows the readings that paint the circle red. ENTER is the plain inclusive rule
      with no open visit; EXIT only once the fix is past `RELEASE_MARGIN_METERS` (25 m), so a phone
      on the rim cannot make the ringer flip. Enqueues are throttled per mosque by a 10 s cooldown.
    - `VerificationWorker` accepts an optional fix in its input. `HomeViewModel.moveMosque`,
      `MonitoringController.onMosqueAdded` and the watch all pass the reading they decided on, so the
      fix that decides an arrival is the fix that records it. Fence events carry none: they are
      verified against `lastKnown()` and, when that says "outside", against one fix requested freshly
      (`LocationProvider.current`, one-shot `getCurrentLocation`, 15 s cap — a single request, not a
      second location stream).
    - A software arrival re-registers the fences (`onMosquesChanged`) because the platform may still
      believe the phone is *outside*. Re-registration with `INITIAL_TRIGGER_ENTER` hands the matching
      `EXIT` back to the OS, which is what lifts the routine when the phone leaves; without it, a
      visit the watch opened had no exit path at all once the app stopped collecting fixes.
    `ZoneBoundaryTest` pins the watch's enter/hold/release branches (§6). Verified on `emulator-5554`
    with `adb emu geo fix`: 91 m inside a 100 m mosque → *Inside Zone*, row 18 opened,
    media/alarm/notification muted, arrival notice posted, both `VerificationWorker`s SUCCESS; at
    236 m out → row closed and every stream unmuted; the rim case (99 m inside a 100 m zone) → row 19
    opened + muted, exactly what used to be swallowed on the phone.
    Fixed alongside: `AttendanceRepository.recordEntry` guards its check-then-insert with a `Mutex` —
    two triggers landing together had produced two open rows with identical timestamps (rows 16/17 in
    the emulator's history).
26. **The you-are-here dot declines long presses, so a pin under it stays draggable.** osmdroid's
    `DefaultOverlayManager.onLongPress` walks the overlays *reversed* (top-most first) and stops at
    the first one whose `Overlay.onLongPress` returns true — and `Marker.onLongPress` returns its hit
    test **whether or not the marker is draggable** (bytecode checked in `osmdroid-android-6.1.20`).
    The dot is added last on purpose (drawn over the pins), so a pin dropped exactly on it — the
    natural way to test a zone without walking — could not be grabbed again: pressing the pin hit the
    dot, the dot reported a hit and dragged nothing. `HomeScreen.LocationDotMarker` overrides
    `onLongPress` to return false, which lets the press fall through to the pin underneath; the tap
    swallow (`setOnMarkerClickListener { _, _ -> true }`) is untouched. Verified on `emulator-5554`
    with a pin sitting on the dot (pin box `513,1146-564,1217`, dot `523,1162-555,1194`): pressing
    the pin's centre and dragging it away moved the mosque, closed visit 20 and unmuted every stream,
    where the same press had done nothing before the fix.

---

## 6. BUILD, TEST & LINT

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat :app:assembleDebug        # debug APK  → app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat :app:assembleRelease      # R8 + resource shrinking → app/build/outputs/apk/release/app-release.apk
.\gradlew.bat :app:testDebugUnitTest    # JUnit 4 + kotlinx-coroutines-test (36 tests)
.\gradlew.bat :app:lintDebug            # must exit 0
```

Unit tests worth knowing: `ZoneBoundaryTest` (the inside/outside rule — boundary, the
accuracy-touches-but-does-not-count case, that the pipeline agrees with the map's pill, and the
software watch's enter/hold/release branches; §5.23, §5.25), `HaversineTest`, `GeoUtilsTest`,
`PrayerQiblaTest`.

Boundary behaviour on a device (emulator only — mock coordinates cannot be injected into a physical
phone):

```powershell
.\tools\zone-boundary-sweep.ps1 -List                                    # saved mosques: coords + radius
.\tools\zone-boundary-sweep.ps1 -Mosque "Al-Noor"                        # point tests at radius ± step
.\tools\zone-boundary-sweep.ps1 -Mosque "Al-Noor" -WatchArrival          # + a real geofence arrival
```

It reads the app database through `run-as` (debug builds only), so the app must be installed and on
the Home tab. Measured on `emulator-5554` against a 100 m mosque: 90/95/99 m → *Inside*,
101/105/110 m → *Outside*, and a `GEOFENCE` attendance row appeared after stepping in from 250 m out.

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
- Release signing material now lives under **`.key_store/`**: `mosquetracker-release.jks` and
  `keystore.properties` (both gitignored). `app/build.gradle.kts` reads `.key_store/keystore.properties`
  first, then falls back to a root-level `keystore.properties` for backward compatibility. If no
  signing material is found (fresh clone/CI), release builds **fall back to debug signing** instead of
  failing, so `assembleRelease` always works. **Never commit or print keys/passwords.**
- `storeFile` in `keystore.properties` is resolved relative to that properties file's directory (so
  `storeFile=mosquetracker-release.jks` under `.key_store/` points to `.key_store/`), making it safe to
  relocate keys without changing passwords. `enableV1Signing = false`, `enableV2/V3/V4Signing = true`.
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
- **Status (2026-10-17, real device):** Samsung **SM-S938B (Galaxy S25 Ultra), Android 17 / SDK 37**,
  debug build. The exit restore was observed working end to end there: entry muted media/alarm/
  notification/system, exit put media and alarm back to their captured values and cleared
  `routine_snapshot`. The emulator's `STREAM_SYSTEM` quirk does **not** reproduce on this build —
  `ringer mode affected streams = 0x126` (a different set from the emulator's `0x1a6`, but it still
  includes `STREAM_SYSTEM`), yet writing that stream lands on NORMAL rather than VIBRATE, and
  `set-ringer-mode SILENT` sticks — so the skip in §5.20 is harmless there and the ring mode is the
  real lever. Access to check when a user reports "nothing happens": Do Not Disturb access (ring
  mode + DND actions) and "Modify system settings" (the display rows), plus the *Run the routine on
  arrival* master switch. `adb shell cmd notification allow_dnd <pkg>` grants the first for testing.
- **Status (2026-10-17, editable routine):** `RingerController` was replaced by `routine/RoutineEngine.kt`
  plus the `model/RoutineAction.kt` catalogue; the Settings "My Routine" card is now an editor with
  one switch per action. Verified on `emulator-5554` with `adb emu geo fix`:
  entry → ringer SILENT, media 0, notification 0, alarm at the device minimum, screen brightness
  24 (from 102); exit → ringer NORMAL, media 5, alarm 6, notification 1, brightness 102 — exactly
  the captured pre-arrival values, with `routine_snapshot` cleared from DataStore. Action toggles
  survive a force-stop + relaunch.
  **Quirk found and fixed:** on this build `STREAM_SYSTEM` is one of the ringer-mode-affected
  streams (`dumpsys audio`: `ringer mode affected streams = 0x1a6`, which includes `STREAM_SYSTEM`),
  so writing it zeroes the ring stream and lets AudioService re-derive the ring mode as VIBRATE a
  moment later — which silently undid "Silent ringer". `RoutineEngine` therefore skips the
  `MUTE_SYSTEM` write whenever a ring-mode action is armed (the ring mode already mutes those
  streams) and re-asserts the ring mode last with a short settle check. The routine row says
  "already handled by the ring mode" so the UI stays honest.
  Needs DND access for the ring-mode/DND actions, granted on the emulator via
  `adb shell cmd notification allow_dnd com.mosquetracker.app.debug`, and WRITE_SETTINGS for the
  display actions, granted via `adb shell appops set com.mosquetracker.app.debug WRITE_SETTINGS allow`
  (the system UI list omits the app, so grant it by shell when testing).
- **Status (2026-10-17, swallowed arrival on the rim):** the S25 reported "the circle becomes red but
  the sound is not muted". Diagnosis, with numbers: the phone's fix was
  `33.510389,-7.832038 hAcc ≈ 10.7 m` and the saved mosque sat at `33.51086,-7.83236` with a 60 m
  radius — **59.8 m away**, i.e. standing on the line, so the pill flickered while every fence `ENTER`
  was rejected against a different sample and never retried (root cause and fix: §5.25). Confirmed
  before the fix with the device DB empty of attendance rows despite the red circle, then fixed and
  verified on the emulator (§5.25). No DND/WRITE_SETTINGS probe is available for this build through
  `appops get` (`cmd appops` rejects `ACCESS_NOTIFICATION_POLICY` on SDK 37), so the phone's grants
  are read from the app's own Settings guard when it matters; `dumpsys location` shows the app's
  `HIGH_ACCURACY` request (`ProviderRequest[@+3s0ms … WorkSource{10548 com.mosquetracker.app.debug}]`)
  while Home is open.
- **Status (2026-10-16, mosque sound profile):** `RingerController` snapshotted ringer mode +
  media/alarm volumes on entry, zeroed them, and restored everything on exit (DataStore-persisted,
  voice-call stream never touched). Verified on `emulator-5554`: entry → ringer SILENT, exit →
  ringer NORMAL, snapshot holds the true originals.
  screenshots and pixel analysis — Home (Inside pill, gold button, check-in card), Settings
  (tracking toggles flip detection both ways, search finds a place and opens the picker at it),
  editor sheet (name + 20–100 m slider + Save & Activate), full save flow (DB row + instant
  check-in), and both Room migrations on real data (v1 → v2 → v3, rows preserved). No crashes.
  Test residue left on the emulator: an `Al-Noor` mosque + its open check-in (delete from
  Settings if unwanted).
- Still outstanding: the **manual checklist** (§9) — geofence enter/exit timing on real hardware,
  reboot and battery behaviour. On an emulator, `adb emu geo fix` can drive the geofence.

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

1. Grant foreground location → "Allow all the time" → notifications → DND access from Home/Settings.
2. Save your current position as a mosque; confirm the pin, the green circle, the blue dot on your
   own position and the instant check-in.
3. Arm a few routine actions in Settings (e.g. *Silent ringer* + *Mute media* + *Dim the screen*),
   then walk/teleport across the zone boundary; confirm each armed action lands on entry and the
   exit actions put the originals back. Confirm Settings stops showing *Restore now*.
4. Confirm an `attendance_logs` row appears in History with the right method.
5. Drag test (no walking): press and hold a saved pin and drag it onto your blue dot — the circle
   turns red, a visit row opens and the routine applies; drag it off again — green, visit closed,
   routine restored. Standing *on* the rim, expect the circle to flicker while the routine holds
   (§5.25).
6. Untrack a mosque in Settings; confirm its fence stops firing and the Home pill ignores it.
7. Switch the map style to *Satellite* (app bar layers button), confirm imagery loads and the credit
   under the map changes, then confirm the choice survives an app restart.
8. Reboot the device; confirm monitoring restarts (boot receiver).
9. Battery: leave monitoring on for a few hours and check drain / OEM process killing.
10. Emulator, boundary: `.\tools\zone-boundary-sweep.ps1 -Mosque "<name>" -WatchArrival` must report
    *Inside* up to the drawn radius and *Outside* beyond it, and record exactly one arrival on entry
    (nothing while waiting outside).

---

## 10. DOCUMENTED DEVIATIONS FROM THE ORIGINAL SPEC

1. Haversine computed in Kotlin, not SQLite (§5.3).
2. Local `ImageVector` icon set instead of the frozen `material-icons-*` artifacts (§5.4).
3. `org.jetbrains.kotlin.android` intentionally not applied (AGP 9 built-in Kotlin) (§5.1).
4. Tier 1 uses Play Services `GeofencingClient` because AOSP has no first-party geofence API (§5.6).
5. The editor sheet has no mini-map: the green circle drawn on the main map behind the sheet *is*
   the live radius preview (and the exact geofence that gets armed).
6. Below API 33 the callback `Geocoder` API does not exist, so place search uses the blocking
   `getFromLocationName` on `Dispatchers.IO` under `@Suppress("DEPRECATION")` there; API 33+ uses
   the listener version. No API key or extra dependency either way.
7. Prayer times are computed offline (MWL convention) and land within ~10 minutes of published
   timetables — fine for a ±15 min silence buffer, not authoritative. Where twilight never gets
   deep enough (high-latitude summer), Fajr/Isha fall back to the seventh-of-night rule.
8. `MUTE_SYSTEM` is skipped when a ring-mode action is armed (§5.20) — the ring mode already mutes
   the streams it governs, and writing them separately lets the platform re-derive the ring mode.
   Actions Android reserves for the system are not built at all; they are listed in
   `RoutineAction.BLOCKED_ACTIONS` and shown in Settings with the reason.

Network sync of attendance is intentionally **out of scope** for the MVP; the schema and repositories
are local-only and a sync layer can be added later without changing the tables.
