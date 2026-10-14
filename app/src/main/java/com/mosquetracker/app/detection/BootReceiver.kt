package com.mosquetracker.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.service.DetectionForegroundService
import com.mosquetracker.app.work.MonitoringScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms geofences and the foreground service after a reboot or app update. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var scheduler: MonitoringScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (settingsStore.isMonitoringEnabled()) {
                    DetectionForegroundService.start(context)
                    scheduler.scheduleRefresh()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
