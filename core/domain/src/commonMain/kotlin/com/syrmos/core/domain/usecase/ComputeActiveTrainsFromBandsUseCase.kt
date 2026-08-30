package com.syrmos.core.domain.usecase

import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.StationOffsetsRepository
import com.syrmos.core.network.SyrmosLivePositionsService
import com.syrmos.core.network.SyrmosSchedulesService
import kotlin.math.roundToInt
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Offline projector for the moving metro/tram dots on the map. Enumerates
 * every train that is currently somewhere along its line from the bundled
 * frequency_bands + schedule_rules, so the map shows live-looking vehicles
 * even when the Pi's `/api/live-positions` feed is unreachable or empty.
 *
 * Source-of-truth mirror of the Pi server projector
 * (`ops/syrmos-api/syrmos_admin/projector.py` -> `active_trains`) and the
 * descriptor stack in [ComputeDeparturesFromBandsUseCase.projectForLine]. The
 * web client's `computeActiveTrainsFromBundle` is the same logic; keep all
 * three in lockstep.
 *
 * A train is "active" when its origin-departure minute is <= now AND it has
 * not yet reached its terminus (0 <= elapsed <= totalTravel). The output
 * matches [SyrmosLivePositionsService.LiveTrain] one-to-one so the caller can
 * feed it straight into the same `simulateTrains` interpolation the online
 * path uses; `TrainSimulator` recovers each train's absolute origin epoch from
 * `elapsedMinutes` against the snapshot's `generatedAt`, so the offline dots
 * glide by wall clock exactly like the live ones.
 */
