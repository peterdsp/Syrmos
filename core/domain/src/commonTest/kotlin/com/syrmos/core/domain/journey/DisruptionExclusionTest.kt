package com.syrmos.core.domain.journey

import com.syrmos.core.domain.assistant.AdvisorySeverity
import com.syrmos.core.domain.assistant.ServiceNotice
import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mirrors fixtures/journeys/disruption.json case-for-case, so the Kotlin
 * DisruptionExclusion and web web-disruption.js produce identical results for the
 * same golden inputs (Phase R disruption exclusions / S10 suspended segment).
 */
class DisruptionExclusionTest {

    private val date = LocalDate.parse("2026-01-15")

    private fun notice(id: String, severity: AdvisorySeverity, lines: List<String>) =
        ServiceNotice(id = id, text = id, affectedLineIds = lines, severity = severity)

    private fun ride(lineId: String) =
        Leg(id = "ride-$lineId", kind = LegKind.RIDE, fromId = "a", toId = "b", lineId = lineId, serviceDate = date)

    private fun transfer() =
        Leg(id = "t", kind = LegKind.TRANSFER, fromId = "b", toId = "b", serviceDate = date)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.TRANSFER },
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "seed"),
    )

    // ---- suspendedLineIds ----

    @Test fun suspended_none() =
        assertEquals(emptySet(), DisruptionExclusion.suspendedLineIds(emptyList()))

    @Test fun suspended_info_not_suspended() =
        assertEquals(
            emptySet(),
            DisruptionExclusion.suspendedLineIds(listOf(notice("n1", AdvisorySeverity.INFO, listOf("M3")))),
        )

    @Test fun suspended_warning_not_suspended() =
        assertEquals(
            emptySet(),
            DisruptionExclusion.suspendedLineIds(listOf(notice("n2", AdvisorySeverity.WARNING, listOf("M1")))),
        )

    @Test fun suspended_closure_suspends() =
        assertEquals(
            setOf("m1"),
            DisruptionExclusion.suspendedLineIds(listOf(notice("n3", AdvisorySeverity.CLOSURE, listOf("M1")))),
        )

    @Test fun suspended_closure_multi_line() =
        assertEquals(
            setOf("m1", "t6"),
            DisruptionExclusion.suspendedLineIds(
                listOf(
                    notice("n4", AdvisorySeverity.CLOSURE, listOf("M1", "T6")),
                    notice("n5", AdvisorySeverity.INFO, listOf("M3")),
                ),
            ),
        )

    @Test fun suspended_closed_variant() =
        // The feed's "closed" maps to CLOSURE via fromRaw, so the caller-normalized
        // notice suspends the line (parity with the fixture's closed_variant case).
        assertEquals(
            setOf("m1"),
            DisruptionExclusion.suspendedLineIds(
                listOf(notice("n6", AdvisorySeverity.fromRaw("closed"), listOf("M1"))),
            ),
        )

    // ---- optionUsesSuspended ----

    @Test fun uses_clear_of_suspension() =
        assertEquals(
            emptySet(),
            DisruptionExclusion.optionUsesSuspended(option(ride("M2"), transfer(), ride("M3")), setOf("m1")),
        )

    @Test fun uses_rides_suspended() =
        assertEquals(
            setOf("m1"),
            DisruptionExclusion.optionUsesSuspended(option(ride("M1"), transfer(), ride("M2")), setOf("m1")),
        )

    @Test fun uses_empty_suspension() =
        assertEquals(emptySet(), DisruptionExclusion.optionUsesSuspended(option(ride("M1")), emptySet()))

    // ---- classify ----

    @Test fun classify_routed_no_disruption() {
        val out = DisruptionExclusion.classify(
            avoidingOptions = listOf(option(ride("M2"))),
            naiveOptions = listOf(option(ride("M2"))),
            notices = emptyList(),
        )
        assertTrue(out is DisruptionOutcome.Routed)
        assertEquals(emptySet(), out.excludedLineIds)
    }

    @Test fun classify_routed_around_closure() {
        val out = DisruptionExclusion.classify(
            avoidingOptions = listOf(option(ride("M2"), transfer(), ride("M3"))),
            naiveOptions = listOf(option(ride("M1"))),
            notices = listOf(notice("c1", AdvisorySeverity.CLOSURE, listOf("M1"))),
        )
        assertTrue(out is DisruptionOutcome.Routed)
        assertEquals(setOf("m1"), out.excludedLineIds)
    }

    @Test fun classify_routed_unrelated_closure() {
        val out = DisruptionExclusion.classify(
            avoidingOptions = listOf(option(ride("M1"), transfer(), ride("M2"))),
            naiveOptions = listOf(option(ride("M1"), transfer(), ride("M2"))),
            notices = listOf(notice("c9", AdvisorySeverity.CLOSURE, listOf("T6"))),
        )
        assertTrue(out is DisruptionOutcome.Routed)
        assertEquals(emptySet(), out.excludedLineIds)
    }

    @Test fun classify_suspended_no_alternative() {
        val out = DisruptionExclusion.classify(
            avoidingOptions = emptyList(),
            naiveOptions = listOf(option(ride("M1"))),
            notices = listOf(notice("c1", AdvisorySeverity.CLOSURE, listOf("M1"))),
        )
        assertTrue(out is DisruptionOutcome.Suspended)
        assertEquals(setOf("m1"), out.affectedLineIds)
        assertEquals(listOf("c1"), out.notices.map { it.id })
    }

    @Test fun classify_no_route_at_all() {
        val out = DisruptionExclusion.classify(emptyList(), emptyList(), emptyList())
        assertEquals(DisruptionOutcome.NoRoute, out)
    }

    @Test fun classify_no_route_unrelated_naive() {
        val out = DisruptionExclusion.classify(
            avoidingOptions = emptyList(),
            naiveOptions = listOf(option(ride("M2"))),
            notices = listOf(notice("c1", AdvisorySeverity.CLOSURE, listOf("M1"))),
        )
        assertEquals(DisruptionOutcome.NoRoute, out)
    }
}
