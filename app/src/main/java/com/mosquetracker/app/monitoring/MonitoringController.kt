package com.mosquetracker.app.monitoring

import android.content.Context
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.GeofenceManager
import com.mosquetracker.app.service.DetectionForegroundService
import com.mosquetracker.app.work.MonitoringScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the background machinery: the foreground service, the registered
 * geofences and the periodic refresh job.
 */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mosqueRepository: MosqueRepository,
    private val geofenceManager: GeofenceManager,
    private val settingsStore: SettingsStore,
    private val scheduler: MonitoringScheduler,
) {

    /** (Re)registers every saved mosque as an OS geofence. Returns the count. */
    suspend fun refreshGeofences(): Int {
        val mosques = mosqueRepository.all()
        geofenceManager.register(mosques)
        return mosques.size
    }

    suspend fun enableMonitoring() {
        settingsStore.setMonitoringEnabled(true)
        DetectionForegroundService.start(context)
        scheduler.scheduleRefresh()
    }

    suspend fun disableMonitoring() {
        settingsStore.setMonitoringEnabled(false)
        DetectionForegroundService.stop(context)
        scheduler.cancel()
        geofenceManager.removeAll()
    }

    /** Called after a mosque is added or edited so its fence exists immediately. */
    suspend fun onMosquesChanged() {
        if (settingsStore.isMonitoringEnabled()) refreshGeofences()
    }

    suspend fun removeGeofence(mosqueId: Long) {
        geofenceManager.remove(mosqueId)
    }
}
