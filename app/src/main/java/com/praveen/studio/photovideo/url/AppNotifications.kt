package com.praveen.studio.photovideo.url

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Notifications that come from Firebase Cloud Messaging (messages the developer publishes). */
object AppNotifications {
    /** Same id as default_notification_channel_id in the manifest (used when the app is closed). */
    const val CHANNEL_ID = "announcements"

    /** Topic every phone listens to: publish to the topic "all" in the Firebase console to reach everybody. */
    const val TOPIC_ALL = "all"

    fun createChannel(context: Context) {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Announcements",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "News and updates from Photo Video URL"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        } catch (_: Throwable) {
        }
    }

    /** Shows a message while the app is open (the system shows it by itself while the app is closed). */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String, link: String?) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

            val openIntent = if (!link.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(link))
            } else {
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (_: Throwable) {
        }
    }
}
