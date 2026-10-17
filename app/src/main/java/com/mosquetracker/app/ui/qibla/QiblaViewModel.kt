package com.mosquetracker.app.ui.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.PrayerRepository
import com.mosquetracker.app.detection.Haversine
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Next prayer, for the countdown line. Null when no fix yet. */
data class NextPrayer(val name: String, val startMillis: Long, val tomorrow: Boolean = false)

/** Today's times plus tomorrow's Fajr, so there is always a next prayer after Isha. */
data class PrayerDay(val today: PrayerTimes.Times, val tomorrowFajr: Long)

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

    private val today: Flow<LocalDate> = ticker
        .map { LocalDate.now(ZoneId.systemDefault()) }
        .distinctUntilChanged()

    // Prayer times barely move within a kilometre; recomputing on every 3 s fix would hammer the API.
    private val place: Flow<LocationSample?> = location.distinctUntilChanged { old, new ->
        old != null && new != null &&
            Haversine.distanceMeters(old.latitude, old.longitude, new.latitude, new.longitude) <
            RECOMPUTE_DISTANCE_METERS
    }

    /** Today's times at the current fix (live API, cache, else solar math), rolled at midnight. */
    private val prayerDay: StateFlow<PrayerDay?> = combine(place, today) { fix, date ->
        fix?.let {
            PrayerDay(
                today = prayerRepository.timesFor(it.latitude, it.longitude, date),
                tomorrowFajr = prayerRepository.timesFor(it.latitude, it.longitude, date.plusDays(1)).fajr,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val times: StateFlow<PrayerTimes.Times?> = prayerDay
        .map { it?.today }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val nextPrayer: StateFlow<NextPrayer?> = combine(prayerDay, ticker) { day, now ->
        day?.let { nextPrayerAfter(it, now) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var locationJob: Job? = null

    init {
        // Best-effort: aim the one-shot prayer workers whenever this screen is opened.
        viewModelScope.launch { prayerScheduler.refresh() }
    }

    /** Started and stopped with the screen, so GPS is not held while the tab is out of sight. */
    fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.locationUpdates().collect { _location.value = it }
        }
    }

    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
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
        private const val RECOMPUTE_DISTANCE_METERS = 1_000.0

        /** Next upcoming prayer, Sunrise excluded; after Isha it is tomorrow's Fajr. */
        fun nextPrayerAfter(day: PrayerDay, nowMillis: Long): NextPrayer =
            day.today.ordered()
                .filter { (name, _) -> name != "Sunrise" }
                .firstOrNull { (_, start) -> start > nowMillis }
                ?.let { (name, start) -> NextPrayer(name, start) }
                ?: NextPrayer("Fajr", day.tomorrowFajr, tomorrow = true)

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
