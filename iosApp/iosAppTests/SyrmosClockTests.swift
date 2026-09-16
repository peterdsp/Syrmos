import XCTest
@testable import Syrmos

/// The capture harness is only trustworthy if an unpinned build is exactly the
/// old behaviour and a pinned one is genuinely frozen.
final class SyrmosClockTests: XCTestCase {

    func testAnUnpinnedClockIsJustTheSystemClock() {
        // No SYRMOS_CAPTURE_NOW in the test environment, so this is the shipping
        // path: `now` must track the wall clock and nothing may be suppressed.
        XCTAssertFalse(SyrmosClock.isPinned)
        XCTAssertFalse(SyrmosClock.animationsSuppressed)
        XCTAssertEqual(SyrmosClock.now.timeIntervalSince1970,
                       Date().timeIntervalSince1970, accuracy: 1.0)
    }

    func testTheClockAdvancesWhenItIsNotPinned() {
        let first = SyrmosClock.now
        Thread.sleep(forTimeInterval: 0.05)
        XCTAssertGreaterThan(SyrmosClock.now, first)
    }

    func testAnimationSuppressionFollowsThePin() {
        // One switch, so a capture cannot end up with a frozen clock but a
        // pulsing countdown, which is what made two screenshots of the same
        // state differ before the harness existed.
        XCTAssertEqual(SyrmosClock.animationsSuppressed, SyrmosClock.isPinned)
    }

    func testTheCaptureReceiptIsOnlyWrittenWhenPinned() {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let receipt = documents.appendingPathComponent("capture-clock.txt")
        try? FileManager.default.removeItem(at: receipt)

        SyrmosClock.writeCaptureReceipt()

        // Unpinned here, so the harness must find no receipt and refuse to treat
        // the run as a baseline.
        XCTAssertFalse(FileManager.default.fileExists(atPath: receipt.path))
    }
}
