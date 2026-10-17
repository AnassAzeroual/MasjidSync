package com.mosquetracker.app.util

import com.mosquetracker.app.model.DetectionMethod

fun DetectionMethod.displayName(): String = when (this) {
    DetectionMethod.GEOFENCE -> "GPS"
    DetectionMethod.MANUAL -> "Manual"
}
