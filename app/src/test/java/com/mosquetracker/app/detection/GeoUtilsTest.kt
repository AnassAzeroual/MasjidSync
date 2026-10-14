package com.mosquetracker.app.detection

import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun latitudeBoxIsWiderThanLongitudeBoxAwayFromEquator() {
        val radiusMeters = 500.0
        val latitude = 51.5074 // London

        val latDelta = GeoUtils.latitudeDegreesFor(radiusMeters)
        val lonDelta = GeoUtils.longitudeDegreesFor(radiusMeters, latitude)

        assertTrue(latDelta > 0)
        assertTrue(lonDelta > latDelta)
    }

    @Test
    fun boundingBoxContainsAPointInsideTheRadius() {
        val lat = 24.4686
        val lon = 39.6142
        val radius = 1_000.0

        val latDelta = GeoUtils.latitudeDegreesFor(radius)
        val lonDelta = GeoUtils.longitudeDegreesFor(radius, lat)

        // A point 100 m north stays inside the box.
        val nearbyLat = lat + GeoUtils.latitudeDegreesFor(100.0)
        assertTrue(nearbyLat in (lat - latDelta)..(lat + latDelta))
        assertTrue(lon in (lon - lonDelta)..(lon + lonDelta))
    }

    @Test
    fun longitudeDegreesAreStableAtEquator() {
        val latDelta = GeoUtils.latitudeDegreesFor(100.0)
        val lonDelta = GeoUtils.longitudeDegreesFor(100.0, 0.0)
        assertTrue(kotlin.math.abs(latDelta - lonDelta) < 1e-9)
    }
}
