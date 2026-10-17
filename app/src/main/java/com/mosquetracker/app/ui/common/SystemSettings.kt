package com.mosquetracker.app.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** The system's Do Not Disturb access list, the only place that access can be granted. */
fun Context.openNotificationPolicySettings() {
    launchSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
}

/** Opens the per-app "Modify system settings" screen, the only way to earn WRITE_SETTINGS. */
fun Context.openWriteSettings() {
    launchSettings(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(packageUri()))
}

/**
 * The app's own settings page. On Android 11+ it is the only place "Allow all the time" location
 * can be chosen, and it is the way back for any permission the user denied for good.
 */
fun Context.openAppDetailsSettings() {
    launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri()))
}

/** The app's notification settings, where a permanently denied notification grant is undone. */
fun Context.openAppNotificationSettings() {
    launchSettings(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}

private fun Context.packageUri() = "package:$packageName".toUri()

// Some OEM builds drop one of these screens; a missing page must not crash the app.
private fun Context.launchSettings(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
