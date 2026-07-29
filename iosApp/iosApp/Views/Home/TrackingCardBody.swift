import SwiftUI

// Uber-style tracking card for the Home screen. Mirrors the Compose
// TrackingCard in feature/home so the tracked-departure surface reads the
// same on iOS, Android, and Web. Ticks every second via TimelineView from
// the caller; this view is just the layout.
//
// Layout:
//   ● LIVE                                Arriving <Station>
//   1 min                                 (huge countdown)
//   ────────────────────────  63%         (progress bar)
//   [M3]  to Doukissis Plakentias · 13:12
//   [ ◼  Stop tracking                 ]
struct TrackingCardBody: View {
    let tracked: TrackedDeparture
    let accent: Color
    let remaining: Int
    let due: Bool
    let now: TimeInterval
    let lang: AppLanguage
    let onStop: () -> Void

    // The moment the card first sees this tracked departure. Anchors the
    // progress bar so it fills predictably from 0 -> 1 as the countdown
    // ticks down. Recomputed when the tracked target changes so a new
    // Track action gets a fresh bar.
    @State private var startedAt: TimeInterval? = nil

    var body: some View {
        let anchor = startedAt ?? now
        let total = max(tracked.targetEpoch - anchor, 1)
        let elapsed = max(now - anchor, 0)
        let progress = min(max(elapsed / total, 0), 1)

        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 6) {
                LivePulseDot(color: accent)
                Text(liveLabel)
                    .font(.caption).fontWeight(.bold)
                    .foregroundStyle(accent)
                Spacer(minLength: 8)
                Text(arrivingLabel)
                    .font(.subheadline).fontWeight(.semibold)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }

            Text(due ? dueLabel : "\(remaining) min")
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .foregroundStyle(accent)

            if tracked.routeStations.count >= 2 {
                StationStrip(
                    stops: tracked.routeStations,
                    progress: progress,
                    accent: accent
                )
            } else {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                    .tint(accent)
            }

            if let currentStation = tracked.currentStationName {
                HStack(spacing: 6) {
                    Image(systemName: "location.fill")
                        .font(.caption2)
                        .foregroundStyle(accent)
                    Text(currentStation)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(accent)
                }
            }

            HStack(spacing: 8) {
                Text(tracked.lineId)
                    .font(.caption).fontWeight(.bold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(accent)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                if !tracked.destination.isEmpty {
                    Text("\(toLabel) \(tracked.destination)")
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }
                Spacer(minLength: 0)
                Text(tracked.scheduledTime)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Button(action: onStop) {
                HStack(spacing: 8) {
                    Image(systemName: "stop.fill")
                    Text(stopTrackingLabel).fontWeight(.semibold)
                }
                .font(.subheadline)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.syrmosSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(accent.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .onAppear {
            if startedAt == nil { startedAt = now }
        }
        .onChange(of: tracked.targetEpoch) { _, _ in
            startedAt = now
        }
    }

    private var liveLabel: String {
        switch lang {
        case .greek: return "ΖΩΝΤΑΝΑ"
        case .albanian: return "LIVE"
        case .english: return "LIVE"
        }
    }
    private var toLabel: String {
        switch lang {
        case .greek: return "προς"
        case .albanian: return "për"
        case .english: return "to"
        }
    }
    private var dueLabel: String {
        switch lang {
        case .greek: return "Τώρα"
        case .albanian: return "Tani"
        case .english: return "Due"
        }
    }
    private var stopTrackingLabel: String {
        switch lang {
        case .greek: return "Διακοπή παρακολούθησης"
        case .albanian: return "Ndalo ndjekjen"
        case .english: return "Stop tracking"
        }
    }
    private var arrivingLabel: String {
        if tracked.isStationMode {
            switch lang {
            case .greek: return "Σταθμός \(tracked.stationName)"
            case .albanian: return "Stacioni \(tracked.stationName)"
            case .english: return "\(tracked.stationName)"
            }
        }
        switch lang {
        case .greek: return "Φτάνει \(tracked.stationName)"
        case .albanian: return "Po arrin \(tracked.stationName)"
        case .english: return "Arriving \(tracked.stationName)"
        }
    }
}

private struct LivePulseDot: View {
    let color: Color
    @State private var pulse: Bool = false

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 10, height: 10)
            .opacity(pulse ? 1.0 : 0.4)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
    }
}

/// Horizontal strip of station dots with a train marker interpolating from
/// the first dot to the last as `progress` goes 0 -> 1. Mirrors the Compose
/// StationStrip. Last stop is the tracked station and always highlighted;
/// stops the train has "passed" get full accent, upcoming stops dim to 30%.
private struct StationStrip: View {
    let stops: [TrackedRouteStop]
    let progress: Double
    let accent: Color

    var body: some View {
        let safe = min(max(progress, 0), 1)
        let lastIndex = max(stops.count - 1, 1)
        let trainIndex = safe * Double(lastIndex)

        VStack(spacing: 4) {
            GeometryReader { geo in
                let width = geo.size.width
                ZStack(alignment: .leading) {
                    // Connector line (upcoming portion).
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(accent.opacity(0.20))
                        .frame(height: 3)
                        .frame(maxWidth: .infinity, alignment: .center)
                    // Connector line (passed portion, filled).
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(accent)
                        .frame(width: width * safe, height: 3)
                    // Station dots.
                    HStack {
                        ForEach(Array(stops.enumerated()), id: \.offset) { pair in
                            let index = pair.offset
                            let passed = Double(index) <= trainIndex
                            let isTarget = index == lastIndex
                            Circle()
                                .fill(passed ? accent : accent.opacity(0.30))
                                .frame(width: isTarget ? 14 : 10, height: isTarget ? 14 : 10)
                            if index != stops.count - 1 { Spacer(minLength: 0) }
                        }
                    }
                    // Train marker interpolating between dots.
                    Text("🚆")
                        .font(.callout)
                        .offset(x: width * safe - 10, y: 0)
                }
                .frame(height: 28)
            }
            .frame(height: 28)

            HStack {
                if let first = stops.first {
                    Text(first.stationName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                if let last = stops.last, stops.count > 1 {
                    Text(last.stationName)
                        .font(.caption2).fontWeight(.semibold)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
        }
    }
}
