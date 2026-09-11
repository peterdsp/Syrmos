package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.TimingKind
import kotlinx.datetime.Instant

/**
 * Schedule-aware planning pass, the Kotlin peer of web `web-schedule-plan.js`.
 *
 * Turns a topology [JourneyOption] (legs with no clock) into a time-aware one by
 * assigning REAL scheduled departure/arrival instants from a [Timetable], so the
 * shared [FeasibilityCalculator] computes honest margins (comfortable/tight) from
 * actual service headways instead of leaving everything estimated. A direct trip
 * gets a real clock; a transfer gets a real margin.
 *
 * Rules (identical to web):
 *  - First ride: earliest departure at/after the requested instant.
 *  - Next ride: earliest departure at/after (previous arrival + transferMinimum),
 *    so the assigned plan is always physically catchable; the margin then measures
 *    the spare time beyond that minimum.
 *  - A leg with no available departure or unknown travel time stays unscheduled
 *    (instants null, timingKind UNKNOWN); later legs cannot be timed either.
 *  - All arithmetic is on absolute epoch seconds, so Europe/Athens DST and
 *    past-midnight service fall out of the offsets in the instants themselves.
 */
object SchedulePlanner {
    const val DEFAULT_TRANSFER_SECONDS = 120

    /**
     * @param departures "lineId|stationId" -> departure instants (any order).
     * @param legSeconds "lineId|fromId|toId" -> ride travel time in seconds.
     */
    data class Timetable(
        val departures: Map<String, List<Instant>>,
        val legSeconds: Map<String, Int>,
    )

    private fun key2(a: String, b: String) = "$a|$b"
    private fun key3(a: String, b: String, c: String) = "$a|$b|$c"

    private fun nextDeparture(tt: Timetable, lineId: String?, stationId: String, readyEpoch: Long): Long? {
        if (lineId == null) return null
        val list = tt.departures[key2(lineId, stationId)] ?: return null
        var best: Long? = null
        for (d in list) {
            val e = d.epochSeconds
            if (e < readyEpoch) continue
            if (best == null || e < best!!) best = e
        }
        return best
    }

    fun assignSchedule(
        option: JourneyOption,
        requestedInstant: Instant,
        timetable: Timetable,
        defaultTransferSeconds: Int = DEFAULT_TRANSFER_SECONDS,
    ): JourneyOption {
        var readyEpoch: Long? = requestedInstant.epochSeconds
        var pendingTransferMin = 0
        var firstDep: Long? = null
        var lastArr: Long? = null
        var timedAll = true

        val newLegs = ArrayList<Leg>(option.legs.size)
        for (leg in option.legs) {
            when (leg.kind) {
                LegKind.TRANSFER, LegKind.WALK -> {
                    pendingTransferMin = leg.transferMinimumSeconds ?: defaultTransferSeconds
                    newLegs.add(leg)
                }
                LegKind.RIDE -> {
                    val ready = readyEpoch
                    if (ready == null || !timedAll) {
                        newLegs.add(unscheduled(leg)); timedAll = false
                    } else {
                        val boardReady = ready + (if (firstDep == null) 0 else pendingTransferMin)
                        val dep = nextDeparture(timetable, leg.lineId, leg.fromId, boardReady)
                        val travel = timetable.legSeconds[key3(leg.lineId ?: "", leg.fromId, leg.toId)]
                        if (dep == null || travel == null) {
                            newLegs.add(unscheduled(leg)); timedAll = false
                        } else {
                            val arr = dep + travel
                            newLegs.add(
                                leg.copy(
                                    departureInstant = Instant.fromEpochSeconds(dep),
                                    arrivalInstant = Instant.fromEpochSeconds(arr),
                                    timingKind = TimingKind.SCHEDULED,
                                ),
                            )
                            if (firstDep == null) firstDep = dep
                            lastArr = arr
                            readyEpoch = arr
                            pendingTransferMin = 0
                        }
                    }
                }
            }
        }

        return option.copy(
            legs = newLegs,
            departureInstant = firstDep?.let { Instant.fromEpochSeconds(it) },
            arrivalInstant = if (timedAll) lastArr?.let { Instant.fromEpochSeconds(it) } else null,
            durationSeconds = if (timedAll && firstDep != null && lastArr != null) (lastArr!! - firstDep!!).toInt() else null,
        )
    }

    private fun unscheduled(leg: Leg): Leg =
        leg.copy(departureInstant = null, arrivalInstant = null, timingKind = TimingKind.UNKNOWN)
}
