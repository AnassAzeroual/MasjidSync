package com.mosquetracker.app.routine

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.mosquetracker.app.data.prefs.RoutineSnapshot
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.model.ActionPhase
import com.mosquetracker.app.model.RoutineAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the user's routine: applies the armed on-arrival actions, then puts every changed
 * setting back on exit. The pre-arrival state is snapshotted the first time and kept in
 * DataStore, so a visit that outlives the process still restores cleanly.
 *
 * Each action is best-effort: an action whose special access was revoked, or that the OEM
 * refuses, is skipped and simply left out of the snapshot.
 */
@Singleton
class RoutineEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    /** Do Not Disturb access — required by every ring-mode and interruption-filter change. */
    val dndAccessGranted: Boolean
        get() = notificationManager?.isNotificationPolicyAccessGranted == true

    /** "Modify system settings" — required by brightness, timeout and rotation changes. */
    val writeSettingsGranted: Boolean
        get() = Settings.System.canWrite(context)

    /** True while the routine has changed something that has not been put back yet. */
    suspend fun isActive(): Boolean =
        settingsStore.readRoutineSnapshot()?.hasPendingRestore() == true

    /**
     * Applies [actions]' on-arrival half. The snapshot is taken once: a repeated ENTER while the
     * user is still inside must not overwrite the real pre-arrival state with the already-changed one.
     *
     * @return true when at least one action actually landed.
     */
    suspend fun apply(actions: Set<RoutineAction>): Boolean {
        val wanted = actions.filterTo(mutableSetOf()) { it.phase == ActionPhase.ON_ARRIVAL }
        if (wanted.isEmpty()) return false

        val existing = settingsStore.readRoutineSnapshot()
        val fresh = captureSnapshot(wanted)
        // A later arrival — or an access grant since the last one — can capture what the first
        // attempt could not, so the snapshot never stays permanently incomplete.
        val snapshot = existing?.fillGaps(
            fresh = fresh,
            skipSystemVolume = existing.actions.any(RoutineAction.RINGER_ACTION_NAMES::contains),
        ) ?: fresh
        settingsStore.writeRoutineSnapshot(snapshot.copy(actions = wanted.mapTo(mutableSetOf()) { it.name }))

        val changed = applyOnArrival(wanted)
        // Nothing landed (all access revoked, or no hardware): do not leave a false snapshot behind.
        if (!changed && existing == null) settingsStore.clearRoutineSnapshot()
        return changed
    }

    /**
     * Runs [actions]' on-exit half. Only the parts the user left enabled are restored; anything
     * still pending stays in the snapshot so [restoreNow] can finish the job later.
     */
    suspend fun revert(actions: Set<RoutineAction>): Boolean {
        val snapshot = settingsStore.readRoutineSnapshot() ?: return false
        val wanted = actions.filterTo(mutableSetOf()) { it.phase == ActionPhase.ON_EXIT }
        var remaining = snapshot

        if (RoutineAction.RESTORE_SOUND in wanted) {
            restoreSound(snapshot)
            remaining = remaining.copy(
                ringerMode = RoutineSnapshot.UNSET,
                musicVolume = RoutineSnapshot.UNSET,
                alarmVolume = RoutineSnapshot.UNSET,
                notificationVolume = RoutineSnapshot.UNSET,
                systemVolume = RoutineSnapshot.UNSET,
                dndFilter = RoutineSnapshot.UNSET,
            )
        }
        if (RoutineAction.RESTORE_DEVICE in wanted) {
            restoreDevice(snapshot)
            remaining = remaining.copy(
                brightness = RoutineSnapshot.UNSET,
                brightnessMode = RoutineSnapshot.UNSET,
                screenTimeout = RoutineSnapshot.UNSET,
                autoRotate = RoutineSnapshot.UNSET,
                flashlight = false,
            )
        }

        if (remaining.hasPendingRestore()) {
            settingsStore.writeRoutineSnapshot(remaining)
        } else {
            settingsStore.clearRoutineSnapshot()
        }
        return true
    }

    /** Force-restores everything still pending, ignoring the exit toggles. The safety valve. */
    suspend fun restoreNow(): Boolean {
        val snapshot = settingsStore.readRoutineSnapshot() ?: return false
        restoreSound(snapshot)
        restoreDevice(snapshot)
        settingsStore.clearRoutineSnapshot()
        return true
    }

    // ---- On arrival -------------------------------------------------------

    private suspend fun applyOnArrival(wanted: Set<RoutineAction>): Boolean {
        var changed = false

        val audio = audioManager

        // On this platform STREAM_SYSTEM is one of the streams the ring mode governs, so writing
        // it re-derives the ring mode from the volume policy a moment later and can quietly undo
        // a silent ringer. When a ring-mode action is armed it already mutes those streams, so the
        // separate write is both redundant and harmful — the ring mode stays the last word.
        val ringModeArmed = RoutineAction.RINGER_ACTIONS.any { it in wanted } && dndAccessGranted

        // Volumes first: they are the cheap, order-independent part.
        if (audio != null) {
            mute(audio, wanted, RoutineAction.MUTE_MEDIA, AudioManager.STREAM_MUSIC) { changed = true }
            mute(audio, wanted, RoutineAction.MUTE_ALARM, AudioManager.STREAM_ALARM) { changed = true }
            mute(audio, wanted, RoutineAction.MUTE_NOTIFICATION, AudioManager.STREAM_NOTIFICATION) { changed = true }
            if (!ringModeArmed) {
                mute(audio, wanted, RoutineAction.MUTE_SYSTEM, AudioManager.STREAM_SYSTEM) { changed = true }
            }
        }

        if (dndAccessGranted) {
            when {
                RoutineAction.DND_TOTAL_SILENCE in wanted ->
                    runCatching {
                        notificationManager?.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_NONE,
                        )
                    }.onSuccess { changed = true }

                RoutineAction.DND_ALARMS_ONLY in wanted ->
                    runCatching {
                        notificationManager?.setInterruptionFilter(
                            NotificationManager.INTERRUPTION_FILTER_ALARMS,
                        )
                    }.onSuccess { changed = true }
            }
        }

        if (RoutineAction.FLASHLIGHT_ON in wanted) {
            runCatching { setTorch(true) }.onSuccess { changed = true }
        }
        if (RoutineAction.VIBRATE_PULSE in wanted) {
            runCatching { pulse() }.onSuccess { changed = true }
        }

        if (writeSettingsGranted) {
            if (RoutineAction.MIN_BRIGHTNESS in wanted) {
                runCatching {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        MIN_BRIGHTNESS,
                    )
                }.onSuccess { changed = true }
            }
            if (RoutineAction.SHORT_SCREEN_TIMEOUT in wanted) {
                runCatching {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        SHORT_TIMEOUT_MILLIS,
                    )
                }.onSuccess { changed = true }
            }
            if (RoutineAction.LOCK_ROTATION in wanted) {
                runCatching {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.ACCELEROMETER_ROTATION,
                        ROTATION_OFF,
                    )
                }.onSuccess { changed = true }
            }
        }

        // The ring mode goes on last and is re-asserted after a short settle gap. Writing a stream
        // the platform aliases onto the ring volume — STREAM_SYSTEM is aliased to STREAM_RING on
        // several builds — makes AudioService re-derive the ring mode from its volume policy a
        // moment later, which would quietly undo a silent ringer. Setting it here, then checking
        // again after the platform has settled, keeps the user's choice the one that sticks.
        if (audio != null && dndAccessGranted) {
            // Silent beats vibrate when the user arms both.
            val target = when {
                RoutineAction.SILENT_RINGER in wanted -> AudioManager.RINGER_MODE_SILENT
                RoutineAction.VIBRATE_RINGER in wanted -> AudioManager.RINGER_MODE_VIBRATE
                else -> null
            }
            if (target != null && setRingModeAndSettle(audio, target)) changed = true
        }

        return changed
    }

    /**
     * Writes [target] to the ring mode until it survives the platform's own bookkeeping.
     * Every attempt but the last is followed by a settle gap and a re-read, so an override that
     * lands just after the write is caught and corrected.
     */
    private suspend fun setRingModeAndSettle(audio: AudioManager, target: Int): Boolean {
        var accepted = false
        for (attempt in 0 until RING_MODE_ATTEMPTS) {
            if (runCatching { audio.ringerMode = target }.isSuccess) accepted = true
            if (audio.ringerMode != target) continue
            delay(RING_MODE_SETTLE_MILLIS)
            if (audio.ringerMode == target) return true
        }
        return accepted
    }

    private fun mute(
        audio: AudioManager,
        wanted: Set<RoutineAction>,
        action: RoutineAction,
        stream: Int,
        onSuccess: () -> Unit,
    ) {
        if (action !in wanted) return
        runCatching { audio.setStreamVolume(stream, 0, 0) }.onSuccess { onSuccess() }
    }

    // ---- Restore ----------------------------------------------------------

    private suspend fun restoreSound(snapshot: RoutineSnapshot) {
        // Mirror of apply: volumes first, ring mode last, for the same STREAM_SYSTEM alias reason.
        audioManager?.let { audio ->
            restoreVolume(audio, AudioManager.STREAM_MUSIC, snapshot.musicVolume)
            restoreVolume(audio, AudioManager.STREAM_ALARM, snapshot.alarmVolume)
            restoreVolume(audio, AudioManager.STREAM_NOTIFICATION, snapshot.notificationVolume)
            restoreVolume(audio, AudioManager.STREAM_SYSTEM, snapshot.systemVolume)
            if (snapshot.ringerMode != RoutineSnapshot.UNSET) {
                setRingModeAndSettle(audio, snapshot.ringerMode)
            }
        }
        if (snapshot.dndFilter != RoutineSnapshot.UNSET) restoreDndFilter(snapshot.dndFilter)
    }

    /**
     * Maps the stored interruption filter back onto a known constant. Restoring is a no-op for
     * anything else, which keeps a hand-edited DataStore file from reaching the system API.
     */
    private fun restoreDndFilter(stored: Int) {
        val filter = when (stored) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            NotificationManager.INTERRUPTION_FILTER_NONE -> NotificationManager.INTERRUPTION_FILTER_NONE
            else -> return
        }
        runCatching { notificationManager?.setInterruptionFilter(filter) }
    }

    private fun restoreVolume(audio: AudioManager, stream: Int, volume: Int) {
        if (volume == RoutineSnapshot.UNSET) return
        runCatching { audio.setStreamVolume(stream, volume, 0) }
    }

    private fun restoreDevice(snapshot: RoutineSnapshot) {
        if (writeSettingsGranted) {
            putSystemInt(Settings.System.SCREEN_BRIGHTNESS, snapshot.brightness)
            putSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE, snapshot.brightnessMode)
            putSystemInt(Settings.System.SCREEN_OFF_TIMEOUT, snapshot.screenTimeout)
            putSystemInt(Settings.System.ACCELEROMETER_ROTATION, snapshot.autoRotate)
        }
        if (snapshot.flashlight) runCatching { setTorch(false) }
    }

    private fun putSystemInt(key: String, value: Int) {
        if (value == RoutineSnapshot.UNSET) return
        runCatching { Settings.System.putInt(contentResolver, key, value) }
    }

    // ---- Snapshot ---------------------------------------------------------

    private fun captureSnapshot(actions: Set<RoutineAction>): RoutineSnapshot {
        val audio = audioManager
        return RoutineSnapshot(
            ringerMode = if (RoutineAction.RINGER_ACTIONS.any { it in actions } && dndAccessGranted) {
                audio?.ringerMode ?: RoutineSnapshot.UNSET
            } else {
                RoutineSnapshot.UNSET
            },
            musicVolume = captureVolume(actions, RoutineAction.MUTE_MEDIA, AudioManager.STREAM_MUSIC),
            alarmVolume = captureVolume(actions, RoutineAction.MUTE_ALARM, AudioManager.STREAM_ALARM),
            notificationVolume = captureVolume(
                actions,
                RoutineAction.MUTE_NOTIFICATION,
                AudioManager.STREAM_NOTIFICATION,
            ),
            // Skipped for the same reason the apply skips it: the ring-mode action owns this stream.
            systemVolume = if (RoutineAction.RINGER_ACTIONS.any { it in actions } && dndAccessGranted) {
                RoutineSnapshot.UNSET
            } else {
                captureVolume(actions, RoutineAction.MUTE_SYSTEM, AudioManager.STREAM_SYSTEM)
            },
            dndFilter = if (RoutineAction.DND_ACTIONS.any { it in actions } && dndAccessGranted) {
                notificationManager?.currentInterruptionFilter ?: RoutineSnapshot.UNSET
            } else {
                RoutineSnapshot.UNSET
            },
            brightness = if (RoutineAction.MIN_BRIGHTNESS in actions && writeSettingsGranted) {
                systemInt(Settings.System.SCREEN_BRIGHTNESS)
            } else {
                RoutineSnapshot.UNSET
            },
            brightnessMode = if (RoutineAction.MIN_BRIGHTNESS in actions && writeSettingsGranted) {
                systemInt(Settings.System.SCREEN_BRIGHTNESS_MODE)
            } else {
                RoutineSnapshot.UNSET
            },
            screenTimeout = if (RoutineAction.SHORT_SCREEN_TIMEOUT in actions && writeSettingsGranted) {
                systemInt(Settings.System.SCREEN_OFF_TIMEOUT)
            } else {
                RoutineSnapshot.UNSET
            },
            autoRotate = if (RoutineAction.LOCK_ROTATION in actions && writeSettingsGranted) {
                systemInt(Settings.System.ACCELEROMETER_ROTATION)
            } else {
                RoutineSnapshot.UNSET
            },
            flashlight = RoutineAction.FLASHLIGHT_ON in actions,
        )
    }

    private fun captureVolume(
        actions: Set<RoutineAction>,
        action: RoutineAction,
        stream: Int,
    ): Int =
        if (action in actions) {
            audioManager?.getStreamVolume(stream) ?: RoutineSnapshot.UNSET
        } else {
            RoutineSnapshot.UNSET
        }

    private fun systemInt(key: String): Int =
        runCatching { Settings.System.getInt(contentResolver, key) }.getOrDefault(RoutineSnapshot.UNSET)

    // ---- Device helpers ---------------------------------------------------

    private fun setTorch(on: Boolean) {
        val manager = cameraManager ?: return
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            runCatching {
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }.getOrDefault(false)
        } ?: return
        manager.setTorchMode(cameraId, on)
    }

    private fun pulse() {
        val vibrator = vibrator() ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(PULSE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object {
        /** Very dim, but never fully black — the user must still be able to read the screen. */
        const val MIN_BRIGHTNESS = 24
        const val SHORT_TIMEOUT_MILLIS = 15_000
        const val ROTATION_OFF = 0
        const val PULSE_MILLIS = 400L

        /** How many times the ring mode is written before giving up on it sticking. */
        const val RING_MODE_ATTEMPTS = 3

        /** Gap allowed for the platform to re-derive the ring mode before it is checked again. */
        const val RING_MODE_SETTLE_MILLIS = 250L
    }
}
