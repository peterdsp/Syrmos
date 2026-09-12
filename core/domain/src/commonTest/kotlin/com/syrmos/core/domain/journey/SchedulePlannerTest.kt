package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.TimingKind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Kotlin peer of web `web-tests/schedule-plan.test.js`, mirroring the golden
 * cases in fixtures/journeys/schedule.json. Proves the schedule-aware pass assigns
 * the same scheduled instants and that feasibility becomes real (comfortable/tight)
 * rather than estimated. Cases are inlined (commonTest has no file IO); the JSON
 * fixture is the shared source of truth for the web side.
 */
class SchedulePlannerTest {

    private val date = LocalDate.parse("2026-01-15")

    private fun ride(id: String, line: String, from: String, to: String) =
        Leg(id = id, kind = LegKind.RIDE, fromId = from, toId = to, lineId = line, serviceDate = date)

    private fun transfer(id: String, minSeconds: Int) =
        Leg(id = id, kind = LegKind.TRANSFER, fromId = "x", toId = "x", serviceDate = date, transferMinimumSeconds = minSeconds)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.RIDE } - 1,
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "pending"),
    )

    private fun tt(departures: Map<String, List<String>>, legSeconds: Map<String, Int>) =
        SchedulePlanner.Timetable(
            departures = departures.mapValues { (_, v) -> v.map { Instant.parse(it) } },
            legSeconds = legSeconds,
        )

    @Test
    fun directGetsARealScheduledClock() {
        val scheduled = SchedulePlanner.assignSchedule(
            option(ride("r1", "M1", "PIR", "OMO")),
            Instant.parse("2026-01-15T08:00:00+02:00"),
            tt(
                mapOf("M1|PIR" to listOf("2026-01-15T07:58:00+02:00", "2026-01-15T08:05:00+02:00", "2026-01-15T08:12:00+02:00")),
                mapOf("M1|PIR|OMO" to 900),
            ),
        )
        assertEquals(Instant.parse("2026-01-15T08:05:00+02:00"), scheduled.departureInstant)
        assertEquals(Instant.parse("2026-01-15T08:20:00+02:00"), scheduled.arrivalInstant)
        assertEquals(TimingKind.SCHEDULED, scheduled.legs.first().timingKind)
        val feas = FeasibilityCalculator.forOption(scheduled)
        assertEquals(FeasibilityStatus.COMFORTABLE, feas.status)
        assertEquals("direct", feas.explanationCode)
    }

    @Test
    fun tightTransferHasA120sMargin() {
        val scheduled = SchedulePlanner.assignSchedule(
            option(ride("r1", "M1", "PIR", "OMO"), transfer("t1", 120), ride("r2", "M2", "OMO", "SYN")),
            Instant.parse("2026-01-15T08:00:00+02:00"),
            tt(
                mapOf("M1|PIR" to listOf("2026-01-15T08:00:00+02:00"), "M2|OMO" to listOf("2026-01-15T08:19:00+02:00", "2026-01-15T08:31:00+02:00")),
                mapOf("M1|PIR|OMO" to 900, "M2|OMO|SYN" to 300),
            ),
        )
        val feas = FeasibilityCalculator.forOption(scheduled)
        assertEquals(FeasibilityStatus.TIGHT, feas.status)
        assertEquals(120, feas.minimumMarginSeconds)
        assertEquals("transfer_tight", feas.explanationCode)
    }

    @Test
    fun comfortableTransferHasA300sMargin() {
        val scheduled = SchedulePlanner.assignSchedule(
            option(ride("r1", "M1", "PIR", "OMO"), transfer("t1", 120), ride("r2", "M2", "OMO", "SYN")),
            Instant.parse("2026-01-15T08:00:00+02:00"),
            tt(
                mapOf("M1|PIR" to listOf("2026-01-15T08:00:00+02:00"), "M2|OMO" to listOf("2026-01-15T08:22:00+02:00")),
                mapOf("M1|PIR|OMO" to 900, "M2|OMO|SYN" to 300),
            ),
        )
        val feas = FeasibilityCalculator.forOption(scheduled)
        assertEquals(FeasibilityStatus.COMFORTABLE, feas.status)
        assertEquals(300, feas.minimumMarginSeconds)
        assertEquals("transfer_comfortable", feas.explanationCode)
    }

    @Test
    fun noServiceAfterTransferIsUnknown() {
        val scheduled = SchedulePlanner.assignSchedule(
            option(ride("r1", "M1", "PIR", "OMO"), transfer("t1", 120), ride("r2", "M2", "OMO", "SYN")),
            Instant.parse("2026-01-15T08:00:00+02:00"),
            tt(
                mapOf("M1|PIR" to listOf("2026-01-15T08:00:00+02:00"), "M2|OMO" to emptyList()),
                mapOf("M1|PIR|OMO" to 900, "M2|OMO|SYN" to 300),
            ),
        )
        assertEquals(TimingKind.UNKNOWN, scheduled.legs.last().timingKind)
        val feas = FeasibilityCalculator.forOption(scheduled)
        assertEquals(FeasibilityStatus.UNKNOWN, feas.status)
        assertEquals("transfer_unknown", feas.explanationCode)
    }

    @Test
    fun pastMidnightPicksTheNextServiceDate() {
        val scheduled = SchedulePlanner.assignSchedule(
            option(ride("r1", "M1", "PIR", "OMO")),
            Instant.parse("2026-01-15T23:50:00+02:00"),
            tt(mapOf("M1|PIR" to listOf("2026-01-16T00:10:00+02:00")), mapOf("M1|PIR|OMO" to 900)),
        )
        assertEquals(Instant.parse("2026-01-16T00:10:00+02:00"), scheduled.departureInstant)
        assertEquals(Instant.parse("2026-01-16T00:25:00+02:00"), scheduled.arrivalInstant)
        assertEquals(TimingKind.SCHEDULED, scheduled.legs.first().timingKind)
    }
}
