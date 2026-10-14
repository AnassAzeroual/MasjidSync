package com.mosquetracker.app.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.mosquetracker.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Switches the phone to silent on entry and restores the previous ringer mode
 * on exit. Requires the ACCESS_NOTIFICATION_POLICY special permission.
 */
@Singleton
class RingerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val notificationManager: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    fun canControlRinger(): Boolean =
        notificationManager?.isNotificationPolicyAccessGranted == true

    /** @return true when the ringer was actually silenced. */
    suspend fun silence(): Boolean {
        val audio = audioManager ?: return false
        if (!canControlRinger()) return false

        return runCatching {
            val current = audio.ringerMode
            if (current != AudioManager.RINGER_MODE_SILENT) {
                settingsStore.setPreviousRingerMode(current)
            }
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            settingsStore.setSilentApplied(true)
            true
        }.getOrDefault(false)
    }

    /** @return true when a previously applied silent mode was reverted. */
    suspend fun restore(): Boolean {
        val audio = audioManager ?: return false
        if (!settingsStore.isSilentApplied()) return false
        if (!canControlRinger()) return false

        return runCatching {
            val previous = settingsStore.previousRingerMode()
            if (previous >= 0) audio.ringerMode = previous
            settingsStore.setSilentApplied(false)
            true
        }.getOrDefault(false)
    }
}
