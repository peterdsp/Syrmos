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
import kotlin.test.assertNull

/**
 * Kotlin peer of web `web-tests/arrive-by.test.js`, mirroring
 * fixtures/journeys/arrive-by.json. Backward (arrive-by) scheduling and
 * last-connection-home assign the LATEST feasible departures.
 */
class SchedulePlannerArriveByTest {

    private val date = LocalDate.parse("2026-01-15")
    private fun ride(id: String, line: String, from: String, to: String) =
        Leg(id = id, kind = LegKind.RIDE, fromId = from, toId = to, lineId = line, serviceDate = date)
    private fun transfer(id: String, min: Int) =
        Leg(id = id, kind = LegKind.TRANSFER, fromId = "x", toId = "x", serviceDate = date, transferMinimumSeconds = min)
    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.RIDE } - 1,
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "pending"),
    )
    private fun tt(departures: Map<String, List<String>>, legSeconds: Map<String, Int>) =
        SchedulePlanner.Timetable(departures.mapValues { (_, v) -> v.map { Instant.parse(it) } }, legSeconds)

    @Test
    fun arriveByDirectPicksLatestDeparture() {
        val out = SchedulePlanner.assignScheduleArriveBy(
            option(ride("r1", "M1", "PIR", "OMO")),
            Instant.parse("2026-01-15T09:00:00+02:00"),
            tt(
                mapOf("M1|PIR" to listOf("2026-01-15T08:30:00+02:00", "2026-01-15T08:40:00+02:00", "2026-01-15T08:45:00+02:00", "2026-01-15T08:50:00+02:00")),
                mapOf("M1|PIR|OMO" to 900),
            ),
        )
        assertEquals(Instant.parse("2026-01-15T08:45:00+02:00"), out.departureInstant)
        assertEquals(Instant.parse("2026-01-15T09:00:00+02:00"), out.arrivalInstant)
        assertEquals(FeasibilityStatus.COMFORTABLE, FeasibilityCalculator.forOption(out).status)
    }

    @Test
    fun arriveByTransferChainsBackwardToTheLeaveByTime() {
        val out = SchedulePlanner.assignScheduleArriveBy(
            option(ride("r1", "M1", "PIR", "OMO"), transfer("t1", 120), ride("r2", "M2", "OMO", "SYN")),
            Instant.parse("2026-01-15T09:00:00+02:00"),
            tt(
                mapOf(
                    "M1|PIR" to listOf("2026-01-15T08:30:00+02:00", "2026-01-15T08:35:00+02:00", "2026-01-15T08:40:00+02:00"),
                    "M2|OMO" to listOf("2026-01-15T08:50:00+02:00", "2026-01-15T08:55:00+02:00", "2026-01-15T09:00:00+02:00"),
                ),
                mapOf("M1|PIR|OMO" to 900, "M2|OMO|SYN" to 300),
            ),
        )
        assertEquals(Instant.parse("2026-01-15T08:35:00+02:00"), out.departureInstant, "leave by 08:35")
        assertEquals(Instant.parse("2026-01-15T09:00:00+02:00"), out.arrivalInstant)
        val feas = FeasibilityCalculator.forOption(out)
        assertEquals(FeasibilityStatus.COMFORTABLE, feas.status)
        assertEquals(180, feas.minimumMarginSeconds)
    }

    @Test
    fun arriveByImpossibleLeavesNoJourney() {
        val out = SchedulePlanner.assignScheduleArriveBy(
            option(ride("r1", "M1", "PIR", "OMO")),
            Instant.parse("2026-01-15T08:10:00+02:00"),
            tt(mapOf("M1|PIR" to listOf("2026-01-15T08:00:00+02:00", "2026-01-15T08:05:00+02:00")), mapOf("M1|PIR|OMO" to 900)),
        )
        assertNull(out.departureInstant)
        assertEquals(TimingKind.UNKNOWN, out.legs.first().timingKind)
    }

    @Test
    fun lastConnectionTakesTheLatestDepartureAcrossMidnight() {
        val out = SchedulePlanner.lastConnection(
            option(ride("r1", "M1", "PIR", "OMO")),
            tt(mapOf("M1|PIR" to listOf("2026-01-15T23:30:00+02:00", "2026-01-15T23:45:00+02:00", "2026-01-16T00:05:00+02:00")), mapOf("M1|PIR|OMO" to 900)),
        )
        assertEquals(Instant.parse("2026-01-16T00:05:00+02:00"), out.departureInstant, "the last train home")
        assertEquals(Instant.parse("2026-01-16T00:20:00+02:00"), out.arrivalInstant)
    }
}
