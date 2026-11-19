package com.praveen.studio.photovideo.url

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL

/** Notifications that come from Firebase Cloud Messaging (messages the developer publishes). */
object AppNotifications {
    /**
     * Channel with HIGH importance: the message pops up on the screen with sound, also when the app is closed.
     * Same id as default_notification_channel_id in the manifest (used for messages the system shows by itself).
     */
    const val CHANNEL_ID = "announcements_high"

    /** Older versions used this channel (normal importance); it is removed. */
    private const val OLD_CHANNEL_ID = "announcements"

    /** Topic every phone listens to: publish to the topic "all" in the Firebase console to reach everybody. */
    const val TOPIC_ALL = "all"

    fun createChannel(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
            val channel = NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "News, updates and messages from Photo Video URL"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        } catch (_: Throwable) {
        }
    }

    /**
     * Shows a message with the app logo, in the brand colour. Used for messages that reach the app itself
     * (the app is open, or the message is a "data" message, which is also delivered while the app is closed).
     * Call it on a background thread when [imageUrl] is used (the picture is downloaded).
     */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String, link: String?, imageUrl: String? = null) {
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

            val picture = if (!imageUrl.isNullOrBlank()) downloadPicture(imageUrl) else null
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setLargeIcon(roundedLogo(context, 192))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            if (picture != null) {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).setSummaryText(body))
            } else {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        } catch (_: Throwable) {
        }
    }

    /** The app logo with rounded corners (large icon of the notification). */
    private fun roundedLogo(context: Context, size: Int): Bitmap? {
        return try {
            val source = BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_icon_fg) ?: return null
            val scaled = Bitmap.createScaledBitmap(source, size, size, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val radius = size * 0.22f
            Canvas(output).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
            output
        } catch (_: Throwable) {
            null
        }
    }

    /** Downloads the big picture of a message (https only, small timeouts, at most about 4 MB). */
    private fun downloadPicture(address: String): Bitmap? {
        if (!address.startsWith("https://")) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 7000
            }
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { stream ->
                val limit = 4 * 1024 * 1024
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > limit) return null
                }
                buffer.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Throwable) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
