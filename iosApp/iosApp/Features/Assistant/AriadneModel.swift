import Foundation
import Combine

struct AriadneMessage: Identifiable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    var departures: [Departure] = []
}

/// Ariadne's iOS resolver. Parses offline with `AthensTransitParser`, then
/// dispatches the intent to the deterministic projector and bundled data, the
/// same contract as the KMP `AssistantViewModel`. No model, no network for
/// normal answers (alerts do an optional refresh).
@MainActor
final class AriadneModel: ObservableObject {
    @Published private(set) var messages: [AriadneMessage] = []
    @Published private(set) var thinking = false

    private let parser = AthensTransitParser(vocabulary: .fromSyrmosData())
    private let alertsService = STASYService()
    private var loc: LocalizationManager { LocalizationManager.shared }

    init() {
        messages = [greeting()]
    }

    func ask(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(AriadneMessage(fromUser: true, text: text))
        thinking = true
        Task {
            // On-device LLM (when available) rewrites fuzzy input; the
            // deterministic parser still classifies and validates it.
            let cleaned = await AriadneBrain.normalize(text) ?? text
            let reply = await resolve(parser.parse(cleaned))
            messages.append(reply)
            thinking = false
        }
    }

    // MARK: - Dispatch

    private func resolve(_ intent: AssistantIntent) async -> AriadneMessage {
        switch intent {
        case let .showDepartures(stationId, lineId, day):
            return resolveDepartures(stationId: stationId, lineId: lineId, day: day)
        case let .lastTrain(stationId, lineId):
            return resolveLastTrain(stationId: stationId, lineId: lineId)
        case let .planTrip(from, to, lowExposure):
            return resolvePlanTrip(from: from, to: to, lowExposure: lowExposure)
        case let .findStation(query):
            return resolveFindStation(query)
        case let .explainLine(lineId):
            return resolveExplainLine(lineId)
        case let .explainFare(airport, from, to):
            return resolveFare(airport: airport, from: from, to: to)
        case let .toggleFavorite(stationId):
            return resolveFavorite(stationId: stationId)
        case let .showAlerts(lineId):
            return await resolveAlerts(lineId: lineId)
        case let .openMap(stationId):
            return resolveOpenMap(stationId)
        case .help:
            return bot(helpText())
        case let .needsClarification(_, missing):
            return bot(clarify(missing))
        case .outOfScope:
            return bot(outOfScopeText())
        }
    }

    private func resolveDepartures(stationId: String?, lineId: String?, day: DayContext) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let lineIds = lineId.map { [$0] } ?? station.lineIds

