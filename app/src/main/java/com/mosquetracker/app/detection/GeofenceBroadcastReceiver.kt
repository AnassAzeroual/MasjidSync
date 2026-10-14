package com.mosquetracker.app.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.mosquetracker.app.work.VerificationWorker

/**
 * Receives Tier 1 geofence transitions. BroadcastReceivers must be fast, so the
 * actual Wi-Fi/BLE verification is handed to a [VerificationWorker].
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        event.triggeringGeofences?.forEach { geofence ->
            val mosqueId = geofence.requestId.toLongOrNull() ?: return@forEach
            VerificationWorker.enqueue(context, mosqueId, transition)
        }
    }

    companion object {
        const val ACTION_GEOFENCE = "com.mosquetracker.app.action.GEOFENCE"
    }
}
