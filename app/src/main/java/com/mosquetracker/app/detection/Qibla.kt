package com.mosquetracker.app.detection

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Qibla direction: the initial great-circle bearing from the user to the Kaaba.
 * Pure math, no sensors — the compass screen pairs this with the magnetometer azimuth.
 */
object Qibla {

    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262

    /** Initial bearing in degrees clockwise from north, in [0, 360). */
    fun bearingDegrees(fromLatitude: Double, fromLongitude: Double): Double {
        val lat1 = Math.toRadians(fromLatitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLon = Math.toRadians(KAABA_LONGITUDE - fromLongitude)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Straight-line distance to the Kaaba in metres. */
    fun distanceMeters(fromLatitude: Double, fromLongitude: Double): Double =
        Haversine.distanceMeters(fromLatitude, fromLongitude, KAABA_LATITUDE, KAABA_LONGITUDE)

    /** 16-wind compass label for a bearing, e.g. 245° → "WSW". */
    fun compassLabel(bearingDegrees: Double): String {
        val winds = arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val index = ((bearingDegrees + 11.25) / 22.5).toInt() % 16
        return winds[index]
    }
}
