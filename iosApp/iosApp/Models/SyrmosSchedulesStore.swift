import Combine
import Foundation

/// Process-wide singleton for the schedules sync state.
///
/// SettingsView and the app boot hook both read the same instance so the
/// "Last updated" row and the "Check now" button reflect reality.
@MainActor
final class SyrmosSchedulesStore: ObservableObject {
    static let shared = SyrmosSchedulesStore()

    @Published private(set) var service: SyrmosSchedulesService
    @Published private(set) var lastResult: SyrmosSchedulesService.RefreshOutcome?
    @Published private(set) var isRefreshing: Bool = false
    /// True the first time the manifest version increases on this device.
    /// SettingsView surfaces a "New schedule data" alert when this flips so
    /// the user sees that something changed server-side without having to
    /// read the timestamp row.
    @Published var hasFreshData: Bool = false
    @Published var freshDataSummary: String = ""

    private let kKnownVersionKey = "syrmos.schedules.knownVersion"
    private var cancellables = Set<AnyCancellable>()

    private init() {
        let svc = SyrmosSchedulesService()
        self.service = svc
        // Forward every objectWillChange from the inner service so any
        // @Published change there (offlineOnly toggle, bundles refresh,
        // lastSyncAt) re-renders views observing the store. Without this
        // bridge SwiftUI only re-renders when `service` itself is reassigned
        // and toggles like "Offline-only mode" never propagate to other
        // bindings on the same screen (e.g. the disabled state of the
        // Check now button).
        svc.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    var lastSyncAt: Date? { service.lastSyncAt }
    var offlineOnly: Bool {
        get { service.offlineOnly }
        set { service.offlineOnly = newValue }
    }
    var manifestVersion: Int? { service.manifest?.version }

    @discardableResult
    func refresh() async -> SyrmosSchedulesService.RefreshOutcome {
        isRefreshing = true
        let outcome = await service.refresh()
        lastResult = outcome
        isRefreshing = false
        evaluateFreshData()
        return outcome
    }

    /// Compares the freshly-loaded manifest version against the last one we
    /// stored. Sets `hasFreshData=true` plus a one-line `freshDataSummary`
    /// so the UI can present a "New data" alert. Idempotent: once the user
    /// dismisses, ackFreshData() bumps the stored version.
    private func evaluateFreshData() {
        guard let version = manifestVersion else { return }
        let defaults = UserDefaults.standard
        let known = defaults.integer(forKey: kKnownVersionKey)
        if version > known {
            hasFreshData = true
            freshDataSummary = composeFreshDataSummary()
        }
    }

    func ackFreshData() {
        if let v = manifestVersion {
            UserDefaults.standard.set(v, forKey: kKnownVersionKey)
        }
        hasFreshData = false
        freshDataSummary = ""
    }

    /// Builds a 1-2 sentence Greek/English string describing what's likely
    /// different. We can't diff every line bundle cheaply, so we surface
    /// the manifest version and the count of bundle hashes that exist —
    /// enough signal that something material changed without overstating.
    private func composeFreshDataSummary() -> String {
        let version = manifestVersion ?? 0
        let bundleCount = service.bundles.count
        let lang = LocalizationManager.shared.language
        if lang == .greek {
            return "Νέα έκδοση δεδομένων v\(version) με \(bundleCount) γραμμές. "
                 + "Ενημερωμένα δρομολόγια, τιμές εισιτηρίων και ανακοινώσεις από STASY / OASA / Hellenic Train."
        }
        return "Schedule data updated to v\(version), covering \(bundleCount) lines. "
             + "Includes refreshed timetables, fare prices and STASY / OASA / Hellenic Train announcements."
    }
}
