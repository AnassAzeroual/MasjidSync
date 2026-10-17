package com.mosquetracker.app.data.repository

import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.detection.Haversine
import com.mosquetracker.app.detection.PrayerTimes
import com.mosquetracker.app.util.PlaceSearch
import com.mosquetracker.app.util.PrayerApi
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prayer times with graceful degradation: live Aladhan API when online, last cached day
 * when the fetch fails but the user hasn't moved, local solar math otherwise. The UI and
 * the silence scheduler never care which source answered.
 */
@Singleton
class PrayerRepository @Inject constructor(
    private val settingsStore: SettingsStore,
    private val placeSearch: PlaceSearch,
) {

    suspend fun timesFor(latitude: Double, longitude: Double, date: LocalDate): PrayerTimes.Times {
        val zone = ZoneId.systemDefault()
        val todayIso = LocalDate.now(zone).toString()

        val cached = settingsStore.readPrayerCache()
        if (cached != null && cached.dateIso == date.toString() && cached.method == PrayerApi.METHOD) {
            val close = Haversine.distanceMeters(
                latitude, longitude, cached.latitude, cached.longitude,
            ) <= CACHE_RADIUS_METERS
            if (close) {
                toTimes(cached.times, cached.timeZoneId, date, zone)?.let { return it }
            }
        }

        if (date.toString() == todayIso && placeSearch.isOnline()) {
            PrayerApi.fetch(latitude, longitude, date)?.let { api ->
                settingsStore.writePrayerCache(
                    SettingsStore.PrayerCache(
                        dateIso = date.toString(),
                        times = api.times,
                        timeZoneId = api.timeZoneId,
                        latitude = latitude,
                        longitude = longitude,
                        method = PrayerApi.METHOD,
                    ),
                )
                toTimes(api.times, api.timeZoneId, date, zone)?.let { return it }
            }
        }

        // Offline, moved away, tomorrow's date, or the API hiccuped: solar math always works.
        return PrayerTimes.forDate(latitude, longitude, date, zone)
    }

    private fun toTimes(
        times: Map<String, String>,
        timeZoneId: String?,
        date: LocalDate,
        fallbackZone: ZoneId,
    ): PrayerTimes.Times? {
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrNull() ?: fallbackZone
        // "24:xx" (past-midnight rendering) rolls into the next day.
        fun epoch(name: String): Long? {
            val raw = times[name] ?: return null
            val parts = raw.split(":")
            if (parts.size != 2) return null
            val totalMinutes = (parts[0].toIntOrNull() ?: return null) * 60 +
                (parts[1].toIntOrNull() ?: return null)
            return date.atStartOfDay(zone).toInstant().toEpochMilli() + totalMinutes * 60_000L
        }
        val parsed = PrayerTimes.Times(
            fajr = epoch("Fajr") ?: return null,
            sunrise = epoch("Sunrise") ?: return null,
            dhuhr = epoch("Dhuhr") ?: return null,
            asr = epoch("Asr") ?: return null,
            maghrib = epoch("Maghrib") ?: return null,
            isha = epoch("Isha") ?: return null,
        )
        // Sanity: a corrupt payload must never produce backwards times.
        val ordered = parsed.ordered().map { (_, millis) -> millis }
        if (!ordered.zipWithNext().all { (a, b) -> a < b }) return null
        return parsed
    }

    private companion object {
        /** Cached times stay valid while the user is within this distance of the fetch point. */
        const val CACHE_RADIUS_METERS = 50_000.0
    }
}
