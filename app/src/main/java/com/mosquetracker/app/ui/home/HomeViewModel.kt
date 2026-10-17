package com.mosquetracker.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.detection.Haversine
import com.mosquetracker.app.detection.TriggerSource
import com.mosquetracker.app.detection.ZoneWatch
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.ActionPhase
import com.mosquetracker.app.model.ActionRequirement
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.MapLayer
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.routine.RoutineEngine
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PickRequest
import com.mosquetracker.app.work.VerificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Home map: status, saved pins, the zone picker and the editor sheet. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
    private val monitoringController: MonitoringController,
    private val locationProvider: LocationProvider,
    private val settingsStore: SettingsStore,
    private val zoneWatch: ZoneWatch,
    private val pickRequest: MosquePickRequest,
    private val routineEngine: RoutineEngine,
) : ViewModel() {

    val mosques: StateFlow<List<MosqueEntity>> = mosqueRepository.mosques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Live fix that drives the map camera, marker distances and the picker's "you are here" read
     * out. Backed by [LocationProvider], which closes its callback when there is no location
     * permission — [startLocationUpdates] re-collects after the user grants access.
     */
    private val _currentLocation = MutableStateFlow<LocationSample?>(null)
    val currentLocation: StateFlow<LocationSample?> = _currentLocation.asStateFlow()

    /** Picker open request raised by Settings ("Use Current Location" / search result). */
    val pickRequested: StateFlow<PickRequest?> = pickRequest.requested

    /** Whether background detection (and therefore arrival alerts) is switched on. */
    val monitoringEnabled: StateFlow<Boolean> = settingsStore.monitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Basemap the map draws, persisted so the choice survives a restart. */
    val mapLayer: StateFlow<MapLayer> = settingsStore.mapLayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapLayer.STREETS)

    private val _nearest = MutableStateFlow<Pair<MosqueEntity, Double>?>(null)

    /** Nearest *tracked* mosque and its distance. Untracked mosques never count as nearest. */
    val nearest: StateFlow<Pair<MosqueEntity, Double>?> = _nearest.asStateFlow()

    /** Mosques with a visit open — what the routine is acting on, as opposed to the raw circle. */
    val openVisitIds: StateFlow<Set<Long>> = attendanceRepository.openVisitIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _dndGranted = MutableStateFlow(routineEngine.dndAccessGranted)

    /**
     * True while the routine is on and an armed arrival action needs Do Not Disturb access it does
     * not have — the case where the phone only vibrates in the mosque instead of going silent.
     */
    val dndMissing: StateFlow<Boolean> = combine(
        settingsStore.monitoringEnabled,
        settingsStore.autoSilent,
        settingsStore.routineActions,
        _dndGranted,
    ) { monitoring, routine, actions, granted ->
        monitoring && routine && !granted && actions.any {
            it.phase == ActionPhase.ON_ARRIVAL && it.requirement == ActionRequirement.DND_ACCESS
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var locationJob: Job? = null

    /**
     * (Re)starts the fix collector. The screen calls it on start and after permission is granted,
     * and [stopLocationUpdates] on stop: brisk GPS is for the visible map only, while the
     * background service keeps its own, slower watch.
     */
    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { sample ->
                _currentLocation.value = sample
                _nearest.value = nearestTo(sample.latitude, sample.longitude)
                // The map decides "red or green" from every fix; the watch makes the arrival, the
                // visit row and the routine follow those same readings instead of one fence
                // decision taken with a different one. Same master switch as the background
                // machinery: detection off means detection off.
                if (settingsStore.isMonitoringEnabled()) zoneWatch.onFix(sample)
            }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
    }

    /** Do Not Disturb access is granted in system settings, so it is re-read on every resume. */
    fun refreshAccess() {
        _dndGranted.value = routineEngine.dndAccessGranted
    }

    /** After a permission prompt: restart the fix collector and let detection use the grant. */
    fun onPermissionsChanged() {
        startLocationUpdates()
        viewModelScope.launch { monitoringController.recheck() }
    }

    fun enableMonitoring() {
        viewModelScope.launch { monitoringController.enableMonitoring() }
    }

    /**
     * Recomputes the nearest mosque from the latest fix. Called after a pin is moved, so the pill
     * answers immediately rather than waiting for the next location update.
     */
    fun refreshNearest() {
        val fix = _currentLocation.value ?: return
        viewModelScope.launch { _nearest.value = nearestTo(fix.latitude, fix.longitude) }
    }

    private suspend fun nearestTo(latitude: Double, longitude: Double) =
        mosqueRepository.nearestWithin(
            latitude = latitude,
            longitude = longitude,
            searchRadiusMeters = NEARBY_SEARCH_RADIUS_METERS,
        )

    /**
     * Persists a mosque pin the user dragged to a new place.
     *
     * Two things have to follow the row. The geofence is re-registered, because the old circle is
     * now in the wrong place. And since a re-registered fence only ever produces an ENTER
     * (`INITIAL_TRIGGER_ENTER`), a mosque dragged off a phone that was inside it would otherwise
     * leave the routine armed with no EXIT to lift it — so the matching verification pass runs
     * here too. The EXIT one is guarded on an open visit, which is what keeps a dragged pin from
     * lifting a routine that belongs to a different mosque.
     */
    fun moveMosque(mosque: MosqueEntity, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            mosqueRepository.update(mosque.copy(latitude = latitude, longitude = longitude))
            monitoringController.onMosquesChanged()

            val fix = _currentLocation.value
            val distance = fix?.let {
                Haversine.distanceMeters(it.latitude, it.longitude, latitude, longitude)
            }
            // See DetectionPipeline.moveTransition for why the app runs this pass itself.
            val transition = distance?.let { moved ->
                DetectionPipeline.moveTransition(
                    distanceMeters = moved,
                    configuredRadiusMeters = mosque.geofenceRadius,
                    hasOpenVisit = attendanceRepository.hasOpenVisit(mosque.id),
                )
            }
            if (transition != null && settingsStore.isMonitoringEnabled()) {
                // The fix that just decided the move is the fix that verifies it.
                VerificationWorker.enqueue(context, mosque.id, transition, TriggerSource.MOVE, fix)
            }

            val radius = DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius).toInt()
            val inside = distance != null &&
                DetectionPipeline.isInsideZone(distance, mosque.geofenceRadius)
            _message.value = when {
                distance == null -> "Moved ${mosque.name}."
                inside -> "Moved ${mosque.name} — %.0f m from the centre, inside its $radius m zone."
                    .format(distance)

                else -> "Moved ${mosque.name} — %.0f m away, outside its $radius m zone."
                    .format(distance)
            }
            refreshNearest()
        }
    }

    /**
     * Saves a mosque whose centre and radius the user drew on the map.
     *
     * New mosques always arrive activated (tracking on): "Save & Activate" must mean it.
     * [enableAlerts] additionally turns background detection on when the user wants arrival
     * notifications — without it the new geofence would never fire, which is confusing
     * right after saving.
     */
    fun addMosque(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        enableAlerts: Boolean,
        onSaved: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val id = mosqueRepository.add(
                MosqueEntity(
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    geofenceRadius = radiusMeters,
                    trackingEnabled = true,
                ),
            )

            monitoringController.onMosqueAdded(
                mosqueId = id,
                enableAlerts = enableAlerts,
                // The reading that was on screen: saving the mosque you are standing in should
                // log the visit from that fix, not from one taken again a moment later.
                fix = _currentLocation.value,
            )
            _message.value = if (enableAlerts) {
                "Saved ${name.trim()}. You'll be notified when you're inside."
            } else {
                "Saved ${name.trim()}."
            }
            onSaved(id)
        }
    }

    fun consumePickRequest() {
        pickRequest.consume()
    }

    fun delete(mosque: MosqueEntity) {
        viewModelScope.launch {
            monitoringController.deleteMosque(mosque)
            _message.value = "Deleted ${mosque.name}."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Switches the basemap. Applied to the live map immediately and remembered for next time. */
    fun setMapLayer(layer: MapLayer) {
        viewModelScope.launch { settingsStore.setMapLayer(layer) }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    /** Last cached fix, if any. Lets Home seed the camera instantly at startup. */
    suspend fun lastKnownFix(): LocationSample? = locationProvider.lastKnown()

    private companion object {
        const val NEARBY_SEARCH_RADIUS_METERS = 500.0
    }
}
