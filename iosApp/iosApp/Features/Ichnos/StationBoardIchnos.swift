import SwiftUI

/// Ichnos, scoped to the departure the rider is actually looking at.
///
/// The community feed lived only in Explore, so the answer to "does this affect
/// MY departure?" was a network-wide summary the rider had to interpret. This
/// attaches the same feed to a board group, at the narrowest scope the backend
/// supports, and says plainly when only a broader scope is available.
///
/// What it must never do: present a report for the opposite direction as a
/// confirmed issue on this train, turn a community delay report into an ETA,
/// treat a failed fetch as "everything is fine", or count a report that was
/// never accepted.
enum StationBoardIchnos {

    /// The scopes a board group can be asked about, narrowest first.
    ///
    /// Scope ids are derived from the SAME complex/line/destination identity the
    /// board uses, so a report filed from a row and a report read on that row
    /// agree. `stableScopeId` mirrors the hashing the Explore surface already
    /// uses, so both surfaces address one backend scope.
    struct Scope: Equatable {
        let id: String
        let label: String
        /// How specific this scope is. A broader scope is still useful, but it
        /// must be visibly labelled as broader.
        let breadth: Breadth

        enum Breadth: Int, Comparable {
            /// This service, in this direction, at this station complex.
            case direction = 0
            /// This service at this station complex, both directions.
            case service = 1
            /// This station complex, every service.
            case station = 2
            /// The whole network.
            case network = 3

            static func < (a: Breadth, b: Breadth) -> Bool { a.rawValue < b.rawValue }
        }
    }

    static func scopes(
        complex: StationComplexBoard.Complex,
        group: StationComplexBoard.Group
    ) -> [Scope] {
        let complexName = complex.name
        return [
            Scope(
                id: stableScopeId("\(complex.id)|\(group.lineId)|\(group.destinationKey)"),
                label: "\(complexName) · \(group.lineId) → \(group.destination)",
                breadth: .direction
            ),
            Scope(
                id: stableScopeId("\(complex.id)|\(group.lineId)"),
                label: "\(complexName) · \(group.lineId)",
                breadth: .service
            ),
            Scope(id: stableScopeId(complex.id), label: complexName, breadth: .station),
            Scope(id: "network", label: "", breadth: .network),
        ]
    }

    /// FNV-1a, byte-identical to the Explore surface's own scope hashing, so the
    /// two surfaces address the same backend scope rather than two shadows of it.
    static func stableScopeId(_ value: String) -> String {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in value.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211
        }
        return "scope_\(String(hash, radix: 16))"
    }

    /// What the rider is shown. Loading, unavailable and "no recent reports" are
    /// three different facts and never collapse into one another.
    enum State: Equatable {
        case idle
        case loading
        /// The request failed. This is NOT "no problems reported".
        case unavailable
        /// The scope loaded and genuinely has no current community reports.
        case noRecentReports(Scope)
        case loaded(Scope, [Issue])
    }

    struct Issue: Equatable, Identifiable {
        let id: String
        let signal: String
        let detail: String
        let count: Int
        let reportedAt: Date?
        /// True when the report is older than the backend's active window, so it
        /// is shown as history rather than as a current warning.
        let expired: Bool

        /// "12 min ago" / "2 h ago". Nil when the backend sent no usable time,
        /// in which case the row says so rather than implying freshness.
        func age(now: Date, language: AppLanguage) -> String? {
            guard let reportedAt else { return nil }
            let minutes = max(0, Int(now.timeIntervalSince(reportedAt) / 60))
            if minutes < 1 {
                switch language {
                case .greek: return "μόλις τώρα"
                case .albanian: return "sapo tani"
                case .italian: return "proprio ora"
                case .english: return "just now"
                }
            }
            if minutes < 60 {
                switch language {
                case .greek: return "πριν \(minutes) λεπ"
                case .albanian: return "\(minutes) min më parë"
                case .italian: return "\(minutes) min fa"
                case .english: return "\(minutes) min ago"
                }
            }
            let hours = minutes / 60
            switch language {
            case .greek: return "πριν \(hours) ώ"
            case .albanian: return "\(hours) o më parë"
            case .italian: return "\(hours) h fa"
            case .english: return "\(hours) h ago"
            }
        }
    }

    /// How long a community report stays a CURRENT warning. Past this it is
    /// still shown, labelled as history, because a stale report silently
    /// presented as current is the defect this window exists to prevent.
    static let activeWindow: TimeInterval = 2 * 60 * 60

    /// Keep only the reports that genuinely belong to this group's scope.
    ///
    /// The backend returns a scope's issues; at a broader scope those can
    /// describe a different service or the opposite direction, so they are
    /// retained but the caller shows the scope label that earned them. Reports
    /// naming a different line are dropped outright: a Line 2 report is not
    /// evidence about an A3 train.
    static func relevant(
        _ issues: [IchnosCommunityIssue],
        group: StationComplexBoard.Group,
        now: Date
    ) -> [Issue] {
        issues.compactMap { raw in
            let scopeFold = StationComplexBoard.fold(raw.scopeLabel)
            let lineFold = StationComplexBoard.fold(group.lineId)
            // A scope label that names a DIFFERENT line is not about this train.
            if !scopeFold.isEmpty,
               scopeFold.contains("→"),
               !scopeFold.contains(lineFold) {
                return nil
            }
            let reportedAt = parseTimestamp(raw.latestAt)
            let expired = reportedAt.map { now.timeIntervalSince($0) > activeWindow } ?? false
            return Issue(
                id: raw.id,
                signal: raw.signal,
                detail: raw.detail,
                count: raw.count,
                reportedAt: reportedAt,
                expired: expired
            )
        }
    }

    static func parseTimestamp(_ raw: String) -> Date? {
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = iso.date(from: raw) { return date }
        iso.formatOptions = [.withInternetDateTime]
        return iso.date(from: raw)
    }
}

