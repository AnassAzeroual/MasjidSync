package com.mosquetracker.app.detection

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.mosquetracker.app.data.local.entity.MosqueEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Registers/removes OS geofences (Tier 1) via Play Services. */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(GeofenceBroadcastReceiver.ACTION_GEOFENCE)
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            // FLAG_MUTABLE is required: Play Services adds the triggering geofences
            // to the Intent before delivering it to the receiver.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Replaces all registered geofences with the given mosques. */
    @SuppressLint("MissingPermission")
    suspend fun register(mosques: List<MosqueEntity>) {
        if (!hasPermission()) return
        if (mosques.isEmpty()) {
            removeAll()
            return
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(mosques.map { it.toGeofence() })
            .build()

        runCatching { client.addGeofences(request, pendingIntent).await() }
    }

    @SuppressLint("MissingPermission")
    suspend fun remove(mosqueId: Long) {
        if (!hasPermission()) return
        runCatching { client.removeGeofences(listOf(mosqueId.toString())).await() }
    }

    suspend fun removeAll() {
        runCatching { client.removeGeofences(pendingIntent).await() }
    }

    private fun MosqueEntity.toGeofence(): Geofence =
        Geofence.Builder()
            .setRequestId(id.toString())
            .setCircularRegion(
                latitude,
                longitude,
                geofenceRadius.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            .build()

    private companion object {
        const val REQUEST_CODE = 42
    }
}
