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
    /// Last minutes value pushed to the Watch, so we push on minute changes
    /// rather than every one-second tick.
    private var lastWatchMinute: Int = -1
    // Wall clock at which tracking of `active` began. Anchors the progress
    // value we push into the Live Activity so the widget renders a
    // predictable 0 -> 1 bar as the countdown ticks down.
    private var startedEpoch: TimeInterval = 0

    private init() {}

    func track(_ departure: TrackedDeparture) {
        active = departure
        startedEpoch = Date().timeIntervalSince1970
        startActivity(departure)
        lastWatchMinute = -1
        pushToWatch(departure)
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

    /// The next few trains on the tracked line, for the Dynamic Island expanded
    /// tri-lane. Best-effort and offline: projects from the same bundled
    /// schedule the tracked departure came from. Empty is fine (the expanded
    /// view falls back to the route strip).
    private func upcomingTrains(for d: TrackedDeparture) -> [SyrmosTrackingAttributes.UpcomingTrain] {
        #if canImport(ActivityKit)
        let deps = ScheduleProjector.nextDepartures(
            for: d.stationId, lineIds: [d.lineId], limit: 6, timeHorizonMinutes: 3 * 60
        )
        return deps.prefix(3).map {
            SyrmosTrackingAttributes.UpcomingTrain(
                lineId: SyrmosLineTokens.label(for: $0.lineId),
                minutes: $0.minutesAway,
                destination: $0.direction
            )
        }
        #else
        return []
        #endif
    }

    private func lastTrainTonight(for d: TrackedDeparture) -> String? {
        ScheduleProjector.lastTrainTonight(for: d.stationId, lineIds: [d.lineId])?.time
    }

    /// Push the latest countdown into the Live Activity. The in-app card ticks
    /// itself; this keeps the Lock Screen / Dynamic Island in step and clears
    /// the track once the train is due.
    func refresh(now: TimeInterval = Date().timeIntervalSince1970) {
        guard let d = active else { return }
        updateActivity(d, now: now)
        // Push to the Watch on minute changes only (not every one-second tick).
        let minute = d.minutesRemaining(now)
        if minute != lastWatchMinute {
            lastWatchMinute = minute
            pushToWatch(d)
        }
    }

    /// Sends the next few trains on the tracked line to the Apple Watch app and
    /// its complications. No-op without a paired, installed Watch app.
    private func pushToWatch(_ d: TrackedDeparture) {
        let deps = ScheduleProjector.nextDepartures(
            for: d.stationId, lineIds: [d.lineId], limit: 6, timeHorizonMinutes: 3 * 60
        )
        let rows = deps.prefix(3).map {
            (lineId: SyrmosLineTokens.label(for: $0.lineId), destination: $0.direction, minutes: $0.minutesAway, time: $0.time)
        }
        WatchSessionManager.shared.push(stationName: d.stationName, departures: Array(rows))
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
                progress: progress(for: d, now: now),
                routeStations: d.routeStations.map { $0.stationName },
                targetEpoch: d.targetEpoch,
                upcoming: upcomingTrains(for: d),
                lastTrain: lastTrainTonight(for: d)
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
                progress: progress(for: d, now: now),
                routeStations: d.routeStations.map { $0.stationName },
                targetEpoch: d.targetEpoch,
                upcoming: upcomingTrains(for: d),
                lastTrain: lastTrainTonight(for: d)
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
