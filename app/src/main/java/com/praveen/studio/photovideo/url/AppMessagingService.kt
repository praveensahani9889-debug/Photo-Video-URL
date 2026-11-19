package com.praveen.studio.photovideo.url

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the messages that the developer publishes with Firebase Cloud Messaging.
 * Message with a "notification" part: shown by the system when the app is closed, and by this service when it is open.
 * Data-only message (shown with the app logo, also while the app is closed): keys "title", "body" (or "message"),
 * an optional "link" that opens when it is tapped and an optional "image" (https picture).
 */
class AppMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return
        val image = message.notification?.imageUrl?.toString() ?: message.data["image"]
        AppNotifications.show(applicationContext, title, body, message.data["link"], image)
    }

    override fun onNewToken(token: String) {
        // Nothing to store: phones receive messages through the topic "all".
    }
}
