package com.mosquetracker.app.ui.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.PrayerRepository
import com.mosquetracker.app.detection.PrayerTimes
import com.mosquetracker.app.detection.Qibla
import com.mosquetracker.app.location.CompassProvider
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.work.PrayerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Next prayer, for the countdown line. Null when no fix yet. */
data class NextPrayer(val name: String, val startMillis: Long)

/** Backs the Qibla tab: compass heading, prayer times and the silent-prayer toggle. */
@HiltViewModel
class QiblaViewModel @Inject constructor(
    private val compassProvider: CompassProvider,
    private val locationProvider: LocationProvider,
    private val settingsStore: SettingsStore,
    private val prayerScheduler: PrayerScheduler,
    private val prayerRepository: PrayerRepository,
) : ViewModel() {

    val azimuth: StateFlow<Float?> = compassProvider.azimuthDegrees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _location = MutableStateFlow<LocationSample?>(null)
    val location: StateFlow<LocationSample?> = _location.asStateFlow()

    val prayerSilentEnabled: StateFlow<Boolean> = settingsStore.prayerSilentEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bufferMinutes: StateFlow<Int> = settingsStore.prayerBufferMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_PRAYER_BUFFER_MINUTES)

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    /** Today's prayer times at the current fix: live API when online, cache, else solar math. */
    val times: StateFlow<PrayerTimes.Times?> = location
        .map { fix ->
            fix?.let {
                prayerRepository.timesFor(
                    it.latitude,
                    it.longitude,
                    LocalDate.now(ZoneId.systemDefault()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Next upcoming prayer (Sunrise excluded — nothing to silence for). */
    val nextPrayer: StateFlow<NextPrayer?> = combine(times, ticker) { t, now ->
        t?.ordered()
            ?.filter { (name, _) -> name != "Sunrise" }
            ?.firstOrNull { (_, start) -> start > now }
            ?.let { (name, start) -> NextPrayer(name, start) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var locationJob: Job? = null

    init {
        startLocationUpdates()
        // Best-effort: aim the one-shot prayer workers whenever this screen is opened.
        viewModelScope.launch { prayerScheduler.refresh() }
    }

    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { _location.value = it }
        }
    }

    fun setPrayerSilent(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setPrayerSilentEnabled(enabled)
            prayerScheduler.refresh()
        }
    }

    fun setBufferMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setPrayerBufferMinutes(minutes)
            prayerScheduler.refresh()
        }
    }

    companion object {
        /** Qibla bearing + distance for the header line. Null without a fix. */
        fun qiblaFor(fix: LocationSample?): Triple<Double, Double, String>? =
            fix?.let {
                val bearing = Qibla.bearingDegrees(it.latitude, it.longitude)
                Triple(
                    bearing,
                    Qibla.distanceMeters(it.latitude, it.longitude),
                    Qibla.compassLabel(bearing),
                )
            }
    }
}
