import Foundation

// Syrmos 3.0 Phase R accessibility-unknown disclosure (iOS mirror).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.AccessibilityDisclosure and web
// web-accessibility.js exactly. Per-leg accessibility is verified | unavailable |
// unknown; absence of data is unknown, never verified. When the rider asks for
// step-free travel the option's disclosure takes the WORST leg state: any
// unavailable leg makes the route not step-free, otherwise any unknown leg means
// step-free is not confirmed, otherwise verified. When step-free was not
// requested the disclosure is inert.
//
// Covered case-for-case by fixtures/journeys/accessibility.json.

enum AccessibilityConfidence: String, Equatable {
    case verified
    case unknown
    case unavailable
}

/// A leg reduced to what accessibility disclosure needs. `accessibility` uses the
/// same lowercase wire tokens as the fixtures ("verified" | "unavailable" | "unknown").
struct AccessibilityLeg: Equatable {
    let id: String
    let accessibility: String
    init(id: String, accessibility: String) {
        self.id = id
        self.accessibility = accessibility
    }
}

struct AccessibilityInfo: Equatable {
    let confidence: AccessibilityConfidence
    let explanationCode: String
    let unknownLegIds: [String]
    let unavailableLegIds: [String]
}

enum AccessibilityDisclosure {

    /// `preference` uses the wire token "stepFree" (or "none").
    static func forOption(legs: [AccessibilityLeg], preference: String) -> AccessibilityInfo {
        guard preference == "stepFree" else {
            return AccessibilityInfo(
                confidence: .verified,
                explanationCode: "not_requested",
                unknownLegIds: [],
                unavailableLegIds: []
            )
        }
        let unknownLegIds = legs.filter { $0.accessibility == "unknown" }.map { $0.id }
        let unavailableLegIds = legs.filter { $0.accessibility == "unavailable" }.map { $0.id }
        let confidence: AccessibilityConfidence
        if !unavailableLegIds.isEmpty { confidence = .unavailable }
        else if !unknownLegIds.isEmpty { confidence = .unknown }
        else { confidence = .verified }
        let code: String
        switch confidence {
        case .unavailable: code = "step_free_unavailable"
        case .unknown: code = "step_free_unknown"
        case .verified: code = "step_free_verified"
        }
        return AccessibilityInfo(
            confidence: confidence,
            explanationCode: code,
            unknownLegIds: unknownLegIds,
            unavailableLegIds: unavailableLegIds
        )
    }
}
