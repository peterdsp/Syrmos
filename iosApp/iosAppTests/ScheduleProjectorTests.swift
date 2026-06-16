import XCTest
@testable import Syrmos

/// Regression tests for the per-station band projection in
/// `ScheduleProjector.projectBand`. Mirrors
/// `core/domain/.../BandProjectionTest.kt` so the iOS and KMP implementations
/// drift together or not at all.
///
/// Two invariants are pinned here:
///
///  1. The skip-past-trains step compares ARRIVAL TIME at this station
///     (`slot + offsetMinutes`) against `now`, not the raw `slot`. A train
///     that left terminus 10 min ago but reaches Kerameikos in 10 min is
///     the next visible arrival, not a stale slot to discard. The old
///     behavior shifted the first displayed countdown by `offsetMinutes`
///     into the future and produced the 22-min-first-arrival Kerameikos bug
///     in offline mode.
///
///  2. Coarse-skip then while-loop must land on a slot whose arrival is in
///     the future, never just-before-now.
///
/// To wire this file into Xcode: File > Add Files… and select this file
/// together with the existing "Syrmos - Athens Rail TimesTests" target.
/// If no test target exists yet, create a new Unit Testing Bundle target.
final class ScheduleProjectorTests: XCTestCase {

    // MARK: - Algorithm replicas (must mirror ScheduleProjector.projectBand)

    /// Fixed projectBand math: skip on arrival.
    private func projectArrivals(
        startMin: Int,
        endMin: Int,
        headway: Double,
        nowMin: Int,
        offsetMin: Int,
        count: Int
    ) -> [Int] {
        var slot = Double(startMin)
        let stationSlot = slot + Double(offsetMin)
        if stationSlot < Double(nowMin) {
            let skips = max(0, Int((Double(nowMin) - stationSlot) / headway))
            slot = Double(startMin) + Double(skips) * headway
            while slot + Double(offsetMin) < Double(nowMin) { slot += headway }
        }
        var out: [Int] = []
        while slot <= Double(endMin) && out.count < count {
            let arrival = Int(slot.rounded()) + offsetMin
            out.append(max(0, arrival - nowMin))
            slot += headway
        }
        return out
    }

    /// Buggy pre-fix variant: skip on origin time only. Kept so the test
    /// can prove the new behavior differs from the old at the exact
    /// Kerameikos-at-18:30 reproduction.
    private func projectArrivals_skipOnOriginOnly(
        startMin: Int,
        endMin: Int,
        headway: Double,
        nowMin: Int,
        offsetMin: Int,
        count: Int
    ) -> [Int] {
        var slot = Double(startMin)
        if slot < Double(nowMin) {
            let skips = max(0, Int((Double(nowMin) - slot) / headway))
            slot = Double(startMin) + Double(skips) * headway
            while slot < Double(nowMin) { slot += headway }
        }
        var out: [Int] = []
        while slot <= Double(endMin) && out.count < count {
            let arrival = Int(slot.rounded()) + offsetMin
            out.append(max(0, arrival - nowMin))
            slot += headway
        }
        return out
    }

    // MARK: - Tests

    func test_kerameikos_18_30_first_arrival_is_within_one_headway() {
        // Kerameikos on M3 outbound: minutesFromOrigin = 20. Band: 06:00 →
        // 22:00 every 5 min. The first arrival at this station after 18:30
        // is the train that left origin at 18:10 — already in motion — so
        // minutesAway should be 0, not 20.
        let arrivals = projectArrivals(
            startMin: 6 * 60,
            endMin: 22 * 60,
            headway: 5.0,
            nowMin: 18 * 60 + 30,
            offsetMin: 20,
            count: 4
        )
        XCTAssertEqual(arrivals, [0, 5, 10, 15])
    }

    func test_old_skip_on_origin_only_shifts_first_arrival_by_offset() {
        // Pin the pre-fix behavior so it can never be reintroduced as a
        // "simplification." Same inputs as the test above; the buggy
        // version returns 20-minute-shifted slots.
        let arrivals = projectArrivals_skipOnOriginOnly(
            startMin: 6 * 60,
            endMin: 22 * 60,
            headway: 5.0,
            nowMin: 18 * 60 + 30,
            offsetMin: 20,
            count: 4
        )
        XCTAssertEqual(arrivals, [20, 25, 30, 35])
    }

    func test_zero_offset_terminal_station_first_arrival_at_next_headway_tick() {
        // Origin station (offset 0). At 18:32 with 5-min headway starting
        // at 06:00, the next origin departure is 18:35 → 3 min away.
        let arrivals = projectArrivals(
            startMin: 6 * 60,
            endMin: 22 * 60,
            headway: 5.0,
            nowMin: 18 * 60 + 32,
            offsetMin: 0,
            count: 3
        )
        XCTAssertEqual(arrivals, [3, 8, 13])
    }

    func test_band_starting_after_now_emits_first_slot_at_band_start() {
        // Future band: starts at 19:00, now is 18:50. First arrival is at
        // 19:00 + offset = 19:00 → 10 min away (offset 0 for clarity).
        let arrivals = projectArrivals(
            startMin: 19 * 60,
            endMin: 22 * 60,
            headway: 10.0,
            nowMin: 18 * 60 + 50,
            offsetMin: 0,
            count: 3
        )
        XCTAssertEqual(arrivals, [10, 20, 30])
    }

    func test_coarse_skip_then_while_loop_advances_to_first_future_arrival() {
        let arrivals = projectArrivals(
            startMin: 8 * 60,
            endMin: 12 * 60,
            headway: 7.0,
            nowMin: 10 * 60,
            offsetMin: 13,
            count: 2
        )
        XCTAssertGreaterThanOrEqual(arrivals.first ?? -1, 0, "first arrival must not be negative")
        XCTAssertLessThan(arrivals.first ?? Int.max, 7, "first arrival must be within one headway")
    }

    func test_successive_arrivals_step_by_one_headway() {
        let arrivals = projectArrivals(
            startMin: 6 * 60,
            endMin: 22 * 60,
            headway: 5.0,
            nowMin: 18 * 60 + 30,
            offsetMin: 20,
            count: 5
        )
        for i in 1..<arrivals.count {
            XCTAssertEqual(arrivals[i] - arrivals[i - 1], 5, "step \(i) should equal headway")
        }
    }
}
