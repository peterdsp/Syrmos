package com.syrmos.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.syrmos.android.MainActivity
import com.syrmos.android.R
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Home Screen widget showing the next few M3 airport-train departures
 * from Syntagma. Mirrors iOS SyrmosDeparturesWidget (a
 * NextDeparturesWidget on iOS) so users on both platforms get the same
 * quick-glance answer.
 *
 * V1 keeps it simple: hardcoded Syntagma -> Airport line, arithmetic
 * departure times based on the M3-airport 36-minute frequency and the
 * 05:35 - 22:15 service window. A future revision can pull from the
 * bundled schedule reader once we lift that into a shared library
 * accessible from the AppWidgetProvider process.
 */
class SyrmosDeparturesWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context))
        }
    }

    private fun buildRemoteViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_departures)
        val slots = nextDepartures(now = Clock.System.now().toEpochMilliseconds())

        val timeIds = intArrayOf(R.id.widget_time1, R.id.widget_time2, R.id.widget_time3)
        val minsIds = intArrayOf(R.id.widget_mins1, R.id.widget_mins2, R.id.widget_mins3)
        val rowIds = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)

        for (i in timeIds.indices) {
            if (i < slots.size) {
                views.setViewVisibility(rowIds[i], android.view.View.VISIBLE)
                views.setTextViewText(timeIds[i], slots[i].timeLabel)
                views.setTextViewText(minsIds[i], "${slots[i].minutesAway} min")
            } else {
                views.setViewVisibility(rowIds[i], android.view.View.GONE)
            }
        }

        views.setTextViewText(R.id.widget_title, "M3 · Syntagma → Airport")

        // Tap to open the app.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, openIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_root, pending)

        return views
    }

    data class Slot(val timeLabel: String, val minutesAway: Int)

    private fun nextDepartures(now: Long): List<Slot> {
        val tz = TimeZone.of("Europe/Athens")
        val localNow = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(tz)
        val nowMinutes = localNow.hour * 60 + localNow.minute

        val slots = mutableListOf<Slot>()
        var probe = alignedNextSlot(nowMinutes)
        while (slots.size < 3 && probe <= LAST_SERVICE_MINUTES + 24 * 60) {
            val effective = if (probe > LAST_SERVICE_MINUTES) probe.wrapNextDay() else probe
            val diff = (effective - nowMinutes).coerceAtLeast(0)
            slots += Slot(formatClock(effective % (24 * 60)), diff)
            probe = effective + AIRPORT_FREQUENCY_MINUTES
        }
        return slots
    }

    private fun Int.wrapNextDay(): Int = FIRST_SERVICE_MINUTES + 24 * 60

    /** First M3-airport slot at or after nowMinutes, aligned to the 05:35 baseline. */
    private fun alignedNextSlot(nowMinutes: Int): Int {
        if (nowMinutes < FIRST_SERVICE_MINUTES) return FIRST_SERVICE_MINUTES
        val elapsed = nowMinutes - FIRST_SERVICE_MINUTES
        val nextIndex = (elapsed / AIRPORT_FREQUENCY_MINUTES) + 1
        return FIRST_SERVICE_MINUTES + nextIndex * AIRPORT_FREQUENCY_MINUTES
    }

    private fun formatClock(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    companion object {
        private const val FIRST_SERVICE_MINUTES = 5 * 60 + 35   // 05:35 Athens local
        private const val LAST_SERVICE_MINUTES = 22 * 60 + 15   // 22:15 Athens local
        private const val AIRPORT_FREQUENCY_MINUTES = 36        // M3-airport service pattern

        /** External refresh trigger used by MainActivity when the user returns to Home. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SyrmosDeparturesWidget::class.java))
            if (ids.isEmpty()) return
            val provider = SyrmosDeparturesWidget()
            provider.onUpdate(context, mgr, ids)
        }
    }
}
