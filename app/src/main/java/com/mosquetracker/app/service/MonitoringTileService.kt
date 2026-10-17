package com.mosquetracker.app.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mosquetracker.app.R
import com.mosquetracker.app.data.prefs.SettingsStore
import com.mosquetracker.app.monitoring.MonitoringController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile: one tap toggles background mosque detection, no need to open the app.
 *
 * TileService is a Service, so Hilt injects it like any other entry point. State is read
 * fresh on every [onStartListening] and right after a toggle, so the tile never lies.
 */
@AndroidEntryPoint
class MonitoringTileService : TileService() {

    @Inject
    lateinit var monitoringController: MonitoringController

    @Inject
    lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (settingsStore.isMonitoringEnabled()) {
                monitoringController.disableMonitoring()
            } else {
                monitoringController.enableMonitoring()
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val enabled = settingsStore.monitoringEnabled.first()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (enabled) "Tracking mosques" else "Mosque tracking off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_mosque)
        // setSubtitle needs API 29+; guard it so behaviour is uniform back to minSdk 26.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "On" else "Off"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
