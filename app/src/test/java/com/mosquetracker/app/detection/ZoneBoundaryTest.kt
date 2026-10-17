package com.mosquetracker.app.detection

import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.local.MosqueDao
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.LocationSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Am I inside the mosque's circle?" — the rule behind the map's Inside/Outside pill, the arrival
 * check and the routine, tested at and around the boundary.
 *
 * The rule is **centre-based**: the reported position is compared with the circle the user drew,
 * and the fix's accuracy is not part of the comparison. A fix whose error circle overlaps the
 * mosque circle, but whose centre sits outside the drawn circle, therefore counts as **outside** —
 * see [accuracyCircleTouchingTheZoneIsNotConfirmed], which exists because that is the case people
 * expect to work the other way round.
 *
 * Two consequences worth knowing before changing anything here:
 *
 *  - The map pill calls this same function, so the UI and the arrival check cannot drift apart
 *    (pinned by [thePipelineAgreesWithTheRuleTheMapUses]).
 *  - The OS geofence is registered with the same radius (`GeofenceManager`, `setCircularRegion`).
 *    Play Services only wakes the app once the reported *position* crosses that circle, so an
 *    outside-but-overlapping fix is not merely rejected here — it usually produces no event at
 *    all. Adopting the generous `distance <= radius + accuracy` rule therefore means widening the
 *    registered geofence too, or the app would never hear about the positions it is meant to
 *    accept.
 *  - Because a fence event can be rejected into silence, arrival and release are also decided by a
 *    software watch over the live fixes — see [DetectionPipeline.watchTransition]. That is the
 *    path that keeps the routine honest while the phone sits on the boundary.
 */
class ZoneBoundaryTest {

    // ---------------------------------------------------------------- the rule itself

    @Test
    fun theDrawnCircleIsTheZone() {
        assertTrue(DetectionPipeline.isInsideZone(49.9, configuredRadiusMeters = 50))
        assertFalse(DetectionPipeline.isInsideZone(50.1, configuredRadiusMeters = 50))
    }

    @Test
    fun exactlyOnTheLineCountsAsInside() {
        // The comparison is inclusive: the boundary belongs to the zone.
        assertTrue(DetectionPipeline.isInsideZone(50.0, configuredRadiusMeters = 50))
        assertFalse(DetectionPipeline.isInsideZone(50.0 + 1e-9, configuredRadiusMeters = 50))
    }

    @Test
    fun aMosqueWithoutARadiusFallsBackTo50m() {
        assertEquals(
            DetectionPipeline.DEFAULT_ZONE_RADIUS_METERS.toDouble(),
            DetectionPipeline.zoneRadiusMeters(configuredRadiusMeters = 0),
            0.0,
        )
        assertEquals(
            DetectionPipeline.DEFAULT_ZONE_RADIUS_METERS.toDouble(),
            DetectionPipeline.zoneRadiusMeters(configuredRadiusMeters = -5),
            0.0,
        )
        assertTrue(DetectionPipeline.isInsideZone(49.0, configuredRadiusMeters = 0))
        assertFalse(DetectionPipeline.isInsideZone(51.0, configuredRadiusMeters = 0))
    }

    // ---------------------------------------------------------------- through the pipeline

    @Test
    fun aFixInsideTheZoneIsConfirmed() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        val outcome = pipeline.verify(mosque.id, fixAt(metresFromCentre = 49.5))

