import Foundation
import CoreLocation

/// A presentation-level station group (finding 2): one selector row per real
/// physical station choice.
///
/// Co-located stops that share a station identity, the same folded name AND the
/// same location, collapse into a single row that carries every underlying
/// routable stop id and the union of the lines that serve them. So the selector
/// never shows the same physical station twice (e.g. the tram `A1_KIF` and
/// `A2_KIF`, both "Kifisias" at one point), while genuinely distinct stations stay
/// separate: "Kifissia" (`M1_KIF`) and "Kifisias" differ in both name and place,
/// so they never merge.
///
/// Identity is the conjunction of name AND location, never name or proximity
/// alone: two ids merge only when their folded names match and they sit at
/// essentially the same point. Selection keeps access to every member line because
/// the planner links co-located members with transfer edges, so no line becomes
/// unreachable when a group resolves to its representative stop.
struct StationGroup: Identifiable, Equatable {
    /// Stable across launches and input order: the smallest member id.
    let id: String
    let name: String
    let nameEl: String
    /// Every routable stop id in the group, sorted, so nothing is lost on merge.
    let memberIds: [String]
    /// Union of the lines serving the group, for the row's disambiguating badges.
    let lineIds: [String]
    let region: TransitRegion
    let coordinate: CLLocationCoordinate2D

    /// The id carried to the planner / stored on selection.
    var representativeId: String { id }

    static func == (a: StationGroup, b: StationGroup) -> Bool {
        a.id == b.id && a.memberIds == b.memberIds && a.lineIds == b.lineIds
    }
}

enum StationGrouping {
    /// Fold case and accents but keep the letters, so accented and unaccented
    /// spellings match for search and grouping while genuinely different spellings
    /// (Kifissia vs Kifisias) never collapse together.
    static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespaces)
    }

    /// ~11m coordinate bucket. Only near-identical points merge, so co-location is
    /// required in addition to a name match: proximity is evidence, not identity.
    private static func bucket(_ c: CLLocationCoordinate2D) -> String {
        String(format: "%.4f,%.4f", c.latitude, c.longitude)
    }

    /// Collapse a raw station list into presentation groups, preserving first-seen
    /// order so the picker stays stable as input is reordered.
    static func groups(from stations: [TransitStation]) -> [StationGroup] {
        var buckets: [String: [TransitStation]] = [:]
        var order: [String] = []
        for st in stations {
            let key = fold(st.name.isEmpty ? st.nameEl : st.name) + "|" + bucket(st.coordinate)
            if buckets[key] == nil { order.append(key) }
            buckets[key, default: []].append(st)
        }
        return order.map { key in
            let members = buckets[key]!.sorted { $0.id < $1.id }
            let rep = members.first!
            var lineIds: [String] = []
            for m in members { for l in m.lineIds where !lineIds.contains(l) { lineIds.append(l) } }
            return StationGroup(
                id: rep.id, name: rep.name, nameEl: rep.nameEl,
                memberIds: members.map { $0.id }, lineIds: lineIds,
                region: rep.region, coordinate: rep.coordinate)
        }
    }

    /// Whether a group matches a folded query on any of its member names (English
    /// or Greek), so accented and unaccented input both hit.
    static func matches(_ group: StationGroup, query folded: String) -> Bool {
        if folded.isEmpty { return true }
        return fold(group.name).contains(folded) || fold(group.nameEl).contains(folded)
    }
}
