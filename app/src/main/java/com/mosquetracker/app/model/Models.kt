package com.mosquetracker.app.model

/** How a visit was confirmed. Mirrors the `detection_method` column. */
enum class DetectionMethod {
    /** GPS only: OS geofence transition, or straight-line distance against the drawn circle. */
    GEOFENCE,

    /** User logged the visit by hand. */
    MANUAL,
}

/**
 * What happens on arrival at one mosque. Mirrors the `alert_mode` column.
 * Notify-only mosques still log visits and post the arrival notice, they just
 * never touch the sound profile.
 */
enum class AlertMode {
    FULL_SILENT,
    NOTIFY_ONLY,
}

/** One GPS reading from the fused location provider. */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
)

/** Result of running the verification pipeline. */
data class DetectionOutcome(
    val confirmed: Boolean,
    val method: DetectionMethod,
    val mosqueId: Long,
    val mosqueName: String,
    val explanation: String,
    val distanceMeters: Double? = null,
)
