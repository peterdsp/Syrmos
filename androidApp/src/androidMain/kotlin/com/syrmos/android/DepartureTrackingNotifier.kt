package com.syrmos.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.DepartureTracking
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.TrackedDeparture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android parallel to the iOS Live Activity. Surfaces the tracked departure as
 * an ongoing notification with a live count-down chronometer, so the countdown
 * keeps ticking on the Lock Screen and in the shade without the app open.
 *
 * It observes the shared [DepartureTracking] primitive in `core/common`, the
 * exact source the in-app Compose card and the iOS Live Activity also read, so
 * all three surfaces stay in sync by construction. The chronometer ticks itself
 * (no per-second posting); a single notify with the target time is enough.
 */
class DepartureTrackingNotifier(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun start() {
        ensureChannel()
        scope.launch {
            DepartureTracking.active.collect { tracked ->
                if (tracked == null) manager.cancel(NOTIFICATION_ID) else post(tracked)
            }
        }
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName(LocalizationManager.language.value),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun post(tracked: TrackedDeparture) {
        // POST_NOTIFICATIONS is runtime-gated on Android 13+. If the user
        // declined, the in-app card still works; we just don't post.
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val lang = LocalizationManager.language.value
        val title = "${trackingLabel(lang)} ${tracked.lineId} · ${tracked.stationName}"
        val body = if (tracked.destination.isNotBlank()) {
            "${toLabel(lang)} ${tracked.destination} · ${tracked.scheduledTime}"
        } else {
            tracked.scheduledTime
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(tracked.targetEpochSeconds * 1000)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun channelName(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "Παρακολούθηση συρμού"
        AppLanguage.ALBANIAN -> "Ndjekja e trenit"
        else -> "Departure tracking"
    }

    private fun trackingLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "Παρακολούθηση"
        AppLanguage.ALBANIAN -> "Po ndiqet"
        else -> "Tracking"
    }

    private fun toLabel(lang: AppLanguage) = when (lang) {
        AppLanguage.GREEK -> "προς"
        AppLanguage.ALBANIAN -> "drejt"
        else -> "to"
    }

    companion object {
        private const val CHANNEL_ID = "departure_tracking"
        private const val NOTIFICATION_ID = 4201
    }
}
