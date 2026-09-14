import XCTest
@testable import Syrmos

/// Mirrors fixtures/journeys/accessibility.json case-for-case, so the iOS
/// AccessibilityDisclosure, the Kotlin AccessibilityDisclosure and web
/// web-accessibility.js agree on step-free disclosure (Phase R).
final class AccessibilityDisclosureTests: XCTestCase {

    private func leg(_ id: String, _ access: String) -> AccessibilityLeg {
        AccessibilityLeg(id: id, accessibility: access)
    }

    private func assertInfo(
        _ info: AccessibilityInfo,
        _ confidence: AccessibilityConfidence,
        _ code: String,
        _ unknown: [String],
        _ unavailable: [String],
        file: StaticString = #filePath, line: UInt = #line
    ) {
        XCTAssertEqual(info.confidence, confidence, "confidence", file: file, line: line)
        XCTAssertEqual(info.explanationCode, code, "code", file: file, line: line)
        XCTAssertEqual(info.unknownLegIds, unknown, "unknownLegIds", file: file, line: line)
        XCTAssertEqual(info.unavailableLegIds, unavailable, "unavailableLegIds", file: file, line: line)
    }

    func testNotRequested() {
        assertInfo(
            AccessibilityDisclosure.forOption(legs: [leg("r1", "unknown")], preference: "none"),
            .verified, "not_requested", [], []
        )
    }
    func testAllVerified() {
        assertInfo(
            AccessibilityDisclosure.forOption(legs: [leg("r1", "verified"), leg("r2", "verified")], preference: "stepFree"),
            .verified, "step_free_verified", [], []
        )
    }
    func testOneUnknown() {
        assertInfo(
            AccessibilityDisclosure.forOption(
                legs: [leg("r1", "verified"), leg("t1", "unknown"), leg("r2", "verified")],
                preference: "stepFree"
            ),
            .unknown, "step_free_unknown", ["t1"], []
        )
    }
    func testUnavailableWinsOverUnknown() {
        assertInfo(
            AccessibilityDisclosure.forOption(
                legs: [leg("r1", "unknown"), leg("r2", "unavailable")],
                preference: "stepFree"
            ),
            .unavailable, "step_free_unavailable", ["r1"], ["r2"]
        )
    }
    func testWalkLegsIgnoredForDefaultState() {
        assertInfo(
            AccessibilityDisclosure.forOption(legs: [leg("w1", "verified"), leg("r1", "verified")], preference: "stepFree"),
            .verified, "step_free_verified", [], []
        )
    }
}
