package com.mosquetracker.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.notifications.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps mosque detection alive: it shows the persistent
 * "detection active" notification and arms the geofences.
 */
@AndroidEntryPoint
class DetectionForegroundService : Service() {

    @Inject lateinit var monitoringController: MonitoringController
    @Inject lateinit var notifier: Notifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifier.FOREGROUND_ID,
            notifier.foregroundNotification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        scope.launch {
            val count = monitoringController.refreshGeofences()
            notifier.updateForeground(count)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DetectionForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DetectionForegroundService::class.java))
        }
    }
}