class ComputeActiveTrainsFromBandsUseCase(
    private val scheduleSync: ScheduleSyncRepository,
    private val stationOffsets: StationOffsetsRepository,
) {
    /**
     * Returns the trains active at Athens now across [OFFLINE_PROJECTED_LINES].
     * Empty when the bundles aren't loaded yet or every line is closed.
     */
    fun invoke(): List<SyrmosLivePositionsService.LiveTrain> {
        val bundles = scheduleSync.lineBundles.value
        if (bundles.isEmpty()) return emptyList()

        val zone = TimeZone.of("Europe/Athens")
        val now: LocalDateTime = Clock.System.now().toLocalDateTime(zone)
        val today = now.date
        // Fractional so the very first placement is sub-minute accurate; after
        // that TrainSimulator drives the glide by wall clock.
        val nowMinutes = now.time.hour * 60 + now.time.minute + now.time.second / 60.0
        val holidayDayType = resolveHolidayDayType(today)

        val out = mutableListOf<SyrmosLivePositionsService.LiveTrain>()
        // Global dedupe by (line, direction, rounded slot): overlapping bands
        // or the today/yesterday descriptors can enumerate the same physical
        // train twice.
        val seen = mutableSetOf<Triple<String, String, Int>>()

        for (lineId in OFFLINE_PROJECTED_LINES) {
            val bundle = bundles[lineId] ?: continue
            projectLine(
                bundle = bundle,
                lineId = lineId,
                today = today,
                nowMinutes = nowMinutes,
                holidayDayType = holidayDayType,
                out = out,
                seen = seen,
            )
        }
        return out
    }

    private fun projectLine(
        bundle: SyrmosSchedulesService.LineSchedule,
        lineId: String,
        today: LocalDate,
        nowMinutes: Double,
        holidayDayType: String?,
        out: MutableList<SyrmosLivePositionsService.LiveTrain>,
        seen: MutableSet<Triple<String, String, Int>>,
    ) {
        val todayDayType = dayTypeFor(today, holidayDayType)
        val yesterday = today.minusOneDay()
        val yesterdayDayType = dayTypeFor(yesterday, holidayDayType = null)

        // Descriptors, in order:
        //  1. today, no shift.
        //  2. yesterday, -24h shift — a train that left origin yesterday and
        //     hasn't reached terminus yet is still active. Always included
        //     (the rule-close gate below skips it outside the overnight tail).
        //  3. yesterday, no shift, next-day-only — yesterday's small-hours
        //     (< 05:00) bands projected at TODAY's clock, i.e. Saturday-night
        //     24h service (M2/M3/T6/T7) running past midnight into Sunday.
        data class Descriptor(val dt: String, val shift: Int, val nextDayOnly: Boolean)
        val descriptors = mutableListOf(
            Descriptor(todayDayType, 0, nextDayOnly = false),
            Descriptor(yesterdayDayType, -24 * 60, nextDayOnly = false),
        )
        if (nowMinutes < 6 * 60 && yesterdayDayType != todayDayType) {
            descriptors.add(Descriptor(yesterdayDayType, 0, nextDayOnly = true))
        }

        for (descriptor in descriptors) {
            val dayType = descriptor.dt
            val shift = descriptor.shift
            val nextDayOnly = descriptor.nextDayOnly
            val rule = bundle.rules.firstOrNull { it.dayType == dayType } ?: continue

            // Honor schedule_rules: skip this descriptor when now is well
            // outside the day's service window. Mirrors the Pi projector's
            // 120-min slack on both ends so a train that departed just before
            // open / just after close is still found while interpolating its
            // tail. is247 lines are never gated here.
            if (!rule.is247) {
                val openMin = rule.openTime.toMinutesOfDay()
                val closeMin = rule.closeTime.toMinutesOfDay()
                if (openMin != null && closeMin != null) {
                    val ruleClose = if (closeMin <= openMin) closeMin + 24 * 60 else closeMin
                    // Bands frequently run past the published station-close
                    // (M1 mon_thu to 01:30 vs rule 00:30); take the max so
                    // late trains still in transit are counted.
                    var bandMaxEnd = ruleClose
                    for (b in bundle.bands) {
                        if (b.dayType != dayType) continue
                        val rs = b.timeStart.toMinutesOfDay() ?: continue
                        val re = b.timeEnd.toMinutesOfDay() ?: continue
                        val bandEnd = re + (if (re < rs) 24 * 60 else 0)
                        if (bandEnd > bandMaxEnd) bandMaxEnd = bandEnd
                    }
                    val effectiveClose = maxOf(ruleClose, bandMaxEnd)
                    // Subtract the shift so today's now lands inside the
                    // descriptor's clock domain (shift = -1440 for yesterday).
                    val effectiveNow = nowMinutes - shift
                    // The next-day-extension descriptor deliberately runs
                    // before the day's open time, so skip the lower bound for
                    // it and only reject when fully past close.
                    val tooEarly = !nextDayOnly && effectiveNow < openMin - 120
                    if (tooEarly || effectiveNow > effectiveClose + 120) continue
                }
            }

            val openMinRule = rule.openTime.toMinutesOfDay()
            val bands = bundle.bands.filter { band ->
                if (band.dayType != dayType) return@filter false
                val rs = band.timeStart.toMinutesOfDay()
                when {
                    nextDayOnly ->
                        // Only the overnight-extension bands (< 05:00) belong
                        // to the next civil day.
                        rs != null && rs < NEXT_DAY_EXTENSION_CUTOFF_MIN
                    shift == 0 && !rule.is247 && openMinRule != null ->
                        // On today's non-247 pass, drop yesterday-tagged
                        // small-hours bands that sit before the rule's open.
                        !(rs != null && rs < openMinRule && rs < NEXT_DAY_EXTENSION_CUTOFF_MIN)
                    else -> true
                }
            }

            for (band in bands) {
                val rawStart = band.timeStart.toMinutesOfDay() ?: continue
                val rawEnd = band.timeEnd.toMinutesOfDay() ?: continue
                val headway = band.headwayMinutes
                if (headway <= 0.0) continue
                val start = rawStart + shift
                // Bands that close past midnight ship with rawEnd < rawStart
                // (timeEnd is the next calendar day); wrap +24h so [start, end]
                // is monotonic.
                val end = rawEnd + shift + (if (rawEnd < rawStart) 24 * 60 else 0)
                if (end < start) continue

                val bandDir = (band.direction ?: "both").lowercase()
                for (directionKey in DIRECTION_KEYS) {
                    if (bandDir != "both" && bandDir != directionKey) continue
                    val travel = stationOffsets.stopsFor(lineId, directionKey)
                        .maxOfOrNull { it.minutesFromOrigin } ?: 0
                    if (travel <= 0) continue

                    // Earliest slot that could still be active: now - travel,
                    // snapped forward onto the band's headway grid.
                    val earliest = maxOf(start.toDouble(), nowMinutes - travel)
                    val skips = maxOf(0L, ((earliest - start) / headway).toLong())
                    var slot = start + skips * headway
                    while (slot <= end && slot <= nowMinutes + 0.5) {
                        val elapsed = nowMinutes - slot
                        if (elapsed in 0.0..travel.toDouble()) {
                            val key = Triple(lineId, directionKey, slot.roundToInt())
                            if (seen.add(key)) {
                                out += SyrmosLivePositionsService.LiveTrain(
                                    lineId = lineId,
                                    directionKey = directionKey,
                                    originDepartureMinute = slot,
                                    elapsedMinutes = elapsed,
                                    totalTravelMinutes = travel,
                                    serviceType = serviceType(band.label),
                                )
                            }
                        }
                        slot += headway
                    }
                }
            }
        }
    }

    private fun serviceType(bandLabel: String): String {
        val label = bandLabel.lowercase()
        return if ("late" in label || "overnight" in label) "late_night" else "regular"
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

    private companion object {
        /// The metro/tram lines the offline projector covers. Mirrors the web
        /// client's OFFLINE_PROJECTED_LINES. M3_AIR and the suburban A1-A4
        /// (scheduled-trip lines) are intentionally excluded.
        private val OFFLINE_PROJECTED_LINES = listOf("M1", "M2", "M3", "T6", "T7")
        private val DIRECTION_KEYS = listOf("outbound", "inbound")
        private const val NEXT_DAY_EXTENSION_CUTOFF_MIN = 5 * 60
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
