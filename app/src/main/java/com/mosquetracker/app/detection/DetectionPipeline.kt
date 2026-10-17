package com.mosquetracker.app.detection

import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.model.DetectionOutcome
import com.mosquetracker.app.model.LocationSample
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS-only arrival verification.
 *
 *  - Tier 1 (OS geofence) has already fired by the time this runs.
 *  - Tier 2 is a straight-line distance check against the circle the user drew on the map.
 *
 * There is no Wi-Fi or Bluetooth tier: GPS is the app's only sensor.
 */
@Singleton
class DetectionPipeline @Inject constructor(
    private val mosqueRepository: MosqueRepository,
) {

    suspend fun verify(mosqueId: Long, location: LocationSample?): DetectionOutcome {
        val mosque = mosqueRepository.getById(mosqueId)
            ?: return DetectionOutcome(
                confirmed = false,
                method = DetectionMethod.GEOFENCE,
                mosqueId = mosqueId,
                mosqueName = "",
                explanation = "This mosque is no longer saved.",
            )

        if (location == null) {
            return DetectionOutcome(
                confirmed = false,
                method = DetectionMethod.GEOFENCE,
                mosqueId = mosque.id,
                mosqueName = mosque.name,
                explanation = "No GPS fix available.",
            )
        }

        val distance = Haversine.distanceMeters(
            location.latitude,
            location.longitude,
            mosque.latitude,
            mosque.longitude,
        )
        // The circle the user drew on the map is the authority, not a global constant: a
        // mosque with a 200 m zone must confirm from 150 m out, and a 15 m one must not
        // confirm from across the street.
        val radius = zoneRadiusMeters(mosque.geofenceRadius)
        return if (distance <= radius) {
            DetectionOutcome(
                confirmed = true,
                method = DetectionMethod.GEOFENCE,
                mosqueId = mosque.id,
                mosqueName = mosque.name,
                explanation = "Within ${distance.toInt()} m of ${mosque.name} (zone radius ${radius.toInt()} m).",
                distanceMeters = distance,
            )
        } else {
            DetectionOutcome(
                confirmed = false,
                method = DetectionMethod.GEOFENCE,
                mosqueId = mosque.id,
                mosqueName = mosque.name,
                explanation = "${distance.toInt()} m away, outside the ${radius.toInt()} m zone.",
                distanceMeters = distance,
            )
        }
    }

    companion object {
        /**
         * Radius assumed when a mosque carries no usable radius. Mosques saved from the map always
         * store the circle the user drew, so this only covers legacy or hand-imported rows.
         */
        const val DEFAULT_ZONE_RADIUS_METERS = 50

        /**
         * The mosque's own configured circle, or [DEFAULT_ZONE_RADIUS_METERS] when it has none.
         * This is the single definition of "inside" shared by the pipeline and the UI.
         */
        fun zoneRadiusMeters(configuredRadiusMeters: Int): Double =
            if (configuredRadiusMeters > 0) {
                configuredRadiusMeters.toDouble()
            } else {
                DEFAULT_ZONE_RADIUS_METERS.toDouble()
            }

        /** True when a fix [distanceMeters] from the centre counts as being inside that mosque. */
        fun isInsideZone(distanceMeters: Double, configuredRadiusMeters: Int): Boolean =
            distanceMeters <= zoneRadiusMeters(configuredRadiusMeters)

        /**
         * The verification pass a mosque needs after the user dragged its pin to a new place, or
         * null when the move changes nothing and no pass should run.
         *
         * Both directions have to be produced here rather than left to the geofence: a re-registered
         * fence only ever raises an ENTER (`INITIAL_TRIGGER_ENTER`), so a mosque dragged off a phone
         * that was inside it would never produce the EXIT that lifts the routine.
         *
         * [hasOpenVisit] guards that EXIT. Without it, dragging a pin the phone is *not* inside
         * would close a visit that never opened and restore the routine while the phone is still
         * standing in a different mosque's zone.
         */
        fun moveTransition(
            distanceMeters: Double,
            configuredRadiusMeters: Int,
            hasOpenVisit: Boolean,
        ): Int? = when {
            isInsideZone(distanceMeters, configuredRadiusMeters) ->
                Geofence.GEOFENCE_TRANSITION_ENTER

            hasOpenVisit -> Geofence.GEOFENCE_TRANSITION_EXIT
            else -> null
        }

        /**
         * How far outside the drawn circle a fix has to be before the software watch *releases*
         * an open visit. Entering stays the plain, inclusive rule the map draws; leaving waits for
         * a clear margin, so a phone parked on the rim does not mute and un-mute with every fix.
         */
        const val RELEASE_MARGIN_METERS = 25.0

        /**
         * The pass the software watch wants for one mosque, or null when there is nothing to do.
         *
         * ENTER is level-triggered here, and that is the whole point: the OS fence only reports a
         * crossing the platform believes in, and every report is verified against a single fix. A
         * phone standing on the rim of a drawn circle can therefore have its ENTER rejected once
         * and then never hear about that mosque again for the whole visit — the map shows red
         * while the routine stays off. Watching the same fixes the map paints (see ZoneWatch) is
         * what keeps the routine and the red circle in agreement.
         *
         * EXIT needs the fix to be clearly outside, and this watch is the only place that margin
         * lives: fence exits and pin moves stay immediate, because those follow an event the
         * platform or the user has already decided on.
         */
        fun watchTransition(
            distanceMeters: Double,
            configuredRadiusMeters: Int,
            hasOpenVisit: Boolean,
        ): Int? = when {
            !hasOpenVisit && isInsideZone(distanceMeters, configuredRadiusMeters) ->
                Geofence.GEOFENCE_TRANSITION_ENTER

            hasOpenVisit &&
                distanceMeters > zoneRadiusMeters(configuredRadiusMeters) + RELEASE_MARGIN_METERS ->
                Geofence.GEOFENCE_TRANSITION_EXIT

            else -> null
        }
    }
}
