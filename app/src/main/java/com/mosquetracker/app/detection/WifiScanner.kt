package com.mosquetracker.app.detection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mosquetracker.app.model.WifiAccessPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 2 Wi-Fi verification.
 *
 * Reads the connected network's BSSID via [NetworkCapabilities.getTransportInfo]
 * (the modern, non-deprecated API on API 29+) and runs a short active scan for
 * nearby access points.
 */
@Singleton
class WifiScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun isSupported(): Boolean = wifiManager != null

    fun isEnabled(): Boolean = wifiManager?.isWifiEnabled == true

    /** NEARBY_WIFI_DEVICES on API 33+, fine location below that. */
    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** BSSID of the Wi-Fi network the phone is joined to, if any. */
    fun connectedBssid(): String? {
        if (!hasPermission()) return null

        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            (wifiManager?.connectionInfo as? WifiInfo)
        } ?: return null

        return info.bssid.normalizedBssid()
    }

    /**
     * Access points from the platform's most recent Wi-Fi scan.
     *
     * App-triggered scans are deprecated ([WifiManager.startScan] was deprecated in API 28 and the
     * platform now throttles or ignores them), so this reads the cached results that the framework
     * maintains itself while Wi-Fi and location are enabled. The connected-network BSSID check
     * above remains the primary Tier 2 signal; this complements it with nearby access points.
     *
     * TODO(device testing): verify the cached results are fresh enough on real hardware before
     *  relying on them; if not, request a scan through a supported API.
     */
    @SuppressLint("MissingPermission")
    suspend fun scanNearby(): List<WifiAccessPoint> = withContext(Dispatchers.IO) {
        val manager = wifiManager ?: return@withContext emptyList()
        if (!hasPermission()) return@withContext emptyList()

        runCatching { manager.scanResults }.getOrDefault(emptyList())
            .mapNotNull { result ->
                val bssid = result.BSSID.normalizedBssid() ?: return@mapNotNull null
                WifiAccessPoint(
                    ssid = result.ssidOrNull(),
                    bssid = bssid,
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                )
            }
            .distinctBy { it.bssid }
            .sortedByDescending { it.rssi }
    }

    /** SSID as a plain string: the modern [ScanResult.getWifiSsid] on API 33+, the legacy field below. */
    private fun ScanResult.ssidOrNull(): String? {
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString()
        } else {
            @Suppress("DEPRECATION")
            SSID
        }
        return value?.takeIf { it.isNotBlank() }
    }

    private fun String?.normalizedBssid(): String? {
        val value = this?.trim()?.uppercase() ?: return null
        return when {
            value.isEmpty() -> null
            value == "02:00:00:00:00:00" -> null // Android's redaction placeholder
            value.any { it == ':' || it.isDigit() || it in 'A'..'F' } && value.contains(":") -> value
            else -> null
        }
    }
}