/// Loads and submits Ichnos context for one board group.
///
/// Submission is idempotent by construction: the report id is minted once per
/// composition and reused on every retry, so repeated taps and a retry after a
/// network failure cannot produce two accepted reports. The local contribution
/// count moves only after the backend confirms acceptance, and undo removes the
/// same id it sent.
@MainActor
final class StationBoardIchnosModel: ObservableObject {
    @Published private(set) var state: StationBoardIchnos.State = .idle
    @Published private(set) var submission: SubmissionState = .ready
    /// Reports this device has filed and had accepted, so undo is offered only
    /// for a report that actually exists.
    @Published private(set) var lastAcceptedReportId: String?

    enum SubmissionState: Equatable {
        case ready
        case sending
        case sent
        /// The send failed. The same report id is retried, so a retry cannot
        /// create a second report.
        case failed
    }

    private var loadTask: Task<Void, Never>?
    private var pendingReportId: String?

    func load(complex: StationComplexBoard.Complex, group: StationComplexBoard.Group) {
        loadTask?.cancel()
        state = .loading
        let scopes = StationBoardIchnos.scopes(complex: complex, group: group)
        loadTask = Task { [weak self] in
            for scope in scopes {
                if Task.isCancelled { return }
                let summary = await IchnosCommunityService.shared.fetchSummary(
                    scopeId: scope.breadth == .network ? nil : scope.id
                )
                guard let summary else {
                    // A failed request at the narrowest scope is not evidence
                    // that the broader scope is clean, so try the next one; only
                    // if every scope fails is the answer "unavailable".
                    continue
                }
                let issues = StationBoardIchnos.relevant(
                    summary.issues, group: group, now: SyrmosClock.now
                )
                guard let self, !Task.isCancelled else { return }
                if issues.isEmpty {
                    // A scope that loaded and is genuinely quiet is an answer.
                    // Only widen when the narrow scope had nothing AND a broader
                    // one might: the station scope is the last one worth widening
                    // to before reporting a quiet network.
                    if scope.breadth < .station { continue }
                    self.state = .noRecentReports(scope)
                    return
                }
                self.state = .loaded(scope, issues)
                return
            }
            guard let self, !Task.isCancelled else { return }
            self.state = .unavailable
        }
    }

    func cancel() {
        loadTask?.cancel()
        loadTask = nil
    }

    /// Submit, or retry the same report. Never mints a second id for a retry.
    func submit(
        complex: StationComplexBoard.Complex,
        group: StationComplexBoard.Group,
        signal: String,
        detail: String,
        language: AppLanguage
    ) {
        guard submission != .sending else { return }
        let reportId = pendingReportId ?? UUID().uuidString
        pendingReportId = reportId
        submission = .sending
        let scope = StationBoardIchnos.scopes(complex: complex, group: group)[0]
        Task { [weak self] in
            let accepted = await IchnosCommunityService.shared.submit(
                reportId: reportId,
                context: RailPulseReportContext(
                    scopeId: scope.id,
                    title: scope.label,
                    subtitle: group.destination
                ),
                signal: signal,
                detail: detail,
                language: language
            )
            guard let self else { return }
            if accepted {
                self.submission = .sent
                // Only now is this a contribution.
                self.lastAcceptedReportId = reportId
                self.pendingReportId = nil
            } else {
                self.submission = .failed
            }
        }
    }

    /// Undo an accepted report, reconciling the local contribution state.
    func undo() {
        guard let reportId = lastAcceptedReportId else { return }
        Task { [weak self] in
            let removed = await IchnosCommunityService.shared.delete(reportId: reportId)
            guard let self, removed else { return }
            self.lastAcceptedReportId = nil
            self.submission = .ready
        }
    }
}
