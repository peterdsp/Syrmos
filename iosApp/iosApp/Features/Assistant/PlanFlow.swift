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
        arriveBy: Date? = nil
    ) -> [PlannedJourney] {
        guard let base = JourneyPlanner.planDetailed(from: fromId, to: toId, language: language) else { return [] }
        var used: [String] = []
        for l in base.legs where !used.contains(l.lineId) { used.append(l.lineId) }
        var routes = [base]
        for banned in used {
            if let alt = JourneyPlanner.planDetailed(from: fromId, to: toId, language: language, bannedLineIds: [banned]) {
                routes.append(alt)
            }
        }
        let built = routes
            .map { buildPlanned(detailed: $0, departuresFor: departuresFor, mode: mode, arriveBy: arriveBy) }
            .sorted { $0.durationSeconds < $1.durationSeconds }
        var seen = Set<String>()
        var distinct: [PlannedJourney] = []
        for p in built {
            let sig = p.lineChain.joined(separator: "-")
            if !seen.contains(sig) { seen.insert(sig); distinct.append(p) }
        }
        return Array(distinct.prefix(3))
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
                var ready = Date().timeIntervalSince1970
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
    @State private var selectedIdx = 0
    @State private var planned = false
    @State private var mode: JourneyPlanAdapter.Mode = .now
    @State private var arriveByTime = Date()
    // Phase R: rider accessibility preference. When on, each route discloses its
    // step-free confidence honestly (unknown until per-station data is plumbed).
    @State private var stepFree = false

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

    var body: some View {
        NavigationStack {
            ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let active = activeStore.active { resumeBanner(active) }
                endpointRow(label: t("From", "Από", "Nga", "Da"), value: name(fromId)) { toggle("from") }
                endpointRow(label: t("To", "Προς", "Për", "A"), value: name(toId)) { toggle("to") }

                if opening != nil {
                    TextField(t("Search station", "Αναζήτηση σταθμού", "Kërko stacion", "Cerca stazione"), text: $query)
                        .textFieldStyle(.roundedBorder)
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 0) {
                            ForEach(matches) { st in
                                Text(displayName(st))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 14)
                                    .contentShape(Rectangle())
                                    .onTapGesture { pick(st) }
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

                // In a backward mode only options that actually scheduled are usable.
                let usable = mode == .now ? results : results.filter { !$0.noJourney && $0.leaveBy != nil }
                if planned && usable.isEmpty {
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
                    ForEach(Array(usable.enumerated()), id: \.offset) { i, r in
                        let leaveByLabel: String? = (mode == .now) ? nil : r.leaveBy.map { lb in
                            let label = mode == .lastConnection
                                ? t("Last train home leaves", "Το τελευταίο τρένο φεύγει", "Treni i fundit niset", "L'ultimo treno parte")
                                : t("Leave by", "Αναχώρηση έως", "Nisu deri", "Parti entro")
                            return "\(label) \(athensClock(lb))"
                        }
                        optionCard(r, selected: i == selectedIdx, leaveByLabel: leaveByLabel) { selectedIdx = i }
                    }
                    // S05 selected-journey detail for the chosen option.
                    if let sel = usable.indices.contains(selectedIdx) ? usable[selectedIdx] : nil {
                        journeyDetail(sel)
                    }
                }

                savedSection
            }
            .padding(16)
            }
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
        .sheet(item: $startPlan) { plan in
            if let detailed = plan.detailed {
                GoJourneyView(
                    journey: GuidanceJourney.from(detailed, language: language),
                    language: language, store: .shared, resuming: false
                ) { startPlan = nil }
            }
        }
        // Resume an in-progress session where it left off (guidance rebuilt from the
        // frozen snapshot, names re-resolved in the current language).
        .sheet(item: $resumeLaunch) { launch in
            GoJourneyView(
                journey: launch.journey, language: language,
                store: .shared, resuming: true
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
            createdAt: ISO8601DateFormatter().string(from: Date()),
            label: nil, preferences: SavedJourneyPreferences(),
        ))
    }
    private func loadSaved(_ entry: SavedJourney) {
        fromId = entry.fromId; toId = entry.toId; opening = nil; query = ""
        runPlan()
    }
    private func deleteSaved(_ entry: SavedJourney) {
        savedStore.remove(id: entry.id)
        pendingUndo = entry
        undoWork?.cancel()
        let work = DispatchWorkItem { pendingUndo = nil; undoWork = nil }
        undoWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: work)
    }

    private var matches: [TransitStation] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return Array(stations.filter {
            q.isEmpty || $0.name.lowercased().contains(q) || $0.nameEl.lowercased().contains(q)
        }.prefix(40))
    }

    private func toggle(_ which: String) { opening = (opening == which) ? nil : which; query = "" }

    private func pick(_ st: TransitStation) {
        if opening == "from" { fromId = st.id } else { toId = st.id }
        opening = nil
        query = ""
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

    /// The S05 detail for the chosen option: summary + leg-by-leg timeline (from
    /// the shared JourneyDetail.timeline) + honest source line + Start journey.
    @ViewBuilder
    private func journeyDetail(_ p: JourneyPlanAdapter.PlannedJourney) -> some View {
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
            let now = Date()
            return ScheduleProjector.nextDepartures(for: boardId, lineIds: lineIds, limit: horizon)
                .filter { $0.lineId == lineId || (lineId == "M3" && $0.lineId == "M3_AIR") }
                .map { now.addingTimeInterval(Double($0.minutesAway) * 60) }
        }
        let arriveBy = mode == .arriveBy ? nextOccurrence(of: arriveByTime) : nil
        results = JourneyPlanAdapter.planAll(from: f, to: t, language: language,
                                             departuresFor: schedule, mode: mode, arriveBy: arriveBy)
        selectedIdx = 0
        planned = true
    }

    /// The next occurrence of the picked HH:MM in Athens time as an absolute Date.
    private func nextOccurrence(of picked: Date) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens")!
        let hm = cal.dateComponents([.hour, .minute], from: picked)
        let now = Date()
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
    private func endpointRow(label: String, value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(value).font(.headline).foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func optionCard(
        _ r: JourneyPlanAdapter.PlannedJourney,
        selected: Bool,
        leaveByLabel: String?,
        onTap: @escaping () -> Void
    ) -> some View {
        let minutes = max(1, r.durationSeconds / 60)
        let changes = r.transferCount == 1
            ? t("1 change", "1 αλλαγή", "1 ndërrim", "1 cambio")
            : "\(r.transferCount) " + t("changes", "αλλαγές", "ndërrime", "cambi")
        VStack(alignment: .leading, spacing: 4) {
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
                    ? t("stop", "στάση", "ndalesë", "fermata")
                    : t("stops", "στάσεις", "ndalesa", "fermate")))
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
