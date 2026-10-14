package com.mosquetracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.detection.WifiScanner
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.model.DetectionOutcome
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DashboardUiState(
    val currentLocation: LocationSample? = null,
    val connectedBssid: String? = null,
    val nearestMosque: MosqueEntity? = null,
    val nearestDistanceMeters: Double? = null,
    val monitoringEnabled: Boolean = false,
    val autoSilent: Boolean = false,
    val mosqueCount: Int = 0,
    val todayVisits: Int = 0,
    val lastOutcome: DetectionOutcome? = null,
    val verifying: Boolean = false,
) {
    val isInside: Boolean
        get() = nearestMosque?.let { mosque ->
            (nearestDistanceMeters ?: Double.MAX_VALUE) <= mosque.geofenceRadius
        } ?: false
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
    private val pipeline: DetectionPipeline,
    private val locationProvider: LocationProvider,
    private val wifiScanner: WifiScanner,
    private val monitoringController: MonitoringController,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            mosqueRepository.mosqueCount.collect { count ->
                _uiState.update { it.copy(mosqueCount = count) }
            }
        }
        viewModelScope.launch {
            settingsStore.monitoringEnabled.collect { enabled ->
                _uiState.update { it.copy(monitoringEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsStore.autoSilent.collect { enabled ->
                _uiState.update { it.copy(autoSilent = enabled) }
            }
        }
        viewModelScope.launch {
            attendanceRepository.visitsSince(TimeFormat.startOfTodayMillis()).collect { visits ->
                _uiState.update { it.copy(todayVisits = visits) }
            }
        }
        startLocationUpdates()
    }

    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            refreshHardware()
            locationProvider.locationUpdates().collect { sample ->
                _uiState.update { it.copy(currentLocation = sample) }
                evaluateNearest(sample)
            }
        }
    }

    fun refreshHardware() {
        viewModelScope.launch {
            val bssid = if (wifiScanner.hasPermission()) {
                withContext(Dispatchers.IO) { wifiScanner.connectedBssid() }
            } else {
                null
            }
            _uiState.update { it.copy(connectedBssid = bssid) }
            _uiState.value.currentLocation?.let { evaluateNearest(it) }
        }
    }

    private suspend fun evaluateNearest(sample: LocationSample) {
        val nearest = mosqueRepository.nearestWithin(
            latitude = sample.latitude,
            longitude = sample.longitude,
            searchRadiusMeters = NEARBY_SEARCH_RADIUS_METERS,
        )
        _uiState.update {
            it.copy(nearestMosque = nearest?.first, nearestDistanceMeters = nearest?.second)
        }
    }

    fun setMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) monitoringController.enableMonitoring() else monitoringController.disableMonitoring()
        }
    }

    fun setAutoSilent(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSilent(enabled) }
    }

    /** Runs the full Tier 2/3 pipeline against the nearest mosque on demand. */
    fun verifyNow() {
        val mosqueId = _uiState.value.nearestMosque?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(verifying = true) }
            val outcome = pipeline.verify(mosqueId, locationProvider.lastKnown())
            _uiState.update { it.copy(verifying = false, lastOutcome = outcome) }
        }
    }

    /** Logs a visit by hand (detection_method = MANUAL). */
    fun logManualVisit() {
        val mosqueId = _uiState.value.nearestMosque?.id ?: return
        viewModelScope.launch {
            attendanceRepository.recordEntry(mosqueId, method = DetectionMethod.MANUAL)
            _uiState.update {
                it.copy(
                    lastOutcome = DetectionOutcome(
                        confirmed = true,
                        method = DetectionMethod.MANUAL,
                        mosqueId = mosqueId,
                        mosqueName = it.nearestMosque?.name.orEmpty(),
                        explanation = "Logged manually.",
                    ),
                )
            }
        }
    }

    private companion object {
        const val NEARBY_SEARCH_RADIUS_METERS = 500.0
    }
}
