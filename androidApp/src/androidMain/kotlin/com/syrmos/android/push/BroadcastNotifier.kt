package com.syrmos.android.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.syrmos.core.common.BroadcastCopy
import com.syrmos.core.common.LocalizationManager

/**
 * Posts "Announcements from Syrmos" notifications on the `syrmos_broadcasts`
 * channel (the channel the server names in the FCM payload), and remembers which
 * broadcast ids were shown so push and polling never notify twice.
 */
object BroadcastNotifier {
    const val CHANNEL_ID = "syrmos_broadcasts"
    private const val NOTIF_BASE = 6000

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                BroadcastCopy.channelName(LocalizationManager.language.value),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Shows one broadcast and records its id as delivered. */
    fun post(context: Context, id: String, title: String, body: String, deepLink: String?) {
        BroadcastPrefs.addDelivered(context, id)
        if (!canPost(context)) return
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notif_type", "broadcast")
            putExtra("notif_alert_id", id)
            if (deepLink != null) putExtra("notif_deep_link", deepLink)
        }
        val content = launch?.let {
            PendingIntent.getActivity(
                context, id.hashCode(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .apply { if (content != null) setContentIntent(content) }
            .build()
        manager.notify(NOTIF_BASE + (id.hashCode() and 0xFFFF), notification)
    }
}

/** Device-local broadcast bookkeeping: delivered ids, seen ids, polling cursor. */
object BroadcastPrefs {
    private const val PREFS = "syrmos_prefs"
    private const val KEY_DELIVERED = "broadcast_delivered_ids"
    private const val KEY_SEEN = "broadcast_seen_ids"
    private const val KEY_SINCE = "broadcast_since"
    private const val MAX_IDS = 200

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context, key: String): List<String> =
        prefs(context).getString(key, "").orEmpty().split(',').filter { it.isNotBlank() }

    private fun save(context: Context, key: String, ids: List<String>) {
        prefs(context).edit().putString(key, ids.distinct().takeLast(MAX_IDS).joinToString(",")).apply()
    }

    fun delivered(context: Context): Set<String> = load(context, KEY_DELIVERED).toSet()
    fun seen(context: Context): Set<String> = load(context, KEY_SEEN).toSet()

    fun addDelivered(context: Context, id: String) = save(context, KEY_DELIVERED, load(context, KEY_DELIVERED) + id)
    fun addSeen(context: Context, ids: Collection<String>) = save(context, KEY_SEEN, load(context, KEY_SEEN) + ids)

    /** First poll looks back one day so a broadcast sent just before install still shows. */
    fun since(context: Context, nowEpochSeconds: Long): Long =
        prefs(context).getLong(KEY_SINCE, nowEpochSeconds - 24 * 3600)

    fun setSince(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_SINCE, value).apply()
    }
}
