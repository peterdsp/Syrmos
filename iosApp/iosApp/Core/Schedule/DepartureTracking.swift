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
/// One stop on the tracked train's route. Ordered lists of these on a
/// TrackedDeparture drive the station-strip visualisation in the tracking
/// card, where the train icon interpolates between dots as the countdown
/// ticks down. Mirrors the KMP `TrackedRouteStop`.
struct TrackedRouteStop: Equatable {
    let stationId: String
    let stationName: String
}

struct TrackedDeparture: Equatable {
    let lineId: String
    let stationId: String
    let stationName: String
    let destination: String
    let scheduledTime: String
    /// Unix epoch second the train is expected.
    let targetEpoch: TimeInterval
    /// Ordered stops on the way to the tracked departure, ordered in the
    /// direction of travel with the tracked station always last. Up to
    /// six items in practice so the strip stays readable. Empty when the
    /// caller couldn't resolve the line's stations; the tracking card
    /// falls back to a plain progress bar.
    let routeStations: [TrackedRouteStop]

    init(
        lineId: String,
        stationId: String,
        stationName: String,
        destination: String,
        scheduledTime: String,
        targetEpoch: TimeInterval,
        routeStations: [TrackedRouteStop] = []
    ) {
        self.lineId = lineId
        self.stationId = stationId
        self.stationName = stationName
        self.destination = destination
        self.scheduledTime = scheduledTime
        self.targetEpoch = targetEpoch
        self.routeStations = routeStations
    }

    func minutesRemaining(_ now: TimeInterval) -> Int {
        let secs = targetEpoch - now
        if secs <= 0 { return 0 }
        return Int((secs + 59) / 60)   // round up
    }

    func isDue(_ now: TimeInterval) -> Bool { now >= targetEpoch }

    /// Slice up to `maxStops` stations approaching the target station in the
    /// direction of travel. Ordered target-last so the strip reads
    /// left-to-right, with earlier stops on the left and the tracked
    /// station on the right. Mirrors `computeRouteStations` in
    /// feature/home Compose.
    static func computeRouteStations(
        stations: [TransitStation],
        targetStationId: String,
        direction: TransitDirection,
        language: AppLanguage,
        maxStops: Int = 6
    ) -> [TrackedRouteStop] {
        guard !stations.isEmpty, maxStops >= 2 else { return [] }
        guard let targetIndex = stations.firstIndex(where: { $0.id == targetStationId }) else {
            return []
        }
        let label = { (s: TransitStation) -> String in
            language == .greek ? s.nameEl : s.name
        }
        switch direction {
        case .outbound, .airport:
            // Airport (M3_AIR) trains share the M3 corridor toward Doukissis
            // Plakentias before branching, so their approach is outbound.
            let start = max(targetIndex - (maxStops - 1), 0)
            return (start...targetIndex).map { i in
                TrackedRouteStop(stationId: stations[i].id, stationName: label(stations[i]))
            }
        case .inbound:
            let end = min(targetIndex + (maxStops - 1), stations.count - 1)
            return stride(from: end, through: targetIndex, by: -1).map { i in
                TrackedRouteStop(stationId: stations[i].id, stationName: label(stations[i]))
            }
        }
    }
}

@MainActor
final class DepartureTracking: ObservableObject {
    static let shared = DepartureTracking()

    @Published private(set) var active: TrackedDeparture?
    private var activityId: String?
    // Wall clock at which tracking of `active` began. Anchors the progress
    // value we push into the Live Activity so the widget renders a
    // predictable 0 -> 1 bar as the countdown ticks down.
    private var startedEpoch: TimeInterval = 0

    private init() {}

    func track(_ departure: TrackedDeparture) {
        active = departure
        startedEpoch = Date().timeIntervalSince1970
        startActivity(departure)
    }

    func stop() {
        active = nil
        startedEpoch = 0
        endActivity()
    }

    private func progress(for d: TrackedDeparture, now: TimeInterval) -> Double {
        let anchor = startedEpoch > 0 ? startedEpoch : now
        let total = max(d.targetEpoch - anchor, 1)
        let elapsed = max(now - anchor, 0)
        return min(max(elapsed / total, 0), 1)
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
            let now = Date().timeIntervalSince1970
            let state = SyrmosTrackingAttributes.ContentState(
                minutesRemaining: d.minutesRemaining(now),
                scheduledTime: d.scheduledTime,
                isDue: false,
                progress: progress(for: d, now: now)
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
                isDue: d.isDue(now),
                progress: progress(for: d, now: now)
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
