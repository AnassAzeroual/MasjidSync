package com.mosquetracker.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mosquetracker.app.model.LocationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the Play Services fused location provider as cold flows. */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean = LocationPermissions.hasForeground(context)

    /**
     * Continuous updates every [intervalMillis]. [highAccuracy] asks for GPS; without it the fused
     * provider may answer from Wi-Fi and cell, which is far cheaper. Emits nothing (and closes)
     * without permission.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMillis: Long = 3_000L,
        highAccuracy: Boolean = true,
    ): Flow<LocationSample> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        LocationSample(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy,
                            timestamp = location.time,
                        ),
                    )
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** Latest cached fix, or null when unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): LocationSample? {
        if (!hasLocationPermission()) return null
        return runCatching {
            client.lastLocation.await()?.let { location ->
                LocationSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    timestamp = location.time,
                )
            }
        }.getOrNull()
    }

    /**
     * A fix asked for right now, or null if none arrives within [timeoutMillis].
     *
     * Reserved for second-guessing a geofence arrival whose cached fix disagrees: a phone that has
     * really walked in has a current reading to prove it, and one request costs nothing next to
     * the continuous stream the map runs.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = CURRENT_FIX_TIMEOUT_MILLIS): LocationSample? {
        if (!hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMillis) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?.let { location ->
                        LocationSample(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy,
                            timestamp = location.time,
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private companion object {
        /** Long enough for a cold GPS start to answer, short enough to hold a worker. */
        const val CURRENT_FIX_TIMEOUT_MILLIS = 15_000L
    }
}
