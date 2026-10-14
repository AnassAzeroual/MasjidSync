package com.mosquetracker.app.detection

import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.model.BleBeacon
import com.mosquetracker.app.model.DetectionMethod
import com.mosquetracker.app.model.DetectionOutcome
import com.mosquetracker.app.model.LocationSample
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cascading verification pipeline described in the project spec.
 *
 *  - Tier 1 (OS geofence) has already fired by the time this runs.
 *  - Tier 2 matches the connected/nearby Wi-Fi BSSID or a BLE beacon.
 *  - Tier 3 falls back to straight-line distance against the saved coordinates.
 */
@Singleton
class DetectionPipeline @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val wifiScanner: WifiScanner,
    private val bleScanner: BleScanner,
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

        // ----- Tier 2a: Wi-Fi BSSID -----------------------------------------
        val boundBssid = mosque.wifiBssid
        if (!boundBssid.isNullOrBlank() && wifiScanner.hasPermission()) {
            val connected = wifiScanner.connectedBssid()
            if (connected != null && connected.equals(boundBssid, ignoreCase = true)) {
                return confirmed(mosque.id, mosque.name, DetectionMethod.WIFI, "Connected to the bound Wi-Fi router ($connected).")
            }

            val nearby = wifiScanner.scanNearby().firstOrNull {
                it.bssid.equals(boundBssid, ignoreCase = true)
            }
            if (nearby != null) {
                return confirmed(
                    mosque.id,
                    mosque.name,
                    DetectionMethod.WIFI,
                    "Bound Wi-Fi router detected nearby (BSSID ${nearby.bssid}, ${nearby.rssi} dBm).",
                )
            }
        }

        // ----- Tier 2b: BLE beacon ------------------------------------------
        val boundBeacon = mosque.bleUuid
        if (!boundBeacon.isNullOrBlank() && bleScanner.hasPermission() && bleScanner.isEnabled()) {
            val hit = bleScanner.scan().firstOrNull { it.matches(boundBeacon) }
            if (hit != null) {
                return confirmed(
                    mosque.id,
                    mosque.name,
                    DetectionMethod.BLE,
                    "Bound beacon detected nearby (${hit.address}, ${hit.rssi} dBm).",
                )
            }
        }

        // ----- Tier 3: straight-line distance -------------------------------
        if (location != null) {
            val distance = Haversine.distanceMeters(
                location.latitude,
                location.longitude,
                mosque.latitude,
                mosque.longitude,
            )
            return if (distance <= TIER_3_RADIUS_METERS) {
                DetectionOutcome(
                    confirmed = true,
                    method = DetectionMethod.GEOFENCE,
                    mosqueId = mosque.id,
                    mosqueName = mosque.name,
                    explanation = "Within ${distance.toInt()} m of ${mosque.name} (distance fallback).",
                    distanceMeters = distance,
                )
            } else {
                DetectionOutcome(
                    confirmed = false,
                    method = DetectionMethod.GEOFENCE,
                    mosqueId = mosque.id,
                    mosqueName = mosque.name,
                    explanation = "No hardware match; ${distance.toInt()} m away.",
                    distanceMeters = distance,
                )
            }
        }

        return DetectionOutcome(
            confirmed = false,
            method = DetectionMethod.GEOFENCE,
            mosqueId = mosque.id,
            mosqueName = mosque.name,
            explanation = "No hardware match and no location fix available.",
        )
    }

    private fun BleBeacon.matches(bound: String): Boolean {
        val needle = bound.lowercase()
        return address.equals(needle, ignoreCase = true) ||
            serviceUuids.any { it.equals(needle, ignoreCase = true) } ||
            (!name.isNullOrBlank() && name.contains(bound, ignoreCase = true))
    }

    private fun confirmed(
        mosqueId: Long,
        name: String,
        method: DetectionMethod,
        explanation: String,
    ) = DetectionOutcome(
        confirmed = true,
        method = method,
        mosqueId = mosqueId,
        mosqueName = name,
        explanation = explanation,
    )

    companion object {
        /** Tier 3 threshold from the spec: 50 m counts as "inside". */
        const val TIER_3_RADIUS_METERS = 50.0
    }
}
