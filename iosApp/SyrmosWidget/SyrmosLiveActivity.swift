import WidgetKit
import ActivityKit
import SwiftUI

/// Lock Screen and Dynamic Island UI for the tracked departure. The app
/// (`DepartureTracking`) starts, updates, and ends the activity; this
/// renders it. `SyrmosTrackingAttributes` is shared with the app target.
///
/// Layout mirrors the in-app TrackingCard (see feature/home TrackingCard):
/// LIVE eyebrow that pulses, huge countdown, progress bar filling as time
/// elapses, and a station -> destination trailer. The countdown uses
/// `.contentTransition(.numericText())` so minute changes ease in place
/// rather than flip; the LIVE dot uses `.symbolEffect(.pulse)` so the
/// widget feels alive between minute-boundary state pushes.
@available(iOS 16.2, *)
struct SyrmosLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SyrmosTrackingAttributes.self) { context in
            LockScreenView(context: context)
                .activityBackgroundTint(accent(for: context.attributes.lineId).opacity(0.10))
                .activitySystemActionForegroundColor(accent(for: context.attributes.lineId))
        } dynamicIsland: { context in
            let tint = accent(for: context.attributes.lineId)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: "tram.fill")
                            .foregroundStyle(tint)
                        Text(context.attributes.lineId)
                            .font(.headline)
                            .foregroundStyle(tint)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.isDue ? "Now" : "\(context.state.minutesRemaining) min")
                        .font(.title3).bold().monospacedDigit()
                        .foregroundStyle(tint)
                        .contentTransition(.numericText())
                        .minimumScaleFactor(0.7)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Text(context.attributes.stationName)
                                .font(.caption).fontWeight(.semibold)
                                .foregroundStyle(.primary)
                            Text("→")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(context.attributes.destination)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .truncationMode(.tail)
                        }
                        ProgressView(value: context.state.progress ?? 0)
                            .tint(tint)
                    }
                    .padding(.top, 2)
                }
            } compactLeading: {
                Image(systemName: "tram.fill")
                    .foregroundStyle(tint)
            } compactTrailing: {
                Text(context.state.isDue ? "now" : "\(context.state.minutesRemaining)m")
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .contentTransition(.numericText())
            } minimal: {
                Text(context.state.isDue ? "•" : "\(context.state.minutesRemaining)")
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .contentTransition(.numericText())
            }
        }
    }

    private func accent(for lineId: String) -> Color {
        switch lineId {
        case "M1": return Color(red: 0.19, green: 0.62, blue: 0.31)  // metro green
        case "M2": return Color(red: 0.85, green: 0.20, blue: 0.20)  // metro red
        case "M3": return Color(red: 0.10, green: 0.36, blue: 0.72)  // metro blue
        case "T6", "T7": return Color(red: 0.95, green: 0.55, blue: 0.11)  // tram orange
        default: return Color(red: 0.42, green: 0.30, blue: 0.66)  // suburban purple
        }
    }
}

@available(iOS 16.2, *)
private struct LockScreenView: View {
    let context: ActivityViewContext<SyrmosTrackingAttributes>

    var body: some View {
        let tint = accentFor(context.attributes.lineId)
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "circle.fill")
                    .font(.system(size: 8))
                    .foregroundStyle(tint)
                    .symbolEffect(.pulse.wholeSymbol, options: .repeating)
                Text("LIVE")
                    .font(.caption).fontWeight(.bold)
                    .foregroundStyle(tint)
                Spacer(minLength: 8)
                Text("Arriving \(context.attributes.stationName)")
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }

            HStack(alignment: .firstTextBaseline) {
                Text(context.state.isDue ? "Now" : "\(context.state.minutesRemaining) min")
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(tint)
                    .monospacedDigit()
                    .contentTransition(.numericText())
                Spacer()
                Text(context.state.scheduledTime)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            ProgressView(value: context.state.progress ?? 0)
                .tint(tint)

            HStack(spacing: 8) {
                Text(context.attributes.lineId)
                    .font(.caption).fontWeight(.bold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6).padding(.vertical, 1)
                    .background(tint)
                    .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                Text("to \(context.attributes.destination)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
        }
        .padding(14)
    }

    private func accentFor(_ lineId: String) -> Color {
        switch lineId {
        case "M1": return Color(red: 0.19, green: 0.62, blue: 0.31)
        case "M2": return Color(red: 0.85, green: 0.20, blue: 0.20)
        case "M3": return Color(red: 0.10, green: 0.36, blue: 0.72)
        case "T6", "T7": return Color(red: 0.95, green: 0.55, blue: 0.11)
        default: return Color(red: 0.42, green: 0.30, blue: 0.66)
        }
    }
}
