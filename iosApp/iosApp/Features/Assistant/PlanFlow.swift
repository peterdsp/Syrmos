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
        /// First-leg departure: the "leave by" answer for arrive-by / last-train.
        var leaveBy: Date? = nil
        /// True in a backward mode when nothing could be scheduled (no train).
        var noJourney: Bool = false
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
        let transfers = max(0, detailed.legs.count - 1)
        let chain = detailed.legs.map { $0.lineId }

        guard let departuresFor = departuresFor else {
            return PlannedJourney(lineChain: chain, transferCount: transfers,
                                  durationSeconds: detailed.totalMinutes * 60,
                                  feasibility: transfers == 0 ? .comfortable : .unknown)
        }

        let transferMin: TimeInterval = 120
        let hops = { (leg: JourneyPlanner.DetailedLeg) in max(1, leg.stationIds.count - 1) }
        let travelOf = { (leg: JourneyPlanner.DetailedLeg) in Double(hops(leg) * perHopSeconds(leg.lineId)) }
        func depsOf(_ leg: JourneyPlanner.DetailedLeg) -> [TimeInterval] {
            departuresFor(leg.lineId, leg.boardId).map { $0.timeIntervalSince1970 }.sorted()
        }

        var depByLeg = [TimeInterval?](repeating: nil, count: detailed.legs.count)
        var arrByLeg = [TimeInterval?](repeating: nil, count: detailed.legs.count)
        var timedAll = true

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
            // Backward: latest departures that still arrive by the deadline. For
            // last-connection the last leg takes the latest available departure.
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
            if mode != .now {
                return PlannedJourney(lineChain: chain, transferCount: transfers,
                                      durationSeconds: detailed.totalMinutes * 60,
                                      feasibility: .unknown, leaveBy: nil, noJourney: true)
            }
            return PlannedJourney(lineChain: chain, transferCount: transfers,
                                  durationSeconds: detailed.totalMinutes * 60,
                                  feasibility: transfers == 0 ? .comfortable : .unknown)
        }

        // Worst transfer margin across consecutive rides.
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
            leaveBy: Date(timeIntervalSince1970: firstDep)
        )
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
    @State private var mode: JourneyPlanAdapter.Mode = .now
    @State private var arriveByTime = Date()

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
                    if mode != .now && r.noJourney {
                        Text(mode == .lastConnection
                             ? t("No more trains tonight.", "Δεν υπάρχουν άλλα τρένα απόψε.", "Nuk ka më trena sonte.", "Nessun altro treno stanotte.")
                             : t("No journey arrives by that time.", "Καμία διαδρομή δεν φτάνει ως τότε.", "Asnjë udhëtim s'mbërrin në kohë.", "Nessun viaggio arriva in tempo."))
                            .foregroundStyle(.secondary)
                    } else {
                        resultCard(r)
                        if mode != .now, let lb = r.leaveBy {
                            let label = mode == .lastConnection
                                ? t("Last train home leaves", "Το τελευταίο τρένο φεύγει", "Treni i fundit niset", "L'ultimo treno parte")
                                : t("Leave by", "Αναχώρηση έως", "Nisu deri", "Parti entro")
                            Text("\(label) \(athensClock(lb))")
                                .font(.headline)
                                .foregroundStyle(Color.syrmosPrimary)
                        }
                    }
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
        result = JourneyPlanAdapter.plan(from: f, to: t, language: language,
                                         departuresFor: schedule, mode: mode, arriveBy: arriveBy)
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
