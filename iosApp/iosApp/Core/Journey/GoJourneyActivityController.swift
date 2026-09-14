import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

/// Phase N J08: starts/updates/ends the GO journey Live Activity. No-op safe when
/// ActivityKit is unavailable or Live Activities are disabled, so GO works
/// regardless. Callers pass plain values; the ActivityKit types stay inside this
/// file so the GO screen needn't import ActivityKit. `staleDate` is set one
/// freshness window ahead on each update, so a backgrounded journey renders as
/// stale honestly instead of implying the step is current.
@MainActor
final class GoJourneyActivityController {
    static let shared = GoJourneyActivityController()

    #if canImport(ActivityKit)
    private var activityId: String?
    private var isStarting = false
    #endif

    func start(
        origin: String, destination: String,
        stateLabel: String, instruction: String, context: String?,
        progress: Double, lineId: String?, arrived: Bool
    ) {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *) else { return }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        // Idempotent: a re-appear (nav pop-back, tab switch) must not tear down and
        // recreate the activity or briefly double it. If one is running (or a
        // request is in flight) just push the latest state instead.
        if activityId != nil || isStarting {
            update(stateLabel: stateLabel, instruction: instruction, context: context,
                   progress: progress, lineId: lineId, arrived: arrived)
            return
        }
        isStarting = true
        let attrs = GoJourneyActivityAttributes(origin: origin, destination: destination)
        let state = GoJourneyActivityAttributes.ContentState(
            stateLabel: stateLabel, instruction: instruction, context: context,
            progress: progress, lineId: lineId, arrived: arrived)
        // End any existing journey activities FIRST (awaited), then request the new
        // one — sequentially in one task. Requesting before the old ones are gone
        // can exceed the per-app activity limit (request throws) or, if ended via a
        // separate task, race and kill the new activity. Doing it in order avoids both.
        Task { @MainActor in
            for a in Activity<GoJourneyActivityAttributes>.activities {
                await a.end(nil, dismissalPolicy: .immediate)
            }
            do {
                // No staleDate: a manually-stepped journey has no time-based expiry,
                // so the current instruction stays valid until the rider advances —
                // greying it out on a fixed timer would be dishonest the other way.
                // A time/GPS-driven staleness signal can set this in a later phase.
                let activity = try Activity.request(
                    attributes: attrs,
                    content: ActivityContent(state: state, staleDate: nil))
                activityId = activity.id
            } catch {
                // Without the widget extension this throws; the in-app GO screen still works.
            }
            isStarting = false
        }
        #endif
    }

    func update(
        stateLabel: String, instruction: String, context: String?,
        progress: Double, lineId: String?, arrived: Bool
    ) {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *), let id = activityId else { return }
        let state = GoJourneyActivityAttributes.ContentState(
            stateLabel: stateLabel, instruction: instruction, context: context,
            progress: progress, lineId: lineId, arrived: arrived)
        Task {
            for activity in Activity<GoJourneyActivityAttributes>.activities where activity.id == id {
                await activity.update(ActivityContent(state: state, staleDate: nil))
            }
        }
        #endif
    }

    func end() {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *) else { return }
        endAll()
        activityId = nil
        #endif
    }

    #if canImport(ActivityKit)
    /// Ends every journey Live Activity, including one orphaned by a previous
    /// process (whose id this instance no longer tracks).
    @available(iOS 16.2, *)
    private func endAll() {
        Task {
            for activity in Activity<GoJourneyActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
    #endif
}
