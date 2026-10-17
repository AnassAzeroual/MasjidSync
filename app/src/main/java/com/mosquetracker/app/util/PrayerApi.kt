package com.mosquetracker.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Aladhan prayer-time API (free, no key): `timings/DD-MM-YYYY?latitude=..&longitude=..&method=21`.
 *
 * Method 21 (Morocco: Fajr 19°, Isha 17°, +5 min Dhuhr/Maghrib) matches Moroccan mosque
 * timetables. Pure HTTP + org.json — deliberately no new dependency for one GET request.
 */
object PrayerApi {

    /** Shared with the cache so a convention change invalidates stale days immediately. */
    const val METHOD = 21
    private const val TIMEOUT_MILLIS = 8_000

    data class ApiTimings(
        /** Prayer name → "HH:mm" (24 h, as returned), e.g. Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
        val times: Map<String, String>,
        /** IANA zone from `data.meta.timezone`, e.g. "Africa/Casablanca". */
        val timeZoneId: String?,
    )

    /** Null on any failure (no network, bad response, timeout) — callers fall back silently. */
    suspend fun fetch(latitude: Double, longitude: Double, date: LocalDate): ApiTimings? =
        withContext(Dispatchers.IO) {
            runCatching {
                val day = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                val url = URL(
                    "https://api.aladhan.com/v1/timings/$day" +
                        "?latitude=$latitude&longitude=$longitude&method=$METHOD",
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MILLIS
                    readTimeout = TIMEOUT_MILLIS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parse(body)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun parse(body: String): ApiTimings? {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 200) return null
        val data = root.optJSONObject("data") ?: return null
        val timings = data.optJSONObject("timings") ?: return null
        val names = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        val map = names.mapNotNull { name ->
            val raw = timings.optString(name, "").trim()
            // Defensive: keep "HH:mm", drop any trailing zone suffix.
            val match = Regex("""^(\d{1,2}:\d{2})""").find(raw)?.groupValues?.get(1)
            if (match != null) name to match else null
        }.toMap()
        if (map.size < names.size) return null
        val zone = data.optJSONObject("meta")?.optString("timezone", "").orEmpty()
        return ApiTimings(map, zone.ifEmpty { null })
    }
}
