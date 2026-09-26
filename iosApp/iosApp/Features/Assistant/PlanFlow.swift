import SwiftUI
import UIKit

// iOS mirror of the web/Android Plan flow (Phase P). Pick From/To, Find routes,
// and see a ranked option with honest estimated timing + feasibility, driven by
// the same JourneyPlanner topology engine. The graph has no schedule, so times
// are estimated (never a fabricated clock): a direct ride reads "Comfortable",
// a transfer reads "Estimated times" (feasibility unknown), matching web/Android.

enum JourneyPlanAdapter {
    enum Feasibility { case comfortable, tight, missed, unknown }

    struct PlannedJourney: Equatable, Identifiable {
        let lineChain: [String]
        let transferCount: Int
        let durationSeconds: Int
        let feasibility: Feasibility
        /// First-leg departure: the "leave by" answer for arrive-by / last-train.
        var leaveBy: Date? = nil
        /// True in a backward mode when nothing could be scheduled (no train).
        var noJourney: Bool = false
        /// The topology plan behind this option, kept so GO can be started (S05 -> S06).
        var detailed: JourneyPlanner.DetailedPlan? = nil
        /// The S05 timeline input (ride + synthesized transfer legs, with clocks).
        var detailLegs: [JourneyDetail.DetailLeg] = []

        /// Stable-ish identity for SwiftUI sheet presentation.
        var id: String { lineChain.joined(separator: "-") + (leaveBy.map { "|\(Int($0.timeIntervalSince1970))" } ?? "|e") }

        /// Reduced shape for the shared DisruptionExclusion engine (ride legs only).
        var disruptionOption: DisruptionOption {
            DisruptionOption(legs: lineChain.map { DisruptionLeg(kind: "ride", lineId: $0) })
        }
    }

    enum Mode { case now, arriveBy, lastConnection }

    /// All routable stations (deduped across lines), the picker's source.
    static func allStations() -> [TransitStation] {
        var seen = Set<String>()
        var out: [TransitStation] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id)
                out.append(st)
            }
        }
        return out
    }

    /// Plan a journey. When `departuresFor` is supplied (real projected departure
    /// instants per line at a board station), the option is scheduled and
    /// feasibility is real (comfortable/tight/missed); otherwise it falls back to
    /// an honest estimated option (transfers => unknown, direct => comfortable).
    /// Mirrors web `SyrmosSchedulePlan` + `SyrmosFeasibility` and Kotlin
    /// `SchedulePlanner` + `FeasibilityCalculator`.
    static func plan(
        from fromId: String,
        to toId: String,
        language: AppLanguage,
        departuresFor: ((_ lineId: String, _ boardId: String) -> [Date])? = nil,
        mode: Mode = .now,
        arriveBy: Date? = nil
    ) -> PlannedJourney? {
        guard let detailed = JourneyPlanner.planDetailed(from: fromId, to: toId, language: language) else { return nil }
        return buildPlanned(detailed: detailed, departuresFor: departuresFor, mode: mode, arriveBy: arriveBy)
    }

    /// Up to 3 materially distinct alternatives via line-banning (re-plan with each
    /// line the base uses removed), deduped by line chain, ordered by duration,
    /// capped at 3. Mirrors web/Android k-shortest.
    static func planAll(
        from fromId: String,
        to toId: String,
        language: AppLanguage,
        departuresFor: ((_ lineId: String, _ boardId: String) -> [Date])? = nil,
        mode: Mode = .now,
        arriveBy: Date? = nil,
        // Phase R: lines the operator has suspended (CLOSURE notices). Every route
        // is planned with these banned so we never hand back a plan through closed
        // track. Normalized ids (see DisruptionExclusion.normalizeLine).
        suspendedLineIds: Set<String> = []
    ) -> [PlannedJourney] {
        // Suspended ids arrive normalized; JourneyPlanner bans by raw Line.id, so
        // map them back to the real ids of the operational lines.
        let rawSuspended = Set(SyrmosData.operationalLines
            .filter { suspendedLineIds.contains(DisruptionExclusion.normalizeLine($0.id)) }
            .map { $0.id })
        func planAvoiding(_ extraBanned: Set<String>) -> JourneyPlanner.DetailedPlan? {
            JourneyPlanner.planDetailed(
                from: fromId, to: toId, language: language,
                bannedLineIds: rawSuspended.union(extraBanned))
        }
        guard let base = planAvoiding([]) else { return [] }
        var used: [String] = []
        for l in base.legs where !used.contains(l.lineId) { used.append(l.lineId) }
        var routes = [base]
        for banned in used {
            if let alt = planAvoiding([banned]) {
                routes.append(alt)
            }
        }
        // Default recommendation is comfortable-first (finding 7, product decision
        // option 2), then the deterministic objective chain that restores parity
        // with the shared Kotlin/web ranker: duration, arrival, changes, stable id.
        // A missed/closed connection sorts last so it can never lead the list; an
        // unknown arrival stays unknown (sorts last among ties), never a fake 0.
        let built = routes
            .map { buildPlanned(detailed: $0, departuresFor: departuresFor, mode: mode, arriveBy: arriveBy) }
            .sorted { recommendedSortKey($0) < recommendedSortKey($1) }
        var seen = Set<String>()
        var distinct: [PlannedJourney] = []
        for p in built {
            let sig = p.lineChain.joined(separator: "-")
            if !seen.contains(sig) { seen.insert(sig); distinct.append(p) }
        }
        return Array(distinct.prefix(3))
    }

    /// Phase R S07: per-transfer connection risk for a scheduled option, computed
    /// from the real leg clocks. transferRisks[i] describes the change from ride
    /// leg i to ride leg i+1, aligned with the GuidanceJourney legs GO steps
    /// through. Empty when the option carries no clocks (honest: no warning).
    static func transferRisks(for p: PlannedJourney) -> [TransferRisk] {
        let rides = p.detailLegs.filter { $0.kind == "ride" }
        guard rides.count > 1 else { return [] }
        var out: [TransferRisk] = []
        for i in 0..<(rides.count - 1) {
            let minSec = transferMinBetween(p.detailLegs, rides[i], rides[i + 1]) ?? ConnectionRisk.defaultRecommendedSeconds
            let available: Int? = (rides[i].arrival != nil && rides[i + 1].departure != nil)
                ? Int(rides[i + 1].departure!.timeIntervalSince(rides[i].arrival!)) : nil
            // Shared S07 rule, consistent with web/Kotlin and the feasibility chip.
            let status = ConnectionRisk.status(availableSeconds: available, recommendedSeconds: minSec)
            out.append(TransferRisk(status: status, availableSeconds: available, minimumSeconds: minSec))
        }
        return out
    }

    private static func transferMinBetween(
        _ legs: [JourneyDetail.DetailLeg],
        _ prev: JourneyDetail.DetailLeg,
        _ next: JourneyDetail.DetailLeg
    ) -> Int? {
        guard let pi = legs.firstIndex(of: prev), let ni = legs.firstIndex(of: next), pi < ni else { return nil }
        for j in (pi + 1)..<ni where legs[j].kind == "transfer" || legs[j].kind == "walk" {
            if let m = legs[j].transferMinimumSeconds { return m }
        }
        return nil
    }

    private static func buildPlanned(
        detailed: JourneyPlanner.DetailedPlan,
        departuresFor: ((_ lineId: String, _ boardId: String) -> [Date])?,
        mode: Mode,
        arriveBy: Date?
    ) -> PlannedJourney {
        let transfers = max(0, detailed.legs.count - 1)
        let chain = detailed.legs.map { $0.lineId }
        let transferMin: TimeInterval = 120

        // Per-leg dep/arr epoch seconds; stay nil when unscheduled or not fully timed.
        var depByLeg = [TimeInterval?](repeating: nil, count: detailed.legs.count)
        var arrByLeg = [TimeInterval?](repeating: nil, count: detailed.legs.count)
        var timedAll = false

        if let departuresFor = departuresFor {
            let hops = { (leg: JourneyPlanner.DetailedLeg) in max(1, leg.stationIds.count - 1) }
            let travelOf = { (leg: JourneyPlanner.DetailedLeg) in Double(hops(leg) * perHopSeconds(leg.lineId)) }
            func depsOf(_ leg: JourneyPlanner.DetailedLeg) -> [TimeInterval] {
                departuresFor(leg.lineId, leg.boardId).map { $0.timeIntervalSince1970 }.sorted()
            }
            timedAll = true
            if mode == .now {
                // Forward: earliest catchable departure per leg from now.
                var ready = SyrmosClock.now.timeIntervalSince1970
                for (i, leg) in detailed.legs.enumerated() {
                    let target = (i == 0) ? ready : ready + transferMin
                    guard let dep = depsOf(leg).first(where: { $0 >= target }) else { timedAll = false; break }
                    let arr = dep + travelOf(leg)
                    depByLeg[i] = dep; arrByLeg[i] = arr; ready = arr
                }
            } else {
                // Backward: latest departures that still arrive by the deadline.
                var deadline = arriveBy?.timeIntervalSince1970 ?? .greatestFiniteMagnitude
                for i in stride(from: detailed.legs.count - 1, through: 0, by: -1) {
                    let leg = detailed.legs[i]
                    let travel = travelOf(leg)
                    guard let dep = depsOf(leg).filter({ $0 + travel <= deadline }).max() else { timedAll = false; break }
                    depByLeg[i] = dep; arrByLeg[i] = dep + travel
                    deadline = dep - transferMin
                }
            }
            if !timedAll {
                // Don't show partial clocks: the timeline stays honestly estimated.
                for i in 0..<depByLeg.count { depByLeg[i] = nil; arrByLeg[i] = nil }
            }
        }

        let detailLegs = makeDetailLegs(detailed, dep: depByLeg, arr: arrByLeg, scheduled: timedAll)

        // Estimated (no timetable) or not-fully-timed: honest estimate, no clocks.
        if departuresFor == nil || !timedAll {
            let noJourney = departuresFor != nil && !timedAll && mode != .now
            return PlannedJourney(
                lineChain: chain, transferCount: transfers,
                durationSeconds: detailed.totalMinutes * 60,
                feasibility: noJourney ? .unknown : (transfers == 0 ? .comfortable : .unknown),
                leaveBy: nil, noJourney: noJourney, detailed: detailed, detailLegs: detailLegs)
        }

        // Fully timed: worst transfer margin across consecutive rides.
        var worst: Feasibility = .comfortable
        for i in 1..<detailed.legs.count {
            let margin = (depByLeg[i]! - arrByLeg[i - 1]! - transferMin)
            let s: Feasibility = margin < 0 ? .missed : (margin <= 179 ? .tight : .comfortable)
            if severity(s) > severity(worst) { worst = s }
        }
        let firstDep = depByLeg[0]!
        let lastArr = arrByLeg[detailed.legs.count - 1]!
        return PlannedJourney(
            lineChain: chain, transferCount: transfers,
            durationSeconds: Int(lastArr - firstDep),
            feasibility: transfers == 0 ? .comfortable : worst,
            leaveBy: Date(timeIntervalSince1970: firstDep),
            detailed: detailed, detailLegs: detailLegs)
    }

    /// Build the S05 timeline legs: each ride plus a synthesized transfer leg
    /// between consecutive rides (the topology plan has no explicit transfers).
    private static func makeDetailLegs(
        _ detailed: JourneyPlanner.DetailedPlan, dep: [TimeInterval?], arr: [TimeInterval?], scheduled: Bool
    ) -> [JourneyDetail.DetailLeg] {
        var out: [JourneyDetail.DetailLeg] = []
        for (i, leg) in detailed.legs.enumerated() {
            if i > 0 {
                let prev = detailed.legs[i - 1]
                out.append(JourneyDetail.DetailLeg(
                    id: "transfer-\(i)", kind: "transfer", fromId: prev.alightId, toId: leg.boardId,
                    orderedStopIds: [prev.alightId, leg.boardId], transferMinimumSeconds: 120, timingKind: "estimated"))
            }
            out.append(JourneyDetail.DetailLeg(
                id: "ride-\(i)", kind: "ride", lineId: leg.lineId, fromId: leg.boardId, toId: leg.alightId,
                orderedStopIds: leg.stationIds,
                departure: scheduled ? dep[i].map { Date(timeIntervalSince1970: $0) } : nil,
                arrival: scheduled ? arr[i].map { Date(timeIntervalSince1970: $0) } : nil,
                timingKind: scheduled ? "scheduled" : "estimated"))
        }
        return out
    }

    private static func severity(_ f: Feasibility) -> Int {
        switch f { case .missed: return 3; case .unknown: return 2; case .tight: return 1; case .comfortable: return 0 }
    }

    /// Total ordering key for the comfortable-first default recommendation
    /// (finding 7). Feasibility class first (comfortable < tight < unknown < missed
    /// via `severity`), then the shared objective chain: duration, arrival (unknown
    /// last, never a fabricated 0), change count, and a stable id from the line
    /// chain so the order is deterministic on ties.
    static func recommendedSortKey(_ p: PlannedJourney) -> (Int, Int, Double, Int, String) {
        let arrival = p.leaveBy.map { $0.timeIntervalSince1970 + Double(p.durationSeconds) } ?? .greatestFiniteMagnitude
        return (severity(p.feasibility), p.durationSeconds, arrival, p.transferCount, p.lineChain.joined(separator: "-"))
    }

    // Per-hop travel estimate by line type, mirroring JourneyPlanner.travelTime.
    private static func perHopSeconds(_ lineId: String) -> Int {
        switch SyrmosData.lines.first(where: { $0.id == lineId })?.type {
        case .metro: return 120
        case .tram: return 180
        case .suburban: return 240
        case .bus: return 240
        default: return 300
        }
    }
}

