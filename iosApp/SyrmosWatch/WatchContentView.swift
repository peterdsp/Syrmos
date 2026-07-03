import SwiftUI

/// Next-three departures for the pinned station, mirroring the iOS Live
/// Departures large widget at Watch scale.
struct WatchContentView: View {
    @EnvironmentObject private var provider: WatchConnectivityProvider

    var body: some View {
        let snap = provider.snapshot
        NavigationStack {
            List {
                ForEach(snap.departures.prefix(3)) { dep in
                    HStack(spacing: 8) {
                        Text(WatchLineTokens.label(for: dep.lineId))
                            .font(.caption2).fontWeight(.bold)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(WatchLineTokens.color(for: dep.lineId),
                                        in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(dep.destination).font(.caption).lineLimit(1)
                            Text(dep.time).font(.caption2).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        Text(dep.minutes <= 1 ? "now" : "\(dep.minutes)m")
                            .font(.callout).fontWeight(.semibold).monospacedDigit()
                            .foregroundStyle(dep.minutes <= 2 ? .red : .primary)
                    }
                }
            }
            .navigationTitle(snap.stationName)
        }
    }
}
