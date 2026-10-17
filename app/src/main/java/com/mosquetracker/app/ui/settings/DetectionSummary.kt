package com.mosquetracker.app.ui.settings

import com.mosquetracker.app.detection.FenceState
import com.mosquetracker.app.detection.FenceStatus
import com.mosquetracker.app.detection.GeofenceManager

/** One line saying what background detection is really doing, and whether that is a problem. */
data class DetectionSummary(val text: String, val problem: Boolean)

/**
 * Describes detection from the switch, the fences Android actually accepted and the tracked
 * mosques. The switch alone is not the truth: it can be on while every fence was refused.
 */
fun detectionSummary(
    monitoringEnabled: Boolean,
    fenceState: FenceState?,
    trackedIds: Set<Long>,
): DetectionSummary {
    if (!monitoringEnabled) return DetectionSummary("Off — no mosque zone is being watched", problem = false)
    if (trackedIds.isEmpty()) return DetectionSummary("On, but no mosque is tracked", problem = true)
    val tracked = trackedIds.size
    return when (fenceState?.status) {
        null -> DetectionSummary("Starting… arming $tracked mosque zone(s)", problem = false)

        FenceStatus.ACTIVE, FenceStatus.NO_MOSQUES -> {
            val armed = fenceState.registeredIds.count { it in trackedIds }
            when {
                armed == tracked -> DetectionSummary(
                    "Watching $tracked mosque zone(s) with Android geofences",
                    problem = false,
                )

                tracked > GeofenceManager.MAX_GEOFENCES && armed > 0 -> DetectionSummary(
                    "Watching the $armed nearest of $tracked mosque zones — Android allows no " +
                        "more; the set follows you",
                    problem = false,
                )

                // A mosque was just tracked or moved and its fence is on the way.
                else -> DetectionSummary("Arming the mosque zones…", problem = false)
            }
        }

        FenceStatus.NO_LOCATION_PERMISSION -> DetectionSummary(
            "Zones not armed: precise location is not granted",
            problem = true,
        )

        FenceStatus.NO_BACKGROUND_PERMISSION -> DetectionSummary(
            "Zones not armed: Android needs location \"Allow all the time\". Arrivals are " +
                "only noticed while the detection service keeps running",
            problem = true,
        )

        FenceStatus.FAILED -> DetectionSummary(
            "Android refused the zones — is Location switched on? Retried automatically",
            problem = true,
        )
    }
}
