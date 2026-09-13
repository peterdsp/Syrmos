import Foundation

/// Selected-journey detail timeline (S05), the Swift peer of web
/// `SyrmosJourneyDetail.timeline` and Kotlin `JourneyDetail.timeline`. Pure
/// transform: an ordered list of legs becomes an ordered list of timeline rows the
/// S05 detail view renders. Language-neutral (ids/dates/counts only; the view
/// localizes) and honest about time (a nil clock stays nil, never a fabricated 0).
/// Matches the shared golden fixture fixtures/journeys/detail.json.
enum JourneyDetail {
    /// A node's role on the rail, driving its size/outline in the view.
    enum Node: String, Equatable { case origin, destination, interchange }

    /// One leg fed to the transform (a ride, or a transfer/walk between rides).
    struct DetailLeg: Equatable {
        let id: String
        let kind: String            // "ride" | "transfer" | "walk"
        let lineId: String?
        let fromId: String
        let toId: String
        let orderedStopIds: [String]
        let departure: Date?
        let arrival: Date?
        let transferMinimumSeconds: Int?
        let timingKind: String      // "scheduled" | "estimated" | "live" | "unknown"

        init(id: String, kind: String, lineId: String? = nil, fromId: String, toId: String,
             orderedStopIds: [String] = [], departure: Date? = nil, arrival: Date? = nil,
             transferMinimumSeconds: Int? = nil, timingKind: String = "unknown") {
            self.id = id; self.kind = kind; self.lineId = lineId; self.fromId = fromId; self.toId = toId
            self.orderedStopIds = orderedStopIds; self.departure = departure; self.arrival = arrival
            self.transferMinimumSeconds = transferMinimumSeconds; self.timingKind = timingKind
        }
    }

    /// One rendered timeline row. Fields are populated per `kind`.
    struct TimelineRow: Equatable {
        let kind: String            // board | stops | alight | transfer | walk
        let legId: String
        var stationId: String? = nil
        var lineId: String? = nil
        var towardsId: String? = nil
        var clock: Date? = nil
        var timingKind: String? = nil
        var node: Node? = nil
        var count: Int? = nil
        var fromId: String? = nil
        var toId: String? = nil
        var seconds: Int? = nil
    }

    static func timeline(_ legs: [DetailLeg]) -> [TimelineRow] {
        var rows: [TimelineRow] = []
        let lastIndex = legs.count - 1
        for (i, leg) in legs.enumerated() {
            if leg.kind == "ride" {
                let towardsId = leg.orderedStopIds.last ?? leg.toId
                rows.append(TimelineRow(
                    kind: "board", legId: leg.id, stationId: leg.fromId, lineId: leg.lineId,
                    towardsId: towardsId, clock: leg.departure, timingKind: leg.timingKind,
                    node: i == 0 ? .origin : .interchange))
                let count = max(0, leg.orderedStopIds.count - 2)
                if count > 0 {
                    rows.append(TimelineRow(kind: "stops", legId: leg.id, lineId: leg.lineId, count: count))
                }
                rows.append(TimelineRow(
                    kind: "alight", legId: leg.id, stationId: leg.toId, clock: leg.arrival,
                    timingKind: leg.timingKind, node: i == lastIndex ? .destination : .interchange))
            } else {
                let prev = i > 0 ? legs[i - 1] : nil
                let next = i < legs.count - 1 ? legs[i + 1] : nil
                var seconds = leg.transferMinimumSeconds
                if seconds == nil, let a = prev?.arrival, let b = next?.departure {
                    seconds = Int(b.timeIntervalSince(a).rounded())
                }
                rows.append(TimelineRow(
                    kind: leg.kind == "walk" ? "walk" : "transfer", legId: leg.id,
                    node: .interchange, fromId: leg.fromId, toId: leg.toId, seconds: seconds))
            }
        }
        return rows
    }
}
