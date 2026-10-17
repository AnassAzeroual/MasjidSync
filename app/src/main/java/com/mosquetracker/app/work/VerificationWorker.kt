package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.detection.PresenceEngine
import com.mosquetracker.app.detection.TriggerSource
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one arrival or departure decision through [PresenceEngine]. Workers are how a short-lived
 * BroadcastReceiver hands off work that needs more than a few milliseconds, and they outlive the
 * screen that noticed a crossing, so a routine is never left half-applied by a closed tab.
 */
@HiltWorker
class VerificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val presenceEngine: PresenceEngine,
    private val notifier: Notifier,
    private val monitoringController: MonitoringController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mosqueId = inputData.getLong(KEY_MOSQUE_ID, INVALID_ID)
        if (mosqueId == INVALID_ID) return Result.failure()

        notifier.ensureChannels()
        val transition = inputData.getInt(KEY_TRANSITION, Geofence.GEOFENCE_TRANSITION_ENTER)
        val source = inputData.getString(KEY_SOURCE)
            ?.let { name -> TriggerSource.entries.firstOrNull { it.name == name } }
            ?: TriggerSource.FENCE

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            presenceEngine.exit(mosqueId, source)
            return Result.success()
        }

        val inside = presenceEngine.enter(mosqueId, readFix())
        // An arrival the app noticed itself has no fence state behind it yet, so re-register and
        // let the platform take over the matching EXIT. Fence-driven arrivals already have it.
        if (inside && source != TriggerSource.FENCE) monitoringController.onMosquesChanged()
        return Result.success()
    }

    /** The fix the trigger was decided on, when it carried one. */
    private fun readFix(): LocationSample? {
        val latitude = inputData.getDouble(KEY_FIX_LAT, NO_FIX)
        val longitude = inputData.getDouble(KEY_FIX_LON, NO_FIX)
        if (latitude == NO_FIX || longitude == NO_FIX) return null
        return LocationSample(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = inputData.getFloat(KEY_FIX_ACCURACY, 0f),
            timestamp = inputData.getLong(KEY_FIX_TIME, 0L),
        )
    }

    companion object {
        private const val KEY_MOSQUE_ID = "mosque_id"
        private const val KEY_TRANSITION = "transition"
        private const val KEY_SOURCE = "source"
        private const val KEY_FIX_LAT = "fix_lat"
        private const val KEY_FIX_LON = "fix_lon"
        private const val KEY_FIX_ACCURACY = "fix_accuracy"
        private const val KEY_FIX_TIME = "fix_time"
        private const val INVALID_ID = -1L

        /** Sentinel for "no fix travelled with this request"; no real latitude looks like this. */
        private const val NO_FIX = -1000.0
        const val TAG = "verification"

        /**
         * Queues a decision for one mosque. Requests for the same mosque run in order, one after
         * the other, so an ENTER and the EXIT right behind it are never decided out of order.
         */
        fun enqueue(
            context: Context,
            mosqueId: Long,
            transition: Int,
            source: TriggerSource,
            fix: LocationSample? = null,
        ) {
            val data = Data.Builder()
                .putLong(KEY_MOSQUE_ID, mosqueId)
                .putInt(KEY_TRANSITION, transition)
                .putString(KEY_SOURCE, source.name)
                .apply {
                    if (fix != null) {
                        putDouble(KEY_FIX_LAT, fix.latitude)
                        putDouble(KEY_FIX_LON, fix.longitude)
                        putFloat(KEY_FIX_ACCURACY, fix.accuracyMeters)
                        putLong(KEY_FIX_TIME, fix.timestamp)
                    }
                }
                .build()

            val request = OneTimeWorkRequestBuilder<VerificationWorker>()
                .setInputData(data)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("$TAG-$mosqueId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
