package com.syrmos.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.syrmos.core.common.NotificationSettings

/**
 * Receives FCM broadcasts. The server sends `notification {title, body}` plus
 * `data {broadcastId, deepLink}` on channel `syrmos_broadcasts`. While the app is
 * in the background the system shows the notification itself; here we handle the
 * foreground case and always record the id as delivered so the polling fallback
 * does not notify the same broadcast again.
 */
class SyrmosMessagingService : FirebaseMessagingService() {

    /**
     * Runs for every FCM delivery, including notification messages the system
     * displays itself while the app is in the background (onMessageReceived is not
     * called then), so the id is recorded as delivered before polling runs.
     */
    override fun handleIntent(intent: android.content.Intent) {
        intent.getStringExtra("broadcastId")?.let { BroadcastPrefs.addDelivered(applicationContext, it) }
        super.handleIntent(intent)
    }

    override fun onNewToken(token: String) {
        SyrmosPush.register(applicationContext, token, force = true)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val id = message.data["broadcastId"] ?: message.messageId ?: return
        if (id in BroadcastPrefs.delivered(applicationContext) || !NotificationSettings.broadcasts.value) {
            BroadcastPrefs.addDelivered(applicationContext, id)
            return
        }
        val title = message.notification?.title ?: message.data["title"].orEmpty()
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        if (title.isBlank() && body.isBlank()) {
            BroadcastPrefs.addDelivered(applicationContext, id)
            return
        }
        BroadcastNotifier.post(applicationContext, id, title, body, message.data["deepLink"])
    }
}
