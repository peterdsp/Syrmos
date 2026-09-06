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

// MARK: - Live vehicle marker freshness

/// Rendering-facing freshness of a REAL-GPS live vehicle marker (a suburban /
/// national train position), computed from that vehicle's OWN `updatedAt`.
/// Swift mirror of the KMP `core.model.status.LiveVehicleState`/
/// `classifyLiveVehicle` - keep the three buckets and the 90s / 600s windows in
/// lockstep across platforms.
///
/// - `.live`    within the fresh window: draw as a live, boardable position.
/// - `.stale`   older than the fresh window but still recent: draw
///              DE-EMPHASISED and label it with its age. It must NEVER render as
///              a plain live dot - an aged position shown as live is the exact
///              dishonesty the data-status rules forbid.
/// - `.expired` so old it is no longer meaningful: the renderer drops the marker
///              (and its line falls back to the schedule projector).
enum LiveVehicleState { case live, stale, expired }

/// The classification of a single live vehicle: its ``state`` plus the age of
/// its position in seconds (`nil` when the timestamp was missing/unusable, or
/// when the position is within the future-skew tolerance and treated as
/// just-now).
struct LiveVehicleFreshness {
    let state: LiveVehicleState
    let ageSeconds: Int?
}

enum LiveVehicleFreshnessRule {
    /// Within this many seconds a position is `.live`. Matches
    /// `LiveDataFreshness.windowSeconds` and the KMP fresh window.
    static let freshWindowSeconds: TimeInterval = 90
    /// Older than ``freshWindowSeconds`` but within this is `.stale`; beyond it
    /// is `.expired`. Ten minutes, mirroring the KMP `LIVE_VEHICLE_EXPIRY_SECONDS`.
    static let expirySeconds: TimeInterval = 600
    /// Tolerated future offset for device clock skew.
    static let futureSkewToleranceSeconds: TimeInterval = 120

    /// Pure classification, mirrors the KMP `classifyLiveVehicle`. A `nil` or
    /// far-future timestamp is `.stale` with no age (never `.live`).
    static func classify(
        updatedAt: Date?,
        now: Date,
        freshWindowSeconds: TimeInterval = freshWindowSeconds,
        expirySeconds: TimeInterval = expirySeconds,
        futureSkewToleranceSeconds: TimeInterval = futureSkewToleranceSeconds
    ) -> LiveVehicleFreshness {
        guard let updatedAt else { return LiveVehicleFreshness(state: .stale, ageSeconds: nil) }
        let age = now.timeIntervalSince(updatedAt)
        if age < 0 {
            // Future timestamp: tolerate small device clock skew as just-now, but
            // do not trust one that sits far ahead of us.
            return -age <= futureSkewToleranceSeconds
                ? LiveVehicleFreshness(state: .live, ageSeconds: 0)
                : LiveVehicleFreshness(state: .stale, ageSeconds: nil)
        }
        if age <= freshWindowSeconds { return LiveVehicleFreshness(state: .live, ageSeconds: Int(age)) }
        if age <= expirySeconds { return LiveVehicleFreshness(state: .stale, ageSeconds: Int(age)) }
        return LiveVehicleFreshness(state: .expired, ageSeconds: Int(age))
    }
}

// MARK: - Live poll backoff

/// Exponential backoff with jitter for the live polling loops. Swift mirror of
/// the KMP `PollBackoff` - keep the 2^failures growth, the +/-25% jitter and the
/// 60s default cap in lockstep. A healthy loop waits its base interval; after
/// consecutive failures it waits `min(base * 2^failures, max)`, jittered so many
/// installed clients never retry a down Pi in lockstep. Reset the failure count
/// to 0 on the next success. Pure + rng-injected so it unit-tests.
enum PollBackoff {
    static let defaultMaxDelaySeconds: TimeInterval = 60
    static let defaultJitterFraction: Double = 0.25
    private static let maxFailures = 16

    static func nextDelaySeconds(
        consecutiveFailures: Int,
        baseDelaySeconds: TimeInterval,
        maxDelaySeconds: TimeInterval = defaultMaxDelaySeconds,
        jitterFraction: Double = defaultJitterFraction,
        random01: Double = Double.random(in: 0..<1)
    ) -> TimeInterval {
        let failures = min(max(consecutiveFailures, 0), maxFailures)
        let exp = failures == 0 ? baseDelaySeconds : baseDelaySeconds * pow(2.0, Double(failures))
        let raw = min(exp, maxDelaySeconds)
        let multiplier = (1.0 - jitterFraction) + (2.0 * jitterFraction * random01)
        return max(raw * multiplier, 0.001)
    }

    /// Convenience for `Task.sleep(nanoseconds:)`.
    static func nextDelayNanos(
        consecutiveFailures: Int,
        baseDelaySeconds: TimeInterval,
        maxDelaySeconds: TimeInterval = defaultMaxDelaySeconds
    ) -> UInt64 {
        UInt64(nextDelaySeconds(
            consecutiveFailures: consecutiveFailures,
            baseDelaySeconds: baseDelaySeconds,
            maxDelaySeconds: maxDelaySeconds
        ) * 1_000_000_000)
    }
}
