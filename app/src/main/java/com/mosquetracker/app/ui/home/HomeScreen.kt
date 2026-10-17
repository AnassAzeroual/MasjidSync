package com.mosquetracker.app.ui.home

import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.R
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.detection.Haversine
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.MapLayer
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PickRequest
import com.mosquetracker.app.ui.common.RequiredPermissions
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.rememberPermissionLauncher
import com.mosquetracker.app.ui.common.rememberPermissionsGranted
import com.mosquetracker.app.ui.theme.DangerRed
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Where the camera parks before we have a fix or a saved mosque: the Kaaba. */
private val DefaultTarget = GeoPoint(21.4225, 39.8262)
private const val DefaultZoom = 15.0
private const val FocusZoom = 17.0
private const val AnimationMillis = 400L

/** Scroll events fired within this window after our own animateTo are ours, not the user's. */
private const val FollowIgnoreWindowMillis = 600L

/** Recentres on a new fix only once it is this far from the camera target. */
private const val FollowReflyThresholdMeters = 50.0

/** Inset for "show all mosques", in pixels, so pins clear the app bar and the card. */
private const val FitBoundsPaddingPx = 120

/** Zone editor range, mirroring the design's 20 m – 60 m – 100 m slider. */
private const val DefaultRadiusMeters = 60
private const val MinRadiusMeters = 20
private const val MaxRadiusMeters = 100
private const val RadiusStepMeters = 10

/** Green detection zone, the map-side twin of the brand palette. */
private const val ZoneFillColor = 0x330B5D33
private val ZoneStrokeColor = 0xFF0B5D33.toInt()
private const val ZoneStrokeWidthPx = 4f

/** Faint zone wash drawn under every tracked mosque so all detection areas stay visible. */
private const val TrackedZoneFillColor = 0x220B5D33
private val TrackedZoneStrokeColor = 0x990B5D33.toInt()
private const val TrackedZoneStrokeWidthPx = 2f

/**
 * A tracked zone the phone is currently standing in. Red rather than the green wash, so "this is
 * the one that just silenced your phone" is readable from across the room; it uses the palette's
 * [DangerRed], the same colour the theme hands to `colorScheme.error`.
 */
private val ZoneInsideFillColor = DangerRed.copy(alpha = 0.20f).toArgb()
private val ZoneInsideStrokeColor = DangerRed.toArgb()
private const val ZoneInsideStrokeWidthPx = 3f

/**
 * "You are here" accuracy halo: light blue so it never reads as one of the green detection zones,
 * and pale enough that a mosque circle drawn over it stays legible.
 */
private const val LocationAccuracyFillColor = 0x1A1A73E8
private val LocationAccuracyStrokeColor = 0x661A73E8.toInt()
private const val LocationAccuracyStrokeWidthPx = 1.5f

/**
 * Above this accuracy the halo is not drawn at all — only the dot is. A fix that loose would paint
 * a blue wash over the whole screen (and over every zone in it) while telling the user little.
 */
private const val MaxAccuracyHaloMeters = 200f

