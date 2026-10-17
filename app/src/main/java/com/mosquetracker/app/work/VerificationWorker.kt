package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.RoutineAction
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
        val outcome = pipeline.verify(mosqueId, locationProvider.lastKnown())
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

    companion object {
        private const val KEY_MOSQUE_ID = "mosque_id"
        private const val KEY_TRANSITION = "transition"
        private const val INVALID_ID = -1L
        const val TAG = "verification"

        fun enqueue(context: Context, mosqueId: Long, transition: Int) {
            val request = OneTimeWorkRequestBuilder<VerificationWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MOSQUE_ID to mosqueId,
                        KEY_TRANSITION to transition,
                    ),
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
