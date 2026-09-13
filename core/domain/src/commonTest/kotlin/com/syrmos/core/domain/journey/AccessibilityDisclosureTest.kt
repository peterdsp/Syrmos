package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.AccessibilityPreference
import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegAccessibility
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors fixtures/journeys/accessibility.json case-for-case, so the Kotlin
 * AccessibilityDisclosure and web web-accessibility.js agree on step-free
 * disclosure for the same golden inputs (Phase R accessibility unknowns).
 */
class AccessibilityDisclosureTest {

    private val date = LocalDate.parse("2026-01-15")

    private fun leg(id: String, kind: LegKind, access: LegAccessibility) =
        Leg(id = id, kind = kind, fromId = "a", toId = "b", serviceDate = date, accessibility = access)

    private fun option(vararg legs: Leg) = JourneyOption(
        id = "o", requestId = "r", legs = legs.toList(),
        transferCount = legs.count { it.kind == LegKind.TRANSFER },
        feasibility = Feasibility(FeasibilityStatus.UNKNOWN, explanationCode = "seed"),
    )

    private fun assertInfo(
        info: AccessibilityInfo,
        confidence: AccessibilityConfidence,
        code: String,
        unknown: List<String>,
        unavailable: List<String>,
    ) {
        assertEquals(confidence, info.confidence, "confidence")
        assertEquals(code, info.explanationCode, "code")
        assertEquals(unknown, info.unknownLegIds, "unknownLegIds")
        assertEquals(unavailable, info.unavailableLegIds, "unavailableLegIds")
    }

    @Test fun not_requested() = assertInfo(
        AccessibilityDisclosure.forOption(
            option(leg("r1", LegKind.RIDE, LegAccessibility.UNKNOWN)),
            AccessibilityPreference.NONE,
        ),
        AccessibilityConfidence.VERIFIED, "not_requested", emptyList(), emptyList(),
    )

    @Test fun all_verified() = assertInfo(
        AccessibilityDisclosure.forOption(
            option(
                leg("r1", LegKind.RIDE, LegAccessibility.VERIFIED),
                leg("r2", LegKind.RIDE, LegAccessibility.VERIFIED),
            ),
            AccessibilityPreference.STEP_FREE,
        ),
        AccessibilityConfidence.VERIFIED, "step_free_verified", emptyList(), emptyList(),
    )

    @Test fun one_unknown() = assertInfo(
        AccessibilityDisclosure.forOption(
            option(
                leg("r1", LegKind.RIDE, LegAccessibility.VERIFIED),
                leg("t1", LegKind.TRANSFER, LegAccessibility.UNKNOWN),
                leg("r2", LegKind.RIDE, LegAccessibility.VERIFIED),
            ),
            AccessibilityPreference.STEP_FREE,
        ),
        AccessibilityConfidence.UNKNOWN, "step_free_unknown", listOf("t1"), emptyList(),
    )

    @Test fun unavailable_wins_over_unknown() = assertInfo(
        AccessibilityDisclosure.forOption(
            option(
                leg("r1", LegKind.RIDE, LegAccessibility.UNKNOWN),
                leg("r2", LegKind.RIDE, LegAccessibility.UNAVAILABLE),
            ),
            AccessibilityPreference.STEP_FREE,
        ),
        AccessibilityConfidence.UNAVAILABLE, "step_free_unavailable", listOf("r1"), listOf("r2"),
    )

    @Test fun walk_legs_ignored_for_default_state() = assertInfo(
        AccessibilityDisclosure.forOption(
            option(
                leg("w1", LegKind.WALK, LegAccessibility.VERIFIED),
                leg("r1", LegKind.RIDE, LegAccessibility.VERIFIED),
            ),
            AccessibilityPreference.STEP_FREE,
        ),
        AccessibilityConfidence.VERIFIED, "step_free_verified", emptyList(), emptyList(),
    )
}
