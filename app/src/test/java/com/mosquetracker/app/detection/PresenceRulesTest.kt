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
        // 90 m out of a 50 m circle, but the fix could be 60 m off.
        assertNull(DetectionPipeline.watchTransition(90.0, 50, hasOpenVisit = true, accuracyMeters = 60f))
        assertEquals(
            Geofence.GEOFENCE_TRANSITION_EXIT,
            DetectionPipeline.watchTransition(111.0, 50, hasOpenVisit = true, accuracyMeters = 60f),
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
    fun dwellNeedsTheFullTwentySeconds() {
        assertFalse(PresenceRules.dwellElapsed(now, now + 19_999L))
        assertTrue(PresenceRules.dwellElapsed(now, now + 20_000L))
    }
}
