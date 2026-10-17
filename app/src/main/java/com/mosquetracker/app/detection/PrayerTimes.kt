package com.mosquetracker.app.detection

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer-time calculation (Muslim World League convention: Fajr 18°, Isha 17°,
 * Asr factor 1 / Standard, Maghrib at sunset).
 *
 * Compact implementation of the standard solar-position algorithm (same family as PrayTimes):
 * equation of time + declination from the Julian day, then hour angles per sun elevation.
 * Typically within ~10 minutes of published timetables — plenty for a silence buffer that
 * defaults to ±15 minutes, not a fatwa. No network, no API key.
 */
object PrayerTimes {

    data class Times(
        val fajr: Long,
        val sunrise: Long,
        val dhuhr: Long,
        val asr: Long,
        val maghrib: Long,
        val isha: Long,
    ) {
        fun ordered(): List<Pair<String, Long>> = listOf(
            "Fajr" to fajr,
            "Sunrise" to sunrise,
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha,
        )
    }

    fun forDate(latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId): Times {
        val julianDay = julianDayNumber(date)
        val declination = sunDeclination(julianDay)
        val equationOfTime = equationOfTimeHours(julianDay)
        val tzOffset = zone.rules
            .getOffset(date.atTime(12, 0).atZone(zone).toInstant())
            .totalSeconds / 3600.0

        // Solar noon, in local hours.
        val dhuhr = 12.0 + tzOffset - longitude / 15.0 - equationOfTime

        fun timeForAngle(angleDegrees: Double, afterNoon: Boolean): Double? {
            val latRad = Math.toRadians(latitude)
            val cosHourAngle = (sin(Math.toRadians(angleDegrees)) - sin(latRad) * sin(declination)) /
                (cos(latRad) * cos(declination))
            // |cos| > 1 means the sun never reaches that depression (high-latitude summer):
            // null triggers the night-fraction fallback below.
            if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
            val hourAngle = Math.toDegrees(acos(cosHourAngle)) / 15.0
            return if (afterNoon) dhuhr + hourAngle else dhuhr - hourAngle
        }

        // Asr (Standard): the sun's altitude when the shadow equals factor 1 plus the
        // noon shadow — a positive altitude (above the horizon), NOT a depression.
        val asrAltitude = Math.toDegrees(
            atan(1.0 / (1.0 + tan(Math.abs(Math.toRadians(latitude) - declination)))),
        )

        val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
        fun epoch(hours: Double): Long = midnight + (hours * 3_600_000L).toLong()

        val sunrise = timeForAngle(-0.833, afterNoon = false) ?: (dhuhr - 6.0)
        val sunset = timeForAngle(-0.833, afterNoon = true) ?: (dhuhr + 6.0)
        // Seventh-of-the-night fallback for Fajr/Isha when twilight never gets deep enough.
        val nightHours = 24.0 - (sunset - sunrise)

        return Times(
            fajr = epoch(timeForAngle(-18.0, afterNoon = false) ?: (sunrise - nightHours / 7.0)),
            sunrise = epoch(sunrise),
            dhuhr = epoch(dhuhr),
            asr = epoch(timeForAngle(asrAltitude, afterNoon = true) ?: (dhuhr + 3.0)),
            maghrib = epoch(sunset),
            isha = epoch(timeForAngle(-17.0, afterNoon = true) ?: (sunset + nightHours / 7.0)),
        )
    }

    private fun julianDayNumber(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth.toDouble()
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = (year / 100.0).toInt()
        val b = 2 - a + (a / 4.0).toInt()
        return (365.25 * (year + 4716)).toInt() +
            (30.6001 * (month + 1)).toInt() + day + b - 1524.5
    }

    private fun fixAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) a += 360.0
        return a
    }

    private fun sunDeclination(julianDay: Double): Double {
        val d = julianDay - 2451545.0
        val g = Math.toRadians(fixAngle(357.529 + 0.98560028 * d))
        val q = fixAngle(280.459 + 0.98564736 * d)
        val sunLon = Math.toRadians(fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2.0 * g)))
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        return asin(sin(obliquity) * sin(sunLon))
    }

    private fun equationOfTimeHours(julianDay: Double): Double {
        val d = julianDay - 2451545.0
        val g = Math.toRadians(fixAngle(357.529 + 0.98560028 * d))
        val q = fixAngle(280.459 + 0.98564736 * d)
        val sunLon = Math.toRadians(fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2.0 * g)))
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        val rightAscension = Math.toDegrees(atan2(cos(obliquity) * sin(sunLon), cos(sunLon))) / 15.0
        var eq = q / 15.0 - fixAngle(rightAscension * 15.0) / 15.0
        // Wrap into [-12, +12] so the date line never shifts noon by a day.
        if (eq > 12) eq -= 24.0
        if (eq < -12) eq += 24.0
        return eq
    }
}
