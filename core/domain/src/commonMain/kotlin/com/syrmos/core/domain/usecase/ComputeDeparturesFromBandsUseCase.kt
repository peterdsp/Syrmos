package com.syrmos.core.domain.usecase

import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.network.SyrmosSchedulesService
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Projects next departures from the API-synced frequency_bands + schedule_rules.
 *
 * Source of truth path. Empty result means bundles aren't loaded yet; the
 * caller should fall back to the seed-based [GetNextDeparturesUseCase].
 *
 * Day-type resolution:
 *  1. Fixed-date holiday rule (Aug 15 → aug_15, Dec 24/31 → dec_24_31,
 *     Jan 1 / May 1 / Oct 28 / Dec 25/26 → sun, Jan 6 / Jan 2 / Nov 17 → sat).
 *  2. Else day-of-week → mon_thu | fri | sat | sun.
 *
 * Past-midnight handling: bands declared "22:30-25:00" or "00:30-02:00 fri"
 * project into the next civil day. When the current time is before 04:00 we
 * also walk yesterday's bands so the Friday-night extension is found at 01:10.
 */
class ComputeDeparturesFromBandsUseCase(
    private val scheduleSync: ScheduleSyncRepository,
) {
    fun invoke(
        lineIds: List<String>,
        direction: Direction,
        limit: Int = 8,
    ): List<UpcomingDeparture> {
        val bundles = scheduleSync.lineBundles.value
        if (bundles.isEmpty()) return emptyList()

        val zone = TimeZone.of("Europe/Athens")
        val now: LocalDateTime = Clock.System.now().toLocalDateTime(zone)
        val today = now.date
        val nowMinutes = now.time.hour * 60 + now.time.minute
        val holidayDayType = resolveHolidayDayType(today)

        val results = mutableListOf<UpcomingDeparture>()
        for (lineId in lineIds) {
            val bundle = bundles[lineId] ?: continue
            projectForLine(
                bundle = bundle,
                today = today,
                nowMinutes = nowMinutes,
                holidayDayType = holidayDayType,
                lineId = lineId,
                direction = direction,
                limit = limit,
                out = results,
            )
        }
        return results.sortedBy { it.minutesAway }.take(limit)
    }

    private fun resolveHolidayDayType(date: LocalDate): String? {
        val mmdd = "${pad(date.monthNumber)}-${pad(date.dayOfMonth)}"
        return when (mmdd) {
            "01-01", "05-01", "10-28", "12-25", "12-26" -> "sun"
            "08-15" -> "aug_15"
            "12-24", "12-31" -> "dec_24_31"
            "01-02", "01-06", "11-17" -> "sat"
            else -> null
        }
    }

    private fun projectForLine(
        bundle: SyrmosSchedulesService.LineSchedule,
        today: LocalDate,
        nowMinutes: Int,
        holidayDayType: String?,
        lineId: String,
        direction: Direction,
        limit: Int,
        out: MutableList<UpcomingDeparture>,
    ) {
        val descriptors = mutableListOf<Pair<String, Int>>().apply {
            add(dayTypeFor(today, holidayDayType) to 0)
            if (nowMinutes < 4 * 60) {
                val y = today.minusOneDay()
                add(dayTypeFor(y, holidayDayType = null) to -24 * 60)
            }
        }

        for ((dayType, shiftMinutes) in descriptors) {
            // Honor schedule_rules: skip if line is CLOSED at the current time.
            // Without this, the projector emits departures from any band whose
            // window contains "now", even when the line operating window doesn't.
            val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: continue
            if (!rule.is247) {
                val openMin = rule.openTime.toMinutesOfDay()
                val closeMin = rule.closeTime.toMinutesOfDay()
                if (openMin != null && closeMin != null) {
                    val effectiveClose = if (closeMin <= openMin) closeMin + 24 * 60 else closeMin
                    val effectiveNow = nowMinutes + shiftMinutes
                    if (effectiveNow < openMin || effectiveNow > effectiveClose) continue
                }
            }

            val bands = bundle.bands.filter { it.dayType == dayType }
                .sortedBy { it.timeStart.toMinutesOfDay() ?: 0 }
            for (band in bands) {
                projectBand(
                    band = band,
                    shiftMinutes = shiftMinutes,
                    nowMinutes = nowMinutes,
                    lineId = lineId,
                    direction = direction,
                    limit = limit - out.size,
                    out = out,
                )
                if (out.size >= limit) return
            }
        }
    }

    private fun projectBand(
        band: SyrmosSchedulesService.BandEntry,
        shiftMinutes: Int,
        nowMinutes: Int,
        lineId: String,
        direction: Direction,
        limit: Int,
        out: MutableList<UpcomingDeparture>,
    ) {
        val rawStart = band.timeStart.toMinutesOfDay() ?: return
        val rawEnd = band.timeEnd.toMinutesOfDay() ?: return
        val start = rawStart + shiftMinutes
        val end = rawEnd + shiftMinutes
        if (end < start) return
        val headway = if (band.headwayMinutes > 0.0) band.headwayMinutes else return

        // Advance to first slot >= now
        var slot = start.toDouble()
        if (slot < nowMinutes) {
            val skips = ((nowMinutes - slot) / headway).toLong().coerceAtLeast(0L)
            slot = start + skips * headway
            while (slot < nowMinutes) slot += headway
        }

        var added = 0
        while (slot <= end && added < limit) {
            val slotMin = slot.roundToInt()
            val displayMinutes = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60)
            val hh = pad(displayMinutes / 60)
            val mm = pad(displayMinutes % 60)
            val minutesAway = (slotMin - nowMinutes).coerceAtLeast(0)
            out += UpcomingDeparture(
                time = "$hh:$mm",
                minutesAway = minutesAway,
                direction = direction,
                lineId = lineId,
                notes = band.label.ifBlank { null },
            )
            slot += headway
            added++
        }
    }

    private fun dayTypeFor(date: LocalDate, holidayDayType: String?): String {
        if (holidayDayType != null) return holidayDayType
        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> "mon_thu"
            DayOfWeek.FRIDAY -> "fri"
            DayOfWeek.SATURDAY -> "sat"
            DayOfWeek.SUNDAY -> "sun"
            else -> "mon_thu"
        }
    }
}

private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

private fun String.toMinutesOfDay(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun LocalDate.minusOneDay(): LocalDate {
    if (dayOfMonth > 1) return LocalDate(year, monthNumber, dayOfMonth - 1)
    val prevMonth = if (monthNumber > 1) monthNumber - 1 else 12
    val prevYear = if (monthNumber > 1) year else year - 1
    return LocalDate(prevYear, prevMonth, daysInMonth(prevYear, prevMonth))
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}
