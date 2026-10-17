package com.mosquetracker.app.ui.settings

import com.mosquetracker.app.detection.FenceState
import com.mosquetracker.app.detection.FenceStatus
import com.mosquetracker.app.detection.GeofenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionSummaryTest {

    private val tracked = setOf(1L, 2L, 3L)

    @Test
    fun offIsNotAProblem() {
        val summary = detectionSummary(false, FenceState(FenceStatus.FAILED, emptySet()), tracked)
        assertFalse(summary.problem)
        assertTrue(summary.text.startsWith("Off"))
    }

    @Test
    fun onWithNothingTrackedIsAProblem() {
        assertTrue(detectionSummary(true, null, emptySet()).problem)
    }

    @Test
    fun notYetRegisteredIsStarting() {
        val summary = detectionSummary(true, null, tracked)
        assertFalse(summary.problem)
        assertTrue(summary.text.startsWith("Starting"))
    }

    @Test
    fun allArmedIsWatching() {
        val summary = detectionSummary(true, FenceState(FenceStatus.ACTIVE, tracked), tracked)
        assertFalse(summary.problem)
        assertTrue(summary.text.contains("Watching 3"))
    }

    @Test
    fun newlyTrackedMosqueIsArming() {
        val summary = detectionSummary(true, FenceState(FenceStatus.ACTIVE, setOf(1L, 2L)), tracked)
        assertFalse(summary.problem)
        assertTrue(summary.text.startsWith("Arming"))
    }

    @Test
    fun overTheCapWatchesTheNearest() {
        val many = (1L..GeofenceManager.MAX_GEOFENCES + 20L).toSet()
        val armed = many.take(GeofenceManager.MAX_GEOFENCES).toSet()
        val summary = detectionSummary(true, FenceState(FenceStatus.ACTIVE, armed), many)
        assertFalse(summary.problem)
        assertTrue(summary.text.contains("${GeofenceManager.MAX_GEOFENCES} nearest of ${many.size}"))
    }

    @Test
    fun refusedFencesAreProblems() {
        listOf(
            FenceStatus.NO_LOCATION_PERMISSION,
            FenceStatus.NO_BACKGROUND_PERMISSION,
            FenceStatus.FAILED,
        ).forEach { status ->
            assertTrue(status.name, detectionSummary(true, FenceState(status, emptySet()), tracked).problem)
        }
    }
}
