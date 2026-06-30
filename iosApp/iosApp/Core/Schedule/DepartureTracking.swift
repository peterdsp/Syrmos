import Foundation
import Combine
#if canImport(ActivityKit)
import ActivityKit
#endif

/// The "track this departure" primitive on iOS, mirroring the KMP
/// `TrackedDeparture` / `DepartureTracking`. One departure the user follows,
/// surfaced as an in-app countdown card and, on supported devices, a Live
/// Activity on the Lock Screen and Dynamic Island.
///
/// The countdown is pure arithmetic against the device clock, so it keeps
/// ticking offline exactly like the projector it came from.
struct TrackedDeparture: Equatable {
    let lineId: String
    let stationId: String
    let stationName: String
    let destination: String
    let scheduledTime: String
    /// Unix epoch second the train is expected.
    let targetEpoch: TimeInterval

    func minutesRemaining(_ now: TimeInterval) -> Int {
        let secs = targetEpoch - now
        if secs <= 0 { return 0 }
        return Int((secs + 59) / 60)   // round up
    }

    func isDue(_ now: TimeInterval) -> Bool { now >= targetEpoch }
}

@MainActor
final class DepartureTracking: ObservableObject {
    static let shared = DepartureTracking()

    @Published private(set) var active: TrackedDeparture?
    private var activityId: String?

    private init() {}

    func track(_ departure: TrackedDeparture) {
        active = departure
        startActivity(departure)
    }

    func stop() {
        active = nil
        endActivity()
    }

    /// Push the latest countdown into the Live Activity. The in-app card ticks
    /// itself; this keeps the Lock Screen / Dynamic Island in step and clears
    /// the track once the train is due.
    func refresh(now: TimeInterval = Date().timeIntervalSince1970) {
        guard let d = active else { return }
        if d.isDue(now) {
            // Leave the card up for a beat showing "Now", then clear.
            updateActivity(d, now: now)
            return
        }
        updateActivity(d, now: now)
    }

    // MARK: - ActivityKit bridge (no-op safe when unsupported)

    private func startActivity(_ d: TrackedDeparture) {
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *) {
            guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
            let attributes = SyrmosTrackingAttributes(
                lineId: d.lineId, stationName: d.stationName, destination: d.destination
            )
            let state = SyrmosTrackingAttributes.ContentState(
                minutesRemaining: d.minutesRemaining(Date().timeIntervalSince1970),
                scheduledTime: d.scheduledTime,
                isDue: false
            )
            do {
                let activity = try Activity.request(
                    attributes: attributes,
                    content: ActivityContent(state: state, staleDate: nil)
                )
                activityId = activity.id
            } catch {
                // Without a registered widget extension this throws; the in-app
                // card still works. Surfacing the Live Activity only needs the
                // extension target added in Xcode.
            }
        }
        #endif
    }

    private func updateActivity(_ d: TrackedDeparture, now: TimeInterval) {
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *), let id = activityId {
            let state = SyrmosTrackingAttributes.ContentState(
                minutesRemaining: d.minutesRemaining(now),
                scheduledTime: d.scheduledTime,
                isDue: d.isDue(now)
            )
            Task {
                for activity in Activity<SyrmosTrackingAttributes>.activities where activity.id == id {
                    await activity.update(ActivityContent(state: state, staleDate: nil))
                }
            }
        }
        #endif
    }

    private func endActivity() {
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *), let id = activityId {
            Task {
                for activity in Activity<SyrmosTrackingAttributes>.activities where activity.id == id {
                    await activity.end(nil, dismissalPolicy: .immediate)
                }
            }
            activityId = nil
        }
        #endif
    }
}
