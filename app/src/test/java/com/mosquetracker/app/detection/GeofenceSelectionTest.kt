package com.mosquetracker.app.detection

import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.model.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GeofenceSelectionTest {

    /** Mosques strung north along a meridian, [id] times 0.01° (~1.1 km) from the equator. */
    private fun mosques(count: Int) = (1..count).map { id ->
        MosqueEntity(id = id.toLong(), name = "M$id", latitude = id * 0.01, longitude = 0.0)
    }

    private fun fixAt(latitude: Double) =
        LocationSample(latitude = latitude, longitude = 0.0, accuracyMeters = 10f, timestamp = 0L)

    @Test
    fun underTheCapEverythingIsKept() {
        val all = mosques(5)
        assertSame(all, GeofenceManager.selectForRegistration(all, fixAt(0.0), max = 5))
        assertSame(all, GeofenceManager.selectForRegistration(all, near = null, max = 10))
    }

    @Test
    fun overTheCapTheNearestAreKept() {
        val all = mosques(10)
        val picked = GeofenceManager.selectForRegistration(all, fixAt(0.10), max = 3)
        assertEquals(listOf(10L, 9L, 8L), picked.map { it.id })
    }

    @Test
    fun overTheCapWithoutAFixTheFirstAreKept() {
        val picked = GeofenceManager.selectForRegistration(mosques(10), near = null, max = 3)
        assertEquals(listOf(1L, 2L, 3L), picked.map { it.id })
    }

    @Test
    fun platformLimitIsOneHundred() {
        val picked = GeofenceManager.selectForRegistration(mosques(150), fixAt(0.0), GeofenceManager.MAX_GEOFENCES)
        assertEquals(100, picked.size)
        assertEquals(1L, picked.first().id)
    }
}
