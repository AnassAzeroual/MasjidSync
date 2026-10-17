package com.mosquetracker.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineTest {

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, Haversine.distanceMeters(21.4225, 39.8262, 21.4225, 39.8262), 0.001)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111km() {
        val distance = Haversine.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertEquals(111_195.0, distance, 50.0)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquatorIsAbout111km() {
        val distance = Haversine.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, distance, 50.0)
    }

    @Test
    fun makkahToMadinahIsRoughly340km() {
        // Kaaba -> Masjid an-Nabawi.
        val distance = Haversine.distanceMeters(21.4225, 39.8262, 24.4686, 39.6142)
        assertTrue("expected ~340km but was $distance", distance in 335_000.0..345_000.0)
    }

    @Test
    fun distanceIsSymmetric() {
        val a = Haversine.distanceMeters(48.8566, 2.3522, 51.5074, -0.1278)
        val b = Haversine.distanceMeters(51.5074, -0.1278, 48.8566, 2.3522)
        assertEquals(a, b, 0.001)
    }

    @Test
    fun defaultZoneRadiusSeparatesNearFromFarPoints() {
        // 0.0004 degrees of latitude is ~44.5 m: inside the fallback zone.
        val near = Haversine.distanceMeters(0.0, 0.0, 0.0004, 0.0)
        assertTrue(DetectionPipeline.isInsideZone(near, configuredRadiusMeters = 0))

        // 0.001 degrees is ~111 m: outside it.
        val far = Haversine.distanceMeters(0.0, 0.0, 0.001, 0.0)
        assertFalse(DetectionPipeline.isInsideZone(far, configuredRadiusMeters = 0))
    }

    @Test
    fun theCircleTheUserDrewGovernsTheZone() {
        // 0.00063 degrees of latitude is ~70 m.
        val distance = Haversine.distanceMeters(0.0, 0.0, 0.00063, 0.0)

        assertFalse(
            "a 50 m zone must not confirm a 70 m fix",
            DetectionPipeline.isInsideZone(distance, configuredRadiusMeters = 50),
        )
        assertTrue(
            "a 200 m zone must confirm a 70 m fix",
            DetectionPipeline.isInsideZone(distance, configuredRadiusMeters = 200),
        )
    }
}
