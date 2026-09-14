import Foundation

// Syrmos 3.0 Phase R disruption exclusions (iOS mirror).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.DisruptionExclusion and web
// web-disruption.js exactly. A CLOSURE-severity service notice suspends its
// affected line ids; the planner must route AROUND suspended track, and when the
// only path rides suspended track it must name the affected line and its notice
// (S10 "Suspended segment") instead of handing the rider a plan they cannot
// travel. Never fabricate a replacement (S07).
//
// Covered case-for-case by fixtures/journeys/disruption.json (DisruptionExclusionTests).

/// A service notice as consumed by the disruption logic. `severity` uses the
/// same lowercase wire tokens as the fixtures ("info" | "warning" | "closure").
struct DisruptionNotice: Equatable {
    let id: String
    let severity: String
    let affectedLineIds: [String]

    init(id: String, severity: String, affectedLineIds: [String]) {
        self.id = id
        self.severity = severity
        self.affectedLineIds = affectedLineIds
    }
}

/// One ride/transfer/walk leg, reduced to what disruption logic needs.
struct DisruptionLeg: Equatable {
    let kind: String
    let lineId: String?

    init(kind: String, lineId: String? = nil) {
        self.kind = kind
        self.lineId = lineId
    }
}

/// A candidate option, reduced to its legs.
struct DisruptionOption: Equatable {
    let legs: [DisruptionLeg]
    init(legs: [DisruptionLeg]) { self.legs = legs }
}

/// Honest outcome of planning with active disruptions considered.
enum DisruptionOutcome: Equatable {
    /// A usable plan; `excludedLineIds` are the suspended lines routed around.
    case routed(options: [DisruptionOption], excludedLineIds: Set<String>)
    /// The only path rides suspended track; name the lines and their notices.
    case suspended(affectedLineIds: Set<String>, notices: [DisruptionNotice])
    /// No path exists even before disruption is considered.
    case noRoute
}

enum DisruptionExclusion {

    /// trim + lowercase, strip the "line"/"metro" wording. Mirror of Kotlin/JS.
    static func normalizeLine(_ id: String) -> String {
        id.trimmingCharacters(in: .whitespaces)
            .lowercased()
            .replacingOccurrences(of: "line", with: "")
            .replacingOccurrences(of: "metro", with: "")
            .replacingOccurrences(of: " ", with: "")
            .trimmingCharacters(in: .whitespaces)
    }

    // Mirror Kotlin AdvisorySeverity.fromRaw: both "closure" and "closed" are a
    // closure, so the engine is robust even if a caller passes the raw feed value.
    private static func isClosure(_ notice: DisruptionNotice) -> Bool {
        let s = notice.severity.trimmingCharacters(in: .whitespaces).lowercased()
        return s == "closure" || s == "closed"
    }

    /// Line ids the operator has SUSPENDED, from CLOSURE notices only.
    static func suspendedLineIds(_ notices: [DisruptionNotice]) -> Set<String> {
        var out = Set<String>()
        for notice in notices where isClosure(notice) {
            for line in notice.affectedLineIds {
                let norm = normalizeLine(line)
                if !norm.isEmpty { out.insert(norm) }
            }
        }
        return out
    }

    /// The suspended lines an option's ride legs actually travel on.
    static func optionUsesSuspended(_ option: DisruptionOption, _ suspended: Set<String>) -> Set<String> {
        if suspended.isEmpty { return [] }
        var out = Set<String>()
        for leg in option.legs where leg.kind == "ride" {
            guard let lineId = leg.lineId else { continue }
            let norm = normalizeLine(lineId)
            if suspended.contains(norm) { out.insert(norm) }
        }
        return out
    }

    /// Classify a disruption-aware plan into routed | suspended | noRoute.
    static func classify(
        avoidingOptions: [DisruptionOption],
        naiveOptions: [DisruptionOption],
        notices: [DisruptionNotice]
    ) -> DisruptionOutcome {
        let suspended = suspendedLineIds(notices)
        if !avoidingOptions.isEmpty {
            // Only claim a detour for lines the naive (unrestricted) plan actually
            // rode: an unrelated closure must not show a "routing around" chip.
            let excluded = suspended.isEmpty ? [] : (naiveOptions.first.map { optionUsesSuspended($0, suspended) } ?? [])
            return .routed(options: avoidingOptions, excludedLineIds: excluded)
        }
        if let naive = naiveOptions.first {
            let hit = optionUsesSuspended(naive, suspended)
            if !hit.isEmpty {
                let relevant = notices.filter { notice in
                    isClosure(notice) && notice.affectedLineIds.contains { hit.contains(normalizeLine($0)) }
                }
                return .suspended(affectedLineIds: hit, notices: relevant)
            }
        }
        return .noRoute
    }
}
