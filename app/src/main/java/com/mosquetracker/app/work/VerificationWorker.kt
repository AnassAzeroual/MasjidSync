package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.notifications.Notifier
import com.mosquetracker.app.routine.RoutineEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the Tier 2/Tier 3 verification for one geofence transition, writes the
 * resulting attendance row and runs the user's routine.
 * Workers are how a short-lived BroadcastReceiver hands off work that needs more
 * than a few milliseconds.
 */
@HiltWorker
class VerificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
    private val pipeline: DetectionPipeline,
    private val locationProvider: LocationProvider,
    private val routineEngine: RoutineEngine,
    private val settingsStore: SettingsStore,
    private val notifier: Notifier,
    private val monitoringController: MonitoringController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mosqueId = inputData.getLong(KEY_MOSQUE_ID, INVALID_ID)
        if (mosqueId == INVALID_ID) return Result.failure()

        notifier.ensureChannels()
        val transition = inputData.getInt(KEY_TRANSITION, Geofence.GEOFENCE_TRANSITION_ENTER)

        return when (transition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> handleExit(mosqueId)
            else -> handleEnter(mosqueId)
        }
    }

    private suspend fun handleEnter(mosqueId: Long): Result {
        val mosque = mosqueRepository.getById(mosqueId) ?: return Result.success()
        // A fence can fire once after its mosque was untracked (removal races delivery).
        if (!mosque.trackingEnabled) return Result.success()

        // The app's own triggers (a dragged pin, the software watch, a fresh save) carry the fix
        // they were decided on, and that fix is the evidence — re-measuring with a different
        // reading is how a genuine arrival used to end in silence. Fence events carry no fix, so
        // they are checked against the cached one, and, when that one says "outside", against a
        // single freshly requested fix: a phone that really walked in has a reading to prove it.
        val carriedFix = readFix()
        var outcome = pipeline.verify(mosqueId, carriedFix ?: locationProvider.lastKnown())
        if (!outcome.confirmed && carriedFix == null) {
            locationProvider.current()?.let { fresh -> outcome = pipeline.verify(mosqueId, fresh) }
        }
        if (!outcome.confirmed) return Result.success()

        val actions = settingsStore.getRoutineActions()
        val entry = attendanceRepository.recordEntry(mosqueId, method = outcome.method)
        // Geofences are re-registered periodically with INITIAL_TRIGGER_ENTER, so ENTER can be
        // delivered again while the same visit is still open. Only announce a brand-new visit.
        if (entry.isNew && RoutineAction.ARRIVAL_NOTICE in actions) {
            notifier.notifyEntry(mosque.name, outcome.method)
        }
        // Notify-only mosques log and announce but never run the routine.
        if (mosque.alertMode == AlertMode.FULL_SILENT &&
            settingsStore.isAutoSilentEnabled()
        ) {
            routineEngine.apply(actions)
        }
        // An arrival the app noticed itself has no fence state behind it yet, so re-register and
        // let the platform take over: that is what reports the matching EXIT when the phone
        // leaves, which is what lifts the routine. Fence-driven arrivals already have it.
        if (carriedFix != null) monitoringController.onMosquesChanged()
        return Result.success()
    }

    private suspend fun handleExit(mosqueId: Long): Result {
        val mosque = mosqueRepository.getById(mosqueId) ?: return Result.success()
        val actions = settingsStore.getRoutineActions()
        val closed = attendanceRepository.recordExit(mosqueId)
        if (closed > 0 && RoutineAction.EXIT_NOTICE in actions) notifier.notifyExit(mosque.name)
        // Only a routine-running mosque may lift the profile: a notify-only exit must never clear
        // another mosque's still-active routine.
        if (mosque.alertMode == AlertMode.FULL_SILENT) routineEngine.revert(actions)
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
        private const val KEY_FIX_LAT = "fix_lat"
        private const val KEY_FIX_LON = "fix_lon"
        private const val KEY_FIX_ACCURACY = "fix_accuracy"
        private const val KEY_FIX_TIME = "fix_time"
        private const val INVALID_ID = -1L

        /** Sentinel for "no fix travelled with this request"; no real latitude looks like this. */
        private const val NO_FIX = -1000.0
        const val TAG = "verification"

        fun enqueue(context: Context, mosqueId: Long, transition: Int, fix: LocationSample? = null) {
            val data = Data.Builder()
                .putLong(KEY_MOSQUE_ID, mosqueId)
                .putInt(KEY_TRANSITION, transition)
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

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
