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
    private let kBaselineMigrationKey = "syrmos.schedules.baseline.v2"
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

    /// Silently records the freshest manifest version against the device's
    /// stored baseline. The blocking "New data available" alert this used
    /// to trigger was bad UX (it fired after the refresh had already
    /// finished, asking the user to make a decision they couldn't act on
    /// meaningfully) — see the in-app refresh discussion in the case
    /// study. `hasFreshData` is therefore kept false. The store keeps
    /// the property and `ackFreshData()` for binary-compat with any
    /// remaining consumers; new code should just observe the underlying
    /// data and let it update.
    private func evaluateFreshData() {
        guard let version = manifestVersion else { return }
        let defaults = UserDefaults.standard
        if !defaults.bool(forKey: kBaselineMigrationKey) {
            defaults.set(true, forKey: kBaselineMigrationKey)
        }
        defaults.set(version, forKey: kKnownVersionKey)
        hasFreshData = false
        freshDataSummary = ""
    }

    func ackFreshData() {
        if let v = manifestVersion {
            UserDefaults.standard.set(v, forKey: kKnownVersionKey)
        }
        hasFreshData = false
        freshDataSummary = ""
    }

    /// Builds a 1-2 sentence Greek/English string describing what's new.
    /// We deliberately don't surface the internal manifest version number
    /// — users only care about the categories of data that changed, not
    /// the bookkeeping integer the Pi maintains. The list of sources is
    /// fixed (STASY metro / tram, OASA fares, Hellenic Train suburban)
    /// because today every snapshot can touch any of them.
    private func composeFreshDataSummary() -> String {
        let lang = LocalizationManager.shared.language
        if lang == .greek {
            return "Ενημερωμένα δρομολόγια, τιμές εισιτηρίων και ανακοινώσεις από STASY, OASA και Hellenic Train."
        }
        return "Refreshed timetables, fare prices and announcements from STASY, OASA and Hellenic Train."
    }
}
