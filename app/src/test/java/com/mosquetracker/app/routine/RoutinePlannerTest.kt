package com.mosquetracker.app.routine

import android.media.AudioManager
import com.mosquetracker.app.data.prefs.RoutineSnapshot
import com.mosquetracker.app.model.RoutineAction
import com.mosquetracker.app.model.RoutineAction.ARRIVAL_NOTICE
import com.mosquetracker.app.model.RoutineAction.EXIT_NOTICE
import com.mosquetracker.app.model.RoutineAction.FLASHLIGHT_ON
import com.mosquetracker.app.model.RoutineAction.MUTE_MEDIA
import com.mosquetracker.app.model.RoutineAction.RESTORE_DEVICE
import com.mosquetracker.app.model.RoutineAction.RESTORE_SOUND
import com.mosquetracker.app.model.RoutineAction.SILENT_RINGER
import com.mosquetracker.app.model.RoutineAction.VIBRATE_PULSE
import com.mosquetracker.app.model.RoutineAction.VIBRATE_RINGER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePlannerTest {

    private val armed = setOf(
        SILENT_RINGER,
        MUTE_MEDIA,
        FLASHLIGHT_ON,
        VIBRATE_PULSE,
        ARRIVAL_NOTICE,
        RESTORE_SOUND,
        RESTORE_DEVICE,
        EXIT_NOTICE,
    )

    private fun applied(vararg actions: RoutineAction, dnd: Boolean = true) = RoutineSnapshot(
        actions = actions.mapTo(mutableSetOf()) { it.name },
        ringerMode = AudioManager.RINGER_MODE_NORMAL,
        dndGranted = dnd,
    )

    private fun plan(
        armed: Set<RoutineAction> = this.armed,
        visitOwned: Boolean = false,
        prayerOwned: Boolean = false,
        snapshot: RoutineSnapshot? = null,
        suppressed: Boolean = false,
        dndGranted: Boolean = true,
        newVisit: Boolean = false,
        extraExit: Set<RoutineAction> = emptySet(),
    ) = RoutinePlanner.plan(
        armed = armed,
        visitOwned = visitOwned,
        prayerOwned = prayerOwned,
        snapshot = snapshot,
        suppressed = suppressed,
        dndGranted = dndGranted,
        writeSettingsGranted = false,
        newVisit = newVisit,
        extraExit = extraExit,
    )

    @Test
    fun newVisitAppliesEveryArrivalActionButTheNotice() {
        val result = plan(visitOwned = true, newVisit = true)
        assertEquals(setOf(SILENT_RINGER, MUTE_MEDIA, FLASHLIGHT_ON, VIBRATE_PULSE), result.apply)
        assertTrue(result.restore.isEmpty())
    }

    @Test
    fun repeatedEnterDoesNotReapply() {
        val result = plan(visitOwned = true, snapshot = applied(SILENT_RINGER, MUTE_MEDIA, FLASHLIGHT_ON))
        assertEquals(RoutinePlan(), result)
    }

    @Test
    fun newlyArmedActionIsAppliedWithoutThePulse() {
        val result = plan(visitOwned = true, snapshot = applied(SILENT_RINGER, MUTE_MEDIA))
        assertEquals(setOf(SILENT_RINGER, MUTE_MEDIA, FLASHLIGHT_ON), result.apply)
    }

    @Test
    fun grantingDndMidVisitReapplies() {
        val result = plan(
            visitOwned = true,
            snapshot = applied(SILENT_RINGER, MUTE_MEDIA, FLASHLIGHT_ON, dnd = false),
            dndGranted = true,
        )
        assertTrue(SILENT_RINGER in result.apply)
        assertFalse(VIBRATE_PULSE in result.apply)
    }

    @Test
    fun leavingRestoresWhatTheUserArmed() {
        val result = plan(snapshot = applied(SILENT_RINGER, MUTE_MEDIA))
        assertEquals(setOf(RESTORE_SOUND, RESTORE_DEVICE, EXIT_NOTICE), result.restore)
        assertTrue(result.apply.isEmpty())
    }

    @Test
    fun nothingToRestoreMeansNothingToDo() {
        assertEquals(RoutinePlan(), plan())
        assertEquals(RoutinePlan(), plan(snapshot = RoutineSnapshot()))
    }

    @Test
    fun visitEndingInsidePrayerWindowOnlyLiftsTheDeviceHalf() {
        val result = plan(prayerOwned = true, snapshot = applied(SILENT_RINGER, MUTE_MEDIA, FLASHLIGHT_ON))
        assertEquals(setOf(RESTORE_DEVICE), result.restore)
        assertTrue(result.apply.isEmpty())
    }

    @Test
    fun prayerWindowOnlyAppliesTheSoundProfile() {
        val result = plan(prayerOwned = true)
        assertEquals(setOf(SILENT_RINGER, MUTE_MEDIA), result.apply)
    }

    @Test
    fun prayerEndRestoresSoundEvenWhenTheUserDisarmedIt() {
        val result = plan(
            armed = armed - RESTORE_SOUND,
            snapshot = applied(SILENT_RINGER, MUTE_MEDIA),
            extraExit = setOf(RESTORE_SOUND),
        )
        assertTrue(RESTORE_SOUND in result.restore)
    }

    @Test
    fun suppressionHoldsOffAReapplyWhileInside() {
        assertEquals(RoutinePlan(), plan(visitOwned = true, suppressed = true))
    }

    @Test
    fun aNewVisitClearsSuppression() {
        val result = plan(visitOwned = true, suppressed = true, newVisit = true)
        assertTrue(result.clearSuppression)
        assertTrue(result.apply.isNotEmpty())
    }

    @Test
    fun suppressionClearsOnceEveryOwnerIsGone() {
        assertTrue(plan(suppressed = true).clearSuppression)
    }

    @Test
    fun disarmedRestoreIsNotForcedOnAVisitExit() {
        val result = plan(armed = armed - RESTORE_SOUND, snapshot = applied(SILENT_RINGER))
        assertFalse(RESTORE_SOUND in result.restore)
    }

    @Test
    fun ringTargetSilencesWithDndAccess() {
        assertEquals(
            AudioManager.RINGER_MODE_SILENT,
            RoutineEngine.ringTarget(setOf(SILENT_RINGER), true, AudioManager.RINGER_MODE_NORMAL),
        )
        assertEquals(
            AudioManager.RINGER_MODE_SILENT,
            RoutineEngine.ringTarget(setOf(SILENT_RINGER, VIBRATE_RINGER), true, AudioManager.RINGER_MODE_NORMAL),
        )
    }

    @Test
    fun ringTargetFallsBackToVibrateWithoutDndAccess() {
        assertEquals(
            AudioManager.RINGER_MODE_VIBRATE,
            RoutineEngine.ringTarget(setOf(SILENT_RINGER), false, AudioManager.RINGER_MODE_NORMAL),
        )
    }

    @Test
    fun ringTargetLeavesAnAlreadySilentPhoneAloneWithoutDndAccess() {
        assertNull(RoutineEngine.ringTarget(setOf(SILENT_RINGER), false, AudioManager.RINGER_MODE_SILENT))
    }

    @Test
    fun ringTargetIsNullWithoutARingerAction() {
        assertNull(RoutineEngine.ringTarget(setOf(MUTE_MEDIA), true, AudioManager.RINGER_MODE_NORMAL))
    }
}
