package com.mosquetracker.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.work.PrayerScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms geofences and the foreground service after a reboot or app update. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var monitoringController: MonitoringController
    @Inject lateinit var prayerScheduler: PrayerScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                monitoringController.ensureRunning(fromBackground = true)
                prayerScheduler.refresh()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
