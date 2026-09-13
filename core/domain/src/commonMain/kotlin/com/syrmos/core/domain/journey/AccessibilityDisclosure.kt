package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.AccessibilityPreference
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.LegAccessibility

/** How confidently an option meets a step-free request. Absence of data is UNKNOWN. */
enum class AccessibilityConfidence { VERIFIED, UNKNOWN, UNAVAILABLE }

/**
 * Honest step-free disclosure for one option (Phase R, "accessibility unknowns").
 * The worst leg state wins: any [LegAccessibility.UNAVAILABLE] leg makes the route
 * not step-free; otherwise any [LegAccessibility.UNKNOWN] leg means step-free is
 * not confirmed; otherwise verified. Both offending leg-id lists are surfaced so
 * the UI can point at the exact legs. When step-free was not requested the
 * disclosure is inert ([explanationCode] "not_requested").
 */
data class AccessibilityInfo(
    val confidence: AccessibilityConfidence,
    val explanationCode: String,
    val unknownLegIds: List<String>,
    val unavailableLegIds: List<String>,
)

/**
 * Pure, offline accessibility disclosure shared by web/iOS/Android. Mirrors web
 * `web-accessibility.js` and iOS `AccessibilityDisclosure.swift` exactly and is
 * covered case-for-case by `fixtures/journeys/accessibility.json`.
 */
object AccessibilityDisclosure {

    fun forOption(option: JourneyOption, preference: AccessibilityPreference): AccessibilityInfo {
        if (preference != AccessibilityPreference.STEP_FREE) {
            return AccessibilityInfo(
                confidence = AccessibilityConfidence.VERIFIED,
                explanationCode = "not_requested",
                unknownLegIds = emptyList(),
                unavailableLegIds = emptyList(),
            )
        }
        val unknown = option.legs.filter { it.accessibility == LegAccessibility.UNKNOWN }.map { it.id }
        val unavailable = option.legs.filter { it.accessibility == LegAccessibility.UNAVAILABLE }.map { it.id }
        val confidence = when {
            unavailable.isNotEmpty() -> AccessibilityConfidence.UNAVAILABLE
            unknown.isNotEmpty() -> AccessibilityConfidence.UNKNOWN
            else -> AccessibilityConfidence.VERIFIED
        }
        val code = when (confidence) {
            AccessibilityConfidence.UNAVAILABLE -> "step_free_unavailable"
            AccessibilityConfidence.UNKNOWN -> "step_free_unknown"
            AccessibilityConfidence.VERIFIED -> "step_free_verified"
        }
        return AccessibilityInfo(
            confidence = confidence,
            explanationCode = code,
            unknownLegIds = unknown,
            unavailableLegIds = unavailable,
        )
    }
}
