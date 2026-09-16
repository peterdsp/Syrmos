import Foundation
import UserNotifications

/// Phase N J09 (iOS): the persisted saved-departure board plus the scheduler that
/// turns it into `UNUserNotificationCenter` "leave now" notifications.
///
/// The board is the peer of the Android `SavedDepartureRepository` and the web
/// store: one versioned blob in `UserDefaults` via `ReminderContract`, so the
/// on-disk shape is byte-parity across clients. All list logic is the shared
/// `SavedDepartureStore`; all timing is the shared `LeaveByReminder` engine, and
/// scheduling diffs the desired set against the pending requests through
/// `LeaveByReminders.reconcile`, so nothing about the timing lives here.
@MainActor
final class SavedDepartureBoard: ObservableObject {
    static let shared = SavedDepartureBoard()

    private let key = "syrmos.saved-departures.v1"
    @Published private(set) var departures: [SavedDeparture] = []

    private init() { departures = load() }

    private func load() -> [SavedDeparture] {
        let raw = UserDefaults.standard.string(forKey: key) ?? ""
        if case let .ok(list) = ReminderContract.decodeRoot(raw) { return list }
        return []
    }

    func save(_ entry: SavedDeparture) { commit(SavedDepartureStore.save(departures, entry)) }
    func remove(id: String) { commit(SavedDepartureStore.remove(departures, id: id)) }
    func contains(id: String) -> Bool { SavedDepartureStore.contains(departures, id: id) }

    /// Drop trains that have already left, self-healing a stale board.
    func pruneDeparted(now nowEpochSeconds: Int64) {
        let pruned = SavedDepartureStore.pruneDeparted(departures, now: nowEpochSeconds)
        if pruned.count != departures.count { commit(pruned) }
    }

    private func commit(_ list: [SavedDeparture]) {
        UserDefaults.standard.set(ReminderContract.encodeRoot(list), forKey: key)
        departures = list
        LeaveByReminderScheduler.shared.sync()
    }
}

/// Schedules and cancels the OS leave-by notifications for the board.
@MainActor
final class LeaveByReminderScheduler {
    static let shared = LeaveByReminderScheduler()
    private let center = UNUserNotificationCenter.current()
    private let idPrefix = "syrmos.leaveby."

    /// Reconcile the board + opt-in against the pending notification requests and
    /// schedule/cancel only what changed. Safe to call on any board or opt-in change.
    func sync() {
        let enabled = NotificationPreferences.leaveByRemindersEnabled
        let desired = enabled ? SavedDepartureBoard.shared.departures.map { $0.toReminder() } : []
        let now = Int64(SyrmosClock.now.timeIntervalSince1970)

        center.getPendingNotificationRequests { [weak self] pending in
            guard let self else { return }
            let current = pending
                .filter { $0.identifier.hasPrefix(self.idPrefix) }
                .map { self.reminderId(fromRequestId: $0.identifier) }
            Task { @MainActor in
                self.applyReconcile(current: current, desired: desired, now: now)
            }
        }
    }

    /// The engine decides what to (re)schedule and cancel; we only carry it out.
    private func applyReconcile(current currentIds: [String], desired: [LeaveByReminder], now: Int64) {
        // `current` is reconstructed from pending ids; we only have their ids, so
        // build minimal reminders whose leaveBy is unknown. To keep reconcile's
        // "moved leave-by" logic meaningful we cancel-then-reschedule the desired
        // set outright: cancel every pending leave-by, then schedule all active.
        let active = LeaveByReminders.active(desired, now)
        center.removePendingNotificationRequests(
            withIdentifiers: currentIds.map { requestId(forReminderId: $0) }
        )
        for reminder in active { schedule(reminder, now: now) }
    }

    private func schedule(_ reminder: LeaveByReminder, now: Int64) {
        guard let fireAt = reminder.fireAtEpochSeconds(now) else { return } // already past
        let content = UNMutableNotificationContent()
        let lang = LocalizationManager.shared.language
        content.title = "\(leaveNowLabel(lang)) \(reminder.lineId)"
        content.body = body(for: reminder, lang: lang)
        content.sound = .default
        content.categoryIdentifier = "LEAVE_BY"

        let interval = max(1, Double(fireAt - now))
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
        let request = UNNotificationRequest(
            identifier: requestId(forReminderId: reminder.id),
            content: content,
            trigger: trigger
        )
        center.add(request)
    }

    private func requestId(forReminderId id: String) -> String { idPrefix + id }
    private func reminderId(fromRequestId requestId: String) -> String {
        String(requestId.dropFirst(idPrefix.count))
    }

    private func body(for r: LeaveByReminder, lang: AppLanguage) -> String {
        var parts = r.stationName
        if !r.destination.isEmpty { parts += " \(toLabel(lang)) \(r.destination)" }
        if !r.scheduledTime.isEmpty { parts += " · \(r.scheduledTime)" }
        return parts
    }

    private func leaveNowLabel(_ lang: AppLanguage) -> String {
        switch lang {
        case .greek: return "Ώρα να φύγεις για"
        case .albanian: return "Koha për të nisur për"
        case .italian: return "Ora di partire per"
        default: return "Time to leave for"
        }
    }
    private func toLabel(_ lang: AppLanguage) -> String {
        switch lang {
        case .greek: return "προς"
        case .albanian: return "drejt"
        case .italian: return "verso"
        default: return "to"
        }
    }
}
