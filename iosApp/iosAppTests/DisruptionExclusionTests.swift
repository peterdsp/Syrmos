import XCTest
@testable import Syrmos

/// Mirrors fixtures/journeys/disruption.json case-for-case, so the iOS
/// DisruptionExclusion, the Kotlin DisruptionExclusion and web web-disruption.js
/// produce identical results for the same golden inputs (Phase R).
final class DisruptionExclusionTests: XCTestCase {

    private func notice(_ id: String, _ severity: String, _ lines: [String]) -> DisruptionNotice {
        DisruptionNotice(id: id, severity: severity, affectedLineIds: lines)
    }
    private func ride(_ lineId: String) -> DisruptionLeg { DisruptionLeg(kind: "ride", lineId: lineId) }
    private func transfer() -> DisruptionLeg { DisruptionLeg(kind: "transfer") }
    private func option(_ legs: DisruptionLeg...) -> DisruptionOption { DisruptionOption(legs: legs) }

    // MARK: suspendedLineIds

    func testSuspendedNone() {
        XCTAssertEqual(DisruptionExclusion.suspendedLineIds([]), [])
    }
    func testSuspendedInfoNotSuspended() {
        XCTAssertEqual(DisruptionExclusion.suspendedLineIds([notice("n1", "info", ["M3"])]), [])
    }
    func testSuspendedWarningNotSuspended() {
        XCTAssertEqual(DisruptionExclusion.suspendedLineIds([notice("n2", "warning", ["M1"])]), [])
    }
    func testSuspendedClosureSuspends() {
        XCTAssertEqual(DisruptionExclusion.suspendedLineIds([notice("n3", "closure", ["M1"])]), ["m1"])
    }
    func testSuspendedClosureMultiLine() {
        XCTAssertEqual(
            DisruptionExclusion.suspendedLineIds([
                notice("n4", "closure", ["M1", "T6"]),
                notice("n5", "info", ["M3"]),
            ]),
            ["m1", "t6"]
        )
    }

    // MARK: optionUsesSuspended

    func testUsesClearOfSuspension() {
        XCTAssertEqual(
            DisruptionExclusion.optionUsesSuspended(option(ride("M2"), transfer(), ride("M3")), ["m1"]),
            []
        )
    }
    func testUsesRidesSuspended() {
        XCTAssertEqual(
            DisruptionExclusion.optionUsesSuspended(option(ride("M1"), transfer(), ride("M2")), ["m1"]),
            ["m1"]
        )
    }
    func testUsesEmptySuspension() {
        XCTAssertEqual(DisruptionExclusion.optionUsesSuspended(option(ride("M1")), []), [])
    }

    // MARK: classify

    func testClassifyRoutedNoDisruption() {
        let out = DisruptionExclusion.classify(
            avoidingOptions: [option(ride("M2"))],
            naiveOptions: [option(ride("M2"))],
            notices: []
        )
        XCTAssertEqual(out, .routed(options: [option(ride("M2"))], excludedLineIds: []))
    }
    func testClassifyRoutedAroundClosure() {
        let out = DisruptionExclusion.classify(
            avoidingOptions: [option(ride("M2"), transfer(), ride("M3"))],
            naiveOptions: [option(ride("M1"))],
            notices: [notice("c1", "closure", ["M1"])]
        )
        guard case let .routed(_, excluded) = out else { return XCTFail("expected routed") }
        XCTAssertEqual(excluded, ["m1"])
    }
    func testClassifySuspendedNoAlternative() {
        let out = DisruptionExclusion.classify(
            avoidingOptions: [],
            naiveOptions: [option(ride("M1"))],
            notices: [notice("c1", "closure", ["M1"])]
        )
        guard case let .suspended(affected, notices) = out else { return XCTFail("expected suspended") }
        XCTAssertEqual(affected, ["m1"])
        XCTAssertEqual(notices.map { $0.id }, ["c1"])
    }
    func testClassifyNoRouteAtAll() {
        XCTAssertEqual(DisruptionExclusion.classify(avoidingOptions: [], naiveOptions: [], notices: []), .noRoute)
    }
    func testClassifyNoRouteUnrelatedNaive() {
        let out = DisruptionExclusion.classify(
            avoidingOptions: [],
            naiveOptions: [option(ride("M2"))],
            notices: [notice("c1", "closure", ["M1"])]
        )
        XCTAssertEqual(out, .noRoute)
    }
}
