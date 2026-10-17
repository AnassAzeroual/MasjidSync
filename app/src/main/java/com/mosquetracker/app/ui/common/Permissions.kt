package com.mosquetracker.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** The runtime permissions the app asks for, grouped by when they are needed. */
object RequiredPermissions {

    fun foreground(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun backgroundLocation(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyList()
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Re-checks a permission set on every resume, so returning from Settings updates the UI. */
@Composable
fun rememberPermissionsGranted(permissions: List<String>): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember(permissions) {
        mutableStateOf(permissions.all { RequiredPermissions.isGranted(context, it) })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.value = permissions.all { RequiredPermissions.isGranted(context, it) }
    }
    return state
}

/** Thin wrapper so screens do not repeat the Activity Result boilerplate. */
@Composable
fun rememberPermissionLauncher(
    onFinished: () -> Unit,
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onFinished() }
}
