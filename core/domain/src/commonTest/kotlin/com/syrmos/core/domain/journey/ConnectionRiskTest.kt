package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Kotlin peer of web `web-tests/connection-risk.test.js`, mirroring the golden
 * cases in fixtures/journeys/connection-risk.json. Proves S07 risk numbers +
 * status are identical on Kotlin and web (and, later, iOS). Cases inlined
 * (commonTest has no file IO); the JSON fixture is the shared source of truth.
 */
class ConnectionRiskTest {
    private val date = LocalDate.parse("2026-01-15")
    private fun inst(s: String) = Instant.parse(s)

    private fun ride(id: String, line: String, from: String, to: String,
                     dep: String? = null, arr: String? = null, unc: Int? = null) =
        Leg(id = id, kind = LegKind.RIDE, fromId = from, toId = to, lineId = line,
            departureInstant = dep?.let(::inst), arrivalInstant = arr?.let(::inst),
            serviceDate = date, uncertaintySeconds = unc)

    private fun transfer(id: String, from: String, to: String, min: Int?) =
        Leg(id = id, kind = LegKind.TRANSFER, fromId = from, toId = to, serviceDate = date,
            transferMinimumSeconds = min)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.RIDE } - 1,
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "pending"),
    )

    @Test
    fun tightTransfer() {
        val opt = option(
            ride("r0", "M1", "PIR", "OMO", arr = "2026-01-15T08:15:00+02:00"),
            transfer("t1", "OMO", "OMO3", 120),
            ride("r2", "M2", "OMO3", "SYN", dep = "2026-01-15T08:18:00+02:00"),
        )
        val rs = ConnectionRisk.risks(opt)
        assertEquals(1, rs.size)
        assertEquals("OMO", rs[0].atStationId)
        assertEquals("M2", rs[0].toLineId)
        assertEquals(FeasibilityStatus.TIGHT, rs[0].status)
        assertEquals(180, rs[0].availableSeconds)
        assertEquals(120, rs[0].recommendedSeconds)
        assertEquals(FeasibilityStatus.TIGHT, ConnectionRisk.worstStatus(opt))
    }

    @Test
    fun missedMatchesSpecExample() {
        val opt = option(
            ride("r0", "M1", "PIR", "OMO", arr = "2026-01-15T08:15:00+02:00"),
            transfer("t1", "OMO", "OMO3", 300),
            ride("r2", "M2", "OMO3", "SYN", dep = "2026-01-15T08:17:00+02:00"),
        )
        val r = ConnectionRisk.risks(opt).single()
        assertEquals(FeasibilityStatus.MISSED, r.status)
        assertEquals(120, r.availableSeconds) // "2 min available"
        assertEquals(300, r.recommendedSeconds) // "allow 5 min"
    }

    @Test
    fun twoTransfersWorstWins() {
        val opt = option(
            ride("r0", "M1", "PIR", "OMO", arr = "2026-01-15T08:15:00+02:00"),
            transfer("t1", "OMO", "OMO2", 120),
            ride("r2", "M2", "OMO2", "SYN", dep = "2026-01-15T08:25:00+02:00", arr = "2026-01-15T08:35:00+02:00"),
            transfer("t3", "SYN", "SYN3", 120),
            ride("r4", "M3", "SYN3", "AIR", dep = "2026-01-15T08:36:00+02:00"),
        )
        val rs = ConnectionRisk.risks(opt)
        assertEquals(listOf(FeasibilityStatus.COMFORTABLE, FeasibilityStatus.MISSED), rs.map { it.status })
        assertEquals(FeasibilityStatus.MISSED, ConnectionRisk.worstStatus(opt))
        val concern = ConnectionRisk.nextConcern(opt, 0)
        assertEquals("M3", concern?.toLineId)
        assertNull(ConnectionRisk.nextConcern(opt, 5))
    }

    @Test
    fun unknownWhenDepartureMissing() {
        val opt = option(
            ride("r0", "M1", "PIR", "OMO", arr = "2026-01-15T08:15:00+02:00"),
            transfer("t1", "OMO", "OMO3", 120),
            ride("r2", "M2", "OMO3", "SYN", dep = null),
        )
        val r = ConnectionRisk.risks(opt).single()
        assertEquals(FeasibilityStatus.UNKNOWN, r.status)
        assertNull(r.availableSeconds)
        assertEquals(120, r.recommendedSeconds)
    }

    @Test
    fun directHasNoRisks() {
        val opt = option(
            ride("r0", "M2", "SYN", "ELL", dep = "2026-01-15T08:00:00+02:00", arr = "2026-01-15T08:20:00+02:00"),
        )
        assertEquals(emptyList(), ConnectionRisk.risks(opt))
        assertEquals(FeasibilityStatus.COMFORTABLE, ConnectionRisk.worstStatus(opt))
        assertNull(ConnectionRisk.nextConcern(opt, 0))
    }

    @Test
    fun tightBoundaryInclusiveAt179() {
        fun statusForGap(gap: Int): FeasibilityStatus {
            val dep = Instant.parse("2026-01-15T08:00:00+02:00") + gap.seconds
            val opt = option(
                ride("r0", "M1", "A", "B", arr = "2026-01-15T08:00:00+02:00"),
                transfer("t1", "B", "B2", 120),
                ride("r2", "M2", "B2", "C", dep = dep.toString()),
            )
            return ConnectionRisk.risks(opt).single().status
        }
        assertEquals(FeasibilityStatus.TIGHT, statusForGap(299))       // margin 179
        assertEquals(FeasibilityStatus.COMFORTABLE, statusForGap(300)) // margin 180
        assertEquals(FeasibilityStatus.MISSED, statusForGap(119))      // margin -1
    }
}