/**
 * Home: the map, the Inside/Outside pill, the gold save button and the status card.
 *
 * GPS is the only sensor the app uses; there is no Wi-Fi or Bluetooth tier.
 *
 * Tile downloads are the app's only network use and are entirely optional: with no connection the
 * basemap is blank but the pins, the circle and the picker all still work. The basemap itself is
 * switchable between a street map and satellite imagery (see [MapLayer]); pins, zone circles and
 * the picker are drawn on top, so they look the same on either.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val mosques by viewModel.mosques.collectAsStateWithLifecycle()
    val location by viewModel.currentLocation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pickRequested by viewModel.pickRequested.collectAsStateWithLifecycle()
    val nearest by viewModel.nearest.collectAsStateWithLifecycle()
    val mapLayer by viewModel.mapLayer.collectAsStateWithLifecycle()

    val foregroundPermissions = remember { RequiredPermissions.foreground() }
    val backgroundPermissions = remember { RequiredPermissions.backgroundLocation() }
    val foregroundGranted by rememberPermissionsGranted(foregroundPermissions)
    val backgroundGranted by rememberPermissionsGranted(backgroundPermissions)
    val launcher = rememberPermissionLauncher { viewModel.startLocationUpdates() }

    var selectedMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Pin being dragged right now: which mosque, and where the finger has taken it. The zone
    // follows it live and the change is only persisted when the drag ends, so an abandoned drag
    // leaves no trace.
    var draggedZone by remember { mutableStateOf<Pair<Long, GeoPoint>?>(null) }

    // ----- zone picker ------------------------------------------------------
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickName by rememberSaveable { mutableStateOf("") }
    var pickLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickRadius by rememberSaveable { mutableIntStateOf(DefaultRadiusMeters) }
    var saving by remember { mutableStateOf(false) }
    var editingMarker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            viewModel.clearMessage()
        }
    }

    val mapView = rememberMosqueMapView(mapLayer)
    val context = LocalContext.current
    val mosquePin = remember(context) {
        ContextCompat.getDrawable(context, R.drawable.mosque_pin)
    }

    // Follow mode: while on, every new fix recentres the map so the current location is
    // always shown. Any manual pan/drag turns it off; the my-location button turns it back on.
    var followFix by rememberSaveable { mutableStateOf(true) }
    // animateTo() itself fires onScroll events; ignore those fired right after our own move.
    var lastProgrammaticMove by remember { mutableLongStateOf(0L) }

    fun flyTo(point: GeoPoint, zoom: Double) {
        lastProgrammaticMove = System.currentTimeMillis()
        mapView.controller.animateTo(point, zoom, AnimationMillis)
    }

    fun beginPickingAt(latitude: Double, longitude: Double, moveCamera: Boolean) {
        selectedMosqueId = null
        pickName = ""
        pickLat = latitude
        pickLon = longitude
        pickRadius = DefaultRadiusMeters
        saving = false
        editingMarker = false
        picking = true
        if (moveCamera) {
            flyTo(GeoPoint(latitude, longitude), FocusZoom)
        }
        // Map-centre picks deliberately keep the camera where the user is looking.
    }

    fun beginPicking(request: PickRequest) {
        when (request) {
            is PickRequest.MapCenter -> {
                val centre = mapView.mapCenter as? GeoPoint ?: DefaultTarget
                beginPickingAt(centre.latitude, centre.longitude, moveCamera = false)
            }

            is PickRequest.CurrentFix -> {
                val fix = location
                if (fix == null) {
                    viewModel.showMessage("Waiting for a GPS fix — try again in a moment.")
                } else {
                    followFix = true
                    beginPickingAt(fix.latitude, fix.longitude, moveCamera = true)
                }
            }

            is PickRequest.AtLocation -> {
                // A searched place must not be yanked back: stay there until the user pans.
                followFix = false
                beginPickingAt(request.latitude, request.longitude, moveCamera = true)
            }
        }
    }

    // Raised by Settings ("Use Current Location" / a search result) which then switches tabs here.
    LaunchedEffect(pickRequested) {
        pickRequested?.let {
            viewModel.consumePickRequest()
            beginPicking(it)
        }
    }

    val followListener = remember {
        object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                if (System.currentTimeMillis() - lastProgrammaticMove > FollowIgnoreWindowMillis) {
                    followFix = false
                }
                return false
            }

            override fun onZoom(event: ZoomEvent): Boolean = false
        }
    }
    DisposableEffect(mapView) {
        mapView.addMapListener(followListener)
        onDispose { mapView.removeMapListener(followListener) }
    }

    // Seed the camera from the last cached fix so the map opens on the user's area instead
    // of the Makkah fallback while live updates warm up. Null means no permission or no
    // cached fix yet — then the fallback stands until the first live fix flies us over.
    LaunchedEffect(Unit) {
        viewModel.lastKnownFix()?.let { flyTo(it.toGeoPoint(), DefaultZoom) }
    }

    // Never move the camera while picking. Otherwise, while follow mode is on, fly only when
    // the fix has drifted well clear of the camera — reflying on every 3 s fix would keep the
    // map in constant animation, fight the user's pans and swallow the pan-off signal below.
    // (A huge drift also self-heals a camera that was reset, e.g. after a recreation.)
    LaunchedEffect(location, picking, followFix) {
        val fix = location ?: return@LaunchedEffect
        if (picking || !followFix) return@LaunchedEffect
        val target = fix.toGeoPoint()
        val centre = mapView.mapCenter as? GeoPoint
        val driftMeters = if (centre != null) {
            Haversine.distanceMeters(fix.latitude, fix.longitude, centre.latitude, centre.longitude)
        } else {
            Double.MAX_VALUE
        }
        if (driftMeters > FollowReflyThresholdMeters) {
            flyTo(target, mapView.zoomLevelDouble)
        }
    }

    // One tap receiver for the life of the map. It reads `picking`/`pickLat`/`pickLon` directly:
    // those are `remember`-backed delegates, so the closure sees their current values and the
    // overlay never has to be rebuilt.
    val tapOverlay = remember {
        MapEventsOverlay(
            object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    if (!picking) return false
                    // Moving the pin via map tap: reposition only, do not open the editor.
                    pickLat = p.latitude
                    pickLon = p.longitude
                    editingMarker = false
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean {
                    // Touch-and-hold drops a new mosque pin right there and opens the
                    // editor, so a name and radius are one step away. On a saved pin
                    // the marker itself consumes the press (drag), never reaching here.
                    beginPickingAt(p.latitude, p.longitude, moveCamera = false)
                    editingMarker = true
                    return true
                }
            },
        )
    }

    // "You are here". The dot marks the live fix; the halo around it is that fix's own accuracy.
    // Both go into the overlay list unconditionally and are merely switched off while there is no
    // fix, so nothing is added or removed behind the rebuild effect's back.
    val locationDot = remember(mapView, context) {
        LocationDotMarker(mapView).apply {
            icon = ContextCompat.getDrawable(context, R.drawable.current_location_dot)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // Swallow taps: no info bubble over the dot, and no picking a zone under it either.
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    val locationHalo = remember(mapView) {
        Polygon(mapView).apply {
            fillPaint.color = LocationAccuracyFillColor
            outlinePaint.color = LocationAccuracyStrokeColor
            outlinePaint.strokeWidth = LocationAccuracyStrokeWidthPx
        }
    }

    val pickerMarker = remember(mapView, mosquePin) {
        Marker(mapView).apply {
            // Explicit icon (same green pin as saved mosques): never the shared osmdroid
            // default, whose BitmapPool recycling on detach can blank it in later instances.
            icon = mosquePin
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setDraggable(true)
            setOnMarkerClickListener { _, _ ->
                editingMarker = !editingMarker
                true
            }
        }
    }
    val pickerCircle = remember(mapView) {
        Polygon(mapView).apply {
            // Polygon's own setFillColor/setStrokeColor/setStrokeWidth are deprecated in osmdroid
            // 6.1.x; the paints they wrap are the supported way to style a polygon.
            fillPaint.color = ZoneFillColor
            outlinePaint.color = ZoneStrokeColor
            outlinePaint.strokeWidth = ZoneStrokeWidthPx
        }
    }

    // Dragging the pin is the other half of "tap to place": both end up setting the same state.
    // Where the finger went down is remembered too, so a grabbed pin can cancel the jump osmdroid
    // applies on pickup (see [keepGrabOffsetOnDrag]). The listener returns false, so the map still
    // handles the gesture itself.
    var lastTouchY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(mapView) {
        mapView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) lastTouchY = event.y
            false
        }
        onDispose { mapView.setOnTouchListener(null) }
    }

    DisposableEffect(pickerMarker) {
        pickerMarker.setOnMarkerDragListener(
            object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {
                    marker.keepGrabOffsetOnDrag(lastTouchY, mapView)
                }

                override fun onMarkerDrag(marker: Marker) {
                    pickLat = marker.position.latitude
                    pickLon = marker.position.longitude
                }

                override fun onMarkerDragEnd(marker: Marker) {
                    pickLat = marker.position.latitude
                    pickLon = marker.position.longitude
                    editingMarker = false
                }
            },
        )
        onDispose { pickerMarker.setOnMarkerDragListener(null) }
    }

    // Saved pins and their zone washes, or the picker pin and its circle, all live in one
    // overlay list, so it is rebuilt whenever the set of things to draw changes. The zones are
    // kept by mosque id so a drag can re-point one without rebuilding the list under the finger.
    val zonePolygons = remember { mutableMapOf<Long, Polygon>() }
    LaunchedEffect(mosques, picking) {
        mapView.overlays.clear()
        zonePolygons.clear()
        // Added first on purpose: osmdroid offers a tap to the *last* overlay first, so the event
        // catcher has to sit underneath the markers for them to keep their own gestures.
        mapView.overlays.add(tapOverlay)
        // The accuracy halo sits under the zones (it is context for the dot, not a detection area).
        mapView.overlays.add(locationHalo)
        if (picking) {
            // Existing pins are hidden so the pending zone is the only thing the user reads.
            mapView.overlays.add(pickerCircle)
            mapView.overlays.add(pickerMarker)
        } else {
            mosques.forEach { mosque ->
                if (mosque.trackingEnabled) {
                    val zone = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(
                            GeoPoint(mosque.latitude, mosque.longitude),
                            DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius),
                        )
                        fillPaint.color = TrackedZoneFillColor
                        outlinePaint.color = TrackedZoneStrokeColor
                        outlinePaint.strokeWidth = TrackedZoneStrokeWidthPx
                    }
                    zonePolygons[mosque.id] = zone
                    mapView.overlays.add(zone)
                }
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = GeoPoint(mosque.latitude, mosque.longitude)
                        title = mosque.name
                        snippet = "${mosque.geofenceRadius} m zone"
                        // Every pin gets our own icon (never the shared osmdroid default, whose
                        // BitmapPool recycling on detach can blank it in later instances).
                        // Untracked mosques render dimmed so the state reads at a glance.
                        icon = mosquePin
                        if (!mosque.trackingEnabled) alpha = 0.45f
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedMosqueId = mosque.id
                            true
                        }
                        // A saved pin can be dragged to move the mosque. It also has to be
                        // draggable for the map's long-press (drop a new pin here) to stay away:
                        // the marker consumes the press first.
                        setDraggable(true)
                        setOnMarkerDragListener(
                            object : Marker.OnMarkerDragListener {
                                override fun onMarkerDragStart(marker: Marker) {
                                    selectedMosqueId = null
                                    marker.keepGrabOffsetOnDrag(lastTouchY, mapView)
                                    draggedZone = mosque.id to marker.position
                                }

                                override fun onMarkerDrag(marker: Marker) {
                                    draggedZone = mosque.id to marker.position
                                }

                                override fun onMarkerDragEnd(marker: Marker) {
                                    val centre = marker.position
                                    // Cleared before the save lands: the row is the source of truth
                                    // again as soon as Room reports it back.
                                    draggedZone = null
                                    viewModel.moveMosque(
                                        mosque = mosque,
                                        latitude = centre.latitude,
                                        longitude = centre.longitude,
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
        // Added last, so "where am I" is drawn over the pins rather than under them.
        mapView.overlays.add(locationDot)
        mapView.invalidate()
    }

    // Zone colour, on every new fix and on every frame of a drag: red for the zone the phone is
    // inside, the quiet green wash otherwise. The circle is what says "this mosque has you".
    LaunchedEffect(location, mosques, draggedZone) {
        if (picking) return@LaunchedEffect
        val fix = location
        val dragged = draggedZone
        mosques.forEach { mosque ->
            val zone = zonePolygons[mosque.id] ?: return@forEach
            val inDrag = dragged != null && dragged.first == mosque.id
            val centre = if (inDrag) dragged.second else GeoPoint(mosque.latitude, mosque.longitude)
            val inside = fix != null && DetectionPipeline.isInsideZone(
                Haversine.distanceMeters(fix.latitude, fix.longitude, centre.latitude, centre.longitude),
                mosque.geofenceRadius,
            )
            applyZoneStyle(zone, inside)
            if (inDrag) {
                zone.points = Polygon.pointsAsCircle(
                    centre,
                    DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius),
                )
            }
        }
        mapView.invalidate()
    }

    // Every new fix moves the dot and resizes the halo. Without a fix both stay hidden, which is
    // also what happens when the location permission has not been granted yet.
    LaunchedEffect(location) {
        val sample = location
        if (sample == null) {
            locationDot.isEnabled = false
            locationHalo.isEnabled = false
        } else {
            val point = GeoPoint(sample.latitude, sample.longitude)
            locationDot.position = point
            locationDot.isEnabled = true
            val accuracy = sample.accuracyMeters
            if (accuracy > 0f && accuracy <= MaxAccuracyHaloMeters) {
                locationHalo.points = Polygon.pointsAsCircle(point, accuracy.toDouble())
                locationHalo.isEnabled = true
            } else {
                locationHalo.isEnabled = false
            }
        }
        mapView.invalidate()
    }

    // Keep the circle in step with the pin and the slider.
    LaunchedEffect(picking, pickLat, pickLon, pickRadius) {
        if (!picking) return@LaunchedEffect
        val lat = pickLat ?: return@LaunchedEffect
        val lon = pickLon ?: return@LaunchedEffect
        val centre = GeoPoint(lat, lon)
        pickerMarker.position = centre
        pickerCircle.points = Polygon.pointsAsCircle(centre, pickRadius.toDouble())
        mapView.invalidate()
    }

    val centreLat = pickLat
    val centreLon = pickLon
    val fix = location
    val distanceToCentre = if (fix != null && centreLat != null && centreLon != null) {
        Haversine.distanceMeters(fix.latitude, fix.longitude, centreLat, centreLon)
    } else {
        null
    }
    val insideZone = distanceToCentre != null &&
        DetectionPipeline.isInsideZone(distanceToCentre, pickRadius)

    // While a zone is being placed the circle says the same thing the saved ones do: red once the
    // phone is standing in it.
    LaunchedEffect(picking, insideZone) {
        if (!picking) return@LaunchedEffect
        applyZoneStyle(pickerCircle, insideZone, ZoneFillColor, ZoneStrokeColor, ZoneStrokeWidthPx)
        mapView.invalidate()
    }

    // Live read-out for the pin under the finger (and the reason the zone can be dragged until it
    // touches the blue dot and then away again, watching the answer flip).
    val dragged = draggedZone
    val draggedMosque = dragged?.let { (id, _) -> mosques.firstOrNull { it.id == id } }
    val draggedDistance = if (dragged != null && fix != null) {
        Haversine.distanceMeters(
            fix.latitude,
            fix.longitude,
            dragged.second.latitude,
            dragged.second.longitude,
        )
    } else {
        null
    }
    val draggedInside = draggedDistance != null && draggedMosque != null &&
        DetectionPipeline.isInsideZone(draggedDistance, draggedMosque.geofenceRadius)

    val nearestMosque = nearest?.first
    val nearestDistance = nearest?.second
    val inside = nearestMosque != null && nearestDistance != null &&
        DetectionPipeline.isInsideZone(nearestDistance, nearestMosque.geofenceRadius)

    val canSave = picking && pickName.isNotBlank() && centreLat != null && centreLon != null && !saving

    fun savePickedMosque() {
        val lat = pickLat ?: return
        val lon = pickLon ?: return
        saving = true
        viewModel.addMosque(
            name = pickName,
            latitude = lat,
            longitude = lon,
            radiusMeters = pickRadius,
            // "Save & Activate" always means tracked with arrival alerts on; the global
            // monitoring switch in Settings stays the master on/off for the background service.
            enableAlerts = true,
            onSaved = { id ->
                saving = false
                picking = false
                editingMarker = false
                selectedMosqueId = id
            },
        )
    }

    // Map style menu. It is offered while picking too: a bare patch of imagery is often the
    // clearest backdrop for drawing a zone.
    var layersMenuOpen by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = if (picking) "New mosque zone" else "MasjidSync",
        modifier = modifier,
        navigationIcon = if (picking) {
            {
                IconButton(onClick = {
                    picking = false
                    editingMarker = false
                }) {
                    Icon(AppIcons.Close, contentDescription = "Cancel")
                }
            }
        } else {
            null
        },
        actions = {
            Box {
                IconButton(onClick = { layersMenuOpen = true }) {
                    Icon(AppIcons.Layers, contentDescription = "Map style: ${mapLayer.label}")
                }
                DropdownMenu(
                    expanded = layersMenuOpen,
                    onDismissRequest = { layersMenuOpen = false },
                ) {
                    MapLayer.entries.forEach { layer ->
                        DropdownMenuItem(
                            text = { Text(layer.label) },
                            onClick = {
                                layersMenuOpen = false
                                viewModel.setMapLayer(layer)
                            },
                            leadingIcon = if (layer == mapLayer) {
                                { Icon(AppIcons.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            if (!picking && mosques.isNotEmpty()) {
                IconButton(
                    onClick = {
                        mapView.zoomToBoundingBox(
                            boundingBoxOf(mosques.map { GeoPoint(it.latitude, it.longitude) }),
                            true,
                            FitBoundsPaddingPx,
                        )
                    },
                ) {
                    Icon(AppIcons.Place, contentDescription = "Show all mosques")
                }
            }
            if (!picking) {
                IconButton(
                    onClick = {
                        val current = location ?: return@IconButton
                        followFix = true
                        flyTo(current.toGeoPoint(), FocusZoom)
                    },
                ) {
                    Icon(AppIcons.MyLocation, contentDescription = "Centre on my location")
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
            )

            // Inside/Outside pill, floating over the top of the map. It gives way to the live
            // read-out while a pin is being dragged, so the two can never disagree mid-gesture.
            if (draggedMosque != null && draggedDistance != null) {
                DragReadout(
                    mosqueName = draggedMosque.name,
                    distanceMeters = draggedDistance,
                    radiusMeters = DetectionPipeline.zoneRadiusMeters(draggedMosque.geofenceRadius).toInt(),
                    inside = draggedInside,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            } else if (!picking) {
                ZonePill(
                    inside = inside,
                    hasFix = fix != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!foregroundGranted) {
                    PermissionBanner(
                        title = "Location permission required",
                        message = "MasjidSync needs GPS to show the map and to tell when you arrive.",
                        buttonText = "Grant",
                        onClick = { launcher.launch(foregroundPermissions.toTypedArray()) },
                    )
                } else if (!backgroundGranted) {
                    PermissionBanner(
                        title = "Allow background location",
                        message = "Choose \"Allow all the time\" so mosque detection keeps working when the app is closed.",
                        buttonText = "Allow",
                        onClick = { launcher.launch(backgroundPermissions.toTypedArray()) },
                    )
                }

                if (picking && editingMarker) {
                    MosqueEditorSheet(
                        name = pickName,
                        onNameChange = { pickName = it },
                        radiusMeters = pickRadius,
                        onRadiusChange = { pickRadius = it },
                        saving = saving,
                        canSave = canSave,
                        onSave = { savePickedMosque() },
                    )
                } else if (picking && !editingMarker) {
                    HintCard(
                        title = "Position the mosque",
                        message = "Touch and hold the pin, then drag it — or tap the map — to move the zone. Tap the pin to name it and size the circle. Tip: touch and hold any empty spot to drop a new pin there.",
                        liveText = distanceToCentre?.let { distance ->
                            "%s — %d m from the centre".format(
                                if (insideZone) "You are inside this zone" else "You are outside this zone",
                                distance.toInt(),
                            )
                        },
                        liveInside = insideZone,
                    )
                } else {
                    message?.let { text -> MessageCard(text) }

                    val selected: MosqueEntity? = mosques.firstOrNull { it.id == selectedMosqueId }
                    if (selected != null) {
                        MosqueDetailsCard(
                            mosque = selected,
                            distanceMeters = fix?.let {
                                Haversine.distanceMeters(
                                    it.latitude,
                                    it.longitude,
                                    selected.latitude,
                                    selected.longitude,
                                )
                            },
                            onClose = { selectedMosqueId = null },
                            onDelete = {
                                viewModel.delete(selected)
                                selectedMosqueId = null
                            },
                        )
                    }

                    SaveCurrentPositionButton(
                        enabled = fix != null,
                        onClick = { beginPicking(PickRequest.CurrentFix) },
                    )
                }

                // Credit for the basemap in use. OpenStreetMap and Esri both hand out their tiles
                // on the condition that this stays visible, so it belongs on the map itself.
                MapCredit(
                    text = mapLayer.credit,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Floating Inside/Outside pill over the map. */
