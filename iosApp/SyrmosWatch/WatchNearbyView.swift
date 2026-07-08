import SwiftUI
import WatchConnectivity
import WatchKit

/// "Next trains nearby" screen pushed from the departures list footer.
///
/// The offline `ScheduleProjector` is not a member of the Watch target, so this
/// screen lists the departures the phone already sent for the pinned station
/// (ticking live via the same absolute `targetEpoch`), and offers an "Open on
/// iPhone for map and tickets" affordance. That best-effort asks the phone to
/// foreground/deeplink over WatchConnectivity with a haptic; it degrades
/// gracefully to a "See on iPhone" hint when the phone is unreachable.
struct WatchNearbyView: View {
    let snapshot: WatchSnapshot

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let now = context.date.timeIntervalSince1970
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(snapshot.stationName)
                            .font(.headline).fontWeight(.bold)
                            .lineLimit(1).minimumScaleFactor(0.7)
                        Text("Nearby departures")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if snapshot.departures.isEmpty {
                        Text("See on iPhone")
                            .font(.caption).foregroundStyle(.secondary)
                            .padding(.vertical, 8)
                    } else {
                        ForEach(snapshot.departures) { dep in
                            NearbyRow(departure: dep, now: now)
                        }
                    }

                    Button {
                        openOnPhone()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "iphone")
                            Text("Open on iPhone for map and tickets")
                                .font(.caption2).fontWeight(.semibold)
                                .multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.gray.opacity(0.22),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 2)
                }
                .padding(.horizontal, 2)
            }
        }
        .navigationTitle("Nearby")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Best-effort request to foreground/deeplink the phone. No-op-safe when the
    /// phone is unreachable; always fires a haptic so the tap feels acknowledged.
    private func openOnPhone() {
        WKInterfaceDevice.current().play(.click)
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated, session.isReachable else { return }
        session.sendMessage(["action": "openApp", "deeplink": "syrmos://station/\(snapshot.stationName)"],
                            replyHandler: nil, errorHandler: nil)
    }
}

private struct NearbyRow: View {
    let departure: WatchDeparture
    let now: TimeInterval
    var body: some View {
        let minutes = departure.liveMinutes(now: now)
        HStack(spacing: 8) {
            LineBadge(lineId: departure.lineId)
            VStack(alignment: .leading, spacing: 1) {
                Text(departure.destination).font(.caption).fontWeight(.semibold).lineLimit(1)
                Text(departure.time).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text(minutes <= 0 ? "now" : "\(minutes)m")
                .font(.callout).fontWeight(.semibold).monospacedDigit()
                .foregroundStyle(minutes <= 2 ? .red : .primary)
        }
        .padding(10)
        .background(Color.gray.opacity(0.14),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
