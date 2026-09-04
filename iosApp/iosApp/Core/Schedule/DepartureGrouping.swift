import Foundation

/// One line -> destination group of upcoming departures. Collapses the stack of
/// near-identical "Line 3 · towards X · Scheduled" rows into a single card that
/// carries the next few times, so a busy line reads as
/// "Line 3 → Doukissis Plakentias · 4 · 12 · 22 min" with the badge and
/// confidence shown once. Mirrors the web `SyrmosDepartures.groupDepartures`
/// transform (and the "then 12, 23 min" tail the Home hero already uses) so the
/// three clients present departures the same way.
struct GroupedDeparture: Identifiable {
    let id = UUID()
    let lineId: String
    /// Destination terminal, shown as the row heading (destination-first).
    let destination: String
    let serviceType: String
    let trainNo: String?
    /// Confidence of the SOONEST time, so the single chip matches `times.first`
    /// and never advertises a later live ETA over a scheduled lead time.
    let sourceConfidence: SourceConfidence
    /// Ascending, capped to `maxTimes`. The first is the dominant countdown.
    let times: [Time]
    /// Members beyond `maxTimes` (renders as "+N").
    let moreCount: Int
    /// Total members in the group (>= times.count).
    let total: Int

    struct Time: Equatable {
        let minutesAway: Int
        let time: String
    }
}

enum DepartureGrouping {
    /// Fold a destination for grouping: trim, lowercase, collapse whitespace so
    /// "Airport" and "airport " land in the same bucket. Display keeps the
    /// original spelling.
    static func destinationKey(_ s: String) -> String {
        s.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\n" })
            .joined(separator: " ")
    }

    /// Collapse departures sharing a `(lineId, destination)` into groups.
    ///
    /// A group appears at the position of its FIRST member. Callers pass a list
    /// already sorted by `minutesAway` (the station board sorts), so first-member
    /// order is soonest-first while keeping distinct destinations (and rail vs
    /// bus) separate. Within a group the times are sorted ascending defensively,
    /// so `times.first` and the confidence are the soonest regardless of input
    /// order. Grouping spans the whole list, so two "Line 3 → Airport"
    /// departures separated by a "Line 3 → Dimotiko" row still merge.
    ///
    /// `maxTimes <= 0` keeps every time. The input is not mutated.
    static func group(_ departures: [Departure], maxTimes: Int = 3) -> [GroupedDeparture] {
        var order: [String] = []
        var byKey: [String: [Departure]] = [:]
        for d in departures {
            let key = d.lineId + "\u{0}" + destinationKey(d.direction)
            if byKey[key] == nil {
                byKey[key] = []
                order.append(key)
            }
            byKey[key]?.append(d)
        }

        return order.map { key in
            // Stable sort by minutesAway so the soonest leads even if a caller
            // passed an unsorted list.
            let members = (byKey[key] ?? [])
                .enumerated()
                .sorted { a, b in
                    a.element.minutesAway != b.element.minutesAway
                        ? a.element.minutesAway < b.element.minutesAway
                        : a.offset < b.offset
                }
                .map(\.element)
            let soonest = members.first
            let cap = maxTimes > 0 ? maxTimes : members.count
            let shown = Array(members.prefix(cap))
            return GroupedDeparture(
                lineId: soonest?.lineId ?? "",
                destination: soonest?.direction ?? "",
                serviceType: members.first(where: { $0.serviceType == "airport" })?.serviceType
                    ?? soonest?.serviceType ?? "",
                trainNo: soonest?.trainNo,
                sourceConfidence: soonest?.sourceConfidence ?? .scheduled,
                times: shown.map { GroupedDeparture.Time(minutesAway: $0.minutesAway, time: $0.time) },
                moreCount: max(0, members.count - shown.count),
                total: members.count
            )
        }
    }
}
