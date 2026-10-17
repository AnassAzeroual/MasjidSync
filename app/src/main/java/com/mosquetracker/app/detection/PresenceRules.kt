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

    /** How long a departure past the release margin has to hold before the visit ends. */
    const val EXIT_DWELL_MILLIS = 20_000L

    /**
     * How long merely outside the drawn circle has to hold before the visit ends anyway. A phone
     * parked on the rim never reaches the release margin, so without this it would stay muted for
     * as long as its readings stayed ambiguous; a minute of the same answer is evidence enough.
     */
    const val OUTSIDE_CIRCLE_EXIT_MILLIS = 60_000L

    /** True when [fix] is recent and precise enough to decide a visit with. */
    fun isUsable(fix: LocationSample, nowMillis: Long): Boolean {
        if (fix.accuracyMeters > MAX_FIX_ACCURACY_METERS) return false
        val age = nowMillis - fix.timestamp
        return age in -MAX_CLOCK_SKEW_MILLIS..MAX_FIX_AGE_MILLIS
    }

    /**
     * Distance past which a fix counts as clearly outside: the drawn circle plus the release
     * margin, widened to the fix's own error when that is the larger of the two. Only a reading
     * this far out ends a visit on the short dwell; a closer one has to hold for
     * [OUTSIDE_CIRCLE_EXIT_MILLIS] instead.
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

    /**
     * Whether a departure has held long enough to end the visit.
     *
     * [outsideForMillis] is how long usable fixes have kept the phone outside the drawn circle, or
     * null when it has not been seen outside. A fix past the release margin only has to hold for
     * [EXIT_DWELL_MILLIS]; anything less clear has to hold for [OUTSIDE_CIRCLE_EXIT_MILLIS], so
     * the wait is as short as the reading is convincing. A fix inside the circle is never a
     * departure, however long the clock has been running.
     */
    fun departureConfirmed(
        distanceMeters: Double,
        configuredRadiusMeters: Int,
        accuracyMeters: Float,
        outsideForMillis: Long?,
    ): Boolean {
        if (outsideForMillis == null) return false
        if (DetectionPipeline.isInsideZone(distanceMeters, configuredRadiusMeters)) return false
        val clear = distanceMeters > releaseDistanceMeters(configuredRadiusMeters, accuracyMeters)
        return outsideForMillis >= if (clear) EXIT_DWELL_MILLIS else OUTSIDE_CIRCLE_EXIT_MILLIS
    }
}
