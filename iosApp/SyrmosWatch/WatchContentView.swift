import SwiftUI
import WatchConnectivity

/// Next departures for the pinned station, redesigned for 1.2 "Phase 4".
///
/// Live without phone pushes: the whole list is wrapped in a
/// `TimelineView(.periodic(from:.now, by:1))`, so each row recomputes its
/// minutes from the departure's absolute `targetEpoch` every second. "3m"
/// counts down and flips to a red "now" locally; the phone push cadence stays
/// data-only. The soonest imminent departure is promoted to a large red hero.
struct WatchContentView: View {
    @EnvironmentObject private var provider: WatchConnectivityProvider

    var body: some View {
        let snap = provider.snapshot
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let now = context.date.timeIntervalSince1970
                DeparturesList(snapshot: snap, now: now)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        speakSoonest(snap)
                    } label: {
                        Image(systemName: "speaker.wave.2.fill")
                    }
                    .accessibilityLabel("Read next departure aloud")
                }
            }
        }
    }

    /// Voiced Ariadne, read-back only: speak the soonest departure with its
    /// live minutes and the phone's app language.
    private func speakSoonest(_ snap: WatchSnapshot) {
        guard let first = snap.departures.first else { return }
        let minutes = first.liveMinutes(now: Date().timeIntervalSince1970)
        WatchSpeech.shared.speak(
            lineLabel: WatchLineTokens.label(for: first.lineId),
            destination: first.destination,
            minutes: minutes,
            language: snap.language ?? "en"
        )
    }
}

// MARK: - Departures list

private struct DeparturesList: View {
    let snapshot: WatchSnapshot
    let now: TimeInterval

    var body: some View {
        let departures = Array(snapshot.departures.prefix(3))
        let soonest = departures.first
        let heroActive = (soonest?.liveMinutes(now: now) ?? 99) <= 2

        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                WatchHeader(
                    stationName: snapshot.stationName,
                    liveTrainCount: snapshot.liveTrainCount ?? 0,
                    isLive: snapshot.isLiveDataFresh
                )

                if heroActive, let hero = soonest {
                    HeroDeparture(departure: hero, now: now)
                    ForEach(departures.dropFirst()) { dep in
                        DepartureCard(departure: dep, now: now)
                    }
                } else {
                    ForEach(departures) { dep in
                        DepartureCard(departure: dep, now: now)
                    }
                }

                if departures.isEmpty {
                    Text("No upcoming departures")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 8)
                }

                UpdatedLabel(updatedEpoch: snapshot.updatedEpoch, now: now)

                NavigationLink {
                    WatchNearbyView(snapshot: snapshot)
                } label: {
                    Text("Next trains nearby")
                        .font(.footnote).fontWeight(.semibold)
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
}

// MARK: - Header

private struct WatchHeader: View {
    let stationName: String
    var liveTrainCount: Int = 0
    var isLive: Bool = false
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(stationName)
                .font(.title3).fontWeight(.bold)
                .lineLimit(1).minimumScaleFactor(0.7)
            HStack(spacing: 4) {
                if isLive {
                    Image(systemName: "circle.fill")
                        .font(.system(size: 5))
                        .foregroundStyle(.green)
                        .symbolEffect(.pulse.wholeSymbol, options: .repeating)
                    Text("\(liveTrainCount) trains live")
                        .font(.caption2).fontWeight(.semibold)
                        .foregroundStyle(.green)
                } else {
                    Text("Syrmos Watch")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 2)
    }
}

// MARK: - Hero (imminent soonest departure)

private struct HeroDeparture: View {
    let departure: WatchDeparture
    let now: TimeInterval

    var body: some View {
        let minutes = departure.liveMinutes(now: now)
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                LineBadge(lineId: departure.lineId)
                Image(systemName: "circle.fill")
                    .font(.system(size: 5))
                    .foregroundStyle(.red)
                    .symbolEffect(.pulse.wholeSymbol, options: .repeating)
                Text(departure.time)
                    .font(.caption2).foregroundStyle(.secondary)
                Spacer(minLength: 0)
            }
            Text(minutes <= 0 ? "now" : "\(minutes)m")
                .font(.system(size: 44, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.red)
                .contentTransition(.numericText())
            Text(departure.destination)
                .font(.callout).fontWeight(.semibold)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.gray.opacity(0.18),
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

// MARK: - Standard row

private struct DepartureCard: View {
    let departure: WatchDeparture
    let now: TimeInterval

    var body: some View {
        let minutes = departure.liveMinutes(now: now)
        let imminent = minutes <= 2
        HStack(spacing: 8) {
            LineBadge(lineId: departure.lineId)
            VStack(alignment: .leading, spacing: 1) {
                Text(departure.destination).font(.caption).fontWeight(.semibold).lineLimit(1)
                Text(departure.time).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text(minutes <= 0 ? "now" : "\(minutes)m")
                .font(.callout).fontWeight(.semibold).monospacedDigit()
                .foregroundStyle(imminent ? .red : .primary)
                .contentTransition(.numericText())
        }
        .padding(10)
        .background(Color.gray.opacity(0.14),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .animation(.easeInOut(duration: 0.3), value: minutes)
    }
}

// MARK: - Line badge

struct LineBadge: View {
    let lineId: String
    var body: some View {
        Text(WatchLineTokens.label(for: lineId))
            .font(.caption2).fontWeight(.bold)
            .foregroundStyle(.white)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(WatchLineTokens.color(for: lineId),
                        in: RoundedRectangle(cornerRadius: 5, style: .continuous))
    }
}

// MARK: - Updated label

private struct UpdatedLabel: View {
    let updatedEpoch: Double
    let now: TimeInterval
    var body: some View {
        Text("Updated \(relative)")
            .font(.caption2)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
    private var relative: String {
        guard updatedEpoch > 0 else { return "just now" }
        let secs = max(0, now - updatedEpoch)
        if secs < 45 { return "just now" }
        let mins = Int((secs / 60).rounded())
        if mins < 60 { return "\(mins)m ago" }
        let hrs = mins / 60
        return "\(hrs)h ago"
    }
}
