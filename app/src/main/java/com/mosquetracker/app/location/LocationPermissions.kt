package com.mosquetracker.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Location grants as the detection machinery needs them. */
object LocationPermissions {

    /** Fine or approximate location while the app is in use. */
    fun hasForeground(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Precise location: geofences are refused with only the approximate grant. */
    fun hasFine(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * "Allow all the time". Below Android 10 the foreground grant already covers the background;
     * from 10 on, geofences and a location service started from the background both need it.
     */
    fun hasBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
