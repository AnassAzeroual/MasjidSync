package com.mosquetracker.app.detection

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mosquetracker.app.model.BleBeacon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Tier 2 BLE verification: a bounded scan that emits each advertiser it sees. */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** BLUETOOTH_SCAN on API 31+, fine location below that. */
    fun hasPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Scans for [durationMillis] then completes. Callers that want a live list
     * should collect continuously; callers verifying a match can use `first`.
     */
    @SuppressLint("MissingPermission")
    fun scan(durationMillis: Long = 5_000L): Flow<BleBeacon> = callbackFlow {
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || !hasPermission()) {
            close()
            return@callbackFlow
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                trySend(
                    BleBeacon(
                        name = runCatching { device.name }.getOrNull(),
                        address = device.address?.uppercase() ?: return,
                        rssi = result.rssi,
                        serviceUuids = result.scanRecord
                            ?.serviceUuids
                            ?.map { it.uuid.toString().lowercase() }
                            .orEmpty(),
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, callback)
        val stopJob = launch {
            delay(durationMillis)
            close()
        }

        awaitClose {
            stopJob.cancel()
            runCatching { scanner.stopScan(callback) }
        }
    }
}
