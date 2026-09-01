package com.syrmos.core.domain.usecase

import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.domain.schedule.ServiceDayResolver
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
    private val stationOffsets: StationOffsetsRepository? = null,
) {
    /**
     * Returns departures at the line origin. When [stationId] is provided
     * and the station-offsets repository has data for the (line, direction,
     * station) triple, every emitted HH:MM is shifted by the station's
     * cumulative minutes-from-origin so the caller sees the time the train
     * passes through THIS station, not the terminal. Without offsets, the
     * legacy behaviour returns unchanged.
     */
    fun invoke(
        lineIds: List<String>,
        direction: Direction,
        limit: Int = 8,
        stationId: String? = null,
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
            // Resolve the per-station offset for the requested direction
            // explicitly. Outbound and inbound differ on T6 (33 vs 35 min)
            // and T7 (54 vs 59 min); using the wrong direction's offset
            // pushes the displayed countdown off by 2-5 minutes.
            val offsetMinutes = if (stationId != null) {
                stationOffsets?.offsetFor(
                    lineId = lineId,
                    direction = direction.name.lowercase(),
                    stationId = stationId,
                )?.minutesFromOrigin ?: 0
            } else {
                0
            }
            projectForLine(
                bundle = bundle,
                today = today,
                nowMinutes = nowMinutes,
                holidayDayType = holidayDayType,
                lineId = lineId,
                direction = direction,
                limit = limit,
                offsetMinutes = offsetMinutes,
                out = results,
            )
        }
        val sorted = results.sortedBy { it.minutesAway }.take(limit).toMutableList()

        // M3 callers pass lineIds = [M3, M3_AIR]. After 23:00 every day the
        // airport branch is closed, so projectForLine emits nothing for
        // M3_AIR and the user has no signal of when the next Airport-bound
        // train runs. Scan forward up to a week to find the next slot from
        // the M3_AIR bundle and append it as a look-ahead row so the
        // dashboard always shows "next Airport train" even when it's 7+
        // hours out.
        val wantsAirport = "M3_AIR" in lineIds && stationId != null
            && stationId !in line3AirportOnlyStations
        val hasAirport = sorted.any { it.lineId == "M3_AIR" }
        if (wantsAirport && !hasAirport) {
            val bundle = bundles["M3_AIR"]
            if (bundle != null) {
                val offset = stationOffsets?.offsetFor(
                    lineId = "M3_AIR",
                    direction = Direction.OUTBOUND.name.lowercase(),
                    stationId = stationId!!,
                )?.minutesFromOrigin ?: 0
                val lookahead = nextAirportLookahead(
                    bundle = bundle,
                    today = today,
                    nowMinutes = nowMinutes,
                    offsetMinutes = offset,
                )
                if (lookahead != null) sorted += lookahead
            }
        }
        return sorted
    }

    /**
     * True when any of [lineIds] runs 24h / overnight today: the day's rule is
     * `is247`, or its close time is at or before its open time (the service
     * window wraps past midnight, e.g. metro M2 and M3 on Saturday). Used to
     * suppress a misleading "last train tonight" on nights when trains never
     * actually stop.
     */
    fun isOvernightServiceToday(lineIds: List<String>): Boolean {
        val bundles = scheduleSync.lineBundles.value
        if (bundles.isEmpty()) return false
        val zone = TimeZone.of("Europe/Athens")
        val today = Clock.System.now().toLocalDateTime(zone).date
        val dayType = dayTypeFor(today, resolveHolidayDayType(today))
        for (lineId in lineIds) {
            val bundle = bundles[lineId] ?: continue
            val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: continue
            if (rule.is247) return true
            val openMin = rule.openTime.toMinutesOfDay()
            val closeMin = rule.closeTime.toMinutesOfDay()
            if (openMin != null && closeMin != null && closeMin <= openMin) return true
        }
        return false
    }

    /**
     * Projects a whole future service day from 00:00, for the assistant's
     * "this weekend / tomorrow / Saturday" questions. [dayOffset] is days from
     * today (0 = today from midnight). Returns the day's earliest [limit]
     * departures at [stationId]; empty when bundles aren't loaded yet.
     */
    fun invokeForDay(
        lineIds: List<String>,
        direction: Direction,
        dayOffset: Int,
        limit: Int = 8,
        stationId: String? = null,
    ): List<UpcomingDeparture> {
        val bundles = scheduleSync.lineBundles.value
        if (bundles.isEmpty()) return emptyList()

        val zone = TimeZone.of("Europe/Athens")
        val today = Clock.System.now().toLocalDateTime(zone).date
        val targetDate = today.plusDays(dayOffset)
        val holidayDayType = resolveHolidayDayType(targetDate)

        val results = mutableListOf<UpcomingDeparture>()
        for (lineId in lineIds) {
            val bundle = bundles[lineId] ?: continue
            val offsetMinutes = if (stationId != null) {
                stationOffsets?.offsetFor(
                    lineId = lineId,
                    direction = direction.name.lowercase(),
                    stationId = stationId,
                )?.minutesFromOrigin ?: 0
            } else {
                0
            }
            projectForLine(
                bundle = bundle,
                today = targetDate,
                nowMinutes = 0,
                holidayDayType = holidayDayType,
                lineId = lineId,
                direction = direction,
                limit = limit,
                offsetMinutes = offsetMinutes,
                out = results,
            )
        }
        return results.sortedBy { it.minutesAway }.take(limit)
    }

    private fun nextAirportLookahead(
        bundle: SyrmosSchedulesService.LineSchedule,
        today: LocalDate,
        nowMinutes: Int,
        offsetMinutes: Int,
    ): UpcomingDeparture? {
        for (dayOffset in 0 until 7) {
            val date = today.plusDays(dayOffset)
            val dayType = dayTypeFor(date, resolveHolidayDayType(date))
            val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: continue
            if (!rule.is247) {
                val openMin = rule.openTime.toMinutesOfDay()
                val closeMin = rule.closeTime.toMinutesOfDay()
                if (openMin != null && closeMin != null) {
                    val effectiveClose = if (closeMin <= openMin) closeMin + 24 * 60 else closeMin
                    if (dayOffset == 0 && nowMinutes > effectiveClose) continue
                }
            }
            val bands = bundle.bands.filter { it.dayType == dayType }
                .sortedBy { it.timeStart.toMinutesOfDay() ?: 0 }
            for (band in bands) {
                val rawStart = band.timeStart.toMinutesOfDay() ?: continue
                val rawEnd = band.timeEnd.toMinutesOfDay() ?: continue
                val headway = band.headwayMinutes
                if (headway <= 0.0) continue
                val end = rawEnd + if (rawEnd < rawStart) 24 * 60 else 0
                var slot = rawStart.toDouble()
                if (dayOffset == 0 && slot < nowMinutes) {
                    val skips = ((nowMinutes - slot) / headway).toLong().coerceAtLeast(0L)
                    slot = rawStart + skips * headway
                    while (slot < nowMinutes) slot += headway
                }
                if (slot <= end) {
                    val totalMinutes = slot.roundToInt() + offsetMinutes + dayOffset * 24 * 60
                    val display = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
                    val hh = pad(display / 60)
                    val mm = pad(display % 60)
                    val minutesAway = (totalMinutes - nowMinutes).coerceAtLeast(0)
                    return UpcomingDeparture(
                        time = "$hh:$mm",
                        minutesAway = minutesAway,
                        direction = Direction.OUTBOUND,
                        lineId = "M3_AIR",
                        serviceType = "airport",
                    )
                }
            }
        }
        return null
    }

    private companion object {
        private val line3AirportOnlyStations = setOf("M3_PAL", "M3_PEA", "M3_KO2", "M3_AER")
    }

    // Delegates to the shared calendar so the holiday table is defined once.
    private fun resolveHolidayDayType(date: LocalDate): String? =
        ServiceDayResolver.holidayDayType(date)

    private fun projectForLine(
        bundle: SyrmosSchedulesService.LineSchedule,
        today: LocalDate,
        nowMinutes: Int,
        holidayDayType: String?,
        lineId: String,
        direction: Direction,
        limit: Int,
        offsetMinutes: Int,
        out: MutableList<UpcomingDeparture>,
    ) {
        // Descriptors to try, in order:
        //  1. today's day-type, no shift.
        //  2. yesterday's day-type with -24h shift — for bands that wrap
        //     past midnight (e.g. 'sat 22:00 -> 00:20' covers Sunday 00:15).
        //  3. yesterday's day-type with no shift — for bands whose
        //     timeStart is already in today's clock domain because the
        //     operator tags them under yesterday's service-day. OASA does
        //     this for Saturday's 24/7 overnight: 'sat 00:30 -> 05:30
        //     saturday_overnight_24_7' literally means Sunday clock
        //     00:30-05:30 inside Saturday's service window.
        val todayDayType = dayTypeFor(today, holidayDayType)
        // Yesterday's "next-day extension" bands have clock times in the
        // small-hours of today (e.g. 'sat 00:30 -> 05:30 saturday_overnight').
        // Only THOSE are valid under the yesterday+shift=0 descriptor, not
        // regular daytime bands of yesterday like 'sat 05:30 -> 10:00'.
        val nextDayExtensionCutoffMinutes = 5 * 60
        data class Descriptor(val dt: String, val shift: Int, val nextDayOnly: Boolean)
        val descriptors = mutableListOf<Descriptor>().apply {
            add(Descriptor(todayDayType, 0, nextDayOnly = false))
            if (nowMinutes < 6 * 60) {
                val y = today.minusOneDay()
                val yesterdayDayType = dayTypeFor(y, holidayDayType = null)
                add(Descriptor(yesterdayDayType, -24 * 60, nextDayOnly = false))
                if (yesterdayDayType != todayDayType) {
                    add(Descriptor(yesterdayDayType, 0, nextDayOnly = true))
                }
            }
        }

        for (descriptor in descriptors) {
            val dayType = descriptor.dt
            val shiftMinutes = descriptor.shift
            // Honor schedule_rules: skip if line is CLOSED at the current time.
            // Without this, the projector emits departures from any band whose
            // window contains "now", even when the line operating window doesn't.
            val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: continue
            if (!rule.is247) {
                val openMin = rule.openTime.toMinutesOfDay()
                val closeMin = rule.closeTime.toMinutesOfDay()
                if (openMin != null && closeMin != null) {
                    // OASA's published rule.closeTime is when the station
                    // officially shuts; bands often extend past it because
                    // trains that left origin before close are still running.
                    // M2 mon_thu rule closes 00:06 but the late_night band
                    // runs until 00:20; M3 mon_thu rule closes 00:01 but the
                    // band reaches 00:20. Take the max so the late-night
                    // entries between rule close and band end aren't thrown
                    // away. Mirrors the iOS / Pi projector fix.
                    val bandMaxEnd = bundle.bands
                        .filter { it.dayType == dayType }
                        .mapNotNull { b ->
                            val rs = b.timeStart.toMinutesOfDay() ?: return@mapNotNull null
                            val re = b.timeEnd.toMinutesOfDay() ?: return@mapNotNull null
                            re + (if (re < rs) 24 * 60 else 0)
                        }
                        .maxOrNull() ?: closeMin
                    val ruleClose = if (closeMin <= openMin) closeMin + 24 * 60 else closeMin
                    val effectiveClose = maxOf(ruleClose, bandMaxEnd)
                    // Subtract the shift so today's nowMinutes lands inside
                    // the descriptor's clock domain (shift = -1440 for the
                    // yesterday overnight pass). The previous formula used
                    // `+ shift`, mapping today's 00:03 to yesterday's -1437
                    // and throwing every late-night band away. Then ONLY
                    // reject when fully past effectiveClose + 120 min slack
                    // — a future band of today's day-type emits future
                    // slots naturally, so at 02:09 Thursday with mon_thu
                    // open 05:30 we still need to enumerate the band and
                    // emit today's 05:30 first train (3h 21min) instead of
                    // rolling to Friday's 00:03 (21h 55min away).
                    val effectiveNow = nowMinutes - shiftMinutes
                    if (effectiveNow > effectiveClose + 120) continue
                }
            }

            val openMinRule = rule.openTime.toMinutesOfDay()
            val bands = bundle.bands.filter { band ->
                if (band.dayType != dayType) return@filter false
                if (descriptor.nextDayOnly) {
                    val rs = band.timeStart.toMinutesOfDay() ?: return@filter false
                    rs < nextDayExtensionCutoffMinutes
                } else if (descriptor.shift == 0 && !rule.is247 && openMinRule != null) {
                    val rs = band.timeStart.toMinutesOfDay() ?: return@filter true
                    !(rs < openMinRule && rs < nextDayExtensionCutoffMinutes)
                } else {
                    true
                }
            }.sortedBy { it.timeStart.toMinutesOfDay() ?: 0 }
            for (band in bands) {
                projectBand(
                    band = band,
                    shiftMinutes = shiftMinutes,
                    nowMinutes = nowMinutes,
                    lineId = lineId,
                    direction = direction,
                    limit = limit - out.size,
                    offsetMinutes = offsetMinutes,
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
        offsetMinutes: Int,
        out: MutableList<UpcomingDeparture>,
    ) {
        val rawStart = band.timeStart.toMinutesOfDay() ?: return
        val rawEnd = band.timeEnd.toMinutesOfDay() ?: return
        val start = rawStart + shiftMinutes
        // Bands that close past midnight (e.g. M2 sat 22:00 -> 00:20) ship
        // with rawEnd < rawStart because timeEnd is the next calendar day.
        // Wrap them forward 24h so [start, end] is monotonic and 22:45
        // still lands inside the window instead of falling off the early
        // return below.
        val end = rawEnd + shiftMinutes + (if (rawEnd < rawStart) 24 * 60 else 0)
        if (end < start) return
        val headway = if (band.headwayMinutes > 0.0) band.headwayMinutes else return

        // Advance to the first slot whose ARRIVAL AT THIS STATION (slot +
        // offsetMinutes) is in the future. The previous comparison against
        // raw slot dropped any train that had already left the line origin,
        // even when offsetMinutes meant it was still en-route to this stop,
        // shifting the first displayed countdown ~offsetMinutes into the
        // future.
        var slot = start.toDouble()
        val stationSlot = slot + offsetMinutes
        if (stationSlot < nowMinutes) {
            val skips = ((nowMinutes - stationSlot) / headway).toLong().coerceAtLeast(0L)
            slot = start + skips * headway
            while (slot + offsetMinutes < nowMinutes) slot += headway
        }

        var added = 0
        while (slot <= end && added < limit) {
            // The slot describes when the train LEAVES the line origin.
            // For station-aware queries, the train passes through this
            // station offsetMinutes later, so we shift both the display
            // time and the countdown by that amount.
            val slotMin = slot.roundToInt() + offsetMinutes
            val displayMinutes = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60)
            val hh = pad(displayMinutes / 60)
            val mm = pad(displayMinutes % 60)
            val minutesAway = (slotMin - nowMinutes).coerceAtLeast(0)
            out += UpcomingDeparture(
                time = "$hh:$mm",
                minutesAway = minutesAway,
                direction = direction,
                lineId = lineId,
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

private fun LocalDate.plusDays(days: Int): LocalDate {
    if (days == 0) return this
    var d = this
    repeat(days) { d = d.plusOneDay() }
    return d
}

private fun LocalDate.plusOneDay(): LocalDate {
    val maxDay = daysInMonth(year, monthNumber)
    if (dayOfMonth < maxDay) return LocalDate(year, monthNumber, dayOfMonth + 1)
    val nextMonth = if (monthNumber < 12) monthNumber + 1 else 1
    val nextYear = if (monthNumber < 12) year else year + 1
    return LocalDate(nextYear, nextMonth, 1)
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
