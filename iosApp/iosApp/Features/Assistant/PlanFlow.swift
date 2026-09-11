import SwiftUI

// iOS mirror of the web/Android Plan flow (Phase P). Pick From/To, Find routes,
// and see a ranked option with honest estimated timing + feasibility, driven by
// the same JourneyPlanner topology engine. The graph has no schedule, so times
// are estimated (never a fabricated clock): a direct ride reads "Comfortable",
// a transfer reads "Estimated times" (feasibility unknown), matching web/Android.

enum JourneyPlanAdapter {
    enum Feasibility { case comfortable, tight, missed, unknown }

    struct PlannedJourney: Equatable {
        let lineChain: [String]
        let transferCount: Int
        let durationSeconds: Int
        let feasibility: Feasibility
    }

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
        departuresFor: ((_ lineId: String, _ boardId: String) -> [Date])? = nil
    ) -> PlannedJourney? {
        guard let detailed = JourneyPlanner.planDetailed(from: fromId, to: toId, language: language) else { return nil }
        let transfers = max(0, detailed.legs.count - 1)
        let chain = detailed.legs.map { $0.lineId }

        guard let departuresFor = departuresFor else {
            return PlannedJourney(lineChain: chain, transferCount: transfers,
                                  durationSeconds: detailed.totalMinutes * 60,
                                  feasibility: transfers == 0 ? .comfortable : .unknown)
        }

        // Schedule-aware pass: assign the earliest catchable departure per leg and
        // take the worst transfer margin (default transfer minimum 120s, tight <=179s).
        let transferMin: TimeInterval = 120
        var readyEpoch = Date().timeIntervalSince1970
        var firstDep: TimeInterval?
        var lastArr: TimeInterval?
        var prevArr: TimeInterval?
        var timedAll = true
        var worst: Feasibility = .comfortable

        for leg in detailed.legs {
            let board = leg.boardId
            let deps = departuresFor(leg.lineId, board).map { $0.timeIntervalSince1970 }.sorted()
            let ready = (firstDep == nil) ? readyEpoch : (prevArr! + transferMin)
            guard let dep = deps.first(where: { $0 >= ready }) else { timedAll = false; break }
            let hops = max(1, leg.stationIds.count - 1)
            let arr = dep + Double(hops * perHopSeconds(leg.lineId))
            if let pArr = prevArr {
                let margin = dep - pArr - transferMin
                let s: Feasibility = margin < 0 ? .missed : (margin <= 179 ? .tight : .comfortable)
                if severity(s) > severity(worst) { worst = s }
            }
            if firstDep == nil { firstDep = dep }
            lastArr = arr
            prevArr = arr
            readyEpoch = arr
        }

        if timedAll, let fd = firstDep, let la = lastArr {
            return PlannedJourney(lineChain: chain, transferCount: transfers,
                                  durationSeconds: Int(la - fd),
                                  feasibility: transfers == 0 ? .comfortable : worst)
        }
        // Could not time the whole trip -> honest estimate + unknown feasibility.
        return PlannedJourney(lineChain: chain, transferCount: transfers,
                              durationSeconds: detailed.totalMinutes * 60,
                              feasibility: transfers == 0 ? .comfortable : .unknown)
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
    @State private var result: JourneyPlanAdapter.PlannedJourney?
    @State private var planned = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
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

                Button { runPlan() } label: {
                    Text(t("Find routes", "Βρες διαδρομές", "Gjej rrugët", "Trova percorsi"))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(fromId == nil || toId == nil || opening != nil)

                if planned, result == nil {
                    Text(t("No route found.", "Δεν βρέθηκε διαδρομή.", "Nuk u gjet rrugë.", "Nessun percorso trovato."))
                        .foregroundStyle(.secondary)
                } else if let r = result {
                    resultCard(r)
                }

                Spacer()
            }
            .padding(16)
            .navigationTitle(t("Plan a journey", "Σχεδίασε διαδρομή", "Planifiko udhëtim", "Pianifica un viaggio"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(t("Close", "Κλείσιμο", "Mbyll", "Chiudi")) { dismiss() }
                }
            }
        }
        .onAppear { if stations.isEmpty { stations = JourneyPlanAdapter.allStations() } }
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

    private func runPlan() {
        guard let f = fromId, let t = toId else { return }
        // Real timetable from the iOS projector: next departures of the leg's line
        // at the board station, as absolute instants (now + minutesAway). Empty
        // when the projector has no data, so the adapter keeps the estimate.
        let schedule: (String, String) -> [Date] = { lineId, boardId in
            let lineIds = lineId == "M3" ? ["M3", "M3_AIR"] : [lineId]
            let now = Date()
            return ScheduleProjector.nextDepartures(for: boardId, lineIds: lineIds, limit: 8)
                .filter { $0.lineId == lineId || (lineId == "M3" && $0.lineId == "M3_AIR") }
                .map { now.addingTimeInterval(Double($0.minutesAway) * 60) }
        }
        result = JourneyPlanAdapter.plan(from: f, to: t, language: language, departuresFor: schedule)
        planned = true
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
    private func resultCard(_ r: JourneyPlanAdapter.PlannedJourney) -> some View {
        let minutes = max(1, r.durationSeconds / 60)
        let changes = r.transferCount == 1
            ? t("1 change", "1 αλλαγή", "1 ndërrim", "1 cambio")
            : "\(r.transferCount) " + t("changes", "αλλαγές", "ndërrime", "cambi")
        VStack(alignment: .leading, spacing: 4) {
            Text("1 " + t("route", "διαδρομή", "rrugë", "percorso")).font(.headline)
            Text("~\(minutes) " + t("min", "λεπ", "min", "min") + " · \(changes) · " + r.lineChain.joined(separator: " → "))
                .foregroundStyle(.secondary)
            Text(feasLabel(r.feasibility)).font(.subheadline).foregroundStyle(Color.syrmosPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.gray.opacity(0.08)))
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
