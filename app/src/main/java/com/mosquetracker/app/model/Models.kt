package com.mosquetracker.app.model

/** How a visit was confirmed. Mirrors the `detection_method` column. */
enum class DetectionMethod {
    /** Tier 1/3: OS geofence transition or straight-line distance fallback. */
    GEOFENCE,

    /** Tier 2: connected/nearby Wi-Fi BSSID matched. */
    WIFI,

    /** Tier 2: BLE beacon matched. */
    BLE,

    /** User logged the visit by hand. */
    MANUAL,
}

/** One GPS reading from the fused location provider. */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)

/** A Wi-Fi access point seen by the scanner. */
data class WifiAccessPoint(
    val ssid: String?,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
)

/** A BLE advertiser seen by the scanner. */
data class BleBeacon(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String>,
)

/** Result of running the cascading verification pipeline. */
data class DetectionOutcome(
    val confirmed: Boolean,
    val method: DetectionMethod,
    val mosqueId: Long,
    val mosqueName: String,
    val explanation: String,
    val distanceMeters: Double? = null,
)