        assertTrue(outcome.confirmed)
        assertTrue(outcome.explanation.contains("Within"))
    }

    @Test
    fun aFixOutsideTheZoneIsNotConfirmed() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        val outcome = pipeline.verify(mosque.id, fixAt(metresFromCentre = 60.0))

        assertFalse(outcome.confirmed)
        assertTrue(outcome.explanation.contains("outside"))
    }

    @Test
    fun accuracyCircleTouchingTheZoneIsNotConfirmed() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        // 50 m zone, fix 80 m out, reported accuracy 30 m: the two circles touch exactly.
        val touching = pipeline.verify(
            mosque.id,
            fixAt(metresFromCentre = 80.0, accuracyMeters = 30f),
        )
        assertFalse("touching is not being inside", touching.confirmed)

        // And with a 200 m accuracy the fix is already deep inside its own error circle, which
        // still does not put its centre inside the mosque's.
        val overlapping = pipeline.verify(
            mosque.id,
            fixAt(metresFromCentre = 80.0, accuracyMeters = 200f),
        )
        assertFalse("a loose fix is still a fix outside the circle", overlapping.confirmed)
    }

    @Test
    fun accuracyDoesNotPushAnInsideFixOut() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        // A poor accuracy reading must not turn an arrival into a non-arrival either.
        val outcome = pipeline.verify(
            mosque.id,
            fixAt(metresFromCentre = 10.0, accuracyMeters = 500f),
        )

        assertTrue(outcome.confirmed)
    }

    @Test
    fun thePipelineAgreesWithTheRuleTheMapUses() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        // Deliberately not exactly 50.0: a fix built from an offset in degrees, then measured back
        // with Haversine, lands a hair either side of the boundary. Inclusivity of the boundary is
        // pinned in exactlyOnTheLineCountsAsInside, where no conversion sits in the way.
        listOf(0.0, 25.0, 49.9, 50.1, 120.0).forEach { metres ->
            assertEquals(
                "pill and pipeline must agree at $metres m",
                DetectionPipeline.isInsideZone(metres, mosque.geofenceRadius),
                pipeline.verify(mosque.id, fixAt(metres)).confirmed,
            )
        }
    }

    @Test
    fun aDeletedMosqueCannotConfirm() = runTest {
        val pipeline = pipelineWith(mosqueAt(radius = 50))

        val outcome = pipeline.verify(mosqueId = 999L, location = fixAt(0.0))

        assertFalse(outcome.confirmed)
    }

    @Test
    fun noFixCannotConfirm() = runTest {
        val mosque = mosqueAt(radius = 50)
        val pipeline = pipelineWith(mosque)

        val outcome = pipeline.verify(mosque.id, location = null)

        assertFalse(outcome.confirmed)
    }

    // ---------------------------------------------------------------- moving a saved pin

    @Test
    fun aPinDraggedOntoThePhoneIsAnArrival() {
        // Dragging the circle over where you are standing is an arrival like any other, so the pass
        // that records it and runs the routine is the ENTER one.
        val transition = DetectionPipeline.moveTransition(
            distanceMeters = 10.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = false,
        )

        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, transition)
    }

    @Test
    fun aPinDraggedOffThePhoneEndsTheVisit() {
        // A re-registered geofence only ever raises an ENTER, so this EXIT has to come from the app:
        // it is what lifts the routine once the circle no longer covers the phone.
        val transition = DetectionPipeline.moveTransition(
            distanceMeters = 300.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )

        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, transition)
    }

    @Test
    fun movingAPinThePhoneWasNeverInsideDoesNothing() {
        // Without an open visit there is no arrival to end, and the routine may well belong to a
        // different mosque the phone is standing in - so the move must stay out of it.
        val transition = DetectionPipeline.moveTransition(
            distanceMeters = 300.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = false,
        )

        assertNull(transition)
    }

    @Test
    fun theBoundaryBelongsToTheZoneWhenAPinMovesToo() {
        val onTheLine = DetectionPipeline.moveTransition(
            distanceMeters = 60.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = false,
        )
        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, onTheLine)

        val justBeyond = DetectionPipeline.moveTransition(
            distanceMeters = 60.0 + 1e-9,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )
        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, justBeyond)
    }

    // ---------------------------------------------------------------- the software watch

    @Test
    fun theWatchStartsAVisitFromAFixInsideTheZone() {
        // Level-triggered arrival: this is what stops a phone standing inside its circle — the map
        // showing red — from waiting forever for a fence event that was already used up.
        val transition = DetectionPipeline.watchTransition(
            distanceMeters = 20.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = false,
        )

        assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, transition)
    }

    @Test
    fun theWatchDoesNotRestartAnOpenVisit() {
        val transition = DetectionPipeline.watchTransition(
            distanceMeters = 20.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )

        assertNull(transition)
    }

    @Test
    fun theWatchHoldsAnOpenVisitThroughBoundaryNoise() {
        // Standing on the rim: a fix a few metres outside must not lift the routine, or the phone
        // would mute and un-mute with every reading.
        val justOutside = DetectionPipeline.watchTransition(
            distanceMeters = 62.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )
        assertNull(justOutside)

        val atTheMargin = DetectionPipeline.watchTransition(
            distanceMeters = 60.0 + DetectionPipeline.RELEASE_MARGIN_METERS,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )
        assertNull("the margin itself still holds the visit", atTheMargin)
    }

    @Test
    fun theWatchEndsAVisitThePhoneClearlyLeft() {
        val transition = DetectionPipeline.watchTransition(
            distanceMeters = 60.0 + DetectionPipeline.RELEASE_MARGIN_METERS + 1e-9,
            configuredRadiusMeters = 60,
            hasOpenVisit = true,
        )

        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, transition)
    }

    @Test
    fun theWatchLeavesAnAbsentPhoneAlone() {
        val transition = DetectionPipeline.watchTransition(
            distanceMeters = 2_000.0,
            configuredRadiusMeters = 60,
            hasOpenVisit = false,
        )

        assertNull(transition)
    }

    private companion object {
        const val CentreLatitude = 31.6295
        const val CentreLongitude = -7.9811

        /**
         * Metres per degree of latitude over [Haversine]'s spherical Earth, so that a test can say
         * "put the fix 30 m out" and get a distance of 30 m to within floating-point noise.
         */
        const val MetresPerDegreeLatitude = 111_195.08

        fun mosqueAt(radius: Int) = MosqueEntity(
            id = 1L,
            name = "Boundary test",
            latitude = CentreLatitude,
            longitude = CentreLongitude,
            geofenceRadius = radius,
        )

        /** A fix due north of the test mosque, [metresFromCentre] metres away. */
        fun fixAt(metresFromCentre: Double, accuracyMeters: Float = 0f) = LocationSample(
            latitude = CentreLatitude + metresFromCentre / MetresPerDegreeLatitude,
            longitude = CentreLongitude,
            accuracyMeters = accuracyMeters,
            timestamp = 0L,
        )

        fun pipelineWith(mosque: MosqueEntity): DetectionPipeline =
            DetectionPipeline(MosqueRepository(FakeMosqueDao(mosque)))
    }
}

/** In-memory [MosqueDao]: enough of the DAO to drive the pipeline without a device. */
private class FakeMosqueDao(vararg mosques: MosqueEntity) : MosqueDao {

    private val rows = mosques.toMutableList()

    override fun observeAll(): Flow<List<MosqueEntity>> = flowOf(rows.toList())

    override suspend fun getAll(): List<MosqueEntity> = rows.toList()

    override suspend fun getById(id: Long): MosqueEntity? = rows.firstOrNull { it.id == id }

    override fun observeCount(): Flow<Int> = flowOf(rows.size)

    override suspend fun getWithinBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<MosqueEntity> = rows.filter {
        it.latitude in minLat..maxLat && it.longitude in minLon..maxLon && it.trackingEnabled
    }

    override suspend fun insert(mosque: MosqueEntity): Long {
        rows += mosque
        return mosque.id
    }

    override suspend fun setTrackingEnabled(id: Long, enabled: Boolean) = Unit

    override suspend fun setAlertMode(id: Long, mode: AlertMode) = Unit

    override suspend fun update(mosque: MosqueEntity) = Unit

    override suspend fun delete(mosque: MosqueEntity) {
        rows -= mosque
    }
}
