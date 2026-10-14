package com.mosquetracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mosquetracker.app.monitoring.MonitoringController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Periodically re-registers geofences so they survive OS cleanup. */
@HiltWorker
class GeofenceRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val monitoringController: MonitoringController,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        monitoringController.refreshGeofences()
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
