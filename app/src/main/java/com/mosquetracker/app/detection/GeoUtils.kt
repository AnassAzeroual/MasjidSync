package com.mosquetracker.app.detection

import kotlin.math.cos

/** Degree-delta helpers used to build latitude/longitude bounding boxes. */
object GeoUtils {

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    fun latitudeDegreesFor(meters: Double): Double = meters / METERS_PER_DEGREE_LATITUDE

    fun longitudeDegreesFor(meters: Double, atLatitude: Double): Double {
        val cosLat = cos(Math.toRadians(atLatitude)).coerceAtLeast(1e-6)
        return meters / (METERS_PER_DEGREE_LATITUDE * cosLat)
    }
}
