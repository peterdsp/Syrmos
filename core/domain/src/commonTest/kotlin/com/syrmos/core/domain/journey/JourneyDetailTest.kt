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
import kotlin.test.assertTrue

/**
 * Kotlin peer of web `web-tests/journey-detail.test.js`, mirroring the golden
 * cases in fixtures/journeys/detail.json. Proves the S05 timeline transform emits
 * the same ordered rows on Kotlin as on web/iOS (node roles, intermediate-stop
 * counts, transfer-gap fallback, and null-clock honesty).
 */
class JourneyDetailTest {
    private val date = LocalDate.parse("2026-01-15")
    private fun inst(s: String) = Instant.parse(s)

    private fun ride(id: String, line: String, from: String, to: String, stops: List<String>,
                     dep: String?, arr: String?, timing: TimingKind = TimingKind.SCHEDULED) =
        Leg(id = id, kind = LegKind.RIDE, fromId = from, toId = to, lineId = line,
            orderedStopIds = stops, departureInstant = dep?.let(::inst), arrivalInstant = arr?.let(::inst),
            serviceDate = date, timingKind = timing)

    private fun transfer(id: String, from: String, to: String, minSeconds: Int? = null, walk: Boolean = false) =
        Leg(id = id, kind = if (walk) LegKind.WALK else LegKind.TRANSFER, fromId = from, toId = to,
            orderedStopIds = listOf(from, to), serviceDate = date, timingKind = TimingKind.ESTIMATED,
            transferMinimumSeconds = minSeconds)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.RIDE } - 1,
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "pending"),
    )

    @Test
    fun twoRidesOneTransfer() {
        val opt = option(
            ride("ride-0", "M1", "M1_PIR", "M1_MON", listOf("M1_PIR", "M1_FAL", "M1_TAV", "M1_MON"),
                "2026-01-15T08:00:00+02:00", "2026-01-15T08:15:00+02:00"),
            transfer("transfer-1", "M1_MON", "M3_MON", minSeconds = 180),
            ride("ride-2", "M3", "M3_MON", "M3_SYN", listOf("M3_MON", "M3_SYN"),
                "2026-01-15T08:20:00+02:00", "2026-01-15T08:25:00+02:00"),
        )
        val rows = JourneyDetail.timeline(opt)
        assertEquals(
            listOf("board", "stops", "alight", "transfer", "board", "alight"),
            rows.map { it.kind },
        )
        assertEquals(
            listOf(JourneyDetail.Node.ORIGIN, JourneyDetail.Node.INTERCHANGE, JourneyDetail.Node.INTERCHANGE, JourneyDetail.Node.INTERCHANGE, JourneyDetail.Node.DESTINATION),
            rows.mapNotNull { it.node },
        )
        val board0 = rows[0]
        assertEquals("M1", board0.lineId); assertEquals("M1_MON", board0.towardsId)
        assertEquals(inst("2026-01-15T08:00:00+02:00"), board0.clock)
        assertEquals("scheduled", board0.timingKind)
        assertEquals(2, rows[1].count)
        assertEquals(180, rows[3].seconds)
    }

    @Test
    fun directRideKeepsNullClocks() {
        val opt = option(
            ride("ride-0", "M2", "M2_SYN", "M2_ELL", listOf("M2_SYN", "M2_SYG", "M2_ELL"),
                null, null, timing = TimingKind.ESTIMATED),
        )
        val rows = JourneyDetail.timeline(opt)
        assertEquals(listOf("board", "stops", "alight"), rows.map { it.kind })
        assertNull(rows.first().clock)
        assertNull(rows.last().clock)
        assertEquals(JourneyDetail.Node.ORIGIN, rows.first().node)
        assertEquals(JourneyDetail.Node.DESTINATION, rows.last().node)
        assertEquals(1, rows[1].count)
    }

    @Test
    fun walkTransferFallsBackToGap() {
        val opt = option(
            ride("ride-0", "M1", "M1_PIR", "M1_MON", listOf("M1_PIR", "M1_MON"),
                "2026-01-15T08:05:00+02:00", "2026-01-15T08:15:00+02:00"),
            transfer("walk-1", "M1_MON", "M3_MON", walk = true),
            ride("ride-2", "M3", "M3_MON", "M3_SYN", listOf("M3_MON", "M3_SYN"),
                "2026-01-15T08:20:00+02:00", "2026-01-15T08:24:00+02:00"),
        )
        val rows = JourneyDetail.timeline(opt)
        assertTrue(rows.none { it.kind == "stops" }, "no stops disclosure for 2-stop legs")
        val walk = rows.first { it.kind == "walk" }
        assertEquals(300, walk.seconds)
    }
}
