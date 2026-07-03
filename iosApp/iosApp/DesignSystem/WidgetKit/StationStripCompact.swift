import SwiftUI

/// Compact horizontal route strip: a thin rail with station dots and a train
/// glyph at the current progress. Promoted from the Live Activity's private
/// `StationStripStrip` so the small / medium widgets, the Lock Screen, and the
/// Dynamic Island expanded region all share one implementation.
///
/// Widget-safe: no continuous animation (state pushes are batched, not
/// per-frame) — it renders a static snapshot per update. Passed stops fill with
/// the accent up to the train; upcoming stops dim to 30%; the tracked (last)
/// stop is slightly larger.
struct StationStripCompact: View {
    let stops: [String]
    var progress: Double = 0
    let tint: Color
    /// Whether to show the first / last station labels under the rail.
    var showLabels: Bool = true

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

            if showLabels {
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
}
