package com.syrmos.android.widget

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Offline fallback rows for the Glance widgets when no snapshot has been written
 * yet (first add, before the WorkManager refresher runs). Same arithmetic the
 * original RemoteViews widget used: the M3 airport branch from Syntagma on its
 * 36-minute frequency within the 05:35 - 22:15 service window. Derived from the
 * bundled service pattern, so it works with no network and no snapshot.
 */
object BundledFallback {
    private const val FIRST_SERVICE_MINUTES = 5 * 60 + 35
    private const val LAST_SERVICE_MINUTES = 22 * 60 + 15
    private const val AIRPORT_FREQUENCY_MINUTES = 36

    fun rows(now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): List<WidgetRow> {
        val tz = TimeZone.of("Europe/Athens")
        val localNow = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz)
        val nowMinutes = localNow.hour * 60 + localNow.minute

        val rows = mutableListOf<WidgetRow>()
        var probe = alignedNextSlot(nowMinutes)
        while (rows.size < 5 && probe <= LAST_SERVICE_MINUTES + 24 * 60) {
            val effective = if (probe > LAST_SERVICE_MINUTES) FIRST_SERVICE_MINUTES + 24 * 60 else probe
            val diff = (effective - nowMinutes).coerceAtLeast(0)
            rows += WidgetRow(
                lineId = "M3",
                destination = "Airport",
                minutes = diff,
                time = formatClock(effective % (24 * 60)),
            )
            probe = effective + AIRPORT_FREQUENCY_MINUTES
        }
        return rows
    }

    fun snapshot(now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): WidgetSnapshot =
        WidgetSnapshot(stationName = "Syntagma", lastTrain = null, rows = rows(now), updatedEpoch = now)

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
}
