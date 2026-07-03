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
            Text(minutesChip)
                .font(.caption)
                .fontWeight(.semibold)
                .monospacedDigit()
                .foregroundStyle(minutesAway <= 2 ? .red : .primary)
                .widgetAccentable()
        }
    }

    private var minutesChip: String {
        minutesAway <= 1 ? "now" : "\(minutesAway)m"
    }
}
