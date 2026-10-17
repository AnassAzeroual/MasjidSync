package com.mosquetracker.app.routine

import com.mosquetracker.app.data.prefs.RoutineSnapshot
import com.mosquetracker.app.model.ActionGroup
import com.mosquetracker.app.model.ActionPhase
import com.mosquetracker.app.model.ActionRequirement
import com.mosquetracker.app.model.RoutineAction

/** What the routine has to do right now to match who wants it. Empty sets mean "nothing". */
data class RoutinePlan(
    /** On-arrival actions to apply. */
    val apply: Set<RoutineAction> = emptySet(),
    /** On-exit actions to run before applying anything. */
    val restore: Set<RoutineAction> = emptySet(),
    /** True when the "Restore now" hold can be dropped. */
    val clearSuppression: Boolean = false,
)

/**
 * Decides the routine from its owners instead of from the last event that happened to arrive.
 *
 * Two things can own the routine: an open visit at a tracked, full-silent mosque, and a prayer
 * window. Each event (arrival, exit, untrack, delete, toggle, prayer start/end) asks for a plan
 * and the plan only ever moves the phone toward "what the current owners want". That is what lets
 * a visit end without lifting a prayer window's silence, a notify-only exit never touch another
 * mosque's routine, and a repeated ENTER never re-mute volumes the user raised by hand.
 */
object RoutinePlanner {

    /** Device-side state an apply can change, lifted by [RoutineAction.RESTORE_DEVICE]. */
    private val DEVICE_STATE: Set<RoutineAction> = setOf(
        RoutineAction.FLASHLIGHT_ON,
        RoutineAction.MIN_BRIGHTNESS,
        RoutineAction.SHORT_SCREEN_TIMEOUT,
        RoutineAction.LOCK_ROTATION,
    )

    /** One-shots: they run with a brand-new visit and are never "missing" afterwards. */
    private val ONE_SHOTS: Set<RoutineAction> = setOf(
        RoutineAction.VIBRATE_PULSE,
        RoutineAction.ARRIVAL_NOTICE,
    )

    /**
     * @param armed the user's armed actions (both phases).
     * @param visitOwned an open visit at a tracked, full-silent mosque wants the routine.
     * @param prayerOwned a prayer-silence window is open.
     * @param newVisit the event that asked for this plan opened a new full-silent visit.
     * @param extraExit exit actions the caller insists on (a prayer window always restores sound).
     */
    fun plan(
        armed: Set<RoutineAction>,
        visitOwned: Boolean,
        prayerOwned: Boolean,
        snapshot: RoutineSnapshot?,
        suppressed: Boolean,
        dndGranted: Boolean,
        writeSettingsGranted: Boolean,
        newVisit: Boolean = false,
        extraExit: Set<RoutineAction> = emptySet(),
    ): RoutinePlan {
        val arrival = armed.filterTo(mutableSetOf()) {
            it.phase == ActionPhase.ON_ARRIVAL && it != RoutineAction.ARRIVAL_NOTICE
        }
        val desired: Set<RoutineAction> = when {
            visitOwned -> arrival
            prayerOwned -> arrival.intersect(RoutineAction.SOUND_PROFILE)
            else -> emptySet()
        }
        val exit = armed.filterTo(mutableSetOf()) { it.phase == ActionPhase.ON_EXIT } + extraExit
        val owned = visitOwned || prayerOwned

        if (desired.isEmpty()) {
            return RoutinePlan(
                restore = if (snapshot?.hasPendingRestore() == true) exit else emptySet(),
                clearSuppression = suppressed && !owned,
            )
        }

        // The user put everything back by hand while still inside; only a new visit re-arms.
        if (suppressed && !newVisit) return RoutinePlan()

        val applied = snapshot?.actions.orEmpty()
        val stateful = desired - ONE_SHOTS
        val missing = stateful.any { it.name !in applied }
        val accessGained = snapshot != null && (
            (dndGranted && !snapshot.dndGranted && desired.any { it.requirement == ActionRequirement.DND_ACCESS }) ||
                (
                    writeSettingsGranted && !snapshot.writeSettingsGranted &&
                        desired.any { it.requirement == ActionRequirement.WRITE_SETTINGS }
                    )
            )
        val needsApply = snapshot == null || missing || accessGained || newVisit

        // Parts the routine applied that nobody wants any more (a visit ended inside a prayer window).
        val restore = mutableSetOf<RoutineAction>()
        val appliedActions = RoutineAction.entries.filterTo(mutableSetOf()) { it.name in applied }
        if (appliedActions.any { it in DEVICE_STATE } && desired.none { it in DEVICE_STATE } &&
            RoutineAction.RESTORE_DEVICE in exit
        ) {
            restore += RoutineAction.RESTORE_DEVICE
        }
        if (appliedActions.any { it.isSound() } && desired.none { it.isSound() } &&
            RoutineAction.RESTORE_SOUND in exit
        ) {
            restore += RoutineAction.RESTORE_SOUND
        }

        return RoutinePlan(
            apply = if (needsApply) {
                if (newVisit) desired else desired - RoutineAction.VIBRATE_PULSE
            } else {
                emptySet()
            },
            restore = restore,
            clearSuppression = suppressed && newVisit,
        )
    }

    private fun RoutineAction.isSound(): Boolean =
        group == ActionGroup.SOUND || group == ActionGroup.FOCUS
}
