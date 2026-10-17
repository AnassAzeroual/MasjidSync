package com.mosquetracker.app.monitoring

import android.content.Context
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.GeofenceManager
import com.mosquetracker.app.service.DetectionForegroundService
import com.mosquetracker.app.work.MonitoringScheduler
import com.mosquetracker.app.work.VerificationWorker
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

    /** (Re)registers every *tracked* saved mosque as an OS geofence. Returns the count. */
    suspend fun refreshGeofences(): Int {
        val mosques = mosqueRepository.all().filter { it.trackingEnabled }
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

    /**
     * Called right after the user saves a mosque from the map picker.
     *
     * People normally save the mosque they are standing in, so instead of waiting for the OS
     * geofence to round-trip we run the entry check once now — the user gets an immediate answer.
     * The check itself is the same pipeline the background path uses, so it cannot produce a
     * result the background path would not.
     */
    suspend fun onMosqueAdded(mosqueId: Long, enableAlerts: Boolean) {
        if (enableAlerts) enableMonitoring()
        if (!settingsStore.isMonitoringEnabled()) return
        refreshGeofences()
        VerificationWorker.enqueue(context, mosqueId, Geofence.GEOFENCE_TRANSITION_ENTER)
    }

    suspend fun removeGeofence(mosqueId: Long) {
        geofenceManager.remove(mosqueId)
    }

    /**
     * Applies one mosque's tracking switch: untracked mosques lose their fence immediately,
     * re-tracked ones rejoin the registered set on the next refresh.
     */
    suspend fun setMosqueTracking(mosqueId: Long, enabled: Boolean) {
        mosqueRepository.setTrackingEnabled(mosqueId, enabled)
        if (settingsStore.isMonitoringEnabled()) refreshGeofences()
    }
}
