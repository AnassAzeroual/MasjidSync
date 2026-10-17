package com.mosquetracker.app.ui.home

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import com.mosquetracker.app.ui.common.AppIcons
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PickRequest
import com.mosquetracker.app.ui.common.RequiredPermissions
import com.mosquetracker.app.ui.common.ScreenScaffold
import com.mosquetracker.app.ui.common.rememberPermissionLauncher
import com.mosquetracker.app.ui.common.rememberPermissionsGranted
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
 * Home: the map, the Inside/Outside pill, the gold save button and the status card.
 *
 * GPS is the only sensor the app uses; there is no Wi-Fi or Bluetooth tier.
 *
 * Tile downloads are the app's only network use and are entirely optional: with no connection the
 * basemap is blank but the pins, the circle and the picker all still work.
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

    val foregroundPermissions = remember { RequiredPermissions.foreground() }
    val backgroundPermissions = remember { RequiredPermissions.backgroundLocation() }
    val foregroundGranted by rememberPermissionsGranted(foregroundPermissions)
    val backgroundGranted by rememberPermissionsGranted(backgroundPermissions)
    val launcher = rememberPermissionLauncher { viewModel.startLocationUpdates() }

    var selectedMosqueId by rememberSaveable { mutableStateOf<Long?>(null) }

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

    val mapView = rememberMosqueMapView()
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
    DisposableEffect(pickerMarker) {
        pickerMarker.setOnMarkerDragListener(
            object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) = Unit

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
    // overlay list, so it is rebuilt whenever the set of things to draw changes.
    LaunchedEffect(mosques, picking) {
        mapView.overlays.clear()
        // Added first on purpose: osmdroid offers a tap to the *last* overlay first, so the event
        // catcher has to sit underneath the markers for them to keep their own gestures.
        mapView.overlays.add(tapOverlay)
        if (picking) {
            // Existing pins are hidden so the pending zone is the only thing the user reads.
            mapView.overlays.add(pickerCircle)
            mapView.overlays.add(pickerMarker)
        } else {
            mosques.forEach { mosque ->
                if (mosque.trackingEnabled) {
                    mapView.overlays.add(
                        Polygon(mapView).apply {
                            points = Polygon.pointsAsCircle(
                                GeoPoint(mosque.latitude, mosque.longitude),
                                DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius),
                            )
                            fillPaint.color = TrackedZoneFillColor
                            outlinePaint.color = TrackedZoneStrokeColor
                            outlinePaint.strokeWidth = TrackedZoneStrokeWidthPx
                        },
                    )
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
                    },
                )
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

            // Inside/Outside pill, floating over the top of the map.
            if (!picking) {
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
                        message = "Drag the pin or tap the map to move it. Tap the pin to name it and size the circle. Tip: touch and hold any spot to drop a new pin there.",
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
            inside -> MaterialTheme.colorScheme.primary
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
                inside -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
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
                text = "The green circle on the map updates live as you drag.",
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
private fun HintCard(title: String, message: String) {
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
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
 */
@Composable
private fun rememberMosqueMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            isTilesScaledToDpi = true
            controller.setZoom(DefaultZoom)
            controller.setCenter(DefaultTarget)
        }
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
