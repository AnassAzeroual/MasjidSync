package com.mosquetracker.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mosquetracker.app.location.LocationPermissions

/**
 * A grant the app is missing, worst first. Each is asked for on its own: bundling notifications
 * with location made a denied notification prompt look like missing location forever, and hid the
 * background-location step behind it.
 */
enum class MissingPermission {
    LOCATION,
    PRECISE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
}

/** The runtime permissions the app asks for, grouped by when they are needed. */
object RequiredPermissions {

    val location: List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    val notifications: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }

    val backgroundLocation: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyList()
        }

    /**
     * From Android 11 "Allow all the time" is only offered on the app's settings page; a runtime
     * request for it is answered without the user ever seeing a prompt.
     */
    val backgroundNeedsSettingsPage: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun missing(context: Context): List<MissingPermission> = buildList {
        when {
            !LocationPermissions.hasForeground(context) -> add(MissingPermission.LOCATION)
            !LocationPermissions.hasFine(context) -> add(MissingPermission.PRECISE_LOCATION)
        }
        if (LocationPermissions.hasForeground(context) && !LocationPermissions.hasBackground(context)) {
            add(MissingPermission.BACKGROUND_LOCATION)
        }
        if (notifications.any { !isGranted(context, it) }) add(MissingPermission.NOTIFICATIONS)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * What the app is missing right now, re-read on every resume and after every prompt. It also owns
 * the prompt launcher, so a result still lands when the banner that asked has already gone.
 */
@Stable
class PermissionStatus internal constructor(private val context: Context) {
    var missing: List<MissingPermission> by mutableStateOf(RequiredPermissions.missing(context))
        private set

    internal var launcher: ActivityResultLauncher<Array<String>>? = null

    fun refresh() {
        missing = RequiredPermissions.missing(context)
    }

    internal fun request(permissions: List<String>) {
        launcher?.launch(permissions.toTypedArray())
    }
}

/** [onResult] runs after every permission prompt the banners raise, granted or not. */
@Composable
fun rememberPermissionStatus(onResult: () -> Unit = {}): PermissionStatus {
    val context = LocalContext.current
    val status = remember(context) { PermissionStatus(context.applicationContext) }
    val currentOnResult by rememberUpdatedState(onResult)
    status.launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        status.refresh()
        currentOnResult()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status.refresh() }
    return status
}

/**
 * One banner per missing grant, each with its own way in. A permission denied for good no longer
 * shows a prompt, so every banner also offers the settings page that can still grant it.
 *
 * [shown] lets a crowded screen pick which ones, in order; it defaults to all of them.
 */
@Composable
fun PermissionBanners(
    status: PermissionStatus,
    shown: List<MissingPermission> = status.missing,
) {
    val context = LocalContext.current

    shown.forEach { missing ->
        when (missing) {
            MissingPermission.LOCATION -> AlertBanner(
                title = "Location permission required",
                message = "MasjidSync needs GPS to show where you are and to tell when you arrive.",
                actionText = "Grant",
                onAction = { status.request(RequiredPermissions.location) },
                secondaryText = "App settings",
                onSecondary = { context.openAppDetailsSettings() },
            )

            MissingPermission.PRECISE_LOCATION -> AlertBanner(
                title = "Turn on precise location",
                message = "Approximate location can be off by kilometres, and Android will not " +
                    "arm a mosque zone without the precise grant.",
                actionText = "Use precise",
                onAction = { status.request(RequiredPermissions.location) },
                secondaryText = "App settings",
                onSecondary = { context.openAppDetailsSettings() },
            )

            MissingPermission.BACKGROUND_LOCATION -> if (RequiredPermissions.backgroundNeedsSettingsPage) {
                AlertBanner(
                    title = "Allow location all the time",
                    message = "Open Permissions → Location and choose \"Allow all the time\", " +
                        "so a mosque visit is noticed while the app is closed.",
                    actionText = "Open settings",
                    onAction = { context.openAppDetailsSettings() },
                )
            } else {
                AlertBanner(
                    title = "Allow location all the time",
                    message = "Choose \"Allow all the time\", so a mosque visit is noticed while " +
                        "the app is closed.",
                    actionText = "Allow",
                    onAction = {
                        status.request(RequiredPermissions.backgroundLocation)
                    },
                    secondaryText = "App settings",
                    onSecondary = { context.openAppDetailsSettings() },
                )
            }

            MissingPermission.NOTIFICATIONS -> AlertBanner(
                title = "Notifications are off",
                message = "Arrival notices and the detection status are shown as notifications.",
                actionText = "Allow",
                onAction = { status.request(RequiredPermissions.notifications) },
                secondaryText = "App settings",
                onSecondary = { context.openAppNotificationSettings() },
            )
        }
    }
}
