package com.mosquetracker.app.detection

import android.content.Context
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.work.VerificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Software watch over the live GPS stream — the tier that notices the zone crossings the OS fence
 * misses.
 *
 * The fence is cheap but event-driven: it reports the crossings the platform believes in, and a
 * phone standing on the rim of a drawn circle can have its arrival rejected once and then never
 * hear about that mosque again for the whole visit (see [DetectionPipeline.watchTransition]). This
 * watch instead runs every usable fix past the saved mosques, so the routine follows the same
 * readings that paint the map's circle red.
 *
 * Arrival is immediate. Departure has to last (see [PresenceRules.departureConfirmed]): a fix
 * clearly outside the circle — past the release margin, or past its own error — ends the visit
 * after [PresenceRules.EXIT_DWELL_MILLIS], and one that never got clear of the circle ends it after
 * [PresenceRules.OUTSIDE_CIRCLE_EXIT_MILLIS]. Either way a single stray reading never un-mutes the
 * phone in the middle of a prayer. Passes are serialized: fixes can come from more than one
 * collector.
 */
@Singleton
class ZoneWatch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
) {
    private val mutex = Mutex()
    private val lastAttemptAt = mutableMapOf<Long, Long>()

    /** When usable fixes first put the phone outside each mosque's drawn circle. */
    private val outsideSince = mutableMapOf<Long, Long>()

    /**
     * Runs one watch pass over [fix]. Safe to call on every fix; most passes do nothing.
     *
     * @return the pace the next fix should come at. Even a fix too rough to decide on still says
     * roughly how far away the mosques are, so it still sets the pace.
     */
    suspend fun onFix(fix: LocationSample): WatchCadence = mutex.withLock {
        val now = System.currentTimeMillis()
        val usable = PresenceRules.isUsable(fix, now)

        val open = attendanceRepository.openVisitMosqueIds()
        var nearestEdge: Double? = null
        mosqueRepository.all().forEach { mosque ->
            if (!mosque.trackingEnabled) {
                outsideSince.remove(mosque.id)
                return@forEach
            }

            val distance = Haversine.distanceMeters(
                fix.latitude,
                fix.longitude,
                mosque.latitude,
                mosque.longitude,
            )
            val edge = distance - DetectionPipeline.zoneRadiusMeters(mosque.geofenceRadius)
            nearestEdge = nearestEdge?.let { minOf(it, edge) } ?: edge
            if (!usable) return@forEach

            // The clock runs from the drawn circle, not from the release margin: a phone parked on
            // the rim is outside the circle too, and that is the case the long wait exists for.
            if (DetectionPipeline.isInsideZone(distance, mosque.geofenceRadius)) {
                outsideSince.remove(mosque.id)
            } else {
                outsideSince.getOrPut(mosque.id) { now }
            }

            val transition = DetectionPipeline.watchTransition(
                distanceMeters = distance,
                configuredRadiusMeters = mosque.geofenceRadius,
                hasOpenVisit = mosque.id in open,
                accuracyMeters = fix.accuracyMeters,
                outsideForMillis = outsideSince[mosque.id]?.let { now - it },
            )
            if (transition == null) return@forEach

            if (now - (lastAttemptAt[mosque.id] ?: 0L) < ATTEMPT_COOLDOWN_MILLIS) return@forEach
            lastAttemptAt[mosque.id] = now
            outsideSince.remove(mosque.id)
            // The fix travels with the request: the reading that decides an arrival is the reading
            // that records it, instead of one taken again a moment later somewhere else.
            VerificationWorker.enqueue(context, mosque.id, transition, TriggerSource.WATCH, fix)
        }
        WatchCadence.choose(hasOpenVisit = open.isNotEmpty(), nearestEdgeMeters = nearestEdge)
    }

    private companion object {
        /** Gap kept between two passes for the same mosque while a decision is still settling. */
        const val ATTEMPT_COOLDOWN_MILLIS = 10_000L
    }
}
