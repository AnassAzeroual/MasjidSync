package com.mosquetracker.app.detection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.mosquetracker.app.data.local.entity.MosqueEntity
import com.mosquetracker.app.location.LocationPermissions
import com.mosquetracker.app.model.LocationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Why the OS fences are, or are not, armed. */
enum class FenceStatus {
    ACTIVE,
    NO_MOSQUES,

    /** Geofences need precise location. */
    NO_LOCATION_PERMISSION,

    /** Android 10+ refuses geofences without "Allow all the time". */
    NO_BACKGROUND_PERMISSION,
    FAILED,
}

/** Outcome of one registration: how many mosques are tracked, how many got a fence, and why. */
data class FenceResult(val tracked: Int, val registered: Int, val status: FenceStatus)

/** The fences armed right now, by mosque, and why the set looks the way it does. */
data class FenceState(val status: FenceStatus, val registeredIds: Set<Long>)

/** Registers and removes the OS geofences via Play Services. */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }
    private val mutex = Mutex()

    private val _state = MutableStateFlow<FenceState?>(null)

    /** Null until the first registration of this process, and again once detection is off. */
    val state: StateFlow<FenceState?> = _state.asStateFlow()

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

    /**
     * Replaces every registered fence with fences for [mosques].
     *
     * The old set is removed first, so a deleted or untracked mosque never keeps a stale fence.
     * Play Services allows [MAX_GEOFENCES] per app; past that, the ones nearest [near] are kept.
     * Registrations are serialized so two refreshes cannot interleave their remove and add.
     */
    @SuppressLint("MissingPermission")
    suspend fun register(mosques: List<MosqueEntity>, near: LocationSample?): FenceResult =
        mutex.withLock {
            val tracked = mosques.size
            val blocked = when {
                !LocationPermissions.hasFine(context) -> FenceStatus.NO_LOCATION_PERMISSION
                !LocationPermissions.hasBackground(context) -> FenceStatus.NO_BACKGROUND_PERMISSION
                mosques.isEmpty() -> FenceStatus.NO_MOSQUES
                else -> null
            }
            removeAllLocked()
            if (blocked != null) {
                _state.value = FenceState(blocked, emptySet())
                return@withLock FenceResult(tracked, 0, blocked)
            }

            val selected = selectForRegistration(mosques, near, MAX_GEOFENCES)
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(selected.map { it.toGeofence() })
                .build()

            try {
                client.addGeofences(request, pendingIntent).await()
                _state.value = FenceState(FenceStatus.ACTIVE, selected.mapTo(mutableSetOf()) { it.id })
                FenceResult(tracked, selected.size, FenceStatus.ACTIVE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = FenceState(FenceStatus.FAILED, emptySet())
                FenceResult(tracked, 0, FenceStatus.FAILED)
            }
        }

    suspend fun remove(mosqueId: Long) = mutex.withLock {
        runCatching { client.removeGeofences(listOf(mosqueId.toString())).await() }
        _state.update { it?.copy(registeredIds = it.registeredIds - mosqueId) }
    }

    suspend fun removeAll() = mutex.withLock {
        removeAllLocked()
        _state.value = null
    }

    private suspend fun removeAllLocked() {
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

    companion object {
        /** Play Services' per-app geofence limit. */
        const val MAX_GEOFENCES = 100
        private const val REQUEST_CODE = 42

        /**
         * The mosques that get a fence: all of them up to [max], otherwise the [max] nearest to
         * [near]. Without a fix there is no "nearest", so the first [max] are kept.
         */
        fun selectForRegistration(
            mosques: List<MosqueEntity>,
            near: LocationSample?,
            max: Int,
        ): List<MosqueEntity> {
            if (mosques.size <= max) return mosques
            if (near == null) return mosques.take(max)
            return mosques
                .sortedBy {
                    Haversine.distanceMeters(near.latitude, near.longitude, it.latitude, it.longitude)
                }
                .take(max)
        }
    }
}
