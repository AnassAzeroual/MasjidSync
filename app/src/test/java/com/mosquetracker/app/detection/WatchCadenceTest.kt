package com.mosquetracker.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCadenceTest {

    @Test
    fun openVisitWinsOverDistance() {
        assertEquals(WatchCadence.INSIDE, WatchCadence.choose(hasOpenVisit = true, nearestEdgeMeters = 5_000.0))
        assertEquals(WatchCadence.INSIDE, WatchCadence.choose(hasOpenVisit = true, nearestEdgeMeters = null))
    }

    @Test
    fun nothingTrackedRunsFar() {
        assertEquals(WatchCadence.FAR, WatchCadence.choose(hasOpenVisit = false, nearestEdgeMeters = null))
    }

    @Test
    fun insideACircleWithoutAVisitRunsNear() {
        // Inside the circle but the arrival is not decided yet: that is exactly when to look often.
        assertEquals(WatchCadence.NEAR, WatchCadence.choose(hasOpenVisit = false, nearestEdgeMeters = -20.0))
    }

    @Test
    fun bandsSwitchAtTheirEdges() {
        assertEquals(WatchCadence.NEAR, WatchCadence.choose(false, WatchCadence.NEAR_EDGE_METERS))
        assertEquals(WatchCadence.APPROACH, WatchCadence.choose(false, WatchCadence.NEAR_EDGE_METERS + 1))
        assertEquals(WatchCadence.APPROACH, WatchCadence.choose(false, WatchCadence.APPROACH_EDGE_METERS))
        assertEquals(WatchCadence.FAR, WatchCadence.choose(false, WatchCadence.APPROACH_EDGE_METERS + 1))
    }

    @Test
    fun insidePaceBeatsTheExitDwell() {
        assertTrue(WatchCadence.INSIDE.intervalMillis <= PresenceRules.EXIT_DWELL_MILLIS)
    }

    @Test
    fun onlyFarSkipsGps() {
        WatchCadence.entries.forEach { pace ->
            if (pace == WatchCadence.FAR) assertFalse(pace.highAccuracy) else assertTrue(pace.highAccuracy)
        }
    }
}
