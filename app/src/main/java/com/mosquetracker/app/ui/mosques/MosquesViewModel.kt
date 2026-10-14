package com.mosquetracker.app.ui.mosques

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.BleScanner
import com.mosquetracker.app.detection.WifiScanner
import com.mosquetracker.app.model.BleBeacon
import com.mosquetracker.app.monitoring.MonitoringController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Backs the "Detection Override Center": manual hardware binding per mosque. */
@HiltViewModel
class MosquesViewModel @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val monitoringController: MonitoringController,
    private val wifiScanner: WifiScanner,
    private val bleScanner: BleScanner,
) : ViewModel() {

    val mosques: StateFlow<List<MosqueEntity>> = mosqueRepository.mosques
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _discoveredBeacons = MutableStateFlow<List<BleBeacon>>(emptyList())
    val discoveredBeacons: StateFlow<List<BleBeacon>> = _discoveredBeacons.asStateFlow()

    private val _scanningBeacons = MutableStateFlow(false)
    val scanningBeacons: StateFlow<Boolean> = _scanningBeacons.asStateFlow()

    private var beaconScanJob: Job? = null

    fun bindConnectedWifi(mosque: MosqueEntity) {
        viewModelScope.launch {
            val bssid = withContext(Dispatchers.IO) {
                if (wifiScanner.hasPermission()) wifiScanner.connectedBssid() else null
            }
            if (bssid == null) {
                _message.value = "Not connected to any Wi-Fi network."
                return@launch
            }
            persist(mosque.copy(wifiBssid = bssid))
            _message.value = "Bound Wi-Fi $bssid to ${mosque.name}."
        }
    }

    fun unbindWifi(mosque: MosqueEntity) {
        viewModelScope.launch {
            persist(mosque.copy(wifiBssid = null))
            _message.value = "Removed Wi-Fi binding from ${mosque.name}."
        }
    }

    fun bindBeacon(mosque: MosqueEntity, beacon: BleBeacon) {
        viewModelScope.launch {
            persist(mosque.copy(bleUuid = beacon.address))
            _message.value = "Bound beacon ${beacon.address} to ${mosque.name}."
        }
    }

    fun unbindBeacon(mosque: MosqueEntity) {
        viewModelScope.launch {
            persist(mosque.copy(bleUuid = null))
            _message.value = "Removed beacon binding from ${mosque.name}."
        }
    }

    fun delete(mosque: MosqueEntity) {
        viewModelScope.launch {
            mosqueRepository.delete(mosque)
            monitoringController.removeGeofence(mosque.id)
            _message.value = "Deleted ${mosque.name}."
        }
    }

    fun scanForBeacons() {
        if (_scanningBeacons.value) return
        beaconScanJob = viewModelScope.launch {
            _scanningBeacons.value = true
            _discoveredBeacons.value = emptyList()
            val seen = LinkedHashMap<String, BleBeacon>()
            bleScanner.scan(durationMillis = BEACON_PICKER_SCAN_MILLIS).collect { beacon ->
                seen[beacon.address] = beacon
                _discoveredBeacons.value = seen.values.sortedByDescending { it.rssi }
            }
            _scanningBeacons.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun persist(mosque: MosqueEntity) {
        mosqueRepository.update(mosque)
        monitoringController.onMosquesChanged()
    }

    override fun onCleared() {
        beaconScanJob?.cancel()
    }

    private companion object {
        const val BEACON_PICKER_SCAN_MILLIS = 12_000L
    }
}