@Composable
private fun ZonePill(inside: Boolean, hasFix: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = when {
            !hasFix -> MaterialTheme.colorScheme.surfaceVariant
            inside -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shadowElevation = 4.dp,
    ) {
        Text(
            text = when {
                !hasFix -> "Waiting for GPS…"
                inside -> "Inside Zone"
                else -> "Outside Zone"
            },
            style = MaterialTheme.typography.titleMedium,
            color = when {
                !hasFix -> MaterialTheme.colorScheme.onSurfaceVariant
                inside -> MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

/**
 * Read-out shown while a mosque pin is under the finger: the zone is following the drag, this says
 * what that does to the phone's position right now. It is the same comparison the arrival check
 * makes, so holding the pin until the text flips is holding it at the exact boundary.
 */
@Composable
private fun DragReadout(
    mosqueName: String,
    distanceMeters: Double,
    radiusMeters: Int,
    inside: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (inside) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shadowElevation = 4.dp,
    ) {
        Text(
            text = "%.0f m from %s · %s the %d m zone".format(
                distanceMeters,
                mosqueName,
                if (inside) "inside" else "outside",
                radiusMeters,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (inside) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Gold button: save wherever the user is standing right now. */
@Composable
private fun SaveCurrentPositionButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color(0xFF3F2E00),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(AppIcons.LocationOn, contentDescription = null)
        Text("  Save Current Position as Mosque")
    }
}

/** Small print under the map naming whoever supplied the tiles currently on screen. */
@Composable
private fun MapCredit(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * The mosque editor sheet: name the mosque and size the circle while the map stays
 * interactive behind it. Deliberately not a modal sheet — a scrim would swallow the map
 * taps this needs. The green circle drawn on the main map is the live radius preview
 * (there is no separate mini-map; what is drawn is what the geofence is armed with).
 */
@Composable
private fun MosqueEditorSheet(
    name: String,
    onNameChange: (String) -> Unit,
    radiusMeters: Int,
    onRadiusChange: (Int) -> Unit,
    saving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    val focusManager: FocusManager = LocalFocusManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )

            Text("Mosque Name", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("My Mosque") },
                trailingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Radius Slider", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = radiusMeters.toFloat(),
                onValueChange = { onRadiusChange(it.toInt()) },
                valueRange = MinRadiusMeters.toFloat()..MaxRadiusMeters.toFloat(),
                steps = (MaxRadiusMeters - MinRadiusMeters) / RadiusStepMeters - 1,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${MinRadiusMeters}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${radiusMeters}m",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${MaxRadiusMeters}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "The circle on the map updates live as you drag — red while you are standing inside it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save & Activate")
            }
        }
    }
}

/** One mosque's zone, shown as the map's selection card. */
@Composable
private fun MosqueDetailsCard(
    mosque: MosqueEntity,
    onDelete: () -> Unit,
    onClose: (() -> Unit)? = null,
    distanceMeters: Double? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mosque.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append("%.5f".format(mosque.latitude))
                            append(", ")
                            append("%.5f".format(mosque.longitude))
                            append("  ·  ")
                            append(DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt())
                            append(" m")
                            if (distanceMeters != null) {
                                append("  ·  ")
                                append("%.0f".format(distanceMeters))
                                append(" m away")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!mosque.trackingEnabled) {
                        Text(
                            text = "Tracking paused — switch it back on in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else {
                        Text(
                            text = "Press and hold the pin, then drag to move this mosque.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(AppIcons.Delete, contentDescription = "Delete ${mosque.name}")
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = "Close details")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun HintCard(title: String, message: String, liveText: String? = null, liveInside: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (liveText != null) {
                Text(
                    text = liveText,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (liveInside) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Styles a zone circle: red while the phone is inside it, [restingFill]/[restingStroke] otherwise.
 * The paints are the supported way to style a polygon in osmdroid 6.1.x — `setFillColor`,
 * `setStrokeColor` and `setStrokeWidth` are deprecated.
 */
private fun applyZoneStyle(
    polygon: Polygon,
    inside: Boolean,
    restingFill: Int = TrackedZoneFillColor,
    restingStroke: Int = TrackedZoneStrokeColor,
    restingWidth: Float = TrackedZoneStrokeWidthPx,
) {
    polygon.fillPaint.color = if (inside) ZoneInsideFillColor else restingFill
    polygon.outlinePaint.color = if (inside) ZoneInsideStrokeColor else restingStroke
    polygon.outlinePaint.strokeWidth = if (inside) ZoneInsideStrokeWidthPx else restingWidth
}

@Composable
private fun PermissionBanner(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

/**
 * Creates the [MapView] once and ties it to the composable's lifecycle.
 *
 * osmdroid keeps a tile-download queue that must be paused when the app goes to the background, and
 * a detached [MapView] cannot be reused, so the view lives and dies with this screen.
 *
 * [initialLayer] seeds the very first draw; later changes arrive through the [LaunchedEffect],
 * which re-points the tile provider at the newly chosen source.
 */
@Composable
private fun rememberMosqueMapView(initialLayer: MapLayer): MapView {
    val context = LocalContext.current
    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(initialLayer.tileSource)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            isTilesScaledToDpi = true
            controller.setZoom(DefaultZoom)
            controller.setCenter(DefaultTarget)
        }
    }

    // Switching basemaps keeps the camera, the pins and the zones: only the tiles change, and
    // each source caches its own tiles under its own name, so the two never mix.
    LaunchedEffect(initialLayer) {
        mapView.setTileSource(initialLayer.tileSource)
        mapView.invalidate()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mapView.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        mapView.onPause()
    }
    DisposableEffect(mapView) {
        onDispose { mapView.onDetach() }
    }

    return mapView
}

private fun LocationSample.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)

/**
 * Keeps a dragged pin under the finger that picked it up.
 *
 * When a drag starts, osmdroid drops the marker's anchor straight onto the touch point
 * (`Marker.moveToEventPosition`), so a pin grabbed by its head would jump the height of the pin
 * the instant it is touched — and, worse, the move that gets saved would be off by that much.
 * [Marker] subtracts [Marker.getDragOffset] from the touch Y, so passing the negated grab distance
 * cancels the jump exactly and the pin keeps the offset the finger took hold of. osmdroid converts
 * that field as millimetres, hence the matching conversion here.
 */
private fun Marker.keepGrabOffsetOnDrag(touchY: Float, mapView: MapView) {
    val anchorY = mapView.projection.toPixels(position, Point()).y
    val grabOffsetPx = anchorY - touchY
    dragOffset = -grabOffsetPx * 25.4f / mapView.resources.displayMetrics.xdpi
}

/**
 * The you-are-here dot.
 *
 * It is drawn last, so it sits on top of the pins and can never be hidden by one. Taps are swallowed
 * there — no info bubble over the dot, and no placing a zone under your own position while picking —
 * but long presses are deliberately **declined**. osmdroid walks the overlays top-most first and
 * treats a hit on a marker as an accepted long press even when that marker cannot be dragged, so a
 * pin dropped exactly on the dot (the natural way to test a zone without walking) would otherwise
 * become impossible to grab again. Returning false lets the press fall through to the pin
 * underneath, which starts its drag as usual.
 */
private class LocationDotMarker(mapView: MapView) : Marker(mapView) {
    override fun onLongPress(event: MotionEvent, mapView: MapView): Boolean = false
}

/** Smallest box containing every point. Callers must pass a non-empty list. */
private fun boundingBoxOf(points: List<GeoPoint>): BoundingBox {
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    // A single point gives a zero-area box, which cannot be zoomed to; pad it a little instead.
    val latPad = if (north - south == 0.0) 0.002 else 0.0
    val lonPad = if (east - west == 0.0) 0.002 else 0.0
    return BoundingBox(north + latPad, east + lonPad, south - latPad, west - lonPad)
}
