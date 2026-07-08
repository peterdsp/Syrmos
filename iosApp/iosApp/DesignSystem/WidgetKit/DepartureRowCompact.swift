import SwiftUI

/// One departure row: line pill + destination (with an optional airplane glyph
/// for the M3 airport branch) + a right-aligned minutes chip. Used by the Live
/// Departures large widget and the Next Train medium strip. Primitive params
/// only, so it stays free of any widget-specific data type.
struct DepartureRowCompact: View {
    let lineId: String
    let destination: String
    let minutesAway: Int
    var isAirport: Bool = false
    var pillSize: LinePill.Size = .small
    /// Absolute arrival time. When present, the minutes tick live every second
    /// via the OS (`Text(timerInterval:)`) with zero reload cost; otherwise the
    /// static `minutesAway` chip is shown.
    var target: Date? = nil

    var body: some View {
        HStack(spacing: 8) {
            LinePill(lineId: lineId, size: pillSize)
            HStack(spacing: 4) {
                Text(destination)
                    .font(.caption)
                    .lineLimit(1)
                if isAirport {
                    Image(systemName: "airplane")
                        .font(.caption2)
                        .foregroundStyle(SyrmosLineTokens.color(for: lineId))
                }
            }
            Spacer(minLength: 4)
            LiveMinutes(minutesAway: minutesAway, target: target)
                .font(.caption)
                .fontWeight(.semibold)
                .monospacedDigit()
                .widgetAccentable()
        }
    }
}

/// A self-ticking minutes label for widgets. Given an absolute `target`, it
/// renders an OS-native `Text(timerInterval:)` countdown that updates every
/// second without a timeline reload; imminent (<= ~1 min out) shows a red
/// "now". Falls back to a static "Nm" chip when no target is available.
struct LiveMinutes: View {
    let minutesAway: Int
    var target: Date? = nil

    var body: some View {
        if let target {
            let imminent = target.timeIntervalSinceNow <= 60
            if imminent {
                Text("now")
                    .foregroundStyle(.red)
            } else {
                Text(timerInterval: Date()...target, countsDown: true)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .foregroundStyle(.primary)
            }
        } else {
            Text(minutesAway <= 1 ? "now" : "\(minutesAway)m")
                .foregroundStyle(minutesAway <= 2 ? .red : .primary)
        }
    }
}
