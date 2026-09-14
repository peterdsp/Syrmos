package com.syrmos.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager

/**
 * Phase N J09: fires when a leave-by alarm goes off and posts the "leave now"
 * notification for that saved departure. The alarm carries the display fields as
 * extras (set by [LeaveByReminderScheduler]) so the receiver needs no state.
 *
 * Each reminder gets a distinct notification id derived from its stable reminder
 * id, so multiple pending reminders never overwrite each other.
 */
class LeaveByReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, manager)

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val lang = LocalizationManager.language.value
        val line = intent.getStringExtra(EXTRA_LINE).orEmpty()
        val station = intent.getStringExtra(EXTRA_STATION).orEmpty()
        val destination = intent.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val scheduled = intent.getStringExtra(EXTRA_SCHEDULED).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, DEFAULT_NOTIF_ID)

        val title = "${leaveNowLabel(lang)} $line"
        val body = buildString {
            append(station)
            if (destination.isNotBlank()) append(" ${toLabel(lang)} $destination")
            if (scheduled.isNotBlank()) append(" · $scheduled")
        }

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = open?.let {
            android.app.PendingIntent.getActivity(
                context, notifId, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()
        manager.notify(notifId, notification)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName(LocalizationManager.language.value),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    private fun channelName(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "Υπενθυμίσεις αναχώρησης"
        AppLanguage.ALBANIAN -> "Kujtues nisjeje"
        AppLanguage.ITALIAN -> "Promemoria di partenza"
        else -> "Leave-by reminders"
    }

    private fun leaveNowLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "Ώρα να φύγεις για"
        AppLanguage.ALBANIAN -> "Koha për të nisur për"
        AppLanguage.ITALIAN -> "Ora di partire per"
        else -> "Time to leave for"
    }

    private fun toLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "προς"
        AppLanguage.ALBANIAN -> "drejt"
        AppLanguage.ITALIAN -> "verso"
        else -> "to"
    }

    companion object {
        const val CHANNEL_ID = "leave_by_reminders"
        const val DEFAULT_NOTIF_ID = 4400
        const val EXTRA_LINE = "line"
        const val EXTRA_STATION = "station"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_SCHEDULED = "scheduled"
        const val EXTRA_NOTIF_ID = "notifId"
    }
}
