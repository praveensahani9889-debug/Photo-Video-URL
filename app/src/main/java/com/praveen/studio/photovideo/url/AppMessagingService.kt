package com.praveen.studio.photovideo.url

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the messages that the developer publishes with Firebase Cloud Messaging.
 * Message with a "notification" part: shown by the system when the app is closed, and by this service when it is open.
 * Data-only message: keys "title", "body" (or "message") and an optional "link" that opens when it is tapped.
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
        AppNotifications.show(applicationContext, title, body, message.data["link"])
    }

    override fun onNewToken(token: String) {
        // Nothing to store: phones receive messages through the topic "all".
    }
}
