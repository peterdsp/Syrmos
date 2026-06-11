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

    private init() {
        self.service = SyrmosSchedulesService()
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
        return outcome
    }
}
