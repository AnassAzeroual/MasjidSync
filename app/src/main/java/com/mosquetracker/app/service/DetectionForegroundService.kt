package com.mosquetracker.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.detection.WatchCadence
import com.mosquetracker.app.detection.ZoneWatch
import com.mosquetracker.app.location.LocationPermissions
import com.mosquetracker.app.location.LocationProvider
import com.mosquetracker.app.monitoring.MonitoringController
import com.mosquetracker.app.notifications.Notifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps mosque detection alive while the app is in the background.
 *
 * It arms the OS geofences and runs the software watch ([ZoneWatch]) over its own location stream,
 * so arrivals and departures are noticed with the screen off, not only while the map is open. The
 * stream's pace follows [WatchCadence]: brisk GPS near a circle, a cheap network fix far from all.
 */
@AndroidEntryPoint
class DetectionForegroundService : Service() {

    @Inject lateinit var monitoringController: MonitoringController
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var zoneWatch: ZoneWatch

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var setupJob: Job? = null
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!promote()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!LocationPermissions.hasForeground(this)) {
            // Location was revoked since the start was requested; a location service is moot.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        isRunning = true

        setupJob?.cancel()
        setupJob = scope.launch {
            // A sticky restart can arrive after the user switched detection off.
            if (!settingsStore.isMonitoringEnabled()) {
                stopSelf()
                return@launch
            }
            val fences = monitoringController.refreshGeofences()
            notifier.updateForeground(fences.tracked)
        }
        if (watchJob?.isActive != true) startWatch()

        return START_STICKY
    }

    /**
     * Each fix both feeds the watch and sets the pace of the next one; a new pace restarts the
     * location request at that pace.
     */
    private fun startWatch() {
        watchJob = scope.launch {
            val cadence = MutableStateFlow(WatchCadence.NEAR)
            cadence.collectLatest { pace ->
                locationProvider.locationUpdates(pace.intervalMillis, pace.highAccuracy)
                    .collect { fix -> cadence.value = zoneWatch.onFix(fix) }
            }
        }
    }

    /**
     * Shows the "detection active" notification. Android can still refuse a location service
     * (Android 12+ background-start limits, Android 14+ while-in-use rules); starts are guarded at
     * the call site in [start], and this catch is the last line for anything that slips through.
     */
    // FOREGROUND_SERVICE_TYPE_LOCATION is inlined from API 29; ServiceCompat.startForeground
    // swallows the type on API < 29, so this is safe on our minSdk 26.
    @SuppressLint("InlinedApi")
    private fun promote(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            Notifier.FOREGROUND_ID,
            notifier.foregroundNotification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        true
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalStateException) {
        false
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether the service is up in this process. */
        @Volatile
        var isRunning = false
            private set

        /**
         * Starts the service when Android will allow it to become a location service.
         *
         * [fromBackground] starts (boot, tile, periodic refresh) need "Allow all the time" on
         * Android 10+: without it the service could not go foreground, and a service started this
         * way that never does crashes the app. Android 12+ may also refuse a background start
         * outright, which is caught here.
         *
         * @return true when the start was handed to the system.
         */
        fun start(context: Context, fromBackground: Boolean): Boolean {
            if (!LocationPermissions.hasForeground(context)) return false
            if (fromBackground && !LocationPermissions.hasBackground(context)) return false
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DetectionForegroundService::class.java),
                )
                true
            } catch (e: IllegalStateException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DetectionForegroundService::class.java))
        }
    }
}
