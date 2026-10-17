package com.mosquetracker.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mosquetracker.app.MainActivity
import com.mosquetracker.app.R
import com.mosquetracker.app.model.DetectionMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Owns all notification channels and posts detection/attendance notifications. */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val detection = NotificationChannel(
            DETECTION_CHANNEL_ID,
            context.getString(R.string.channel_detection_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_detection_desc)
            setShowBadge(false)
        }

        val events = NotificationChannel(
            EVENTS_CHANNEL_ID,
            context.getString(R.string.channel_events_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_events_desc)
        }

        manager.createNotificationChannels(listOf(detection, events))
    }

    fun foregroundNotification(mosqueCount: Int): Notification =
        NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mosque)
            .setContentTitle(context.getString(R.string.detection_notification_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.detection_notification_text,
                    mosqueCount,
                    mosqueCount,
                ),
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())
            .build()

    // Lint cannot see through the canPostNotifications() guard, but every notify() call
    // below is gated by an explicit POST_NOTIFICATIONS check before it runs.
    @SuppressLint("MissingPermission")
    fun updateForeground(mosqueCount: Int) {
        if (!canPostNotifications()) return
        runCatching { manager.notify(FOREGROUND_ID, foregroundNotification(mosqueCount)) }
    }

    fun notifyEntry(mosqueId: Long, mosqueName: String, method: DetectionMethod) {
        notify(
            mosqueId,
            title = "You're inside $mosqueName",
            text = "Visit logged — detected by ${method.label()}.",
        )
    }

    fun notifyExit(mosqueId: Long, mosqueName: String) {
        notify(mosqueId, title = "Left $mosqueName", text = "Visit closed in your log.")
    }

    // One slot per mosque: the exit notice replaces that visit's entry notice instead of piling up.
    @SuppressLint("MissingPermission")
    private fun notify(mosqueId: Long, title: String, text: String) {
        if (!canPostNotifications()) return
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mosque)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { manager.notify("$VISIT_TAG$mosqueId", VISIT_ID, notification) }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun DetectionMethod.label(): String = when (this) {
        DetectionMethod.GEOFENCE -> "GPS"
        DetectionMethod.MANUAL -> "manual entry"
    }

    companion object {
        const val DETECTION_CHANNEL_ID = "detection"
        const val EVENTS_CHANNEL_ID = "events"
        const val FOREGROUND_ID = 1001
        private const val VISIT_TAG = "visit-"
        private const val VISIT_ID = 1
    }
}
