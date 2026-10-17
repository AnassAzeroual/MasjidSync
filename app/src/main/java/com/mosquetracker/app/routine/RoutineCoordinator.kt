package com.mosquetracker.app.routine

import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.data.repository.AttendanceRepository
import com.mosquetracker.app.model.RoutineAction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single door to the routine. Every event that could change what the phone should be doing
 * calls [reconcile], which reads who currently owns the routine and moves the phone there through
 * [RoutineEngine]; see [RoutinePlanner] for the rules. Calls are serialized, so an arrival and an
 * exit landing together cannot interleave their snapshot reads and writes.
 */
@Singleton
class RoutineCoordinator @Inject constructor(
    private val engine: RoutineEngine,
    private val settingsStore: SettingsStore,
    private val attendanceRepository: AttendanceRepository,
) {
    private val mutex = Mutex()

    /**
     * @param newVisit the caller just opened a visit at a full-silent mosque.
     * @param extraExit exit actions to run even if the user disarmed them (prayer end → sound).
     */
    suspend fun reconcile(
        newVisit: Boolean = false,
        extraExit: Set<RoutineAction> = emptySet(),
    ) = mutex.withLock {
        val plan = RoutinePlanner.plan(
            armed = settingsStore.getRoutineActions(),
            visitOwned = visitOwned(),
            prayerOwned = prayerOwned(),
            snapshot = settingsStore.readRoutineSnapshot(),
            suppressed = settingsStore.isRoutineSuppressed(),
            dndGranted = engine.dndAccessGranted,
            writeSettingsGranted = engine.writeSettingsGranted,
            newVisit = newVisit,
            extraExit = extraExit,
        )
        if (plan.restore.isNotEmpty()) engine.revert(plan.restore)
        if (plan.apply.isNotEmpty()) engine.apply(plan.apply)
        if (plan.clearSuppression) settingsStore.setRoutineSuppressed(false)
    }

    /**
     * Puts everything back now. While something still wants the routine, it is held off until a
     * new visit starts or every owner is gone, so the next trigger does not undo the user's tap.
     */
    suspend fun restoreNow() = mutex.withLock {
        engine.restoreNow()
        if (visitOwned() || prayerOwned()) settingsStore.setRoutineSuppressed(true)
    }

    private suspend fun visitOwned(): Boolean =
        settingsStore.isAutoSilentEnabled() && attendanceRepository.hasOpenRoutineVisit()

    private suspend fun prayerOwned(): Boolean =
        settingsStore.isAutoSilentEnabled() &&
            settingsStore.isPrayerSilentEnabled() &&
            System.currentTimeMillis() < settingsStore.prayerWindowEnd()
}
