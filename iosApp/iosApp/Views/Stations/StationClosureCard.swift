import SwiftUI

/// The closure card a station board shows instead of its departures (3.0.2):
/// the status chip, the window ("08:00 to 16:00" / "until further notice"), what
/// riders can still do there, the reason, and the replacement bus with its
/// frequency. Within 24 h of a scheduled closure it shows an upcoming banner
/// instead. Renders nothing when the station is open.
struct StationClosureCard: View {
    let stationIds: [String]
    var lineId: String? = nil
    @ObservedObject private var store = ClosuresStore.shared
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        let now = Int64(store.tick.timeIntervalSince1970)
        let effect = store.effect(stationIds: stationIds, lineId: lineId, at: max(now, store.nowEpoch))
        if effect.active, let closure = store.closure(for: effect) {
            activeCard(effect: effect, closure: closure)
        } else if let startsAt = effect.upcomingStartsAtEpochSeconds, let closure = store.closure(for: effect) {
            upcomingBanner(startsAt: startsAt, closure: closure)
        }
    }

    private func activeCard(effect: ClosureEffect, closure: StationClosure) -> some View {
        let lang = loc.language
        let now = store.nowEpoch
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                ClosureStatusChip(mode: effect.mode, language: lang)
                Text(ClosureCopy.window(startsAt: closure.startsAtEpochSeconds,
                                        endsAt: effect.endsAtEpochSeconds, now: now, lang))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
            }
            Text(ClosureCopy.modeDescription(effect.mode, lang))
                .fixedSize(horizontal: false, vertical: true)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let reason = closure.reason?.pick(lang) {
                Text(reason)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let bus = closure.replacementBus {
                Label {
                    Text(ClosureCopy.replacementBus(bus, lang))
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                } icon: {
                    Image(systemName: "bus.fill")
                        .foregroundStyle(Color.orange)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private func upcomingBanner(startsAt: Int64, closure: StationClosure) -> some View {
        let lang = loc.language
        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: "calendar.badge.exclamationmark")
                .foregroundStyle(Color.orange)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(ClosureCopy.upcoming(startsAt: startsAt, now: store.nowEpoch, mode: closure.mode, lang))
                    .font(.subheadline.weight(.semibold))
                if let reason = closure.reason?.pick(lang) {
                    Text(reason).font(.footnote).foregroundStyle(.secondary)
                }
                if let bus = closure.replacementBus {
                    Text(ClosureCopy.replacementBus(bus, lang)).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// "Closed" / "Exit only" / "Entry only" as one atomic, non-wrapping chip.
struct ClosureStatusChip: View {
    let mode: String?
    let language: AppLanguage

    var body: some View {
        Text(ClosureCopy.chip(mode: mode, language).uppercasedForDisplay(language))
            .font(.caption2.weight(.bold))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(.white)
            .background(mode == "exit_only" || mode == "entry_only" ? Color.orange : Color.red, in: Capsule())
    }
}
