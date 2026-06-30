import WidgetKit
import ActivityKit
import SwiftUI

/// Lock Screen and Dynamic Island UI for the tracked departure. The app
/// (`DepartureTracking`) starts, updates, and ends the activity; this renders
/// it. `SyrmosTrackingAttributes` is shared with the app target.
@available(iOS 16.2, *)
struct SyrmosLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SyrmosTrackingAttributes.self) { context in
            // Lock Screen / banner.
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(context.attributes.lineId) · \(context.attributes.stationName)")
                        .font(.headline)
                    if !context.attributes.destination.isEmpty {
                        Text("to \(context.attributes.destination) · \(context.state.scheduledTime)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(context.state.isDue ? "Now" : "\(context.state.minutesRemaining) min")
                    .font(.title2).bold()
                    .monospacedDigit()
            }
            .padding()
            .activityBackgroundTint(Color.blue.opacity(0.12))
            .activitySystemActionForegroundColor(.blue)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.lineId, systemImage: "tram.fill")
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.isDue ? "Now" : "\(context.state.minutesRemaining) min")
                        .font(.title3).bold().monospacedDigit()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("\(context.attributes.stationName) → \(context.attributes.destination)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "tram.fill")
            } compactTrailing: {
                Text(context.state.isDue ? "now" : "\(context.state.minutesRemaining)m")
                    .monospacedDigit()
            } minimal: {
                Text(context.state.isDue ? "•" : "\(context.state.minutesRemaining)")
                    .monospacedDigit()
            }
        }
    }
}
