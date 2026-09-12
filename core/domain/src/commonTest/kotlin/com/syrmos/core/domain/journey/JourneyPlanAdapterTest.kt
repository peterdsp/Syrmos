package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.Ranking
import com.syrmos.core.model.journey.TimingKind
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.planner.JourneySegment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android/KMP mirror of web `web-tests/journey-plan.test.js`: the topology
 * `JourneyResult` composes into a ranked `JourneyOption` with honest estimated
 * timing and the same feasibility rules as web (unknown for a transfer we cannot
 * time, direct for a single ride).
 */
class JourneyPlanAdapterTest {

    private val date = LocalDate.parse("2026-01-15")

    private fun seg(line: String, from: String, to: String, stations: Int, minutes: Int) =
        JourneySegment(
            lineId = line, lineName = line,
            fromStationId = from, fromStationName = from,
            toStationId = to, toStationName = to,
            stationCount = stations, estimatedMinutes = minutes,
        )

    @Test
    fun crossLineResultBecomesOneEstimatedOptionWithATransfer() {
        val result = JourneyResult(
            segments = listOf(
                seg("M1", "PIR", "OMONIA", 8, 16),
                seg("M2", "OMONIA", "ELL", 10, 20),
            ),
            totalMinutes = 36,
            transferCount = 1,
        )
        val options = JourneyPlanAdapter.toOptions(result, Ranking.FASTEST, date)
        assertEquals(1, options.size, "one real option, not padded")
        val opt = options.first()
        assertEquals(1, opt.transferCount)
        assertEquals(listOf(LegKind.RIDE, LegKind.TRANSFER, LegKind.RIDE), opt.legs.map { it.kind })
        for (leg in opt.legs) {
            assertEquals(TimingKind.ESTIMATED, leg.timingKind)
            assertNull(leg.departureInstant, "no invented departure clock")
            assertNull(leg.arrivalInstant, "no invented arrival clock")
        }
        assertNull(opt.arrivalInstant)
        assertNull(opt.walkingSeconds, "walking unknown, never invented")
        // 36 travel + 1 transfer * 3 est = 39 min
        assertEquals(39 * 60, opt.durationSeconds)
        assertEquals(FeasibilityStatus.UNKNOWN, opt.feasibility.status)
        assertNull(opt.feasibility.minimumMarginSeconds)
        assertEquals("fastest", opt.rankingBadge)
    }

    @Test
    fun sameLineResultIsASingleRideDirect() {
        val result = JourneyResult(
            segments = listOf(seg("M1", "MON", "THI", 2, 2)),
            totalMinutes = 2,
            transferCount = 0,
        )
        val opt = JourneyPlanAdapter.toOptions(result, Ranking.FASTEST, date).single()
        assertEquals(0, opt.transferCount)
        assertEquals(1, opt.legs.count { it.kind == LegKind.RIDE })
        assertEquals(FeasibilityStatus.COMFORTABLE, opt.feasibility.status)
        assertEquals("direct", opt.feasibility.explanationCode)
    }

    @Test
    fun aSuppliedTimetableUpgradesFeasibilityFromEstimatedToReal() {
        val result = JourneyResult(
            segments = listOf(
                seg("M1", "PIR", "OMONIA", 8, 16),
                seg("M2", "OMONIA", "ELL", 10, 20),
            ),
            totalMinutes = 36,
            transferCount = 1,
        )
        val timetable = SchedulePlanner.Timetable(
            departures = mapOf(
                "M1|PIR" to listOf(kotlinx.datetime.Instant.parse("2026-01-15T08:00:00+02:00")),
                // arr OMONIA 08:15, ready 08:17 (+120), next dep 08:19 -> margin 120 -> tight
                "M2|OMONIA" to listOf(kotlinx.datetime.Instant.parse("2026-01-15T08:19:00+02:00")),
            ),
            legSeconds = mapOf("M1|PIR|OMONIA" to 900, "M2|OMONIA|ELL" to 300),
        )
        val opt = JourneyPlanAdapter.toOptions(
            result, Ranking.FASTEST, date,
            timetable = timetable,
            requestedInstant = kotlinx.datetime.Instant.parse("2026-01-15T08:00:00+02:00"),
        ).single()
        // No longer estimated/unknown: a real tight connection with a 120s margin.
        assertEquals(FeasibilityStatus.TIGHT, opt.feasibility.status)
        assertEquals(120, opt.feasibility.minimumMarginSeconds)
        assertEquals(TimingKind.SCHEDULED, opt.legs.first { it.kind == LegKind.RIDE }.timingKind)
    }

    @Test
    fun nullOrEmptyResultYieldsNoOptions() {
        assertTrue(JourneyPlanAdapter.toOptions(null, Ranking.FASTEST, date).isEmpty())
        assertTrue(JourneyPlanAdapter.toOptions(
            JourneyResult(emptyList(), 0, 0), Ranking.FASTEST, date,
        ).isEmpty())
    }
}
