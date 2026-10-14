package com.mosquetracker.app.detection

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle distance between two coordinates.
 *
 * The project spec called for this inside SQLite, but Android's bundled SQLite
 * ships without the math functions (`sin`, `cos`, `asin`) unless the platform
 * enables `SQLITE_ENABLE_MATH_FUNCTIONS`. Computing it here is portable, exact
 * and unit-testable; a bounding-box SQL pre-filter keeps the candidate set tiny.
 */
object Haversine {

    /** IUGG mean Earth radius in metres. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2).pow(2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)

        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }
}
