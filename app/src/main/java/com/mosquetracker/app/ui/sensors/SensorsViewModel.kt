package com.mosquetracker.app.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.detection.BleScanner
import com.mosquetracker.app.detection.WifiScanner
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.BleBeacon
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.WifiAccessPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val wifiScanner: WifiScanner,
    private val bleScanner: BleScanner,
) : ViewModel() {

    data class UiState(
        val location: LocationSample? = null,
        val wifiAccessPoints: List<WifiAccessPoint> = emptyList(),
        val beacons: List<BleBeacon> = emptyList(),
        val scanningWifi: Boolean = false,
        val scanningBle: Boolean = false,
        val wifiSupported: Boolean = true,
        val wifiEnabled: Boolean = true,
        val wifiPermission: Boolean = false,
        val bleSupported: Boolean = true,
        val bleEnabled: Boolean = false,
        val blePermission: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null
    private var bleJob: Job? = null

    init {
        refreshCapabilities()
        startLocation()
        scanWifi()
    }

    private fun refreshCapabilities() {
        _uiState.update {
            it.copy(
                wifiSupported = wifiScanner.isSupported(),
                wifiEnabled = wifiScanner.isEnabled(),
                wifiPermission = wifiScanner.hasPermission(),
                bleSupported = bleScanner.isSupported(),
                bleEnabled = bleScanner.isEnabled(),
                blePermission = bleScanner.hasPermission(),
            )
        }
    }

    fun startLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { sample ->
                _uiState.update { it.copy(location = sample) }
            }
        }
    }

    fun scanWifi() {
        viewModelScope.launch {
            refreshCapabilities()
            _uiState.update { it.copy(scanningWifi = true) }
            val results = wifiScanner.scanNearby()
            _uiState.update { it.copy(wifiAccessPoints = results, scanningWifi = false) }
        }
    }

    fun toggleBleScan() {
        if (_uiState.value.scanningBle) {
            stopBleScan()
            return
        }
        bleJob?.cancel()
        bleJob = viewModelScope.launch {
            refreshCapabilities()
            _uiState.update { it.copy(scanningBle = true, beacons = emptyList()) }
            val seen = LinkedHashMap<String, BleBeacon>()
            bleScanner.scan(durationMillis = BLE_SCAN_DURATION_MILLIS).collect { beacon ->
                seen[beacon.address] = beacon
                _uiState.update { it.copy(beacons = seen.values.sortedByDescending { b -> b.rssi }) }
            }
            _uiState.update { it.copy(scanningBle = false) }
        }
    }

    fun stopBleScan() {
        bleJob?.cancel()
        bleJob = null
        _uiState.update { it.copy(scanningBle = false) }
    }

    override fun onCleared() {
        stopBleScan()
        super.onCleared()
    }

    private companion object {
        const val BLE_SCAN_DURATION_MILLIS = 20_000L
    }
}