struct PlanView: View {
    let language: AppLanguage
    @Environment(\.dismiss) private var dismiss

    @State private var stations: [TransitStation] = []
    @State private var fromId: String?
    @State private var toId: String?
    @State private var opening: String?   // "from" | "to" | nil
    @State private var query = ""
    @State private var results: [JourneyPlanAdapter.PlannedJourney] = []
    /// Selected itinerary by id (legs + schedule identity), never a list index,
    /// so a re-plan or a fold that refreshes results keeps the traveller's choice.
    @State private var selectedId: String? = nil
    @State private var planned = false
    @State private var mode: JourneyPlanAdapter.Mode = .now
    @State private var arriveByTime = SyrmosClock.now
    // Phase R: rider accessibility preference. When on, each route discloses its
    // step-free confidence honestly (unknown until per-station data is plumbed).
    @State private var stepFree = false
    // Phase R: live service notices drive disruption exclusion (S10 suspended
    // segment). A CLOSURE-affected line is never routed through.
    @StateObject private var alerts = STASYService()
    @State private var disruption: DisruptionOutcome? = nil
    // Phase R S10: offline-with-usable-data. Plans still work from the bundled
    // schedule; the banner just discloses the mode + offers Retry.
    @ObservedObject private var freshness = LiveDataFreshness.shared
    // Phase R S10: set when a loaded saved journey references a station that no
    // longer exists; the rider is asked to choose a replacement.
    @State private var invalidSavedNote: String? = nil

    // Saved journeys (S08 / J05): locally owned, no account.
    @ObservedObject private var savedStore = SavedJourneysStore.shared
    @State private var pendingUndo: SavedJourney?
    @State private var undoWork: DispatchWorkItem?
    @State private var renameTarget: SavedJourney?
    @State private var renameText = ""
    @State private var savedEditMode: EditMode = .inactive
    /// The option whose GO session is being started (S05 -> S06 sheet).
    @State private var startPlan: JourneyPlanAdapter.PlannedJourney?
    /// The single live GO session (S06): drives the resume banner.
    @ObservedObject private var activeStore = GoActiveJourneyStore.shared
    /// A resumed session being reopened (drives the resume sheet).
    @State private var resumeLaunch: GoResumeLaunch?

    // Plan body split into logical blocks (foldables / Duo prompt section 9.2) so
    // the same content renders as one scrolling column (compact) or two panes
    // (regular width / iPhone Duo inner display). `combined` uses all three, so
    // there is no duplicated source of truth.
    @ViewBuilder private var planPreamble: some View {
        if let active = activeStore.active { resumeBanner(active) }
        let freshnessState = FreshnessPresentation.evaluate(
            isNetworkAvailable: freshness.isNetworkAvailable, isLive: freshness.freshness == .live)
        if FreshnessPresentation.showsBanner(freshnessState) { offlineBanner(freshnessState) }
        if let note = invalidSavedNote { invalidSavedBanner(note) }
    }

