import Foundation
import Combine

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
    private var ticker: Timer?

    private init() {
        // Re-publish periodically so a screen left open downgrades from .live
        // back to .predicted once the last fetch ages past the window, without
        // needing a fresh fetch to trigger the change.
        ticker = Timer.scheduledTimer(withTimeInterval: 30, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.objectWillChange.send() }
        }
    }

    /// Called by live network paths (suburban trains, live positions,
    /// announcements) on a successful fetch.
    func markLive(at date: Date = Date()) {
        lastLiveUpdate = date
    }

    var freshness: DataFreshness {
        DataFreshness.evaluate(
            lastLiveUpdate: lastLiveUpdate,
            now: Date(),
            windowSeconds: Self.windowSeconds
        )
    }
}
