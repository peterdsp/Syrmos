import WidgetKit
import SwiftUI

/// Watch complications for Syrmos (1.2). Three families, per the plan:
///   Corner       — the minutes count to the next train
///   Circular     — a single line-color dot
///   Rectangular  — line pill + minutes + destination
///
/// The complication reads the latest snapshot the WatchConnectivityProvider
/// persists to a shared UserDefaults suite, so it stays in step with the app.
/// This lives in a Watch Widget Extension target (see README-wiring.md).

struct WatchComplicationEntry: TimelineEntry {
    let date: Date
    let lineId: String
    let destination: String
    let minutes: Int
}

struct WatchComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchComplicationEntry {
        WatchComplicationEntry(date: .now, lineId: "M3", destination: "Airport", minutes: 3)
    }
    func getSnapshot(in context: Context, completion: @escaping (WatchComplicationEntry) -> Void) {
        completion(current())
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchComplicationEntry>) -> Void) {
        let entry = current()
        let next = Calendar.current.date(byAdding: .minute, value: 1, to: .now) ?? .now.addingTimeInterval(60)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func current() -> WatchComplicationEntry {
        let snap = WatchComplicationStore.read() ?? .placeholder
        let first = snap.departures.first
        return WatchComplicationEntry(
            date: .now,
            lineId: first?.lineId ?? "M3",
            destination: first?.destination ?? "—",
            minutes: first?.minutes ?? 0
        )
    }
}

/// Reads the snapshot the WatchConnectivityProvider persists.
enum WatchComplicationStore {
    static let suite = "group.com.syrmosApp.watch"
    static func read() -> WatchSnapshot? {
        guard let d = UserDefaults(suiteName: suite)?.data(forKey: "snapshot") else { return nil }
        return try? JSONDecoder().decode(WatchSnapshot.self, from: d)
    }
    static func write(_ snapshot: WatchSnapshot) {
        guard let d = try? JSONEncoder().encode(snapshot) else { return }
        UserDefaults(suiteName: suite)?.set(d, forKey: "snapshot")
    }
}

struct SyrmosWatchComplications: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SyrmosWatchComplication", provider: WatchComplicationProvider()) { entry in
            ComplicationView(entry: entry)
        }
        .configurationDisplayName("Next Train")
        .description("Your next Syrmos departure.")
        .supportedFamilies([.accessoryCorner, .accessoryCircular, .accessoryRectangular])
    }
}

struct ComplicationView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WatchComplicationEntry

    var body: some View {
        switch family {
        case .accessoryCorner:
            Text(entry.minutes <= 1 ? "now" : "\(entry.minutes)m")
                .font(.headline).monospacedDigit()
                .widgetLabel(entry.lineId)
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Circle().fill(WatchLineTokens.color(for: entry.lineId)).padding(10)
            }
        case .accessoryRectangular:
            HStack(spacing: 6) {
                Text(WatchLineTokens.label(for: entry.lineId))
                    .font(.caption2).fontWeight(.bold).foregroundStyle(.white)
                    .padding(.horizontal, 5).padding(.vertical, 1)
                    .background(WatchLineTokens.color(for: entry.lineId),
                                in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                VStack(alignment: .leading, spacing: 0) {
                    Text(entry.minutes <= 1 ? "now" : "\(entry.minutes) min")
                        .font(.caption).fontWeight(.semibold).monospacedDigit()
                    Text(entry.destination).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
        default:
            Text("\(entry.minutes)m").monospacedDigit()
        }
    }
}
