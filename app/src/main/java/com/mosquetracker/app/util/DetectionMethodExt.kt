package com.mosquetracker.app.util

import com.mosquetracker.app.model.DetectionMethod

fun DetectionMethod.displayName(): String = when (this) {
    DetectionMethod.GEOFENCE -> "Geofence"
    DetectionMethod.WIFI -> "Wi-Fi"
    DetectionMethod.BLE -> "Bluetooth"
    DetectionMethod.MANUAL -> "Manual"
}