    @ViewBuilder private var planQuery: some View {
        endpointRow(label: t("From", "Από", "Nga", "Da"), value: fromId == nil ? nil : name(fromId)) { toggle("from") }
        endpointRow(label: t("To", "Προς", "Për", "A"), value: toId == nil ? nil : name(toId)) { toggle("to") }

        if opening != nil {
            TextField(t("Search station", "Αναζήτηση σταθμού", "Kërko stacion", "Cerca stazione"), text: $query)
                .textFieldStyle(.roundedBorder)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(matches) { group in
                        HStack(spacing: 8) {
                            Text(groupDisplayName(group))
                                .frame(maxWidth: .infinity, alignment: .leading)
                            // Line badges disambiguate a shared physical station
                            // and any same-name stations kept separate.
                            ForEach(group.lineIds, id: \.self) { lid in
                                Text(lid)
                                    .font(.caption2.weight(.semibold))
                                    .padding(.horizontal, 6).padding(.vertical, 2)
                                    .background(Color.syrmosPrimary.opacity(0.14), in: Capsule())
                                    .foregroundStyle(Color.syrmosPrimary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 14)
                        .contentShape(Rectangle())
                        .onTapGesture { pick(group) }
                        Divider()
                    }
                }
            }
            .frame(maxHeight: 280)
        }

        // Travel-time mode: Leave now / Arrive by / Last train home.
        Picker("", selection: $mode) {
            Text(t("Leave now", "Τώρα", "Tani", "Ora")).tag(JourneyPlanAdapter.Mode.now)
            Text(t("Arrive by", "Άφιξη", "Mbërri", "Arriva")).tag(JourneyPlanAdapter.Mode.arriveBy)
            Text(t("Last train", "Τελευταίο", "I fundit", "Ultimo")).tag(JourneyPlanAdapter.Mode.lastConnection)
        }
        .pickerStyle(.segmented)

        if mode == .arriveBy {
            DatePicker(
                t("Arrive by", "Άφιξη έως", "Mbërri deri", "Arriva entro"),
                selection: $arriveByTime, displayedComponents: .hourAndMinute
            )
        }

        Toggle(isOn: $stepFree) {
            Text(t("Step-free routes", "Διαδρομές χωρίς σκαλιά", "Rrugë pa shkallë", "Percorsi senza gradini"))
                .font(.subheadline)
        }
        .tint(.syrmosPrimary)

        Button { runPlan() } label: {
            Text(t("Find routes", "Βρες διαδρομές", "Gjej rrugët", "Trova percorsi"))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .disabled(fromId == nil || toId == nil || opening != nil)
    }

    /// Companion-pane title on a paired layout (foldables / Duo): the results
    /// pane reads as its own surface, with the same name in every state.
    private var planCompanionHeader: some View {
        Text(t("Selected journey", "Επιλεγμένη διαδρομή", "Udhëtimi i zgjedhur", "Viaggio selezionato"))
            .font(.title3.weight(.semibold))
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }

    /// Calm empty state for the companion pane before the first search, so the
    /// unfolded display never shows a blank half (six-posture prompt, section 5).
    /// `afterSearch` is the honest state once a search ran but nothing is
    /// selectable (no route, suspended line): no invented route fills the pane.
    private func planCompanionPlaceholder(afterSearch: Bool) -> some View {
        VStack(alignment: .leading, spacing: SyrmosTokens.Space.sm) {
            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                .font(.title2)
                .foregroundStyle(Color.syrmosPrimary)
            Text(afterSearch
                ? t("No journey to show yet.", "Καμία διαδρομή για προβολή ακόμη.", "Ende asnjë udhëtim për t'u shfaqur.", "Nessun viaggio da mostrare ancora.")
                : t("Choose where you are going.", "Διάλεξε πού πηγαίνεις.", "Zgjidh ku po shkon.", "Scegli dove vai."))
                .font(.headline)
            Text(afterSearch
                ? t("Pick one of the routes to read it here.",
                    "Διάλεξε μία από τις διαδρομές για να τη δεις εδώ.",
                    "Zgjidh një nga rrugët për ta lexuar këtu.",
                    "Scegli uno dei percorsi per leggerlo qui.")
                : t("The selected journey's stops, times and changes appear here.",
                    "Οι στάσεις, οι ώρες και οι αλλαγές της επιλεγμένης διαδρομής εμφανίζονται εδώ.",
                    "Ndalesat, oraret dhe ndërrimet e udhëtimit të zgjedhur shfaqen këtu.",
                    "Fermate, orari e cambi del viaggio selezionato compaiono qui."))
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(SyrmosTokens.Space.xl)
        .background(
            RoundedRectangle(cornerRadius: SyrmosTokens.Radius.lg, style: .continuous)
                .fill(Color.syrmosSurfaceMuted)
        )
        .accessibilityElement(children: .combine)
    }

    /// Usable alternatives: in a backward mode only options that actually scheduled.
    private var usableResults: [JourneyPlanAdapter.PlannedJourney] {
        mode == .now ? results : results.filter { !$0.noJourney && $0.leaveBy != nil }
    }

    /// Shared comparison facts, index-aligned with `usableResults`.
    private var resultFacts: [JourneyComparison.Facts] {
        let u = usableResults
        return JourneyComparison.facts(
            durations: u.map { Optional($0.durationSeconds) },
            changes: u.map { $0.transferCount })
    }

    /// The selected option by id, falling back to the first offered one.
    private var selectedResult: JourneyPlanAdapter.PlannedJourney? {
        let u = usableResults
        guard let i = JourneySelection.index(of: selectedId, in: u.map(\.id)) else { return u.first }
        return u[i]
    }

    /// Chips on an alternative: the shared ranker's "recommended" (top option
    /// unless missed, as on Kotlin/web) plus the differences that are really there.
    private func badgeLabels(index: Int, _ r: JourneyPlanAdapter.PlannedJourney, _ f: JourneyComparison.Facts) -> [String] {
        var out: [String] = []
        if index == 0 && r.feasibility != .missed {
            out.append(t("Recommended", "Προτεινόμενη", "E rekomanduar", "Consigliato"))
        }
        if f.fastest { out.append(t("Fastest", "Ταχύτερη", "Më e shpejta", "Più veloce")) }
        if f.fewestChanges { out.append(t("Fewest changes", "Λιγότερες αλλαγές", "Më pak ndërrime", "Meno cambi")) }
        return out
    }

    /// One line under the selected journey's summary saying how it compares.
    private func comparisonLine(_ f: JourneyComparison.Facts) -> String? {
        var parts: [String] = []
        if f.fastest { parts.append(t("Fastest route", "Ταχύτερη διαδρομή", "Rruga më e shpejtë", "Percorso più veloce")) }
        if let m = f.minutesSlowerThanFastest {
            parts.append("+\(m) " + t("min vs fastest", "λεπ από την ταχύτερη", "min nga më e shpejta", "min rispetto al più veloce"))
        }
        if f.fewestChanges { parts.append(t("Fewest changes", "Λιγότερες αλλαγές", "Më pak ndërrime", "Meno cambi")) }
        if f.extraChanges == 1 {
            parts.append(t("1 more change", "1 αλλαγή παραπάνω", "1 ndërrim më shumë", "1 cambio in più"))
        } else if f.extraChanges > 1 {
            parts.append("\(f.extraChanges) " + t("more changes", "αλλαγές παραπάνω", "ndërrime më shumë", "cambi in più"))
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// The alternatives: disruption chip, suspended and no-route states, the
    /// count/save row and one card per usable option. On a paired layout this is
    /// the task pane under the query; the selected journey reads in the companion.
    @ViewBuilder private var planAlternatives: some View {
        // Phase R: disclose when we routed around a suspended line.
        if planned, case .routed(_, let excluded)? = disruption, !excluded.isEmpty {
            routingAroundChip(excluded)
        }

        let usable = usableResults
        if planned, case .suspended(let suspLines, let suspNotices)? = disruption {
            suspendedState(lines: suspLines, notices: suspNotices)
        } else if planned && usable.isEmpty {
            Text(
                mode == .lastConnection
                    ? t("No more trains tonight.", "Δεν υπάρχουν άλλα τρένα απόψε.", "Nuk ka më trena sonte.", "Nessun altro treno stanotte.")
                    : mode == .arriveBy
                        ? t("No journey arrives by that time.", "Καμία διαδρομή δεν φτάνει ως τότε.", "Asnjë udhëtim s'mbërrin në kohë.", "Nessun viaggio arriva in tempo.")
                        : t("No route found.", "Δεν βρέθηκε διαδρομή.", "Nuk u gjet rrugë.", "Nessun percorso trovato.")
            ).foregroundStyle(.secondary)
        } else if !usable.isEmpty {
            HStack {
                Text("\(usable.count) " + (usable.count == 1 ? t("route", "διαδρομή", "rrugë", "percorso") : t("routes", "διαδρομές", "rrugë", "percorsi")))
                    .font(.headline)
                Spacer()
                let alreadySaved = fromId != nil && toId != nil && savedStore.isSaved(fromId: fromId!, toId: toId!)
                Button(alreadySaved
                    ? t("Saved", "Αποθηκεύτηκε", "U ruajt", "Salvato")
                    : t("Save journey", "Αποθήκευση", "Ruaj udhëtimin", "Salva viaggio")) { saveCurrent() }
                    .buttonStyle(.bordered)
                    .disabled(alreadySaved)
            }
            let facts = resultFacts
            let selectedID = selectedResult?.id
            ForEach(Array(usable.enumerated()), id: \.element.id) { i, r in
                let leaveByLabel: String? = (mode == .now) ? nil : r.leaveBy.map { lb in
                    let label = mode == .lastConnection
                        ? t("Last train home leaves", "Το τελευταίο τρένο φεύγει", "Treni i fundit niset", "L'ultimo treno parte")
                        : t("Leave by", "Αναχώρηση έως", "Nisu deri", "Parti entro")
                    return "\(label) \(athensClock(lb))"
                }
                optionCard(r, selected: r.id == selectedID, badges: badgeLabels(index: i, r, facts[i]),
                           leaveByLabel: leaveByLabel) { selectedId = r.id }
            }
        }
    }

    /// S05 selected-journey detail for the chosen option (with its comparison line).
    @ViewBuilder private var planSelectedDetail: some View {
        let usable = usableResults
        if let sel = selectedResult, let i = usable.firstIndex(where: { $0.id == sel.id }) {
            journeyDetail(sel, facts: resultFacts[i])
        }
    }

    /// Single-column order: alternatives, then the selected journey under them.
    @ViewBuilder private var planResults: some View {
        planAlternatives
        planSelectedDetail
    }

    var body: some View {
        NavigationStack {
            SyrmosArrangement(
                pairs: true,
                task: .plan,
                primary: {
                    // Task pane: the editable query and the route alternatives
                    // (Plan contract: compare here, read the choice alongside).
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            planPreamble
                            planQuery
                            if planned { planAlternatives }
                            savedSection
                        }
                        .padding(16)
                    }
                },
                companion: {
                    // Companion pane: the selected journey's stops, times and
                    // changes, with a calm state before and after an empty search.
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            planCompanionHeader
                            if planned && selectedResult != nil {
                                planSelectedDetail
                            } else {
                                planCompanionPlaceholder(afterSearch: planned)
                            }
                        }
                        .padding(16)
                    }
                },
                combined: {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            planPreamble
                            planQuery
                            planResults
                            savedSection
                        }
                        .padding(16)
                    }
                }
            )
            .background(Color.syrmosBackground.ignoresSafeArea())
            .navigationTitle(t("Plan a journey", "Σχεδίασε διαδρομή", "Planifiko udhëtim", "Pianifica un viaggio"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("Close", "Κλείσιμο", "Mbyll", "Chiudi")) { dismiss() }
                }
            }
        }
        .onAppear {
            if stations.isEmpty { stations = JourneyPlanAdapter.allStations() }
            savedStore.refresh()
            activeStore.refresh()
        }
        .task {
            // Load live service notices so disruption exclusion has data to act on.
            await alerts.fetchAnnouncements()
        }
        .alert(t("Name this journey", "Ονόμασε τη διαδρομή", "Emërto këtë udhëtim", "Nomina questo viaggio"),
               isPresented: Binding(get: { renameTarget != nil }, set: { if !$0 { renameTarget = nil } })) {
            TextField(t("Name", "Όνομα", "Emri", "Nome"), text: $renameText)
            Button(t("Save", "Αποθήκευση", "Ruaj", "Salva")) {
                if let target = renameTarget { savedStore.rename(id: target.id, label: renameText) }
                renameTarget = nil
            }
            Button(t("Cancel", "Άκυρο", "Anulo", "Annulla"), role: .cancel) { renameTarget = nil }
        }
        // S05 -> S06: Start journey opens the GO live-guidance screen for the option,
        // starting (and persisting) a fresh live session so it survives a kill.
        // Full-window (not a form sheet) so GO can render its two-pane layout on a
        // regular-width display, and so ending is a deliberate toolbar action.
        .fullScreenCover(item: $startPlan) { plan in
            if let detailed = plan.detailed {
                GoJourneyView(
                    journey: GuidanceJourney.from(detailed, language: language),
                    language: language, store: .shared, resuming: false,
                    transferRisks: JourneyPlanAdapter.transferRisks(for: plan),
                    onFindAlternatives: { from, to in
                        startPlan = nil
                        fromId = from; toId = to; opening = nil
                        runPlan()
                    }
                ) { startPlan = nil }
            }
        }
        // Resume an in-progress session where it left off (guidance rebuilt from the
        // frozen snapshot, names re-resolved in the current language).
        .fullScreenCover(item: $resumeLaunch) { launch in
            GoJourneyView(
                journey: launch.journey, language: language,
                store: .shared, resuming: true,
                onFindAlternatives: { from, to in
                    resumeLaunch = nil
                    fromId = from; toId = to; opening = nil
                    runPlan()
                }
            ) { resumeLaunch = nil }
        }
    }

    /// A resumable launch: wraps the rebuilt GuidanceJourney with a stable id so it
    /// can drive an `item`-based sheet.
    struct GoResumeLaunch: Identifiable {
        let id = UUID()
        let journey: GuidanceJourney
    }

    // MARK: - Resume live GO session (S06)

    /// Phase R S10 / N J07 freshness banner: compact, discloses that plans come from
    /// the saved timetable and offers Retry. Driven by the shared FreshnessPresentation
    /// rule so it also shows when online but no live data is available (predicted),
    /// not connectivity-only. Wording reuses the app's runningOffline /
    /// predictedFromSchedule set. Never blocks planning.
    @ViewBuilder
    private func offlineBanner(_ state: FreshnessBannerState) -> some View {
        let offline = state == .offline
        let icon = offline ? "wifi.slash" : "clock.arrow.circlepath"
        let title = offline
            ? t("Running offline", "Εκτός σύνδεσης", "Pa internet", "Offline")
            : t("Predicted from schedule", "Πρόβλεψη από το πρόγραμμα", "Parashikuar nga orari", "Previsto dall'orario")
        HStack(spacing: 10) {
            Image(systemName: icon).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(t("Routes use the saved timetable.", "Οι διαδρομές χρησιμοποιούν το αποθηκευμένο δρομολόγιο.",
                       "Rrugët përdorin orarin e ruajtur.", "I percorsi usano l'orario salvato."))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(t("Retry", "Επανάληψη", "Riprovo", "Riprova")) { freshness.requestRetry() }
                .font(.subheadline).buttonStyle(.bordered)
        }
        .frame(minHeight: 48)
        .padding(.horizontal, 12).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    /// Phase R S10 invalid saved/deep-link id: names the missing selection and
    /// points the rider at the empty picker to choose a replacement.
    private func invalidSavedBanner(_ note: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "questionmark.circle.fill").foregroundStyle(.orange)
            Text(note).font(.subheadline).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.orange.opacity(0.12)))
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func resumeBanner(_ active: GoActiveJourney) -> some View {
        let fromId = active.itinerarySnapshot.legs.first?.fromId
        let toId = active.itinerarySnapshot.legs.last?.toId
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(t("Journey in progress", "Διαδρομή σε εξέλιξη", "Udhëtim në vazhdim", "Viaggio in corso"))
                    .font(.subheadline.weight(.semibold))
                Text("\(name(fromId)) → \(name(toId))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { resume(active) }

            Button(t("End", "Τέλος", "Përfundo", "Termina")) { activeStore.clear() }
                .buttonStyle(.bordered)
            Button(t("Resume", "Συνέχεια", "Vazhdo", "Riprendi")) { resume(active) }
                .buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.accentColor.opacity(0.12)))
    }

    private func resume(_ active: GoActiveJourney) {
        let journey = GoActiveJourneyContract.guidance(from: active.itinerarySnapshot, language: language)
        guard !journey.legs.isEmpty else { return }
        resumeLaunch = GoResumeLaunch(journey: journey)
    }

    // MARK: - Saved journeys (S08 / J05)

    @ViewBuilder
    private var savedSection: some View {
        Divider()
        HStack {
            Text(t("Saved journeys", "Αποθηκευμένες διαδρομές", "Udhëtimet e ruajtura", "Viaggi salvati"))
                .font(.headline)
            Spacer()
            // Native reorder: toggling edit mode reveals the List's drag handles and
            // delete controls. Only shown with more than one journey to reorder.
            if savedStore.items.count > 1 {
                Button(savedEditMode == .active
                    ? t("Done", "Τέλος", "U krye", "Fine")
                    : t("Reorder", "Αναδιάταξη", "Risistemo", "Riordina")) {
                    withAnimation { savedEditMode = (savedEditMode == .active) ? .inactive : .active }
                }
                .font(.subheadline)
            }
        }
        if let undo = pendingUndo {
            HStack {
                Text(t("Journey deleted", "Η διαδρομή διαγράφηκε", "Udhëtimi u fshi", "Viaggio eliminato"))
                Spacer()
                Button(t("Undo", "Αναίρεση", "Zhbëj", "Annulla")) {
                    savedStore.save(undo)
                    pendingUndo = nil
                    undoWork?.cancel(); undoWork = nil
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        }
        if savedStore.items.isEmpty {
            Text(t(
                "No saved journeys yet. Plan a route and tap Save.",
                "Καμία αποθηκευμένη διαδρομή. Σχεδίασε μια διαδρομή και πάτα Αποθήκευση.",
                "Ende s'ka udhëtime të ruajtura. Planifiko një rrugë dhe shtyp Ruaj.",
                "Nessun viaggio salvato. Pianifica un percorso e tocca Salva."))
                .font(.subheadline).foregroundStyle(.secondary)
        } else {
            // A List gives native drag-to-reorder (.onMove) and swipe/edit delete
            // (.onDelete). Height is bound to the row count so it sits inside the
            // non-scrolling Plan layout without a greedy List grabbing all space.
            List {
                ForEach(savedStore.items) { entry in
                    savedRow(entry)
                }
                .onMove { source, destination in moveSaved(from: source, to: destination) }
                .onDelete { indices in deleteSaved(at: indices) }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .scrollDisabled(true)
            .environment(\.editMode, $savedEditMode)
            .frame(height: CGFloat(savedStore.items.count) * 68 + 4)
        }
    }

    @ViewBuilder
    private func savedRow(_ entry: SavedJourney) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.label ?? pairName(entry.fromId, entry.toId))
                    .font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                if entry.label != nil {
                    Text(pairName(entry.fromId, entry.toId)).font(.caption).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture { if savedEditMode != .active { loadSaved(entry) } }
            // Borderless so the List row doesn't treat the whole row as one button.
            Button { renameTarget = entry; renameText = entry.label ?? "" } label: {
                Image(systemName: "pencil")
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(t("Rename", "Μετονομασία", "Riemërto", "Rinomina"))
        }
    }

    /// Native List reorder: `Array.move` handles up/down moves in one call, then the
    /// new id order is persisted through the shared store.
    private func moveSaved(from source: IndexSet, to destination: Int) {
        var ids = savedStore.items.map { $0.id }
        ids.move(fromOffsets: source, toOffset: destination)
        savedStore.reorder(ids)
    }

    /// List swipe/edit delete, routed through the same 5s-Undo delete as the button.
    private func deleteSaved(at indices: IndexSet) {
        for idx in indices where savedStore.items.indices.contains(idx) {
            deleteSaved(savedStore.items[idx])
        }
    }

    private func pairName(_ from: String, _ to: String) -> String {
        name(from) + " → " + name(to)
    }
    private func saveCurrent() {
        guard let f = fromId, let to = toId, f != to else { return }
        savedStore.save(SavedJourney(
            id: savedStore.newId(), fromId: f, toId: to,
            createdAt: ISO8601DateFormatter().string(from: SyrmosClock.now),
            label: nil, preferences: SavedJourneyPreferences(),
        ))
    }
    private func stationExists(_ id: String?) -> Bool {
        guard let id else { return false }
        return stations.contains { $0.id == id }
    }

    /// Phase R S10 invalid saved/deep-link id: preserve the endpoints that still
    /// resolve, name the missing one, and prompt for a replacement rather than
    /// silently planning an impossible trip or crashing.
    private func loadSaved(_ entry: SavedJourney) {
        let fromOk = stationExists(entry.fromId)
        let toOk = stationExists(entry.toId)
        fromId = fromOk ? entry.fromId : nil
        toId = toOk ? entry.toId : nil
        opening = nil; query = ""
        if fromOk && toOk {
            invalidSavedNote = nil
            runPlan()
        } else {
            planned = false
            results = []
            invalidSavedNote = t(
                "A station in this saved journey is no longer available. Choose a replacement.",
                "Ένας σταθμός σε αυτή την αποθηκευμένη διαδρομή δεν είναι πλέον διαθέσιμος. Επίλεξε αντικατάσταση.",
                "Një stacion në këtë udhëtim të ruajtur nuk është më i disponueshëm. Zgjidh një zëvendësim.",
                "Una stazione di questo viaggio salvato non è più disponibile. Scegli un'alternativa.")
        }
    }
    private func deleteSaved(_ entry: SavedJourney) {
        savedStore.remove(id: entry.id)
        pendingUndo = entry
        undoWork?.cancel()
        let work = DispatchWorkItem { pendingUndo = nil; undoWork = nil }
        undoWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: work)
    }

    // One row per real physical station (finding 2): co-located same-name stops
    // collapse into a single group with line badges, so the picker never lists the
    // same station twice, while distinct stations (Kifissia vs Kifisias) stay apart.
    private var matches: [StationGroup] {
        let q = StationGrouping.fold(query)
        return Array(StationGrouping.groups(from: stations)
            .filter { StationGrouping.matches($0, query: q) }
            .prefix(40))
    }

    private func toggle(_ which: String) { opening = (opening == which) ? nil : which; query = "" }

    private func pick(_ group: StationGroup) {
        // Carry the group's representative stop; the planner reaches every member
        // line through its co-located transfer edges, so no service is lost.
        if opening == "from" { fromId = group.representativeId } else { toId = group.representativeId }
        opening = nil
        query = ""
        // S10 recovery: once both endpoints resolve again, clear the note and plan.
        if invalidSavedNote != nil, stationExists(fromId), stationExists(toId) {
            invalidSavedNote = nil
            runPlan()
        }
    }

    private func groupDisplayName(_ g: StationGroup) -> String {
        language == .greek && !g.nameEl.isEmpty ? g.nameEl : g.name
    }

    private func name(_ id: String?) -> String {
        guard let id, let st = stations.first(where: { $0.id == id }) else { return "-" }
        return displayName(st)
    }

    private func displayName(_ st: TransitStation) -> String {
        language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
    }

    private func stationName(_ id: String) -> String {
        stations.first(where: { $0.id == id }).map(displayName) ?? id
    }

    // MARK: - S05 selected-journey detail

    /// Phase R accessibility-unknown disclosure for one option. No per-station
    /// step-free data is plumbed on iOS yet, so every leg reads `unknown` and the
    /// honest disclosure is "not confirmed" — never a fabricated "accessible".
    /// Uses the shared `AccessibilityDisclosure` engine (mirrored on web/KMP).
    private func stepFreeInfo(_ p: JourneyPlanAdapter.PlannedJourney) -> AccessibilityInfo {
        let legs = p.detailLegs.enumerated().map { i, _ in
            AccessibilityLeg(id: "leg-\(i)", accessibility: "unknown")
        }
        return AccessibilityDisclosure.forOption(legs: legs, preference: "stepFree")
    }

    @ViewBuilder
    private func stepFreeDisclosure(_ p: JourneyPlanAdapter.PlannedJourney) -> some View {
        let info = stepFreeInfo(p)
        let (icon, tint, text): (String, Color, String) = {
            switch info.confidence {
            case .verified:
                return ("figure.roll", .green,
                    t("Step-free the whole way.", "Χωρίς σκαλιά σε όλη τη διαδρομή.",
                      "Pa shkallë gjatë gjithë rrugës.", "Senza gradini per tutto il percorso."))
            case .unavailable:
                return ("exclamationmark.triangle.fill", .orange,
                    t("This route isn't step-free.", "Αυτή η διαδρομή δεν είναι χωρίς σκαλιά.",
                      "Kjo rrugë nuk është pa shkallë.", "Questo percorso non è senza gradini."))
            case .unknown:
                return ("questionmark.circle.fill", .secondary,
                    t("Step-free access isn't confirmed for this route.",
                      "Η πρόσβαση χωρίς σκαλιά δεν επιβεβαιώνεται για αυτή τη διαδρομή.",
                      "Qasja pa shkallë nuk është konfirmuar për këtë rrugë.",
                      "L'accesso senza gradini non è confermato per questo percorso."))
            }
        }()
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon).foregroundStyle(tint)
            Text(text).font(.footnote).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }

    /// Phase R S10 "Suspended segment": the only path rode a closed line and no
    /// route avoids it. Name the affected line(s) and the operator source; offer
    /// the official update. Never fabricate a replacement.
    @ViewBuilder
    private func suspendedState(lines: Set<String>, notices: [DisruptionNotice]) -> some View {
        let lineList = lines.map { $0.uppercased() }.sorted().joined(separator: ", ")
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.octagon.fill").foregroundStyle(.orange)
                Text(t("\(lineList) is suspended", "Η \(lineList) έχει ανασταλεί", "\(lineList) është pezulluar", "\(lineList) è sospesa"))
                    .font(.headline)
            }
            Text(t("No route avoids the closed section. Check the operator for alternatives and updates.",
                   "Καμία διαδρομή δεν παρακάμπτει το κλειστό τμήμα. Δες τον πάροχο για εναλλακτικές και ενημερώσεις.",
                   "Asnjë rrugë s'e shmang pjesën e mbyllur. Shiko operatorin për alternativa dhe përditësime.",
                   "Nessun percorso evita il tratto chiuso. Controlla l'operatore per alternative e aggiornamenti."))
                .font(.subheadline).foregroundStyle(.secondary)
            ForEach(notices, id: \.id) { n in
                if let ann = alerts.announcements.first(where: { $0.id == n.id }) {
                    Text(ann.displayTitle(language: language))
                        .font(.footnote).foregroundStyle(.secondary)
                    if let url = ann.url {
                        Link(t("Official update", "Επίσημη ενημέρωση", "Përditësim zyrtar", "Aggiornamento ufficiale"), destination: url)
                            .font(.footnote)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.orange.opacity(0.12)))
    }

    /// Phase R: a compact chip disclosing that the plan detours around a suspended line.
    @ViewBuilder
    private func routingAroundChip(_ excluded: Set<String>) -> some View {
        let list = excluded.map { $0.uppercased() }.sorted().joined(separator: ", ")
        HStack(spacing: 8) {
            Image(systemName: "arrow.triangle.branch").foregroundStyle(.orange)
            Text(t("Routing around suspended \(list).", "Παράκαμψη της ανασταλμένης \(list).",
                   "Duke anashkaluar \(list) të pezulluar.", "Percorso che evita \(list) sospesa."))
                .font(.footnote).foregroundStyle(.secondary)
        }
        .padding(.vertical, 6).padding(.horizontal, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.orange.opacity(0.12)))
    }

    /// The S05 detail for the chosen option: summary + leg-by-leg timeline (from
    /// the shared JourneyDetail.timeline) + honest source line + Start journey.
    @ViewBuilder
    private func journeyDetail(_ p: JourneyPlanAdapter.PlannedJourney,
                               facts: JourneyComparison.Facts = JourneyComparison.Facts()) -> some View {
        let rows = JourneyDetail.timeline(p.detailLegs)
        let dep = rows.first(where: { $0.kind == "board" })?.clock
        let arr = rows.last(where: { $0.kind == "alight" })?.clock
        let anyScheduled = rows.contains { $0.timingKind == "scheduled" || $0.timingKind == "live" }
        let minutes = max(1, p.durationSeconds / 60)
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("~\(minutes) " + t("min", "λεπ", "min", "min")).font(.title2.weight(.bold))
                if let dep, let arr {
                    Text("\(athensClock(dep)) – \(athensClock(arr))")
                        .font(.headline).foregroundStyle(.secondary)
                }
            }
            if let line = comparisonLine(facts) {
                Text(line).font(.subheadline.weight(.semibold)).foregroundStyle(Color.syrmosPrimary)
            }
            ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                timelineRow(r, legs: p.detailLegs)
            }
            Text(anyScheduled
                ? t("Times from the published timetable.", "Χρόνοι από το επίσημο δρομολόγιο.", "Kohët nga orari zyrtar.", "Orari dal calendario ufficiale.")
                : t("Estimated times — no live schedule for this route yet.", "Εκτιμώμενοι χρόνοι — χωρίς ζωντανό δρομολόγιο ακόμη.", "Kohë të vlerësuara — ende pa orar të drejtpërdrejtë.", "Orari stimato — nessun orario dal vivo per questo percorso."))
                .font(.footnote).foregroundStyle(.secondary)
            if stepFree { stepFreeDisclosure(p) }
            Button { startPlan = p } label: {
                Text(t("Start journey", "Ξεκίνα τη διαδρομή", "Nis udhëtimin", "Avvia il viaggio"))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(p.detailed == nil)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.gray.opacity(0.08)))
    }

    @ViewBuilder
    private func timelineRow(_ r: JourneyDetail.TimelineRow, legs: [JourneyDetail.DetailLeg]) -> some View {
        let major = r.node == .origin || r.node == .destination || r.node == .interchange
        HStack(alignment: .top, spacing: 10) {
            // Clock column.
            Text(r.clock.map(athensClock) ?? ((r.kind == "board" || r.kind == "alight") ? "~" : ""))
                .font(.caption).foregroundStyle(.secondary)
                .frame(width: 46, alignment: .trailing)
            // Node column: rail line + dot.
            ZStack(alignment: .top) {
                Rectangle()
                    .fill(Color.secondary.opacity(r.kind == "board" || r.kind == "alight" ? 0.9 : 0.35))
                    .frame(width: 3).frame(maxHeight: .infinity)
                if r.kind == "board" || r.kind == "alight" {
                    Circle()
                        .fill(major ? Color(uiColor: .systemBackground) : Color.syrmosPrimary)
                        .frame(width: major ? 14 : 10, height: major ? 14 : 10)
                        .overlay(Circle().stroke(Color.syrmosPrimary, lineWidth: major ? 2 : 0))
                        .padding(.top, 3)
                }
            }
            .frame(width: 16)
            // Instruction column.
            VStack(alignment: .leading, spacing: 2) {
                switch r.kind {
                case "board":
                    Text(t("Board", "Επιβίβαση", "Hip", "Sali") + " \(r.lineId ?? "") "
                        + t("toward", "προς", "drejt", "verso") + " " + stationName(r.towardsId ?? ""))
                        .font(.subheadline)
                case "alight":
                    Text(t("Alight", "Αποβίβαση", "Zbrit", "Scendi") + " " + stationName(r.stationId ?? ""))
                        .font(.subheadline)
                case "stops":
                    StopsDisclosure(count: r.count ?? 0, legId: r.legId, legs: legs, nm: stationName, t: t)
                default:
                    let mins = r.seconds.map { max(1, $0 / 60) }
                    let word = r.kind == "walk" ? t("Walk", "Περπάτημα", "Ecje", "Cammina") : t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio")
                    Text(word + (mins.map { " · \($0) " + t("min", "λεπ", "min", "min") } ?? ""))
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, 12)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private func runPlan() {
        guard let f = fromId, let t = toId else { return }
        // Real timetable from the iOS projector: next departures of the leg's line
        // at the board station, as absolute instants (now + minutesAway). Empty
        // when the projector has no data, so the adapter keeps the estimate.
        // Forward needs ~20 departures; backward (arrive-by / last train) needs the
        // whole remaining service day to find the true latest catchable train.
        let horizon = (mode == .now) ? 20 : 120
        let schedule: (String, String) -> [Date] = { lineId, boardId in
            let lineIds = lineId == "M3" ? ["M3", "M3_AIR"] : [lineId]
            let now = SyrmosClock.now
            return ScheduleProjector.nextDepartures(for: boardId, lineIds: lineIds, limit: horizon)
                .filter { $0.lineId == lineId || (lineId == "M3" && $0.lineId == "M3_AIR") }
                .map { now.addingTimeInterval(Double($0.minutesAway) * 60) }
        }
        let arriveBy = mode == .arriveBy ? nextOccurrence(of: arriveByTime) : nil

        // Phase R disruption exclusion. Suspended lines (CLOSURE notices) are
        // banned so no route rides closed track. If a route around them exists we
        // show it (disclosing the detour); if the only path used a suspended line
        // we surface the S10 suspended state instead of a plan we can't travel.
        let notices = disruptionNotices()
        let suspended = DisruptionExclusion.suspendedLineIds(notices)
        let naive = JourneyPlanAdapter.planAll(from: f, to: t, language: language,
                                               departuresFor: schedule, mode: mode, arriveBy: arriveBy)
        let avoiding = suspended.isEmpty ? naive : JourneyPlanAdapter.planAll(
            from: f, to: t, language: language, departuresFor: schedule,
            mode: mode, arriveBy: arriveBy, suspendedLineIds: suspended)
        let outcome = DisruptionExclusion.classify(
            avoidingOptions: avoiding.map { $0.disruptionOption },
            naiveOptions: naive.map { $0.disruptionOption },
            notices: notices)
        disruption = outcome
        if case .suspended = outcome {
            results = []          // show the suspended state, not an untravellable plan
        } else {
            results = avoiding
        }
        selectedId = JourneySelection.retain(previous: selectedId, ids: results.map(\.id))
        planned = true
    }

    /// Projects the live STASY announcements into the disruption engine's notice
    /// shape (severity token + affected line ids). Pure mapping.
    private func disruptionNotices() -> [DisruptionNotice] {
        // Same pre-filter as the Android/web callers: service alerts or any
        // non-info severity (only CLOSURE ultimately suspends a line).
        alerts.announcements.filter {
            $0.category == .serviceAlert || AdvisorySeverity.fromRaw($0.severity) != .info
        }.map { a in
            let token: String
            switch AdvisorySeverity.fromRaw(a.severity) {
            case .closure: token = "closure"
            case .warning: token = "warning"
            case .info: token = "info"
            }
            return DisruptionNotice(id: a.id, severity: token, affectedLineIds: a.affectedLines)
        }
    }

    /// The next occurrence of the picked HH:MM in Athens time as an absolute Date.
    private func nextOccurrence(of picked: Date) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens")!
        let hm = cal.dateComponents([.hour, .minute], from: picked)
        let now = SyrmosClock.now
        let nowHM = cal.dateComponents([.hour, .minute], from: now)
        var delta = ((hm.hour ?? 0) * 60 + (hm.minute ?? 0)) - ((nowHM.hour ?? 0) * 60 + (nowHM.minute ?? 0))
        if delta < 0 { delta += 24 * 60 }
        return now.addingTimeInterval(Double(delta) * 60)
    }

    private func athensClock(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = TimeZone(identifier: "Europe/Athens")
        f.dateFormat = "HH:mm"
        return f.string(from: date)
    }

    @ViewBuilder
    /// An endpoint field. Before a station is chosen it reads as an invitation
    /// ("Choose a station", secondary, with a chevron) instead of a bare dash.
    private func endpointRow(label: String, value: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(label).font(.caption).foregroundStyle(.secondary)
                    Text(value ?? t("Choose a station", "Διάλεξε σταθμό", "Zgjidh stacion", "Scegli una stazione"))
                        .font(.headline)
                        .foregroundStyle(value == nil ? Color.secondary : Color.primary)
                }
                Spacer(minLength: 0)
                Image(systemName: value == nil ? "chevron.right" : "pencil")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label + ", " + (value ?? t("not chosen", "δεν έχει επιλεγεί", "pa zgjedhur", "non scelta")))
    }

    @ViewBuilder
    private func optionCard(
        _ r: JourneyPlanAdapter.PlannedJourney,
        selected: Bool,
        badges: [String] = [],
        leaveByLabel: String?,
        onTap: @escaping () -> Void
    ) -> some View {
        let minutes = max(1, r.durationSeconds / 60)
        let changes = r.transferCount == 1
            ? t("1 change", "1 αλλαγή", "1 ndërrim", "1 cambio")
            : "\(r.transferCount) " + t("changes", "αλλαγές", "ndërrime", "cambi")
        VStack(alignment: .leading, spacing: 4) {
            if !badges.isEmpty {
                // Atomic chips: a label never wraps or compresses mid-word.
                HStack(spacing: 6) {
                    ForEach(badges, id: \.self) { b in
                        Text(b)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Capsule().fill(Color.syrmosPrimary.opacity(0.14)))
                            .foregroundStyle(Color.syrmosPrimary)
                    }
                }
                .padding(.bottom, 2)
            }
            Text("~\(minutes) " + t("min", "λεπ", "min", "min") + " · \(changes) · " + r.lineChain.joined(separator: " → "))
                .foregroundStyle(.primary)
            Text(feasLabel(r.feasibility)).font(.subheadline).foregroundStyle(Color.syrmosPrimary)
            if let lb = leaveByLabel {
                Text(lb).font(.subheadline.weight(.semibold)).foregroundStyle(Color.syrmosPrimary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(selected ? Color.syrmosPrimary.opacity(0.12) : Color.gray.opacity(0.08)))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(selected ? Color.syrmosPrimary : Color.clear, lineWidth: 2))
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }

    private func feasLabel(_ f: JourneyPlanAdapter.Feasibility) -> String {
        switch f {
        case .comfortable: return t("Comfortable", "Άνετη", "Komode", "Comoda")
        case .tight: return t("Tight", "Στενή", "E ngushtë", "Stretta")
        case .missed: return t("Missed", "Χαμένη", "Humbur", "Persa")
        case .unknown: return t("Estimated times", "Εκτιμώμενοι χρόνοι", "Kohë të vlerësuara", "Orari stimato")
        }
    }

    private func t(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        default: return en
        }
    }
}

