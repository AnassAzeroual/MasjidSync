package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.audio.RingerController
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.DetectionPipeline
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.notifications.Notifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the Tier 2/Tier 3 verification for one geofence transition and writes the
 * resulting attendance row. Workers are how a short-lived BroadcastReceiver hands
 * off work that needs more than a few milliseconds.
 */
@HiltWorker
class VerificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
    private val pipeline: DetectionPipeline,
    private val locationProvider: LocationProvider,
    private val ringerController: RingerController,
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
        val outcome = pipeline.verify(mosqueId, locationProvider.lastKnown())
        if (!outcome.confirmed) return Result.success()

        attendanceRepository.recordEntry(mosqueId, method = outcome.method)
        notifier.notifyEntry(mosque.name, outcome.method)
        if (settingsStore.isAutoSilentEnabled()) ringerController.silence()
        return Result.success()
    }

    private suspend fun handleExit(mosqueId: Long): Result {
        val mosque = mosqueRepository.getById(mosqueId) ?: return Result.success()
        val closed = attendanceRepository.recordExit(mosqueId)
        if (closed > 0) notifier.notifyExit(mosque.name)
        ringerController.restore()
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
