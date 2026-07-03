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
                    if context.state.isDue {
                        Text("Now")
                            .font(.title3).bold().foregroundStyle(tint)
                    } else if let epoch = context.state.targetEpoch {
                        // Self-ticking countdown so the widget stays alive
                        // between minute-boundary state pushes.
                        Text(timerInterval: Date()...Date(timeIntervalSince1970: epoch), countsDown: true)
                            .font(.title3).bold().monospacedDigit()
                            .foregroundStyle(tint)
                            .minimumScaleFactor(0.7)
                            .lineLimit(1)
                    } else {
                        Text("\(context.state.minutesRemaining) min")
                            .font(.title3).bold().monospacedDigit()
                            .foregroundStyle(tint)
                            .contentTransition(.numericText())
                            .minimumScaleFactor(0.7)
                            .lineLimit(1)
                    }
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
                        let stops = context.state.routeStations ?? []
                        if stops.count >= 2 {
                            StationStripStrip(stops: stops, progress: context.state.progress ?? 0, tint: tint)
                        } else {
                            ProgressView(value: context.state.progress ?? 0)
                                .tint(tint)
                        }
                    }
                    .padding(.top, 2)
                }
            } compactLeading: {
                Image(systemName: "tram.fill")
                    .foregroundStyle(tint)
            } compactTrailing: {
                if context.state.isDue {
                    Text("now").foregroundStyle(tint)
                } else if let epoch = context.state.targetEpoch {
                    Text(timerInterval: Date()...Date(timeIntervalSince1970: epoch), countsDown: true)
                        .monospacedDigit()
                        .foregroundStyle(tint)
                } else {
                    Text("\(context.state.minutesRemaining)m")
                        .monospacedDigit()
                        .foregroundStyle(tint)
                        .contentTransition(.numericText())
                }
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
                Group {
                    if context.state.isDue {
                        Text("Now")
                    } else if let epoch = context.state.targetEpoch {
                        Text(timerInterval: Date()...Date(timeIntervalSince1970: epoch), countsDown: true)
                    } else {
                        Text("\(context.state.minutesRemaining) min")
                            .contentTransition(.numericText())
                    }
                }
                .font(.system(size: 36, weight: .bold, design: .rounded))
                .foregroundStyle(tint)
                .monospacedDigit()
                Spacer()
                Text(context.state.scheduledTime)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            // Route strip when the app supplied enough stations; otherwise
            // a plain progress bar. Kept compact so the Lock Screen tile
            // doesn't overflow past ~5 station dots.
            let stops = context.state.routeStations ?? []
            if stops.count >= 2 {
                StationStripStrip(stops: stops, progress: context.state.progress ?? 0, tint: tint)
            } else {
                ProgressView(value: context.state.progress ?? 0)
                    .tint(tint)
            }

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

/// Compact route strip for the Live Activity Lock Screen + Dynamic Island
/// expanded region. Mirrors the in-app StationStrip: passed stops fill with
/// the accent, upcoming stops dim to 30%, and the tracked (last) stop is
/// slightly larger. Widget-safe: no continuous animation (LA state pushes
/// are batched, not per-frame), just a static snapshot per update.
@available(iOS 16.2, *)
struct StationStripStrip: View {
    let stops: [String]
    let progress: Double
    let tint: Color

    var body: some View {
        let safe = min(max(progress, 0), 1)
        let lastIndex = max(stops.count - 1, 1)
        let trainIndex = safe * Double(lastIndex)

        VStack(spacing: 3) {
            GeometryReader { geo in
                let width = geo.size.width
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(tint.opacity(0.22))
                        .frame(height: 3)
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(tint)
                        .frame(width: width * safe, height: 3)
                    HStack {
                        ForEach(Array(stops.enumerated()), id: \.offset) { pair in
                            let index = pair.offset
                            let passed = Double(index) <= trainIndex
                            let isTarget = index == lastIndex
                            Circle()
                                .fill(passed ? tint : tint.opacity(0.30))
                                .frame(width: isTarget ? 9 : 7, height: isTarget ? 9 : 7)
                            if index != stops.count - 1 { Spacer(minLength: 0) }
                        }
                    }
                    Text("🚆")
                        .font(.system(size: 10))
                        .offset(x: max(0, width * safe - 6), y: -1)
                }
                .frame(height: 14)
            }
            .frame(height: 14)

            HStack {
                if let first = stops.first {
                    Text(first)
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                if let last = stops.last, stops.count > 1 {
                    Text(last)
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
        }
    }
}