/// The "N stops" disclosure in the S05 timeline: a tap reveals the intermediate
/// station names of the ride leg (kept out of the way by default).
private struct StopsDisclosure: View {
    let count: Int
    let legId: String
    let legs: [JourneyDetail.DetailLeg]
    let nm: (String) -> String
    let t: (String, String, String, String) -> String
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Button { expanded.toggle() } label: {
                Text("\(count) " + (count == 1
                    ? t("intermediate stop", "ενδιάμεση στάση", "ndalesë e ndërmjetme", "fermata intermedia")
                    : t("intermediate stops", "ενδιάμεσες στάσεις", "ndalesa të ndërmjetme", "fermate intermedie")))
                    .font(.footnote).foregroundStyle(Color.syrmosPrimary)
            }
            .buttonStyle(.plain)
            if expanded {
                let leg = legs.first { $0.id == legId }
                let mid = leg.map { Array($0.orderedStopIds.dropFirst().dropLast()) } ?? []
                Text(mid.map(nm).joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

/// Adaptive two-pane container (foldables / iPhone Duo prompt, section 9.2;
/// six-posture prompt, section 9, item 1).
///
/// The arrangement is decided by `SyrmosAdaptiveWorkspacePolicy`, the Swift twin
/// of the shared Kotlin policy, from the container's measured size, the regions
/// the system reports for it (a fold division, an occluding hinge), the Dynamic
/// Type scale and the task:
///
///   single       the shipped single scrolling column (`combined`)
///   sideBySide   task pane beside its companion (a wide window, a book fold,
///                a planner on the Duo inner display held upright)
///   stacked      companion (map, overview) above, task and controls below (a
///                horizontal fold, GO or Explore on the tall Duo inner display)
///
/// On iOS 27.1 the native `ArrangementView` split style renders the pair on the
/// chosen axis; older systems get an `HStack` / `VStack` fallback that honours
/// the same pane rectangles. `pairs` is false for single-focus tasks (forms).
struct SyrmosArrangement<Primary: View, Companion: View, Combined: View>: View {
    var pairs: Bool = true
    /// The task driving the workspace; selects the preferred axis on a tall,
    /// medium-width window and whether a companion is offered at all.
    var task: SyrmosWorkspaceTask = .plan
    @ViewBuilder var primary: () -> Primary
    @ViewBuilder var companion: () -> Companion
    @ViewBuilder var combined: () -> Combined

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.syrmosReservedGeometryOverride) private var geometryOverride

    var body: some View {
        GeometryReader { geo in
            let geometry = geometryOverride ?? geo.syrmosReservedGeometry()
            let workspace = SyrmosAdaptiveWorkspacePolicy.resolve(
                size: geo.size,
                task: pairs ? task : .form,
                regions: geometry.regions,
                fontScale: SyrmosDynamicType.fontScale(dynamicTypeSize)
            )
            content(for: workspace, size: geo.size)
        }
    }

    @ViewBuilder private func content(for ws: SyrmosAdaptiveWorkspace, size: CGSize) -> some View {
        switch ws.arrangement {
        case .single:
            // The policy's single column is a READABLE column (outer insets and a
            // content width from the shared breakpoint), so a wide window that
            // cannot pair still reads as a centred column, not a full-width one.
            combined()
                .frame(maxWidth: CGFloat(ws.pane(.task)?.rect.width ?? Int(size.width)))
                .frame(maxWidth: .infinity)
                .environment(\.syrmosIsPaired, false).environment(\.syrmosArrangementAxis, nil)
        case .sideBySide:
            paired(ws, size: size, axis: .horizontal)
                .environment(\.syrmosIsPaired, true).environment(\.syrmosArrangementAxis, .horizontal)
        case .stacked:
            paired(ws, size: size, axis: .vertical)
                .environment(\.syrmosIsPaired, true).environment(\.syrmosArrangementAxis, .vertical)
        }
    }

    @ViewBuilder private func paired(_ ws: SyrmosAdaptiveWorkspace, size: CGSize, axis: Axis) -> some View {
        // `ArrangementView` is an iOS 27.1 SDK symbol. `#available` only gates the
        // runtime, not compilation, and the compiler version does not discriminate
        // (Xcode 27.0 and 27.1 both ship Swift 6.4, but only the 27.1 SDK exposes the
        // symbol). Swift has no SDK-version `#if`, so gate on the custom flag
        // `SYRMOS_DUO_SDK`, which is defined only in a build against the 27.1 SDK
        // (see docs/design/FOLDABLE-READINESS.md). CI and release builds (Xcode 26.x
        // / 27.0 SDK) leave it undefined and compile the fallback, which is the
        // shipping two-pane.
        #if SYRMOS_DUO_SDK
        if #available(iOS 27.1, *) {
            nativeSplit(ws, size: size, axis: axis)
        } else {
            fallbackSplit(ws, size: size, axis: axis)
        }
        #else
        fallbackSplit(ws, size: size, axis: axis)
        #endif
    }

    #if SYRMOS_DUO_SDK
    /// Native paired content. The system's split style owns the axis on the Duo
    /// runtime (restricting it with `.axes(_:)` hid the secondary pane there, see
    /// docs/design/FOLDABLE-READINESS.md), so the policy contributes the pane
    /// ORDER and the ratio: on a stacked decision the companion (map, overview)
    /// is the arrangement's primary so it lands on top, with its share of the
    /// height; on a side-by-side decision the task pane leads with its share of
    /// the width.
    @available(iOS 27.1, *)
    @ViewBuilder private func nativeSplit(_ ws: SyrmosAdaptiveWorkspace, size: CGSize, axis: Axis) -> some View {
        switch axis {
        case .horizontal:
            ArrangementView {
                primary()
            } secondary: {
                companion()
            }
            .arrangementViewStyle(.split)
            .splitArrangementLayoutRatio(SyrmosArrangementRule.taskShare(ws, size: size))
        case .vertical:
            ArrangementView {
                companion()
            } secondary: {
                primary()
            }
            .arrangementViewStyle(.split)
            .splitArrangementLayoutRatio(SyrmosArrangementRule.companionShare(ws, size: size))
        }
    }
    #endif

    /// Two-pane used on toolchains / systems without the native `ArrangementView`
    /// (pre iOS 27.1, or a pre-27.1 SDK build). Honours the policy's pane
    /// rectangles: the task column (or the companion band) takes its fitted
    /// extent, an occluding hinge is left empty, a division gets a hairline.
    @ViewBuilder private func fallbackSplit(_ ws: SyrmosAdaptiveWorkspace, size: CGSize, axis: Axis) -> some View {
        let gap = SyrmosArrangementRule.gap(ws, axis: axis)
        switch axis {
        case .horizontal:
            HStack(spacing: 0) {
                primary().frame(width: SyrmosArrangementRule.taskExtent(ws, axis: axis))
                if gap > 0 { Color.clear.frame(width: gap) } else { Divider() }
                companion().frame(maxWidth: .infinity)
            }
        case .vertical:
            VStack(spacing: 0) {
                companion().frame(height: SyrmosArrangementRule.companionExtent(ws, axis: axis))
                if gap > 0 { Color.clear.frame(height: gap) } else { Divider() }
                primary().frame(maxHeight: .infinity)
            }
        }
    }
}

/// True inside a paired arrangement (side by side or stacked), so list content
/// can select into the companion pane instead of pushing a new screen.
private struct SyrmosIsPairedKey: EnvironmentKey {
    static let defaultValue = false
}

/// The axis of the current paired arrangement (nil when single), so a screen can
/// compose its panes per posture: a stacked GO keeps the map alone above and the
/// timeline with the instruction below.
private struct SyrmosArrangementAxisKey: EnvironmentKey {
    static let defaultValue: Axis? = nil
}

extension EnvironmentValues {
    var syrmosIsPaired: Bool {
        get { self[SyrmosIsPairedKey.self] }
        set { self[SyrmosIsPairedKey.self] = newValue }
    }
    var syrmosArrangementAxis: Axis? {
        get { self[SyrmosArrangementAxisKey.self] }
        set { self[SyrmosArrangementAxisKey.self] = newValue }
    }
}

/// Reads the arrangement axis INSIDE a pane. The screen that owns the
/// arrangement cannot read it from its own environment (the value is set on the
/// panes' content), so pane builders wrap their content in this reader.
struct SyrmosAxisReader<Content: View>: View {
    @Environment(\.syrmosArrangementAxis) private var axis
    @ViewBuilder let content: (Axis?) -> Content
    var body: some View { content(axis) }
}

/// The pure numbers `SyrmosArrangement` derives from a resolved workspace, kept
/// out of the generic view so tests can pin them.
enum SyrmosArrangementRule {
    /// The narrowest width at which a planner pairs side by side at default
    /// text: two map-floor halves. Mirrors the shared policy.
    static var pairFloor: CGFloat { CGFloat(SyrmosAdaptiveWorkspacePolicy.minMapPane * 2) }

