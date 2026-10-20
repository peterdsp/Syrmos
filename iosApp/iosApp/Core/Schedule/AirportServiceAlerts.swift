import Foundation

/// Pure selector: which live service alerts affect an airport hub.
///
/// The airport hub no longer shows a static "no feed available" placeholder.
/// Instead it intersects each live `STASYAnnouncement`'s affectedLines /
/// affectedStationIds with the hub's relevant line and station id sets, so an
/// alert like the M3 rail-works notice surfaces directly on the hub.
///
/// Matching rules (mirror of the KMP `AirportServiceAlerts.matches`):
///  - only service alerts are considered,
///  - a line id matches case-insensitively after trimming (the M3 / M3_AIR
///    split is handled by listing both ids in the hub set),
///  - a station id matches after trimming (case-sensitive, as seeded),
///  - results are ordered highest severity first, ties keeping feed order.
enum AirportServiceAlerts {
    static func matches(
        announcements: [STASYAnnouncement],
        lineIds: Set<String>,
        stationIds: Set<String>
    ) -> [STASYAnnouncement] {
        let lines = Set(
            lineIds
                .map { $0.trimmingCharacters(in: .whitespaces).uppercased() }
                .filter { !$0.isEmpty }
        )
        let stations = Set(
            stationIds
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
        )
        if lines.isEmpty && stations.isEmpty { return [] }
        let rank = ["info": 0, "warning": 1, "closure": 2]
        return announcements
            .enumerated()
            .filter { _, ann in ann.category == .serviceAlert }
            .filter { _, ann in
                ann.affectedLines.contains { lines.contains($0.trimmingCharacters(in: .whitespaces).uppercased()) }
                    || ann.affectedStationIds.contains { stations.contains($0.trimmingCharacters(in: .whitespaces)) }
            }
            // Sort by severity descending; the enumerated offset keeps the sort
            // stable so equal-severity alerts stay in feed order.
            .sorted { lhs, rhs in
                let rl = rank[lhs.element.severity.lowercased(), default: 0]
                let rr = rank[rhs.element.severity.lowercased(), default: 0]
                if rl != rr { return rl > rr }
                return lhs.offset < rhs.offset
            }
            .map { $0.element }
    }
}
