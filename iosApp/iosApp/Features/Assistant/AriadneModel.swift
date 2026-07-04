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
    // Own location source for travel-time ("how long to X") answers. Started
    // early so a fix is usually ready by the time the user asks; when it isn't
    // (or permission is off) the ETA resolver asks for an origin instead.
    private let location = LocationService()
    private let weather = WeatherStore.shared
    private var loc: LocalizationManager { LocalizationManager.shared }

    // Conversation state: after NeedsClarification we remember what we
    // asked for so the user's next bare answer completes it instead of
    // starting a fresh unrelated intent.
    private var pendingIntent: AssistantIntent?
    private var pendingMissing: MissingSlot?

    init() {
        messages = [greeting()]
        location.requestIfNeeded()
    }

    func ask(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(AriadneMessage(fromUser: true, text: text))
        thinking = true
        Task {
            // Clever mode: when Apple Foundation Models is available, the
            // on-device model classifies the message into a grounded intent
            // (station / line resolved to ids by Swift, never invented). When it
            // is unavailable or can't ground the result, fall back to the rule
            // parser, first letting the model at least normalize fuzzy input.
            let raw: AssistantIntent
            if let guided = await AriadneGuided.classify(text, vocabulary: parser.vocabulary) {
                raw = guided
            } else {
                let cleaned = await AriadneBrain.normalize(text) ?? text
                raw = parser.parse(cleaned)
            }
            let intent = mergePendingIfApplicable(raw)
            if case let .needsClarification(base, missing) = intent {
                pendingIntent = base
                pendingMissing = missing
            } else {
                pendingIntent = nil
                pendingMissing = nil
            }
            let reply = await resolve(intent)
            messages.append(reply)
            thinking = false
        }
    }

    /// Fill the pending clarification slot with a station id the user just
    /// named. Bare "Syntagma" turns into a Departures intent from the
    /// parser; we grab its stationId and merge it into whatever the
    /// pending PlanTrip / LastTrain / etc was waiting for.
    private func mergePendingIfApplicable(_ raw: AssistantIntent) -> AssistantIntent {
        guard let pending = pendingIntent, let missing = pendingMissing else { return raw }
        let stationId: String? = {
            switch raw {
            case let .showDepartures(id, _, _): return id
            case let .lastTrain(id, _): return id
            case let .needsClarification(base, _):
                if case let .showDepartures(id, _, _) = base { return id }
                return nil
            default: return nil
            }
        }()
        guard let sid = stationId else { return raw }

        switch pending {
        case let .planTrip(from, to, lowExposure):
            var patchedFrom = from, patchedTo = to
            switch missing {
            case .originStation: patchedFrom = sid
            case .destinationStation: patchedTo = sid
            default: return raw
            }
            if let _ = patchedFrom, let _ = patchedTo {
                return .planTrip(fromStationId: patchedFrom, toStationId: patchedTo, lowExposure: lowExposure)
            }
            let stillMissing: MissingSlot = patchedFrom == nil ? .originStation : .destinationStation
            let base: AssistantIntent = .planTrip(fromStationId: patchedFrom, toStationId: patchedTo, lowExposure: lowExposure)
            return .needsClarification(base: base, missing: stillMissing)
        case let .lastTrain(_, lineId):
            return .lastTrain(stationId: sid, lineId: lineId)
        case let .showDepartures(_, lineId, day):
            return .showDepartures(stationId: sid, lineId: lineId, day: day)
        case let .toggleFavorite(_):
            return .toggleFavorite(stationId: sid)
        case let .travelTime(to, from):
            if to == nil { return .travelTime(toStationId: sid, fromStationId: from) }
            return raw
        default:
            return raw
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
        case let .travelTime(to, from):
            return resolveTravelTime(to: to, from: from)
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
        case .easterEggLiepur:
            return bot(catJoke())
        case let .weatherAt(stationId):
            return await resolveWeather(stationId: stationId)
        case let .planTripByArrival(from, to, absMin, relMin):
            return resolvePlanByArrival(from: from, to: to, absMin: absMin, relMin: relMin)
        }
    }

    // MARK: - Time-anchored planning

    private func resolvePlanByArrival(from: String?, to: String?, absMin: Int?, relMin: Int?) -> AriadneMessage {
        guard let fromId = from else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }

        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(noRouteText(from: name(fromId), to: name(toId)))
        }
        let duration = plan.totalMinutes

        let cal = Calendar(identifier: .gregorian)
        let tz = TimeZone(identifier: "Europe/Athens") ?? .current
        var comps = cal.dateComponents(in: tz, from: Date())
        let nowMin = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)

        let targetMin: Int
        if let absMin { targetMin = absMin }
        else if let relMin { targetMin = nowMin + relMin }
        else { return bot(clarify(.destinationStation)) }

        let effective = (targetMin < nowMin && absMin != nil) ? targetMin + 24 * 60 : targetMin
        let leaveByMin = effective - duration
        let slack = leaveByMin - nowMin

        let fromName = name(fromId)
        let toName = name(toId)
        let leaveLabel = formatClock(leaveByMin % (24 * 60))
        let arriveLabel = formatClock(effective % (24 * 60))

        if slack < 0 {
            return bot(arrivalMissed(to: toName, arrive: arriveLabel, minutesOver: -slack))
        }
        if slack < 5 {
            return bot(arrivalTight(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack))
        }
        if slack > duration + 45 {
            return bot(arrivalEarly(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack))
        }
        return bot(arrivalOk(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack))
    }

    private func name(_ id: String) -> String {
        for line in SyrmosData.lines {
            for s in SyrmosData.stations(for: line.id) where s.id == id {
                return loc.language == .greek ? s.nameEl : s.name
            }
        }
        return id
    }

    private func formatClock(_ minutes: Int) -> String {
        let h = (minutes / 60) % 24
        let m = minutes % 60
        return String(format: "%02d:%02d", h, m)
    }

    private func noRouteText(from: String, to: String) -> String {
        switch loc.language {
        case .greek: return "Δεν βρήκα διαδρομή από \(from) προς \(to)."
        case .albanian: return "S'gjeta rrugë nga \(from) për te \(to)."
        case .english: return "I couldn't find a route from \(from) to \(to)."
        }
    }
    private func arrivalOk(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Ξεκίνα από \(from) έως \(leaveBy) και θα είσαι στο \(to) στις \(arrive). \(slack) λεπτά περιθώριο."
        case .albanian: return "Nis nga \(from) deri në \(leaveBy) dhe do të jesh në \(to) në \(arrive). \(slack) minuta hapësirë."
        case .english: return "Leave \(from) by \(leaveBy) and you'll be at \(to) by \(arrive). \(slack) min to spare."
        }
    }
    private func arrivalTight(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Στριμωγμένα. Πρέπει να είσαι εκτός από \(from) μέσα στα επόμενα \(slack) λεπτά για να προλάβεις στο \(to) στις \(arrive)."
        case .albanian: return "Ngushtë. Duhet të nisesh nga \(from) brenda \(slack) minutash për të arritur në \(to) në \(arrive)."
        case .english: return "Tight. You need to leave \(from) within the next \(slack) min to make \(to) by \(arrive)."
        }
    }
    private func arrivalMissed(to: String, arrive: String, minutesOver: Int) -> String {
        switch loc.language {
        case .greek: return "Δύσκολο. Για να είσαι στο \(to) στις \(arrive) θα έπρεπε να έχεις ξεκινήσει πριν \(minutesOver) λεπτά."
        case .albanian: return "E vështirë. Për të qenë në \(to) në \(arrive) duhej të kishe nisur \(minutesOver) minuta më parë."
        case .english: return "Cutting it close. To make \(to) by \(arrive) you'd have needed to leave \(minutesOver) min ago."
        }
    }
    private func arrivalEarly(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Έχεις άπλα. Ξεκίνα από \(from) όποτε θες μέσα στα επόμενα \(slack) λεπτά και θα φτάσεις στο \(to) στις \(arrive)."
        case .albanian: return "Ke kohë. Nisu nga \(from) kur të duash brenda \(slack) minutash dhe do të jesh në \(to) në \(arrive)."
        case .english: return "You have time. Leave \(from) anytime in the next \(slack) min and you'll reach \(to) by \(arrive)."
        }
    }

    // MARK: - Weather

    private func resolveWeather(stationId: String?) async -> AriadneMessage {
        let anchor = weatherAnchor(stationId: stationId)
        guard let anchor else {
            if let snap = weather.snapshot {
                return bot(formatWeather(snap: snap, placeName: snap.placeName))
            }
            return bot(weatherUnavailable())
        }
        // Trigger a fresh fetch; iOS WeatherStore refreshes centrally, so
        // we just ask for a refresh at that coord and read snapshot back.
        await weather.refresh(latitude: anchor.lat, longitude: anchor.lng, placeName: anchor.name)
        if let snap = weather.snapshot {
            return bot(formatWeather(snap: snap, placeName: anchor.name))
        }
        return bot(weatherUnavailable())
    }

    private struct WeatherAnchor { let lat: Double; let lng: Double; let name: String }

    private func weatherAnchor(stationId: String?) -> WeatherAnchor? {
        if let stationId {
            for line in SyrmosData.lines {
                for s in SyrmosData.stations(for: line.id) where s.id == stationId {
                    let name = loc.language == .greek ? s.nameEl : s.name
                    return WeatherAnchor(lat: s.coordinate.latitude, lng: s.coordinate.longitude, name: name)
                }
            }
        }
        // No explicit station: use the nearest station LocationService
        // already computes, so weather reflects where the user actually is.
        if let nearest = location.nearbyStations.first {
            let node = nearest.station
            let name = loc.language == .greek ? node.nameEl : node.displayName
            return WeatherAnchor(lat: node.coordinate.latitude, lng: node.coordinate.longitude, name: name)
        }
        return nil
    }

    private func formatWeather(snap: WeatherSnapshot, placeName: String) -> String {
        let tempC = Int(snap.current.temperatureC.rounded())
        let feels = Int(snap.current.apparentC.rounded())
        let cond = conditionLabel(snap.current.condition)
        let ageMin = max(0, Int(Date().timeIntervalSince(snap.fetchedAt)) / 60)
        let ageSuffix: String = ageMin >= 5 ? {
            switch loc.language {
            case .greek: return " (πριν \(ageMin) λεπτά)"
            case .albanian: return " (\(ageMin) min më parë)"
            case .english: return " (\(ageMin) min ago)"
            }
        }() : ""
        switch loc.language {
        case .greek:  return "\(placeName) τώρα: \(tempC)°C, \(cond). Αίσθηση \(feels)°C.\(ageSuffix)"
        case .albanian: return "\(placeName) tani: \(tempC)°C, \(cond). Ndihet si \(feels)°C.\(ageSuffix)"
        case .english: return "\(placeName) right now: \(tempC)°C, \(cond). Feels like \(feels)°C.\(ageSuffix)"
        }
    }

    private func conditionLabel(_ c: WeatherCondition) -> String {
        switch loc.language {
        case .greek:
            switch c {
            case .clear: return "καθαρός"
            case .partlyCloudy: return "μερική συννεφιά"
            case .cloudy: return "συννεφιασμένος"
            case .fog: return "ομίχλη"
            case .drizzle: return "ψιχάλα"
            case .rain: return "βροχή"
            case .snow: return "χιόνι"
            case .showers: return "μπόρες"
            case .thunderstorm: return "καταιγίδα"
            case .unknown: return "άγνωστη"
            }
        case .albanian:
            switch c {
            case .clear: return "kthjellët"
            case .partlyCloudy: return "pjesërisht i vranët"
            case .cloudy: return "i vranët"
            case .fog: return "mjegull"
            case .drizzle: return "shi i lehtë"
            case .rain: return "shi"
            case .snow: return "borë"
            case .showers: return "reshje"
            case .thunderstorm: return "stuhi"
            case .unknown: return "e panjohur"
            }
        case .english:
            switch c {
            case .clear: return "clear"
            case .partlyCloudy: return "partly cloudy"
            case .cloudy: return "cloudy"
            case .fog: return "foggy"
            case .drizzle: return "drizzling"
            case .rain: return "raining"
            case .snow: return "snowing"
            case .showers: return "showery"
            case .thunderstorm: return "thunderstorm"
            case .unknown: return "unknown"
            }
        }
    }

    private func weatherUnavailable() -> String {
        switch loc.language {
        case .greek: return "Δεν έχω ακόμα δεδομένα καιρού. Δοκίμασε ξανά όταν είσαι online."
        case .albanian: return "Ende s'kam të dhëna moti. Provo përsëri kur je online."
        case .english: return "I don't have weather data yet. Try again when you're online."
        }
    }

    private func catJoke() -> String {
        let jokes: [String]
        switch loc.language {
        case .greek:
            jokes = [
                "Γιατί οι γάτες δεν παίζουν πόκερ στη ζούγκλα; Έχει πολλά τσιτάχ.",
                "Πώς λέγεται μια στοίβα γατάκια; Μιαοβούνο.",
                "Τι κάνει ένας γάτος στον υπολογιστή; Προσέχει το ποντίκι.",
                "Γιατί ο γάτος πήγε στο νοσοκομείο; Είχε πυρετό αγέλας.",
                "Πώς τελειώνει η μάχη δύο γάτων; Με ένα σφύριγμα και ένα μιάου.",
            ]
        case .albanian:
            jokes = [
                "Pse macet nuk luajnë poker në xhungël? Sepse ka shumë çita.",
                "Si e quajnë një grumbull macesh të vogla? Një mjaumal.",
                "Pse ishte macja ulur mbi kompjuter? Për të vëzhguar miun.",
                "Cila është ëmbëlsira e preferuar e maces? Muslet me çokollatë.",
                "Si e mbyllin macet një grindje? Me një fshirje dhe një mjau.",
            ]
        default:
            jokes = [
                "Why don't cats play poker in the jungle? Too many cheetahs.",
                "What do you call a pile of kittens? A meowntain.",
                "Why was the cat sitting on the computer? To keep an eye on the mouse.",
                "What's a cat's favourite dessert? Chocolate mousse.",
                "How do two cats end a fight? They hiss and make up.",
            ]
        }
        return jokes.randomElement() ?? jokes[0]
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

    /// "How long to X". Origin is the user's nearest station from GPS, or an
    /// explicitly named origin. With no explicit origin and no location fix (or
    /// permission off), it asks for the origin instead of guessing.
    private func resolveTravelTime(to: String?, from: String?) -> AriadneMessage {
        guard let toId = to else { return bot(clarify(.destinationStation)) }

        let originId: String?
        if let from {
            originId = from
        } else if location.hasPermission, let nearest = location.nearbyStations.first {
            originId = nearest.id
        } else {
            originId = nil
        }
        guard let fromId = originId else {
            // No explicit origin and no usable location: ask which station.
            return bot(clarify(.originStation))
        }
        if fromId == toId {
            return bot(t("You're already at \(stationName(toId)).",
                "Είσαι ήδη στον \(stationName(toId)).",
                "Je tashmë te \(stationName(toId))."))
        }
        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))
        }
        let transfers = plan.transfers == 0
            ? t("no change", "χωρίς αλλαγή", "pa ndërrim")
            : t("\(plan.transfers) change(s)", "\(plan.transfers) αλλαγή/ές", "\(plan.transfers) ndërrim(e)")
        return bot(t("About \(plan.totalMinutes) min from \(stationName(fromId)) to \(stationName(toId)), \(transfers).",
            "Περίπου \(plan.totalMinutes) λεπτά από \(stationName(fromId)) προς \(stationName(toId)), \(transfers).",
            "Rreth \(plan.totalMinutes) min nga \(stationName(fromId)) te \(stationName(toId)), \(transfers)."))
    }

    private func stationName(_ id: String) -> String {
        guard let s = allStations().first(where: { $0.id == id }) else { return id }
        return name(s)
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
        bot(t("Hi, I'm Ariadne. Ask about departures, weather, or trips like \"airport by 21:30\".",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για αναχωρήσεις, καιρό ή διαδρομές όπως «αεροδρόμιο στις 21:30».",
            "Përshëndetje, jam Ariadne. Më pyet për nisje, motin ose udhëtime si «aeroporti në 21:30»."))
    }

    private func helpText() -> String {
        t("I handle departures, last train home, trip planning (including \"be there by X:XX\"), weather at a station, service alerts, ticket prices, and Athens rail info. Offline-safe.",
            "Χειρίζομαι αναχωρήσεις, τελευταίο τρένο, σχεδιασμό διαδρομής (και «να είσαι εκεί στις X:XX»), καιρό σταθμού, ειδοποιήσεις, τιμές εισιτηρίων και πληροφορίες των συγκοινωνιών Αθήνας. Λειτουργώ offline.",
            "Trajtoj nisjet, trenin e fundit, planifikim udhëtimi (edhe «të jesh atje deri në X:XX»), motin te një stacion, njoftime, çmime biletash dhe informacione për transportin e Athinës. Punoj pa internet.")
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
