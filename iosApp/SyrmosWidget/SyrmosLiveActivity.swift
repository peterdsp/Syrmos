import WidgetKit
import ActivityKit
import SwiftUI

/// Lock Screen and Dynamic Island UI for the tracked departure (1.2 redesign).
/// The app (`DepartureTracking`) starts, updates, and ends the activity; this
/// renders it. `SyrmosTrackingAttributes` is shared with the app target.
///
/// Visual language: shared DesignSystem/WidgetKit components (LinePill,
/// StationStripCompact, SyrmosLineTokens). The countdown self-ticks via
/// `Text(timerInterval:countsDown:)` so it stays alive between the batched
/// minute-boundary state pushes; the LIVE dot pulses. `widgetAccentable()` on
/// the pill and minutes keeps tinted StandBy / accented modes legible.
@available(iOS 16.2, *)
struct SyrmosLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SyrmosTrackingAttributes.self) { context in
            LockScreenView(context: context)
                .activityBackgroundTint(SyrmosLineTokens.color(for: context.attributes.lineId).opacity(0.10))
                .activitySystemActionForegroundColor(SyrmosLineTokens.color(for: context.attributes.lineId))
        } dynamicIsland: { context in
            let tint = SyrmosLineTokens.color(for: context.attributes.lineId)
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    LinePill(lineId: context.attributes.lineId, size: .regular)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    countdown(context)
                        .font(.title3).bold().monospacedDigit()
                        .foregroundStyle(tint)
                        .widgetAccentable()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ExpandedTriLane(context: context, tint: tint)
                }
            } compactLeading: {
                // Compact leading = line pill.
                LinePill(lineId: context.attributes.lineId, size: .small)
                    .widgetAccentable()
            } compactTrailing: {
                // Compact trailing = "3m" monospaced.
                countdown(context)
                    .monospacedDigit()
                    .foregroundStyle(tint)
                    .widgetAccentable()
            } minimal: {
                // Minimal = a single line-color dot.
                Circle()
                    .fill(tint)
                    .frame(width: 10, height: 10)
                    .widgetAccentable()
            }
        }
    }

    /// Self-ticking countdown shared by the compact trailing and expanded
    /// trailing regions.
    @ViewBuilder
    private func countdown(_ context: ActivityViewContext<SyrmosTrackingAttributes>) -> some View {
        if context.state.isDue {
            Text("now")
        } else if let epoch = context.state.targetEpoch {
            Text(timerInterval: Date()...Date(timeIntervalSince1970: epoch), countsDown: true)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        } else {
            Text("\(context.state.minutesRemaining)m")
                .contentTransition(.numericText())
        }
    }
}

// MARK: - Dynamic Island expanded tri-lane

/// The next three trains on the tracked line, one lane each. Falls back to the
/// station route strip when the app hasn't supplied `upcoming` (older state).
@available(iOS 16.2, *)
private struct ExpandedTriLane: View {
    let context: ActivityViewContext<SyrmosTrackingAttributes>
    let tint: Color

    var body: some View {
        let upcoming = context.state.upcoming ?? []
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(context.attributes.stationName)
                    .font(.caption).fontWeight(.semibold)
                Text("→").font(.caption).foregroundStyle(.secondary)
                Text(context.attributes.destination)
                    .font(.caption).foregroundStyle(.secondary)
                    .lineLimit(1).truncationMode(.tail)
            }
            if upcoming.count >= 2 {
                ForEach(Array(upcoming.prefix(3).enumerated()), id: \.offset) { pair in
                    let t = pair.element
                    HStack(spacing: 8) {
                        LinePill(lineId: t.lineId, size: .small)
                        Text(t.destination).font(.caption).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(t.minutes <= 1 ? "now" : "\(t.minutes)m")
                            .font(.caption).fontWeight(.semibold).monospacedDigit()
                            .foregroundStyle(pair.offset == 0 ? tint : .secondary)
                    }
                }
            } else {
                let stops = context.state.routeStations ?? []
                if stops.count >= 2 {
                    StationStripCompact(stops: stops, progress: context.state.progress ?? 0, tint: tint)
                } else {
                    ProgressView(value: context.state.progress ?? 0).tint(tint)
                }
            }
        }
        .padding(.top, 2)
    }
}

