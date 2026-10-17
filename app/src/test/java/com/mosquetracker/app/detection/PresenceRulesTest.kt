package com.mosquetracker.app.detection

import com.google.android.gms.location.Geofence
import com.mosquetracker.app.model.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceRulesTest {

    private val now = 1_000_000_000L

    private fun fix(accuracy: Float = 10f, ageMillis: Long = 0L) =
        LocationSample(latitude = 0.0, longitude = 0.0, accuracyMeters = accuracy, timestamp = now - ageMillis)

    @Test
    fun freshPreciseFixIsUsable() {
        assertTrue(PresenceRules.isUsable(fix(), now))
        assertTrue(PresenceRules.isUsable(fix(accuracy = 100f, ageMillis = 120_000L), now))
    }

    @Test
    fun coarseFixIsNotUsable() {
        assertFalse(PresenceRules.isUsable(fix(accuracy = 100.5f), now))
    }

    @Test
    fun staleFixIsNotUsable() {
        assertFalse(PresenceRules.isUsable(fix(ageMillis = 120_001L), now))
    }

    @Test
    fun slightlyFutureFixIsToleratedButFarFutureIsNot() {
        assertTrue(PresenceRules.isUsable(fix(ageMillis = -30_000L), now))
        assertFalse(PresenceRules.isUsable(fix(ageMillis = -30_001L), now))
    }

    @Test
    fun releaseDistanceUsesMarginForPreciseFixes() {
        assertEquals(75.0, PresenceRules.releaseDistanceMeters(50, 5f), 0.0)
    }

    @Test
    fun releaseDistanceWidensToTheFixError() {
        assertEquals(110.0, PresenceRules.releaseDistanceMeters(50, 60f), 0.0)
    }

    @Test
    fun releaseDistanceFallsBackToDefaultRadius() {
        assertEquals(75.0, PresenceRules.releaseDistanceMeters(0, 0f), 0.0)
    }

    @Test
    fun negativeAccuracyIsTreatedAsUnknownNotAsShrinkage() {
        assertEquals(75.0, PresenceRules.releaseDistanceMeters(50, -1f), 0.0)
    }

    @Test
    fun watchDoesNotReleaseWhileTheFixCouldStillBeInside() {
        // 90 m out of a 50 m circle, but the fix could be 60 m off, so this is not the clear
        // departure the short dwell is for.
        assertNull(
            DetectionPipeline.watchTransition(
                distanceMeters = 90.0,
                configuredRadiusMeters = 50,
                hasOpenVisit = true,
                accuracyMeters = 60f,
                outsideForMillis = PresenceRules.EXIT_DWELL_MILLIS,
            ),
        )
        assertEquals(
            Geofence.GEOFENCE_TRANSITION_EXIT,
            DetectionPipeline.watchTransition(
                distanceMeters = 111.0,
                configuredRadiusMeters = 50,
                hasOpenVisit = true,
                accuracyMeters = 60f,
                outsideForMillis = PresenceRules.EXIT_DWELL_MILLIS,
            ),
        )
    }

    @Test
    fun fenceExitStandsWithoutAFreshFix() {
        assertTrue(PresenceRules.fenceExitConfirmed(null, 50))
    }

    @Test
    fun fenceExitIsOverruledOnlyByAFixInside() {
        assertFalse(PresenceRules.fenceExitConfirmed(50.0, 50))
        assertTrue(PresenceRules.fenceExitConfirmed(50.1, 50))
    }

    @Test
    fun aClearDepartureNeedsTheFullDwell() {
        assertFalse(
            PresenceRules.departureConfirmed(200.0, 50, 10f, PresenceRules.EXIT_DWELL_MILLIS - 1),
        )
        assertTrue(PresenceRules.departureConfirmed(200.0, 50, 10f, PresenceRules.EXIT_DWELL_MILLIS))
    }

    @Test
    fun anUnclearDepartureNeedsAFullMinuteOutsideTheCircle() {
        // 60 m out of a 50 m circle: outside the circle the map draws, but inside the release
        // margin, so it takes the long wait.
        assertFalse(PresenceRules.departureConfirmed(60.0, 50, 10f, 59_999L))
        assertTrue(
            PresenceRules.departureConfirmed(60.0, 50, 10f, PresenceRules.OUTSIDE_CIRCLE_EXIT_MILLIS),
        )
    }

    @Test
    fun aPhoneNeverSeenOutsideIsNeverReleased() {
        assertFalse(PresenceRules.departureConfirmed(500.0, 50, 10f, null))
    }
}
