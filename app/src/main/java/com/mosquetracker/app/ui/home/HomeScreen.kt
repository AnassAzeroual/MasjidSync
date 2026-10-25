package com.mosquetracker.app.ui.home

import android.graphics.Point
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosquetracker.app.R
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.detection.Haversine
import com.mosquetracker.app.detection.PresenceRules
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.MapLayer
import com.mosquetracker.app.ui.common.AlertBanner
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.MissingPermission
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PermissionBanners
import com.mosquetracker.app.ui.common.PickRequest
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.openNotificationPolicySettings
import com.mosquetracker.app.ui.common.rememberPermissionStatus
import com.mosquetracker.app.ui.theme.DangerRed
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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

/** While the radius thumb is held: the sheet nearly disappears, the slider stays half-visible. */
private const val DraggingSheetAlpha = 0.05f
private const val DraggingSliderAlpha = 0.5f

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
 * Home: the map, the Inside/Outside pill, the gold add button and the status card.
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
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val openVisitIds by viewModel.openVisitIds.collectAsStateWithLifecycle()
    val dndMissing by viewModel.dndMissing.collectAsStateWithLifecycle()

    val permissions = rememberPermissionStatus(onResult = viewModel::onPermissionsChanged)

    LifecycleStartEffect(viewModel) {
        viewModel.startLocationUpdates()
        onStopOrDispose { viewModel.stopLocationUpdates() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccess() }

    var selectedMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Pin being dragged right now: which mosque, and where the finger has taken it. The zone
    // follows it live and the change is only persisted when the drag ends, so an abandoned drag
    // leaves no trace.
    var draggedZone by remember { mutableStateOf<Pair<Long, GeoPoint>?>(null) }

    // ----- zone picker ------------------------------------------------------
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickRadius by rememberSaveable { mutableIntStateOf(DefaultRadiusMeters) }
    var saving by remember { mutableStateOf(false) }
    var editingMarker by rememberSaveable { mutableStateOf(false) }
    // True while the radius thumb is held: the sheet fades away so the circle can be watched.
    var draggingRadius by remember { mutableStateOf(false) }

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
                        // No title or snippet: the map stays nameless on purpose. New zones are
                        // numbered and the real name lives in Settings, so a label here is noise.
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
    val presence = ZonePresence.of(
        hasFix = fix != null,
        insideMosqueId = nearestMosque?.id?.takeIf { inside },
        fixUsable = fix != null && PresenceRules.isUsable(fix, System.currentTimeMillis()),
        monitoringEnabled = monitoringEnabled,
        openVisitIds = openVisitIds,
    )

    val canSave = picking && centreLat != null && centreLon != null && !saving

    fun savePickedMosque() {
        val lat = pickLat ?: return
        val lon = pickLon ?: return
        saving = true
        viewModel.addMosque(
            name = nextMosqueName(mosques.map { it.name }),
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
            val nextMapLayer = when (mapLayer) {
                MapLayer.STREETS -> MapLayer.SATELLITE
                MapLayer.SATELLITE -> MapLayer.STREETS
            }
            IconButton(
                onClick = { viewModel.setMapLayer(nextMapLayer) },
                modifier = Modifier.semantics { stateDescription = mapLayer.label },
            ) {
                Icon(AppIcons.Layers, contentDescription = "Switch to ${nextMapLayer.label} map")
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val wideLayout = maxWidth >= 600.dp && maxWidth > maxHeight
                    val panelWidth = if (wideLayout) (maxWidth * 0.4f).coerceAtMost(360.dp) else 560.dp
                    val statusModifier = Modifier
                        .align(if (wideLayout) Alignment.TopStart else Alignment.TopCenter)
                        .widthIn(max = panelWidth)
                        .heightIn(max = if (wideLayout) maxHeight else maxHeight * 0.3f)
                        .verticalScroll(rememberScrollState())

                    // Inside/Outside pill, floating over the top of the map. It gives way to the live
                    // read-out while a pin is being dragged, so the two can never disagree mid-gesture.
                    if (draggedMosque != null && draggedDistance != null) {
                        DragReadout(
                            distanceMeters = draggedDistance,
                            radiusMeters = DetectionPipeline.zoneRadiusMeters(draggedMosque.geofenceRadius).toInt(),
                            inside = draggedInside,
                            modifier = statusModifier,
                        )
                    } else if (!picking) {
                        ZonePill(presence = presence, modifier = statusModifier)
                    }

                    Column(
                        modifier = Modifier
                            .align(if (wideLayout) Alignment.BottomEnd else Alignment.BottomCenter)
                            .widthIn(max = panelWidth)
                            .fillMaxWidth()
                            .heightIn(max = if (wideLayout) maxHeight else maxHeight * 0.65f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // One banner at most, so the map stays visible: location grants first (nothing
                            // works without them), then what stops the routine, then notifications.
                            val locationMissing = permissions.missing.filter { it != MissingPermission.NOTIFICATIONS }
                            when {
                                locationMissing.isNotEmpty() -> PermissionBanners(
                                    status = permissions,
                                    shown = locationMissing.take(1),
                                )

                                presence == ZonePresence.DETECTION_OFF && nearestMosque != null -> AlertBanner(
                                    title = "Detection is off",
                                    message = "You are inside a saved zone, but background detection " +
                                        "is switched off, so the routine will not run.",
                                    actionText = "Turn on",
                                    onAction = viewModel::enableMonitoring,
                                )

                                dndMissing -> AlertBanner(
                                    title = "Do Not Disturb access needed",
                                    message = "Without it the routine can only set the phone to vibrate, " +
                                        "not silent, and skips its Do Not Disturb actions.",
                                    actionText = "Grant",
                                    onAction = { context.openNotificationPolicySettings() },
                                )

                                MissingPermission.NOTIFICATIONS in permissions.missing -> PermissionBanners(
                                    status = permissions,
                                    shown = listOf(MissingPermission.NOTIFICATIONS),
                                )
                            }

                            if (picking && editingMarker) {
                                MosqueEditorSheet(
                                    radiusMeters = pickRadius,
                                    onRadiusChange = { pickRadius = it },
                                    dragging = draggingRadius,
                                    onDraggingChange = { draggingRadius = it },
                                    saving = saving,
                                    canSave = canSave,
                                    onSave = { savePickedMosque() },
                                )
                            } else if (picking && !editingMarker) {
                                HintCard(
                                    title = "Position your zone",
                                    message = "Hold and drag the pin, or tap the map, to move it. Tap the pin to adjust the radius.\n\nHold an empty spot to drop a new pin. Rename it later in Settings.",
                                    liveText = distanceToCentre?.let { distance ->
                                        "%s · %d m from centre".format(
                                            if (insideZone) "Inside this zone" else "Outside this zone",
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
                            }
                        }

                        if (!picking) {
                            AddMosqueButton(
                                enabled = fix != null,
                                onClick = { beginPicking(PickRequest.CurrentFix) },
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                }

                // Credit for the basemap in use. OpenStreetMap and Esri both hand out their tiles
                // on the condition that this stays visible, so it belongs on the map itself.
                MapCredit(
                    text = mapLayer.credit,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

/**
 * Floating status pill over the map. Red whenever the reading is inside a circle — the same answer
 * the map paints — with a suffix whenever the app has not (or not yet) acted on it.
 */
@Composable
private fun ZonePill(presence: ZonePresence, modifier: Modifier = Modifier) {
    val (container, content) = when (presence) {
        ZonePresence.INSIDE,
        ZonePresence.CONFIRMING,
        ZonePresence.WEAK_FIX,
        ZonePresence.DETECTION_OFF,
        -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError

        ZonePresence.LEAVING ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        ZonePresence.NO_FIX ->
            MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurface

        ZonePresence.OUTSIDE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = content,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = when (presence) {
                ZonePresence.NO_FIX -> "Waiting for GPS…"
                ZonePresence.OUTSIDE -> "Outside zone"
                ZonePresence.INSIDE -> "Inside zone"
                ZonePresence.CONFIRMING -> "Inside zone · confirming…"
                ZonePresence.WEAK_FIX -> "Inside zone · GPS too weak to confirm"
                ZonePresence.DETECTION_OFF -> "Inside zone · detection off"
                ZonePresence.LEAVING -> "Leaving zone · confirming…"
            },
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
    distanceMeters: Double,
    radiusMeters: Int,
    inside: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = if (inside) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shadowElevation = 2.dp,
    ) {
        Text(
            text = "%.0f m · %s the %d m zone".format(
                distanceMeters,
                if (inside) "inside" else "outside",
                radiusMeters,
            ),
            style = MaterialTheme.typography.titleSmall,
            color = if (inside) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Gold round button, bottom right: save wherever the user is standing right now. */
@Composable
private fun AddMosqueButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            AppIcons.Add,
            contentDescription = "Save current position as mosque",
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun MapCredit(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            shadow = Shadow(
                color = Color.White.copy(alpha = 0.9f),
                offset = Offset(1f, 1f),
                blurRadius = 3f,
            ),
        ),
        color = Color(0xFF1A2821),
        modifier = modifier,
    )
}

/** The next free "Mosque N" name: one past the highest auto-named mosque already saved. */
internal fun nextMosqueName(existingNames: List<String>): String {
    val highest = existingNames.maxOfOrNull { name ->
        AutoNamePattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    } ?: 0
    return "Mosque ${highest + 1}"
}

private val AutoNamePattern = Regex("""Mosque (\d+)""")

/**
 * The mosque editor sheet: size the circle while the map stays interactive behind it.
 * Deliberately not a modal sheet — a scrim would swallow the map taps this needs.
 *
 * While the thumb is held the whole sheet drops to 5% opacity and only the slider stays
 * readable, at 50%: the circle on the map is the preview the user is actually sizing, and a
 * solid panel would hide the very thing being changed. Everything returns on release.
 *
 * There is no name field — new mosques are numbered ("Mosque 1", "Mosque 2", …) and renamed in
 * Settings, so drawing a zone is one less thing to fill in.
 */
@Composable
private fun MosqueEditorSheet(
    radiusMeters: Int,
    onRadiusChange: (Int) -> Unit,
    dragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    saving: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    // The slider sits inside the sheet, so every part gets its own alpha: the card and its
    // chrome at 5%, the slider block at 50%. (Fading the whole card and dividing the slider's
    // alpha out does not work — layer alpha is clamped to 1.)
    val cardAlpha by animateFloatAsState(if (dragging) DraggingSheetAlpha else 1f, label = "sheet")
    val sliderAlpha by animateFloatAsState(
        if (dragging) DraggingSliderAlpha else 1f,
        label = "slider",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = cardAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.alpha(sliderAlpha)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Zone radius",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$radiusMeters m",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
                Slider(
                    value = radiusMeters.toFloat(),
                    // Truncating would turn a thumb resting at 59.99 into 59: snap to the 10 m step.
                    onValueChange = {
                        onDraggingChange(true)
                        onRadiusChange((it / RadiusStepMeters).roundToInt() * RadiusStepMeters)
                    },
                    onValueChangeFinished = { onDraggingChange(false) },
                    valueRange = MinRadiusMeters.toFloat()..MaxRadiusMeters.toFloat(),
                    steps = (MaxRadiusMeters - MinRadiusMeters) / RadiusStepMeters - 1,
                    modifier = Modifier.semantics { contentDescription = "Zone radius in metres" },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$MinRadiusMeters m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$MaxRadiusMeters m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "The circle updates as you drag. Red means you are inside. Hold the slider to fade this card and see the map. Rename the mosque later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(cardAlpha),
            )

            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .alpha(cardAlpha),
            ) {
                Text(
                    text = if (saving) "Saving…" else "Save & Activate",
                    textAlign = TextAlign.Center,
                )
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saved zone",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        AppIcons.Delete,
                        contentDescription = "Delete ${mosque.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = "Close details")
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt()} m radius",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (distanceMeters != null) {
                        Text(
                            text = "%.0f m away".format(distanceMeters),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Text(
                text = "%.5f, %.5f".format(mosque.latitude, mosque.longitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (!mosque.trackingEnabled) {
                    "Tracking paused — switch it back on in Settings."
                } else {
                    "Hold and drag the pin to move this mosque."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageCard(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun HintCard(title: String, message: String, liveText: String? = null, liveInside: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (liveText != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (liveInside) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (liveInside) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ) {
                    Text(
                        text = liveText,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
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
