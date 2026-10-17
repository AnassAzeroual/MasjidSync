package com.mosquetracker.app.detection

import com.mosquetracker.app.model.LocationSample

/**
 * When a GPS reading is good enough to decide a visit with, and how sure "left" has to be.
 *
 * Inside stays the centre-based rule in [DetectionPipeline.isInsideZone]. These rules only decide
 * which readings may be put to that test, and how long a departure has to last before it counts,
 * so a single bad fix can neither start a visit nor end one.
 */
object PresenceRules {

    /** A reading looser than this is a cell-tower guess, not a position inside a 20–100 m zone. */
    const val MAX_FIX_ACCURACY_METERS = 100f

    /** A cached reading older than this describes where the phone was, not where it is. */
    const val MAX_FIX_AGE_MILLIS = 2 * 60_000L

    /** Clock skew tolerated between the fix's time and ours before a "future" fix is distrusted. */
    private const val MAX_CLOCK_SKEW_MILLIS = 30_000L

    /** How long the watch must keep seeing the phone clearly outside before the visit ends. */
    const val EXIT_DWELL_MILLIS = 20_000L

    /** True when [fix] is recent and precise enough to decide a visit with. */
    fun isUsable(fix: LocationSample, nowMillis: Long): Boolean {
        if (fix.accuracyMeters > MAX_FIX_ACCURACY_METERS) return false
        val age = nowMillis - fix.timestamp
        return age in -MAX_CLOCK_SKEW_MILLIS..MAX_FIX_AGE_MILLIS
    }

    /**
     * Distance past which a fix counts as clearly outside: the drawn circle plus the release
     * margin, widened to the fix's own error when that is the larger of the two. A reading that
     * could still plausibly be inside the circle never ends a visit.
     */
    fun releaseDistanceMeters(configuredRadiusMeters: Int, accuracyMeters: Float): Double =
        DetectionPipeline.zoneRadiusMeters(configuredRadiusMeters) +
            maxOf(DetectionPipeline.RELEASE_MARGIN_METERS, accuracyMeters.toDouble().coerceAtLeast(0.0))

    /**
     * Whether a platform EXIT should close the visit, given the distance of a fresh fix (null when
     * none could be had). Only a fix that puts the phone back inside the circle overrules the
     * platform: without a reading, ending the visit is the safe choice, because a phone stuck on
     * silent is worse than one visit logged a little short.
     */
    fun fenceExitConfirmed(distanceMeters: Double?, configuredRadiusMeters: Int): Boolean =
        distanceMeters == null || !DetectionPipeline.isInsideZone(distanceMeters, configuredRadiusMeters)

    /** True once a departure first seen at [outsideSinceMillis] has lasted the exit dwell. */
    fun dwellElapsed(outsideSinceMillis: Long, nowMillis: Long): Boolean =
        nowMillis - outsideSinceMillis >= EXIT_DWELL_MILLIS
}
