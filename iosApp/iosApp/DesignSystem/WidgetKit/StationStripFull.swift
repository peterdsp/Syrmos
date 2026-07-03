import SwiftUI

/// Vertical route list with a connector rail, for the large widget families and
/// the redesigned Lock Screen. Each station is a dot on a continuous rail;
/// stops up to `currentIndex` are filled with the accent, the rest dim. The
/// current stop gets a ring so the eye lands on "where the train is now".
///
/// No scroll (widget philosophy): callers pass a already-trimmed list sized to
/// the family; overflow is the caller's responsibility, never a scroll view.
struct StationStripFull: View {
    let stops: [String]
    /// Index of the station the train has most recently reached / passed.
    var currentIndex: Int = 0
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(stops.enumerated()), id: \.offset) { pair in
                let index = pair.offset
                let name = pair.element
                let passed = index <= currentIndex
                let isCurrent = index == currentIndex
                HStack(alignment: .center, spacing: 10) {
                    VStack(spacing: 0) {
                        // Rail above the dot (hidden for the first stop).
                        Rectangle()
                            .fill(index == 0 ? Color.clear : (passed ? tint : tint.opacity(0.25)))
                            .frame(width: 2, height: 9)
                        ZStack {
                            if isCurrent {
                                Circle()
                                    .strokeBorder(tint, lineWidth: 2)
                                    .frame(width: 13, height: 13)
                            }
                            Circle()
                                .fill(passed ? tint : tint.opacity(0.30))
                                .frame(width: 8, height: 8)
                        }
                        // Rail below the dot (hidden for the last stop).
                        Rectangle()
                            .fill(index == stops.count - 1 ? Color.clear : (index < currentIndex ? tint : tint.opacity(0.25)))
                            .frame(width: 2, height: 9)
                    }
                    .frame(width: 13)

                    Text(name)
                        .font(.caption)
                        .fontWeight(isCurrent ? .semibold : .regular)
                        .foregroundStyle(isCurrent ? .primary : .secondary)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                }
            }
        }
    }
}