    /// Width of the task column on the horizontal axis: the task pane's right
    /// edge, so any outer inset the policy placed before it stays inside the
    /// column (the panes carry their own padding).
    static func taskExtent(_ ws: SyrmosAdaptiveWorkspace, axis: Axis) -> CGFloat {
        guard let task = ws.pane(.task) else { return 0 }
        return CGFloat(axis == .horizontal ? task.rect.right : task.rect.bottom)
    }

    /// Height of the companion band on the vertical axis (its bottom edge).
    static func companionExtent(_ ws: SyrmosAdaptiveWorkspace, axis: Axis) -> CGFloat {
        guard let companion = ws.pane(.companion) else { return 0 }
        return CGFloat(axis == .vertical ? companion.rect.bottom : companion.rect.right)
    }

    /// The empty extent between the two panes: an occluding hinge's thickness,
    /// or the policy's column gap on a wide window; 0 when the halves abut.
    static func gap(_ ws: SyrmosAdaptiveWorkspace, axis: Axis) -> CGFloat {
        guard let task = ws.pane(.task), let companion = ws.pane(.companion) else { return 0 }
        switch axis {
        case .horizontal: return CGFloat(max(0, companion.rect.left - task.rect.right))
        case .vertical: return CGFloat(max(0, task.rect.top - companion.rect.bottom))
        }
    }

    /// The task pane's share of the width (native horizontal split ratio).
    static func taskShare(_ ws: SyrmosAdaptiveWorkspace, size: CGSize) -> CGFloat {
        guard size.width > 0 else { return 0.5 }
        return min(max(taskExtent(ws, axis: .horizontal) / size.width, 0.2), 0.8)
    }

    /// The companion band's share of the height (native vertical split ratio).
    static func companionShare(_ ws: SyrmosAdaptiveWorkspace, size: CGSize) -> CGFloat {
        guard size.height > 0 else { return 0.5 }
        return min(max(companionExtent(ws, axis: .vertical) / size.height, 0.2), 0.8)
    }
}
