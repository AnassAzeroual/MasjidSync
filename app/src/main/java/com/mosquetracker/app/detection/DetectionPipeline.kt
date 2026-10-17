package com.mosquetracker.app.detection

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
    }
}
