import Foundation

/// Syrmos 3.0 Phase N J09: user-enabled "leave by" reminders (iOS engine).
///
/// Mirrors Kotlin `com.syrmos.core.common.LeaveByReminder` / `LeaveByReminders`
/// and web `web-reminders.js` exactly. One pure rule computes the leave-by moment
/// (departure minus the rider's walk+buffer lead), classifies the reminder state,
/// and reconciles a desired reminder set against what is scheduled so duplicates
/// collapse and a changed or removed departure cancels/replaces its pending
/// notification. Validated against `fixtures/reminders/leave-by.json`.
///
/// Pure and offline: arithmetic against an epoch-second clock the caller passes.
/// `LeaveByReminderScheduler` (UNUserNotificationCenter) and the saved-departure
/// board layer on top; none of the timing logic lives there, so it cannot drift.
enum ReminderState: String {
    case scheduled
    case leaveNow
    case departed
}

struct LeaveByReminder: Equatable {
    let lineId: String
    let stationId: String
    let stationName: String
    let destination: String
    /// Scheduled clock time, HH:MM, for display.
    let scheduledTime: String
    /// Unix epoch second the train departs.
    let departureEpochSeconds: Int64
    /// Seconds the rider needs before departure (walk + buffer), floored at 0.
    let leadSeconds: Int64

    /// Stable identity: one reminder per (line, station, departure instant).
    var id: String { LeaveByReminders.idFor(lineId, stationId, departureEpochSeconds) }

    /// The epoch second the rider should set off. Never after the departure itself.
    var leaveByEpochSeconds: Int64 { departureEpochSeconds - max(0, leadSeconds) }

    func state(_ nowEpochSeconds: Int64) -> ReminderState {
        if nowEpochSeconds >= departureEpochSeconds { return .departed }
        if nowEpochSeconds >= leaveByEpochSeconds { return .leaveNow }
        return .scheduled
    }

    /// The epoch second a notification should fire, or nil once that moment has
    /// passed (leaveNow or departed): prompt immediately or drop, never schedule
    /// into the past.
    func fireAtEpochSeconds(_ nowEpochSeconds: Int64) -> Int64? {
        leaveByEpochSeconds > nowEpochSeconds ? leaveByEpochSeconds : nil
    }

    /// Whole minutes until the rider must leave, rounded up; 0 once leave-by passed.
    func minutesUntilLeave(_ nowEpochSeconds: Int64) -> Int {
        let secs = leaveByEpochSeconds - nowEpochSeconds
        if secs <= 0 { return 0 }
        return Int((secs + 59) / 60)
    }
}

/// The result of reconciling a desired reminder set against what is scheduled.
struct ReminderReconciliation: Equatable {
    let toSchedule: [LeaveByReminder]
    let toCancel: [String]
    let unchanged: [LeaveByReminder]
}

enum LeaveByReminders {

    /// The dedup key for a departure.
    static func idFor(_ lineId: String, _ stationId: String, _ departureEpochSeconds: Int64) -> String {
        "\(lineId)|\(stationId)|\(departureEpochSeconds)"
    }

    /// Collapse duplicates by id, keeping the last occurrence (matches Kotlin's
    /// associateBy last-wins).
    static func dedupe(_ reminders: [LeaveByReminder]) -> [LeaveByReminder] {
        var order: [String] = []
        var byId: [String: LeaveByReminder] = [:]
        for r in reminders {
            if byId[r.id] == nil { order.append(r.id) }
            byId[r.id] = r
        }
        return order.map { byId[$0]! }
    }

    /// Reminders still worth a pending notification: not yet departed.
    static func active(_ reminders: [LeaveByReminder], _ nowEpochSeconds: Int64) -> [LeaveByReminder] {
        dedupe(reminders).filter { $0.state(nowEpochSeconds) != .departed }
    }

    /// Diff `desired` against the `current` scheduled set at `now`: collapse
    /// duplicates, drop departed, then return what to (re)schedule, cancel, and
    /// leave untouched. See the Kotlin doc for the full contract.
    static func reconcile(
        current: [LeaveByReminder],
        desired: [LeaveByReminder],
        now nowEpochSeconds: Int64
    ) -> ReminderReconciliation {
        let desiredActive = active(desired, nowEpochSeconds)
        let currentDeduped = dedupe(current)
        var currentById: [String: LeaveByReminder] = [:]
        for r in currentDeduped { currentById[r.id] = r }
        var desiredById: [String: LeaveByReminder] = [:]
        for r in desiredActive { desiredById[r.id] = r }

        var toSchedule: [LeaveByReminder] = []
        var unchanged: [LeaveByReminder] = []
        for d in desiredActive {
            if let existing = currentById[d.id], existing.leaveByEpochSeconds == d.leaveByEpochSeconds {
                unchanged.append(d)
            } else {
                toSchedule.append(d)
            }
        }

        var toCancel: [String] = []
        for r in currentDeduped {
            if let d = desiredById[r.id], d.leaveByEpochSeconds == r.leaveByEpochSeconds { continue }
            toCancel.append(r.id)
        }

        return ReminderReconciliation(toSchedule: toSchedule, toCancel: toCancel, unchanged: unchanged)
    }
}
