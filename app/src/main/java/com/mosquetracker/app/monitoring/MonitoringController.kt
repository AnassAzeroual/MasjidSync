package com.mosquetracker.app.monitoring

import android.content.Context
import com.google.android.gms.location.Geofence
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.detection.FenceResult
import com.mosquetracker.app.detection.FenceState
import com.mosquetracker.app.detection.FenceStatus
import com.mosquetracker.app.detection.GeofenceManager
import com.mosquetracker.app.detection.PresenceEngine
import com.mosquetracker.app.detection.TriggerSource
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.routine.RoutineCoordinator
import com.mosquetracker.app.service.DetectionForegroundService
import com.mosquetracker.app.work.MonitoringScheduler
import com.mosquetracker.app.work.VerificationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the background machinery — the foreground service, the registered geofences and the
 * periodic refresh job — and every mosque change that can end a visit or move the routine.
 */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mosqueRepository: MosqueRepository,
    private val geofenceManager: GeofenceManager,
    private val settingsStore: SettingsStore,
    private val scheduler: MonitoringScheduler,
    private val presenceEngine: PresenceEngine,
    private val routineCoordinator: RoutineCoordinator,
    private val locationProvider: LocationProvider,
) {

    /** Which tracked mosques have an OS geofence right now, and why the rest do not. */
    val fenceState: StateFlow<FenceState?> = geofenceManager.state

    /**
     * (Re)registers every *tracked* saved mosque as an OS geofence, nearest first when there are
     * more than the platform allows.
     */
    suspend fun refreshGeofences(): FenceResult {
        val mosques = mosqueRepository.all().filter { it.trackingEnabled }
        return geofenceManager.register(mosques, near = locationProvider.lastKnown())
    }

    /**
     * Switches detection on. [fromBackground] is for callers outside an open screen (the Quick
     * Settings tile), whose service start Android holds to stricter rules.
     */
    suspend fun enableMonitoring(fromBackground: Boolean = false) {
        settingsStore.setMonitoringEnabled(true)
        DetectionForegroundService.start(context, fromBackground)
        scheduler.scheduleRefresh()
    }

    /**
     * Brings the background machinery back when detection is on but the service is not running —
     * after a reboot, an update, the system reclaiming the process, or a start Android refused
     * earlier. A no-op when detection is off.
     */
    suspend fun ensureRunning(fromBackground: Boolean) {
        if (!settingsStore.isMonitoringEnabled()) return
        scheduler.scheduleRefresh()
        if (!DetectionForegroundService.isRunning) {
            DetectionForegroundService.start(context, fromBackground)
        }
    }

    /**
     * Called whenever the app comes to the front or a permission prompt returns: a grant made in
     * system settings brings no callback of its own, so fences refused for want of it are retried
     * here instead of waiting up to 15 minutes for the refresh worker.
     */
    suspend fun recheck() {
        if (!settingsStore.isMonitoringEnabled()) return
        ensureRunning(fromBackground = false)
        when (fenceState.value?.status) {
            FenceStatus.NO_LOCATION_PERMISSION,
            FenceStatus.NO_BACKGROUND_PERMISSION,
            FenceStatus.FAILED,
            -> refreshGeofences()

            else -> Unit
        }
    }

    /**
     * Turns detection off. Open visits are closed and the routine is lifted: with nothing watching
     * any more, nothing would ever end them, and the phone would stay silent indefinitely.
     */
    suspend fun disableMonitoring() {
        settingsStore.setMonitoringEnabled(false)
        DetectionForegroundService.stop(context)
        scheduler.cancel()
        geofenceManager.removeAll()
        presenceEngine.endAllVisits()
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
     * [fix] is the reading that was on screen when the mosque was saved, so the fix that turned
     * the picker's circle red is the one that records the visit.
     */
    suspend fun onMosqueAdded(mosqueId: Long, enableAlerts: Boolean, fix: LocationSample? = null) {
        if (enableAlerts) enableMonitoring()
        if (!settingsStore.isMonitoringEnabled()) return
        refreshGeofences()
        VerificationWorker.enqueue(
            context,
            mosqueId,
            Geofence.GEOFENCE_TRANSITION_ENTER,
            TriggerSource.SAVE,
            fix,
        )
    }

    /** Deletes a mosque. Its visits go with it (cascade), so the routine is re-decided after. */
    suspend fun deleteMosque(mosque: MosqueEntity) {
        mosqueRepository.delete(mosque)
        geofenceManager.remove(mosque.id)
        routineCoordinator.reconcile()
    }

    /**
     * Applies one mosque's tracking switch. An untracked mosque loses its fence and its open visit
     * at once, which lifts the routine it was holding; a re-tracked one rejoins the fence set.
     */
    suspend fun setMosqueTracking(mosqueId: Long, enabled: Boolean) {
        mosqueRepository.setTrackingEnabled(mosqueId, enabled)
        if (enabled) {
            onMosquesChanged()
        } else {
            geofenceManager.remove(mosqueId)
            presenceEngine.endVisit(mosqueId)
        }
    }

    /** Switches full-silent / notify-only; a visit in progress gains or loses the routine now. */
    suspend fun setAlertMode(mosqueId: Long, mode: AlertMode) {
        mosqueRepository.setAlertMode(mosqueId, mode)
        routineCoordinator.reconcile()
    }
}
