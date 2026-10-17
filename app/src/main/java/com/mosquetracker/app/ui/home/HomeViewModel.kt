package com.mosquetracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.ui.common.MosquePickRequest
import com.mosquetracker.app.ui.common.PickRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Home map: status, saved pins, the zone picker and the editor sheet. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val monitoringController: MonitoringController,
    private val locationProvider: LocationProvider,
    private val settingsStore: SettingsStore,
    private val pickRequest: MosquePickRequest,
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

    private val _nearest = MutableStateFlow<Pair<MosqueEntity, Double>?>(null)

    /** Nearest *tracked* mosque and its distance. Untracked mosques never count as nearest. */
    val nearest: StateFlow<Pair<MosqueEntity, Double>?> = _nearest.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var locationJob: Job? = null

    init {
        startLocationUpdates()
    }

    /** (Re)starts the fix collector — called on init and after permission is granted. */
    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { sample ->
                _currentLocation.value = sample
                _nearest.value = mosqueRepository.nearestWithin(
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    searchRadiusMeters = NEARBY_SEARCH_RADIUS_METERS,
                )
            }
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

            monitoringController.onMosqueAdded(id, enableAlerts = enableAlerts)
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
            mosqueRepository.delete(mosque)
            monitoringController.removeGeofence(mosque.id)
            _message.value = "Deleted ${mosque.name}."
        }
    }

    fun clearMessage() {
        _message.value = null
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
