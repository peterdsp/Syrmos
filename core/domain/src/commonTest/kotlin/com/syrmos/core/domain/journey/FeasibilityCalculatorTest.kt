package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors fixtures/journeys/feasibility.json case-for-case, so the Kotlin
 * FeasibilityCalculator and web SyrmosFeasibility produce identical results for
 * the same golden inputs (prompt 7.3). Times and margins here are the exact
 * numbers in that fixture.
 */
class FeasibilityCalculatorTest {

    private val date = LocalDate.parse("2026-01-15")
    private fun at(hms: String) = Instant.parse("2026-01-15T$hms+02:00")

    private fun ride(id: String, arr: Instant? = null, dep: Instant? = null, unc: Int? = null) =
        Leg(id = id, kind = LegKind.RIDE, fromId = "a", toId = "b",
            arrivalInstant = arr, departureInstant = dep, uncertaintySeconds = unc, serviceDate = date)

    private fun transfer(id: String, min: Int? = null) =
        Leg(id = id, kind = LegKind.TRANSFER, fromId = "a", toId = "a",
            transferMinimumSeconds = min, serviceDate = date)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.TRANSFER },
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "seed"),
    )

    private fun assertF(f: Feasibility, status: FeasibilityStatus, margin: Int?, code: String, legId: String?) {
        assertEquals(status, f.status, "status")
        assertEquals(margin, f.minimumMarginSeconds, "margin")
        assertEquals(code, f.explanationCode, "code")
        assertEquals(legId, f.limitingLegId, "limitingLegId")
    }

    @Test fun direct() =
        assertF(FeasibilityCalculator.forOption(option(ride("r1", arr = at("09:12:00"), dep = at("08:42:00")))),
            FeasibilityStatus.COMFORTABLE, null, "direct", null)

    @Test fun comfortable240() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", 60), ride("r2", dep = at("09:05:00")))),
            FeasibilityStatus.COMFORTABLE, 240, "transfer_comfortable", "t1")

    @Test fun tight120() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", 60), ride("r2", dep = at("09:03:00")))),
            FeasibilityStatus.TIGHT, 120, "transfer_tight", "t1")

    @Test fun boundary179Tight() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", 0), ride("r2", dep = at("09:02:59")))),
            FeasibilityStatus.TIGHT, 179, "transfer_tight", "t1")

    @Test fun boundary180Comfortable() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", 0), ride("r2", dep = at("09:03:00")))),
            FeasibilityStatus.COMFORTABLE, 180, "transfer_comfortable", "t1")

    @Test fun missedNegative() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", 120), ride("r2", dep = at("09:01:00")))),
            FeasibilityStatus.MISSED, -60, "transfer_missed", "t1")

    @Test fun unknownMissingArrival() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = null), transfer("t1", 60), ride("r2", dep = at("09:05:00")))),
            FeasibilityStatus.UNKNOWN, null, "transfer_unknown", "t1")

    @Test fun defaultBufferTight() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")), transfer("t1", null), ride("r2", dep = at("09:03:00")))),
            FeasibilityStatus.TIGHT, 60, "transfer_tight", "t1")

    @Test fun uncertaintyReducesMargin() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00"), unc = 120), transfer("t1", 60), ride("r2", dep = at("09:05:00")))),
            FeasibilityStatus.TIGHT, 120, "transfer_tight", "t1")

    @Test fun worstOfTwoTransfers() =
        assertF(FeasibilityCalculator.forOption(option(
            ride("r1", arr = at("09:00:00")),
            transfer("t1", 60),
            ride("r2", arr = at("09:20:00"), dep = at("09:05:00")),
            transfer("t2", 60),
            ride("r3", dep = at("09:23:00")))),
            FeasibilityStatus.TIGHT, 120, "transfer_tight", "t2")

    @Test fun closureOverrides() =
        assertF(FeasibilityCalculator.forOption(
            option(ride("r1", arr = at("09:00:00")), transfer("t1", 60), ride("r2", dep = at("09:05:00"))),
            closedLegIds = setOf("r2")),
            FeasibilityStatus.MISSED, null, "segment_closed", "r2")
}
