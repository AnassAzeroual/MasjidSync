package com.mosquetracker.app.detection

import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.data.repository.MosqueRepository
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.model.AlertMode
import com.mosquetracker.app.model.LocationSample
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.notifications.Notifier
import com.mosquetracker.app.routine.RoutineCoordinator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Who asked for a visit decision; it decides how much evidence the decision needs. */
enum class TriggerSource {
    /** The OS geofence. Carries no fix, so one is looked up. */
    FENCE,

    /** The software watch over live fixes. Its exits have already waited out the dwell. */
    WATCH,

    /** The user dragged a saved pin. */
    MOVE,

    /** The user saved a mosque. */
    SAVE,
}

/**
 * Opens and closes visits, then hands the routine to [RoutineCoordinator]. Decisions are taken one
 * at a time, so two triggers for one arrival (fence and watch) cannot both act on a stale view.
 */
@Singleton
class PresenceEngine @Inject constructor(
    private val mosqueRepository: MosqueRepository,
    private val attendanceRepository: AttendanceRepository,
    private val pipeline: DetectionPipeline,
    private val locationProvider: LocationProvider,
    private val coordinator: RoutineCoordinator,
    private val settingsStore: SettingsStore,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()

    /**
     * Opens a visit when a usable fix puts the phone inside the mosque's circle.
     *
     * The fix the trigger was decided on is the evidence when it is still usable: re-measuring with
     * a different reading is how a genuine arrival used to end in silence. Without one (fence
     * events, or a stale carried fix) the cached fix is tried, then a freshly requested one.
     *
     * @return true when the phone is inside and a visit is open.
     */
    suspend fun enter(mosqueId: Long, carried: LocationSample?): Boolean = mutex.withLock {
        // A watch fix or fence event already in flight when detection was switched off.
        if (!settingsStore.isMonitoringEnabled()) return@withLock false
        val mosque = mosqueRepository.getById(mosqueId) ?: return@withLock false
        // A fence can fire once after its mosque was untracked (removal races delivery).
        if (!mosque.trackingEnabled) return@withLock false

        val evidence = carried?.takeIf(::usable)
        var outcome = pipeline.verify(mosqueId, evidence ?: locationProvider.lastKnown()?.takeIf(::usable))
        if (!outcome.confirmed && evidence == null) {
            locationProvider.current()?.takeIf(::usable)?.let { outcome = pipeline.verify(mosqueId, it) }
        }
        if (!outcome.confirmed) return@withLock false

        val entry = attendanceRepository.recordEntry(mosqueId, method = outcome.method)
        if (entry.isNew && RoutineAction.ARRIVAL_NOTICE in settingsStore.getRoutineActions()) {
            notifier.notifyEntry(mosqueId, mosque.name, outcome.method)
        }
        coordinator.reconcile(newVisit = entry.isNew && mosque.alertMode == AlertMode.FULL_SILENT)
        true
    }

    /**
     * Closes the mosque's visit. Fence exits are re-checked first: a fresh, usable fix that puts the
     * phone back inside the circle overrules the platform. Watch exits and pin moves are trusted.
     */
    suspend fun exit(mosqueId: Long, source: TriggerSource): Boolean = mutex.withLock {
        if (!attendanceRepository.hasOpenVisit(mosqueId)) {
            // Nothing to close, but the routine may still be out of step (e.g. a lost exit).
            coordinator.reconcile()
            return@withLock false
        }
        val mosque = mosqueRepository.getById(mosqueId)
        if (source == TriggerSource.FENCE && mosque != null) {
            val distance = locationProvider.current()?.takeIf(::usable)?.let {
                Haversine.distanceMeters(it.latitude, it.longitude, mosque.latitude, mosque.longitude)
            }
            if (!PresenceRules.fenceExitConfirmed(distance, mosque.geofenceRadius)) return@withLock false
        }
        closeVisit(mosqueId, mosque?.name, announce = true)
        true
    }

    /** Ends a visit the user made moot (mosque untracked or removed). No notice is posted. */
    suspend fun endVisit(mosqueId: Long) = mutex.withLock {
        closeVisit(mosqueId, name = null, announce = false)
    }

    /** Ends every visit, for when detection is switched off: nothing would ever end them otherwise. */
    suspend fun endAllVisits() = mutex.withLock {
        attendanceRepository.closeAllOpen()
        coordinator.reconcile()
    }

    private suspend fun closeVisit(mosqueId: Long, name: String?, announce: Boolean) {
        val closed = attendanceRepository.recordExit(mosqueId)
        if (announce && closed > 0 && name != null &&
            RoutineAction.EXIT_NOTICE in settingsStore.getRoutineActions()
        ) {
            notifier.notifyExit(mosqueId, name)
        }
        coordinator.reconcile()
    }

    private fun usable(fix: LocationSample): Boolean =
        PresenceRules.isUsable(fix, System.currentTimeMillis())
}
