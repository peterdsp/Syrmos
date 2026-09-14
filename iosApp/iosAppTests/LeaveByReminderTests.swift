import XCTest
@testable import Syrmos

/// Mirrors fixtures/reminders/leave-by.json case-for-case, so the iOS
/// LeaveByReminder, the Kotlin LeaveByReminder and web web-reminders.js agree for
/// the same golden inputs (Phase N J09).
final class LeaveByReminderTests: XCTestCase {

    private func rem(
        line: String = "M1", station: String = "M1_VIC", name: String = "Victoria",
        dest: String = "Kifisia", time: String = "08:00", dep: Int64, lead: Int64
    ) -> LeaveByReminder {
        LeaveByReminder(lineId: line, stationId: station, stationName: name,
                        destination: dest, scheduledTime: time,
                        departureEpochSeconds: dep, leadSeconds: lead)
    }

    func testStateCases() {
        let dep: Int64 = 1_000_000

        let before = rem(dep: dep, lead: 900)
        XCTAssertEqual(before.leaveByEpochSeconds, 999_100)
        XCTAssertEqual(before.state(998_000), .scheduled)
        XCTAssertEqual(before.fireAtEpochSeconds(998_000), 999_100)
        XCTAssertEqual(before.minutesUntilLeave(998_000), 19)

        let atLeaveBy = rem(dep: dep, lead: 900)
        XCTAssertEqual(atLeaveBy.state(999_100), .leaveNow)
        XCTAssertNil(atLeaveBy.fireAtEpochSeconds(999_100))
        XCTAssertEqual(atLeaveBy.minutesUntilLeave(999_100), 0)

        XCTAssertEqual(rem(dep: dep, lead: 900).state(999_500), .leaveNow)
        XCTAssertEqual(rem(dep: dep, lead: 900).state(1_000_000), .departed)
        XCTAssertEqual(rem(dep: dep, lead: 900).state(1_000_600), .departed)
        XCTAssertNil(rem(dep: dep, lead: 900).fireAtEpochSeconds(1_000_600))

        XCTAssertEqual(rem(dep: dep, lead: 900).minutesUntilLeave(999_010), 2)

        let zeroLead = rem(dep: dep, lead: 0)
        XCTAssertEqual(zeroLead.leaveByEpochSeconds, 1_000_000)
        XCTAssertEqual(zeroLead.state(999_940), .scheduled)
        XCTAssertEqual(zeroLead.minutesUntilLeave(999_940), 1)

        let negLead = rem(dep: dep, lead: -300)
        XCTAssertEqual(negLead.leaveByEpochSeconds, 1_000_000)
        XCTAssertEqual(negLead.minutesUntilLeave(999_900), 2)
    }

    func testIdIsStableDedupKey() {
        XCTAssertEqual(LeaveByReminders.idFor("M1", "M1_VIC", 1_000_000), "M1|M1_VIC|1000000")
        XCTAssertEqual(rem(dep: 1_000_000, lead: 900).id, rem(dep: 1_000_000, lead: 1200).id)
    }

    private var m1: LeaveByReminder { rem(dep: 1_000_000, lead: 900) }
    private var m3: LeaveByReminder {
        rem(line: "M3", station: "M3_SYN", name: "Syntagma", dest: "Airport", time: "08:10",
            dep: 1_000_600, lead: 600)
    }

    func testReconcileSchedulesNewAndCollapsesDuplicates() {
        let r = LeaveByReminders.reconcile(current: [], desired: [m1, m3, m1], now: 998_000)
        XCTAssertEqual(r.toSchedule.map { $0.id }, ["M1|M1_VIC|1000000", "M3|M3_SYN|1000600"])
        XCTAssertEqual(r.toCancel, [])
        XCTAssertEqual(r.unchanged, [])
    }

    func testReconcileUnchanged() {
        let r = LeaveByReminders.reconcile(current: [m1], desired: [m1], now: 998_000)
        XCTAssertEqual(r.toSchedule, [])
        XCTAssertEqual(r.toCancel, [])
        XCTAssertEqual(r.unchanged.map { $0.id }, ["M1|M1_VIC|1000000"])
    }

    func testReconcileRemovedIsCancelled() {
        let r = LeaveByReminders.reconcile(current: [m1], desired: [], now: 998_000)
        XCTAssertEqual(r.toCancel, ["M1|M1_VIC|1000000"])
        XCTAssertEqual(r.toSchedule, [])
    }

    func testReconcileMovedLeaveByReschedules() {
        let moved = rem(dep: 1_000_000, lead: 1200) // same id, later leave-by
        let r = LeaveByReminders.reconcile(current: [m1], desired: [moved], now: 998_000)
        XCTAssertEqual(r.toSchedule.map { $0.id }, ["M1|M1_VIC|1000000"])
        XCTAssertEqual(r.toCancel, ["M1|M1_VIC|1000000"])
        XCTAssertEqual(r.unchanged, [])
    }

    func testReconcileDepartedDroppedAndScheduledDepartedCancelled() {
        let dropped = LeaveByReminders.reconcile(current: [], desired: [m1], now: 1_000_600)
        XCTAssertEqual(dropped.toSchedule, [])
        XCTAssertEqual(dropped.toCancel, [])

        let cancelled = LeaveByReminders.reconcile(current: [m1], desired: [m1], now: 1_000_600)
        XCTAssertEqual(cancelled.toCancel, ["M1|M1_VIC|1000000"])
        XCTAssertEqual(cancelled.toSchedule, [])
    }
}
