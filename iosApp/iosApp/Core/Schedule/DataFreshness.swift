import Foundation
import Combine
import Network

/// Whether the arrivals on screen came from a recent live network fetch, or
/// are being predicted from the bundled schedule + projector.
///
/// Surfacing only. The projector and simulator already keep trains moving with
/// no network; this just lets HomeView tell the user which mode they're looking
/// at, so "4 min" reads as a confident prediction instead of a silent guess.
///
/// Mirrors `core/common` `DataFreshness` on the Compose side so the two stacks
/// behave identically: default state is `.predicted` (the honest default for
/// the offline-first model), flipping to `.live` only when something actually
/// reaches the API this session, and back to `.predicted` once that data ages
/// past the window.
enum DataFreshness {
    case live
    case predicted

    /// Pure decision, mirrors `core/common` `FreshnessEvaluator.evaluate`:
    /// LIVE only when a fetch landed inside the window, else PREDICTED. A
    /// future timestamp (clock skew) reads as PREDICTED, never live.
    static func evaluate(
        lastLiveUpdate: Date?,
        now: Date,
        windowSeconds: TimeInterval
    ) -> DataFreshness {
        guard let last = lastLiveUpdate else { return .predicted }
        let age = now.timeIntervalSince(last)
        return (age >= 0 && age <= windowSeconds) ? .live : .predicted
    }
}

@MainActor
final class LiveDataFreshness: ObservableObject {
    static let shared = LiveDataFreshness()

    /// Live data older than this reads as predicted-from-schedule.
    static let windowSeconds: TimeInterval = 90

    @Published private(set) var lastLiveUpdate: Date?
    @Published private(set) var isNetworkAvailable: Bool = true
    private var ticker: Timer?
    private var connectivityTimer: Timer?
    private let monitor = NWPathMonitor()
    var onRetryRequested: (() -> Void)?

    private init() {
        ticker = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.objectWillChange.send() }
        }
        connectivityTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.freshness == .predicted else { return }
                self.onRetryRequested?()
            }
        }
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                let available = path.status == .satisfied
                self?.isNetworkAvailable = available
                if available, self?.freshness == .predicted {
                    self?.onRetryRequested?()
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "syrmos.connectivity"))
    }

    func markLive(at date: Date = Date()) {
        lastLiveUpdate = date
    }

    func requestRetry() {
        onRetryRequested?()
    }

    var freshness: DataFreshness {
        DataFreshness.evaluate(
            lastLiveUpdate: lastLiveUpdate,
            now: Date(),
            windowSeconds: Self.windowSeconds
        )
    }
}