// MARK: - Lock Screen

@available(iOS 16.2, *)
private struct LockScreenView: View {
    let context: ActivityViewContext<SyrmosTrackingAttributes>

    var body: some View {
        let tint = SyrmosLineTokens.color(for: context.attributes.lineId)
        VStack(alignment: .leading, spacing: 10) {
            // LIVE eyebrow + "Arriving <station>".
            HStack(spacing: 6) {
                Image(systemName: "circle.fill")
                    .font(.system(size: 8))
                    .foregroundStyle(tint)
                    .symbolEffect(.pulse.wholeSymbol, options: .repeating)
                Text("LIVE").font(.caption).fontWeight(.bold).foregroundStyle(tint)
                    .widgetAccentable()
                Spacer(minLength: 8)
                Text("Arriving \(context.attributes.stationName)")
                    .font(.subheadline).fontWeight(.semibold)
                    .lineLimit(1).truncationMode(.tail)
            }

            // Big minutes, full-width row.
            HStack(alignment: .firstTextBaseline) {
                Group {
                    if context.state.isDue {
                        Text("Now")
                    } else if let epoch = context.state.targetEpoch {
                        Text(timerInterval: Date()...Date(timeIntervalSince1970: epoch), countsDown: true)
                    } else {
                        Text("\(context.state.minutesRemaining) min").contentTransition(.numericText())
                    }
                }
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(tint)
                .monospacedDigit()
                .widgetAccentable()
                Spacer()
                Text(context.state.scheduledTime).font(.caption).foregroundStyle(.secondary)
            }

            // Full-width route strip when available.
            let stops = context.state.routeStations ?? []
            if stops.count >= 2 {
                StationStripCompact(stops: stops, progress: context.state.progress ?? 0, tint: tint)
            } else {
                ProgressView(value: context.state.progress ?? 0).tint(tint)
            }

            // Current station label when live position is available.
            if let currentStation = context.state.currentStation {
                HStack(spacing: 4) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 8))
                        .foregroundStyle(tint)
                    Text(currentStation)
                        .font(.caption2).fontWeight(.semibold)
                        .foregroundStyle(tint)
                }
            }

            if let status = context.state.communityStatus {
                HStack(spacing: 9) {
                    Circle()
                        .fill(Color(hex: 0xFFC24A))
                        .frame(width: 12, height: 12)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Community: \(status.lowercased())")
                            .font(.caption).fontWeight(.semibold)
                        HStack(spacing: 4) {
                            if let detail = context.state.communityDetail {
                                Text(detail)
                            }
                            if let count = context.state.communityConfirmations {
                                Text("· \(count) confirmations")
                            }
                        }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 4)
                    if #available(iOSApplicationExtension 17.0, *) {
                        Button(intent: ConfirmRailPulseIntent(signal: status.lowercased())) {
                            Text("Confirm")
                                .font(.caption2).fontWeight(.bold)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(Color(hex: 0x17492D))
                    }
                }
                .padding(10)
                .background(Color.black.opacity(0.06), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            if context.state.unexpectedStop == true {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Unexpected stop ahead")
                        .font(.caption).fontWeight(.bold)
                    Text("Community supported · not an official operator alert")
                        .font(.caption2)
                }
                .foregroundStyle(Color(hex: 0x9B1C2D))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Color(hex: 0xFDE7E9), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            // Line pill + destination, with the "last train" footer trailing.
            HStack(spacing: 8) {
                LinePill(lineId: context.attributes.lineId, size: .regular)
                Text("to \(context.attributes.destination)")
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Spacer(minLength: 0)
                if let last = context.state.lastTrain {
                    Text("Last \(last) 🌙").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .padding(14)
    }
}