        if day != .today {
            return resolveDeparturesForDay(day: day, station: station, lineIds: lineIds)
        }

        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4)
        if deps.isEmpty {
            return bot(t("No more trains from \(name(station)) right now.",
                "Δεν υπάρχουν άλλα δρομολόγια από \(name(station)) τώρα.",
                "Nuk ka më trena nga \(name(station)) tani."))
        }
        let header = t("Next from \(name(station)):", "Επόμενα από \(name(station)):", "Të ardhshmet nga \(name(station)):")
        return AriadneMessage(fromUser: false, text: header, departures: Array(deps.prefix(4)))
    }

    /// "this weekend / tomorrow / Saturday": project that whole service day from
    /// 00:00 via the projector's dayOffset path. Times are shown as text (a
    /// countdown from midnight is meaningless for a future day).
    private func resolveDeparturesForDay(day: DayContext, station: TransitStation, lineIds: [String]) -> AriadneMessage {
        let offset = Self.dayOffset(for: day)
        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4, dayOffset: offset)
        let label = dayLabel(day)
        if deps.isEmpty {
            return bot(t("I don't have \(label)'s schedule for \(name(station)) offline.",
                "Δεν έχω το πρόγραμμα \(label) για \(name(station)) εκτός σύνδεσης.",
                "Nuk e kam orarin \(label) për \(name(station)) pa internet."))
        }
        let times = deps.prefix(4).map { "\($0.lineId) \($0.time)" }.joined(separator: ", ")
        return bot(t("First trains \(label) from \(name(station)): \(times).",
            "Πρώτα δρομολόγια \(label) από \(name(station)): \(times).",
            "Trenat e parë \(label) nga \(name(station)): \(times)."))
    }

    /// Days from today to the requested DayContext (Athens calendar). Mirrors
    /// the KMP DayContextResolver.
    private static func dayOffset(for day: DayContext) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens") ?? .current
        // Calendar weekday: Sunday = 1 ... Saturday = 7. Convert to ISO Mon=1..Sun=7.
        let weekday = cal.component(.weekday, from: Date())
        let iso = weekday == 1 ? 7 : weekday - 1
        switch day {
        case .today: return 0
        case .tomorrow: return 1
        case .saturday: return (6 - iso + 7) % 7
        case .sunday: return (7 - iso + 7) % 7
        case .weekend: return iso >= 6 ? 0 : 6 - iso
        }
    }

    private func dayLabel(_ day: DayContext) -> String {
        switch day {
        case .tomorrow: return t("tomorrow", "αύριο", "nesër")
        case .weekend: return t("this weekend", "το Σαββατοκύριακο", "këtë fundjavë")
        case .saturday: return t("Saturday", "το Σάββατο", "të shtunën")
        case .sunday: return t("Sunday", "την Κυριακή", "të dielën")
        case .today: return t("today", "σήμερα", "sot")
        }
    }

    private func resolveFare(airport: Bool, from: String?, to: String?) -> AriadneMessage {
        let products = SyrmosFaresStore.shared.products
        if products.isEmpty {
            return bot(t("I don't have fare prices available offline right now.",
                "Δεν έχω διαθέσιμες τιμές εισιτηρίων εκτός σύνδεσης τώρα.",
                "Nuk kam çmime biletash të disponueshme pa internet tani."))
        }
        // Journey-derived: airport fare if the word was used OR either endpoint
        // is actually an airport station.
        let isAirport = airport || isAirportStation(from) || isAirportStation(to)
        let picks: [SyrmosFaresStore.Product]
        if isAirport {
            picks = products.filter { $0.tags.contains { $0.contains("airport") } }
                .sorted { ($0.fullPriceEur ?? .greatestFiniteMagnitude) < ($1.fullPriceEur ?? .greatestFiniteMagnitude) }
        } else {
            picks = products.filter { $0.section == "single" }
                .sorted { ($0.fullPriceEur ?? .greatestFiniteMagnitude) < ($1.fullPriceEur ?? .greatestFiniteMagnitude) }
        }
        let chosen = (picks.isEmpty ? Array(products.prefix(2)) : Array(picks.prefix(2)))
        let line = chosen.map { "\($0.localizedTitle(loc.language)) \(money($0.fullPriceEur))" }.joined(separator: " · ")
        return bot(line)
    }

    private func isAirportStation(_ stationId: String?) -> Bool {
        guard let id = stationId, let st = station(id) else { return false }
        let name = (st.name + " " + st.nameEl).lowercased()
        return name.contains("airport") || name.contains("αεροδρ")
    }

    private func resolveFavorite(stationId: String?) -> AriadneMessage {
        guard let id = stationId else { return bot(clarify(.station)) }
        let st = station(id)
        let label = st.map { name($0) } ?? id
        let nowFavorite = toggleFavorite(id)
        return bot(nowFavorite
            ? t("Added \(label) to your favorites.", "Πρόσθεσα τον \(label) στα αγαπημένα σου.", "Shtova \(label) te të preferuarat e tua.")
            : t("Removed \(label) from your favorites.", "Αφαίρεσα τον \(label) από τα αγαπημένα σου.", "Hoqa \(label) nga të preferuarat e tua."))
    }

    /// Favorites persistence on iOS (UserDefaults set of station ids). Returns
    /// the new state. Parallels the KMP FavoritesRepository.
    private func toggleFavorite(_ stationId: String) -> Bool {
        let key = "syrmos.favorite.stations"
        var set = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
        let nowFavorite: Bool
        if set.contains(stationId) { set.remove(stationId); nowFavorite = false }
        else { set.insert(stationId); nowFavorite = true }
        UserDefaults.standard.set(Array(set), forKey: key)
        return nowFavorite
    }

    private func money(_ amount: Double?) -> String {
        guard let amount else { return "" }
        let cents = Int((amount * 100).rounded())
        return "€\(cents / 100).\(String(format: "%02d", cents % 100))"
    }

    private func resolveLastTrain(stationId: String?, lineId: String?) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let line = lineId ?? station.lineIds.first
        guard let line else { return bot(clarify(.station)) }
        guard let last = ScheduleProjector.lastTrainTonight(for: station.id, lineIds: [line]) else {
            return bot(t("Service is over for tonight at \(name(station)).",
                "Τα δρομολόγια για απόψε τελείωσαν στον \(name(station)).",
                "Shërbimi për sonte ka mbaruar te \(name(station))."))
        }
        return bot(t("Last \(displayLine(last.lineId)) from \(name(station)) leaves at \(last.time). Leave by then.",
            "Ο τελευταίος \(displayLine(last.lineId)) από \(name(station)) φεύγει \(last.time). Φύγε ως τότε.",
            "Treni i fundit \(displayLine(last.lineId)) nga \(name(station)) niset \(last.time). Nisu deri atëherë."))
    }

    /// Full point-to-point routing via `JourneyPlanner` (Dijkstra), matching
    /// the Android/Web `PlanJourneyUseCase`.
    private func resolvePlanTrip(from: String?, to: String?, lowExposure: Bool) -> AriadneMessage {
        guard let fromId = from else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }
        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))
        }
        let legs = plan.legs.map { "\(displayLine($0.lineId)) \($0.toName)" }.joined(separator: " → ")
        let transfers = plan.transfers == 0
            ? t("no change", "χωρίς αλλαγή", "pa ndërrim")
            : t("\(plan.transfers) change(s)", "\(plan.transfers) αλλαγή/ές", "\(plan.transfers) ndërrim(e)")
        let exposure = lowExposure ? "\n" + weatherAdvice(plan) : ""
        return bot(t("\(legs). About \(plan.totalMinutes) min, \(transfers).",
            "\(legs). Περίπου \(plan.totalMinutes) λεπτά, \(transfers).",
            "\(legs). Rreth \(plan.totalMinutes) min, \(transfers).") + exposure)
    }

    private enum RouteExposure { case sheltered, mixed, exposed }

    /// Real weather-aware advice: cached weather + route exposure (metro is
    /// underground/sheltered, tram is open-air). Mirrors the KMP StationComfort.
    private func weatherAdvice(_ plan: JourneyPlanner.Plan) -> String {
        let types = plan.legs.compactMap { SyrmosData.line(for: $0.lineId)?.type }
        let shelter: String
        switch routeExposure(types) {
        case .sheltered: shelter = t("mostly underground and sheltered", "κυρίως υπόγεια και υπό στέγη", "kryesisht nëntokë dhe e mbrojtur")
        case .mixed: shelter = t("partly at surface level", "εν μέρει σε επιφάνεια", "pjesërisht në sipërfaqe")
        case .exposed: shelter = t("open-air (tram/surface stops)", "σε ανοιχτό χώρο (τραμ/επιφάνεια)", "në ajër të hapur (tram/sipërfaqe)")
        }
        if let snap = WeatherStore.shared.snapshot {
            if snap.current.condition.isWet {
                return t("It's wet in \(snap.placeName) right now, and this route is \(shelter).",
                    "Έχει βροχή στην \(snap.placeName) τώρα, και η διαδρομή είναι \(shelter).",
                    "Ka shi në \(snap.placeName) tani, dhe kjo rrugë është \(shelter).")
            }
            return t("It's dry in \(snap.placeName) right now; this route is \(shelter).",
                "Δεν βρέχει στην \(snap.placeName) τώρα· η διαδρομή είναι \(shelter).",
                "S'ka shi në \(snap.placeName) tani; kjo rrugë është \(shelter).")
        }
        return t("I can't check live weather offline, but this route is \(shelter).",
            "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά η διαδρομή είναι \(shelter).",
            "Nuk e kontrolloj dot motin pa internet, por kjo rrugë është \(shelter).")
    }

    private func routeExposure(_ types: [TransitType]) -> RouteExposure {
        func e(_ type: TransitType) -> RouteExposure {
            switch type { case .metro: return .sheltered; case .tram: return .exposed; case .suburban: return .mixed }
        }
        let es = types.map(e)
        if es.contains(.exposed) { return .exposed }
        if es.contains(.mixed) { return .mixed }
        return .sheltered
    }

    private func resolveFindStation(_ query: String) -> AriadneMessage {
        let folded = AthensTransitParser.fold(query)
        let matches = allStations().filter {
            AthensTransitParser.fold($0.name).contains(folded) || AthensTransitParser.fold($0.nameEl).contains(folded)
        }
        guard let top = matches.first else {
            return bot(t("I couldn't find a station matching that.",
                "Δεν βρήκα σταθμό που να ταιριάζει.", "Nuk gjeta një stacion që përputhet."))
        }
        let names = matches.prefix(3).map { name($0) }.joined(separator: ", ")
        _ = top
        return bot(t("Found: \(names).", "Βρέθηκαν: \(names).", "U gjet: \(names)."))
    }

    private func resolveExplainLine(_ lineId: String) -> AriadneMessage {
        guard let line = SyrmosData.line(for: normalizeLine(lineId)) else { return bot(outOfScopeText()) }
        return bot(t("\(line.name): \(line.terminalA) to \(line.terminalB), \(line.stationCount) stations.",
            "\(line.name): \(line.terminalA) ως \(line.terminalB), \(line.stationCount) σταθμοί.",
            "\(line.name): \(line.terminalA) deri \(line.terminalB), \(line.stationCount) stacione."))
    }

    private func resolveAlerts(lineId: String?) async -> AriadneMessage {
        await alertsService.fetchAnnouncements()
        let alerts = alertsService.announcements.filter { $0.category == .serviceAlert }
        if let first = alerts.first {
            let titles = alerts.prefix(2).map { $0.displayTitle(language: loc.language) }.joined(separator: "; ")
            _ = first
            return bot(t("Active alerts: \(titles)", "Ενεργές ειδοποιήσεις: \(titles)", "Njoftime aktive: \(titles)"))
        }
        if let status = alertsService.serviceStatus, status.status == "alert" {
            return bot(status.displayMessage(language: loc.language))
        }
        return bot(t("No active service alerts right now.",
            "Δεν υπάρχουν ενεργές ειδοποιήσεις τώρα.", "Nuk ka njoftime aktive tani."))
    }

    private func resolveOpenMap(_ stationId: String?) -> AriadneMessage {
        if let id = stationId, let st = station(id) {
            return bot(t("\(name(st)) is on the Map tab, with live train positions.",
                "Ο \(name(st)) είναι στον Χάρτη, με ζωντανές θέσεις συρμών.",
                "\(name(st)) është te Harta, me pozicione të drejtpërdrejta."))
        }
        return bot(t("Open the Map tab to see live train positions.",
            "Άνοιξε τον Χάρτη για ζωντανές θέσεις συρμών.", "Hap Hartën për pozicionet e trenave."))
    }

    // MARK: - Helpers

    private func resolveStation(stationId: String?, lineId: String?) -> TransitStation? {
        if let id = stationId { return station(id) }
        if let line = lineId { return SyrmosData.stations(for: normalizeLine(line)).first }
        return nil
    }

    private func allStations() -> [TransitStation] {
        var seen = Set<String>()
        var out: [TransitStation] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id); out.append(st)
            }
        }
        return out
    }

    private func station(_ id: String) -> TransitStation? { allStations().first { $0.id == id } }

    private func name(_ st: TransitStation) -> String {
        loc.language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
    }

    private func displayLine(_ lineId: String) -> String { normalizeLine(lineId) }
    private func normalizeLine(_ lineId: String) -> String { lineId.hasPrefix("M3") ? "M3" : lineId }

    private func greeting() -> AriadneMessage {
        bot(t("Hi, I'm Ariadne. Ask me about Athens trains, last departures, or how to get somewhere.",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για τα τρένα της Αθήνας, τελευταία δρομολόγια ή πώς να πας κάπου.",
            "Përshëndetje, jam Ariadne. Më pyet për trenat e Athinës, nisjet e fundit ose si të shkosh diku."))
    }

    private func helpText() -> String {
        t("I can show next departures, the last train home, plan a trip between two stations, explain a line, and show alerts. I only cover Syrmos and Athens public transport, fully offline.",
            "Μπορώ να δείξω επόμενες αναχωρήσεις, το τελευταίο τρένο, διαδρομή, να εξηγήσω μια γραμμή και ειδοποιήσεις. Καλύπτω μόνο το Syrmos και τις συγκοινωνίες της Αθήνας, εκτός σύνδεσης.",
            "Mund të tregoj nisjet, trenin e fundit, një udhëtim, të shpjegoj një linjë dhe njoftimet. Mbuloj vetëm Syrmos dhe transportin e Athinës, pa internet.")
    }

    private func outOfScopeText() -> String {
        t("I can only help with Syrmos and Athens public transport.",
            "Μπορώ να βοηθήσω μόνο με το Syrmos και τις συγκοινωνίες της Αθήνας.",
            "Mund të ndihmoj vetëm me Syrmos dhe transportin publik të Athinës.")
    }

    private func clarify(_ missing: MissingSlot) -> String {
        switch missing {
        case .originStation: return t("From which station?", "Από ποιον σταθμό;", "Nga cili stacion?")
        case .destinationStation: return t("To which station?", "Προς ποιον σταθμό;", "Te cili stacion?")
        case .station: return t("Which station?", "Ποιος σταθμός;", "Cili stacion?")
        }
    }

    private func bot(_ text: String) -> AriadneMessage { AriadneMessage(fromUser: false, text: text) }

    private func t(_ en: String, _ el: String, _ sq: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        default: return en
        }
    }
}
