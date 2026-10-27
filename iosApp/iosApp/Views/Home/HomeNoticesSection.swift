import SwiftUI

/// 3.0.2 Home notices: active announcements from Syrmos (dismissible) and
/// closures at the rider's favourite stations, each with its "Closed" chip.
/// Renders nothing when there is nothing to say.
struct HomeNoticesSection: View {
    @ObservedObject private var broadcasts = BroadcastStore.shared
    @ObservedObject private var closures = ClosuresStore.shared
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var router = DeepLinkRouter.shared

    private struct FavouriteClosure: Identifiable {
        let id: String
        let name: String
        let effect: ClosureEffect
        let closure: StationClosure
    }

    private var favouriteClosures: [FavouriteClosure] {
        _ = closures.tick
        let now = closures.nowEpoch
        return FavouriteStations.stationIds.sorted().compactMap { sid in
            let e = closures.effect(stationId: sid, at: now)
            guard e.active || e.upcomingClosureId != nil, let c = closures.closure(for: e) else { return nil }
            let station = SyrmosData.bundleStations.first { $0.id == sid }
            let name = loc.language == .greek ? (station?.nameEl ?? sid)
                : SyrmosData.localizedStationName(station?.name ?? sid, language: loc.language)
            return FavouriteClosure(id: sid, name: name, effect: e, closure: c)
        }
    }

    var body: some View {
        let lang = loc.language
        let favs = favouriteClosures
        if !broadcasts.active.isEmpty || !favs.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(broadcasts.active.prefix(3)) { b in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "megaphone.fill")
                            .foregroundStyle(Color.syrmosPrimary)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(BroadcastCopy.inboxTitle(lang).uppercasedForDisplay(lang))
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.secondary)
                            if let title = b.title?.pick(lang) {
                                Text(title).font(.subheadline.weight(.semibold))
                            }
                            if let body = b.body?.pick(lang) {
                                Text(body).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        Spacer(minLength: 4)
                        Button {
                            withAnimation { broadcasts.dismiss(b.id) }
                        } label: {
                            Image(systemName: "xmark").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(travelText(lang, "Dismiss", "Απόρριψη", "Mbyll", "Chiudi"))
                    }
                    .padding(14)
                    .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if let dest = BroadcastRules.destination(fromDeepLink: b.deepLink) { router.pending = dest }
                    }
                }
                ForEach(favs) { f in
                    Button {
                        router.pending = .station(id: f.id)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "star.fill").foregroundStyle(Color.yellow).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 6) {
                                    Text(f.name).font(.subheadline.weight(.semibold))
                                    if f.effect.active { ClosureStatusChip(mode: f.effect.mode, language: lang) }
                                }
                                Text(f.effect.active
                                     ? ClosureCopy.window(startsAt: f.closure.startsAtEpochSeconds, endsAt: f.effect.endsAtEpochSeconds,
                                                          now: closures.nowEpoch, lang)
                                     : ClosureCopy.upcoming(startsAt: f.effect.upcomingStartsAtEpochSeconds ?? f.closure.startsAtEpochSeconds,
                                                            now: closures.nowEpoch, mode: f.closure.mode, lang))
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.tertiary)
                        }
                        .padding(14)
                        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}
