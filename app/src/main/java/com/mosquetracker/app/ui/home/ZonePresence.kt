package com.mosquetracker.app.ui.home

/**
 * What the Home pill says. The circle on the map answers "is this reading inside the drawn
 * zone?"; the pill also says whether the app has acted on it, because the two can differ for a
 * while — an arrival waiting for a usable fix, an exit held through its dwell, detection off.
 */
enum class ZonePresence {
    NO_FIX,
    OUTSIDE,

    /** Inside, and the visit is open: the routine is running. */
    INSIDE,

    /** Inside the circle, the arrival is being verified. */
    CONFIRMING,

    /** Inside the circle, but the fix is too coarse to log a visit on. */
    WEAK_FIX,

    /** Inside the circle with background detection switched off: nothing will happen. */
    DETECTION_OFF,

    /** Outside the circle with a visit still open: the exit is being confirmed. */
    LEAVING,
    ;

    companion object {
        /**
         * [insideMosqueId] is the tracked mosque whose circle the current reading lies in, if any.
         * [openVisitIds] are the mosques with a visit still open.
         */
        fun of(
            hasFix: Boolean,
            insideMosqueId: Long?,
            fixUsable: Boolean,
            monitoringEnabled: Boolean,
            openVisitIds: Set<Long>,
        ): ZonePresence = when {
            !hasFix -> NO_FIX
            insideMosqueId != null && !monitoringEnabled -> DETECTION_OFF
            insideMosqueId != null && insideMosqueId in openVisitIds -> INSIDE
            insideMosqueId != null && !fixUsable -> WEAK_FIX
            insideMosqueId != null -> CONFIRMING
            monitoringEnabled && openVisitIds.isNotEmpty() -> LEAVING
            else -> OUTSIDE
        }
    }
}
