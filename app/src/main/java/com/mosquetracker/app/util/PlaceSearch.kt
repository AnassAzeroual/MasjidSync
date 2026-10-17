package com.mosquetracker.app.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Place-name search for Settings → "Search via Map".
 *
 * Uses the platform [Geocoder]: free, no API key, no extra dependency. Results drive the Home
 * map picker (the camera flies to the match and the pin drops there), so search never needs
 * its own map. When offline the search field is disabled outright — geocoding cannot work
 * without a connection, and failing loudly on submit would be worse UX.
 */
@Singleton
class PlaceSearch @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    sealed interface Result {
        data class Found(val latitude: Double, val longitude: Double, val label: String?) : Result
        data object NoResults : Result
        data class Unavailable(val message: String) : Result
    }

    /** True when there is a validated internet connection. Backs the search field's enabled state. */
    fun isOnline(): Boolean {
        val manager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun search(query: String): Result {
        if (!isOnline()) return Result.Unavailable("You're offline — search needs a connection.")
        if (!Geocoder.isPresent()) {
            return Result.Unavailable("Search isn't available on this device.")
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(
                        query,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses.toList())
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                // Documented deviation: the callback Geocoder API only exists on API 33+, so
                // below that the blocking call runs on Dispatchers.IO. See README/context file.
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    runCatching { geocoder.getFromLocationName(query, 1) }.getOrNull()
                }
            }
        val first = addresses?.firstOrNull()
            ?: return Result.NoResults
        return Result.Found(
            latitude = first.latitude,
            longitude = first.longitude,
            label = first.featureName ?: first.locality ?: first.getAddressLine(0),
        )
    }
}
