package com.mosquetracker.app.detection

import android.content.Context
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.work.VerificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Software watch over the live GPS stream — the tier that notices the zone crossings the OS fence
 * misses.
 *
 * The fence is cheap but event-driven: it reports the crossings the platform believes in, and each
 * report is verified against a single fix. A phone standing on the rim of a drawn circle can have
 * its arrival rejected once and then never hear about that mosque again for the whole visit (see
 * [DetectionPipeline.watchTransition]). This watch instead runs every fix the app is already
 * collecting past the saved mosques, so the routine follows the same readings that paint the map's
 * circle red. Crossings that happen where nobody is watching stay the fence's job.
 *
 * Enqueuing is what costs, not checking, so a mosque whose pass has just been asked for is left
 * alone until the cooldown window has passed.
 */
@Singleton
class ZoneWatch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
) {
    private val lastAttemptAt = mutableMapOf<Long, Long>()

    /** Runs one watch pass over [fix]. Safe to call on every fix; most passes do nothing. */
    suspend fun onFix(fix: LocationSample) {
        val now = System.currentTimeMillis()
        mosqueRepository.all().forEach { mosque ->
            if (!mosque.trackingEnabled) return@forEach

            val distance = Haversine.distanceMeters(
                fix.latitude,
                fix.longitude,
                mosque.latitude,
                mosque.longitude,
            )
            val transition = DetectionPipeline.watchTransition(
                distanceMeters = distance,
                configuredRadiusMeters = mosque.geofenceRadius,
                hasOpenVisit = attendanceRepository.hasOpenVisit(mosque.id),
            ) ?: return@forEach

            if (now - (lastAttemptAt[mosque.id] ?: 0L) < ATTEMPT_COOLDOWN_MILLIS) return@forEach
            lastAttemptAt[mosque.id] = now
            // The fix travels with the request: the reading that decides an arrival is the reading
            // that records it, instead of one taken again a moment later somewhere else.
            VerificationWorker.enqueue(context, mosque.id, transition, fix)
        }
    }

    private companion object {
        /** Gap kept between two passes for the same mosque while a decision is still settling. */
        const val ATTEMPT_COOLDOWN_MILLIS = 10_000L
    }
}
