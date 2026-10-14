package com.mosquetracker.app.ui.addmosque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.WifiScanner
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.monitoring.MonitoringController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddMosqueViewModel @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val monitoringController: MonitoringController,
    private val locationProvider: LocationProvider,
    private val wifiScanner: WifiScanner,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val accuracyMeters: Float? = null,
        val connectedBssid: String? = null,
        val saving: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        capture()
    }

    /** Pulls one fresh-enough fix plus the currently connected Wi-Fi BSSID. */
    fun capture() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val sample = locationProvider.lastKnown()
                ?: locationProvider.locationUpdates().firstOrNull()

            val bssid = if (wifiScanner.hasPermission()) {
                withContext(Dispatchers.IO) { wifiScanner.connectedBssid() }
            } else {
                null
            }

            _state.update {
                it.copy(
                    loading = false,
                    latitude = sample?.latitude,
                    longitude = sample?.longitude,
                    accuracyMeters = sample?.accuracyMeters,
                    connectedBssid = bssid,
                )
            }
        }
    }

    fun save(
        name: String,
        radiusMeters: Int,
        bindCurrentWifi: Boolean,
        onSaved: (Long) -> Unit,
    ) {
        val snapshot = _state.value
        val latitude = snapshot.latitude ?: return
        val longitude = snapshot.longitude ?: return

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }

            val id = mosqueRepository.add(
                MosqueEntity(
                    name = name.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    geofenceRadius = radiusMeters,
                    wifiBssid = if (bindCurrentWifi) snapshot.connectedBssid else null,
                ),
            )

            monitoringController.onMosquesChanged()
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }
}
