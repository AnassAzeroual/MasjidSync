package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.monitoring.MonitoringController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically re-registers geofences so they survive OS cleanup, and revives the foreground
 * service if the system reclaimed it. Detection switched off means this job has no business
 * running, so it cancels itself.
 */
@HiltWorker
class GeofenceRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val monitoringController: MonitoringController,
    private val settingsStore: SettingsStore,
    private val scheduler: MonitoringScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!settingsStore.isMonitoringEnabled()) {
            scheduler.cancel()
            return Result.success()
        }
        monitoringController.refreshGeofences()
        monitoringController.ensureRunning(fromBackground = true)
        return Result.success()
    }
}

@Singleton
class MonitoringScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleRefresh() {
        val request = PeriodicWorkRequestBuilder<GeofenceRefreshWorker>(15, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(TAG)
    }

    companion object {
        private const val TAG = "geofence-refresh"
    }
}
