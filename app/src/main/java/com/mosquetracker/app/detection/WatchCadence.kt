package com.mosquetracker.app.detection

/**
 * How often, and how precisely, the background watch asks for a fix.
 *
 * Near a circle's edge a crossing can happen any moment, so GPS is polled briskly; far from every
 * mosque nothing can change for minutes, so the cheap network fix is enough. Inside a visit the
 * pace only has to beat [PresenceRules.EXIT_DWELL_MILLIS] comfortably.
 */
enum class WatchCadence(val intervalMillis: Long, val highAccuracy: Boolean) {
    INSIDE(20_000L, true),
    NEAR(10_000L, true),
    APPROACH(60_000L, true),
    FAR(180_000L, false),
    ;

    companion object {
        /** Distance to the nearest circle's edge under which the watch runs at [NEAR]. */
        const val NEAR_EDGE_METERS = 300.0

        /** Distance to the nearest circle's edge under which the watch runs at [APPROACH]. */
        const val APPROACH_EDGE_METERS = 2_000.0

        /**
         * @param nearestEdgeMeters distance from the fix to the closest tracked circle's edge
         * (negative inside it), or null when nothing is tracked.
         */
        fun choose(hasOpenVisit: Boolean, nearestEdgeMeters: Double?): WatchCadence = when {
            hasOpenVisit -> INSIDE
            nearestEdgeMeters == null -> FAR
            nearestEdgeMeters <= NEAR_EDGE_METERS -> NEAR
            nearestEdgeMeters <= APPROACH_EDGE_METERS -> APPROACH
            else -> FAR
        }
    }
}
