import Foundation

// Ariadne, the offline Athens transit assistant on iOS. A faithful Swift mirror
// of the KMP `AthensTransitParser` / `AssistantIntent` so iOS and Android/Web
// behave identically: an intent router over the deterministic projector, never
// a generative chatbot, fully offline, EN / EL / SQ.

enum DayContext: Equatable { case today, tomorrow, weekend, saturday, sunday }

enum MissingSlot: Equatable { case originStation, destinationStation, station }

indirect enum AssistantIntent: Equatable {
    case showDepartures(stationId: String?, lineId: String?, day: DayContext)
    case lastTrain(stationId: String?, lineId: String?)
    case findStation(query: String)
    case planTrip(fromStationId: String?, toStationId: String?, lowExposure: Bool)
    case explainLine(lineId: String)
    case explainFare(airport: Bool, fromStationId: String?, toStationId: String?)
    case toggleFavorite(stationId: String?)
    case showAlerts(lineId: String?)
    case openMap(stationId: String?)
    case help
    case needsClarification(base: AssistantIntent, missing: MissingSlot)
    case outOfScope
}

struct StationVocab { let id: String; let names: [String]; let lineIds: [String] }
struct LineVocab { let id: String; let aliases: [String] }

struct AssistantVocabulary {
    let stations: [StationVocab]
    let lines: [LineVocab]

    /// Built from the bundled SyrmosData so the parser stays in sync with the
    /// shipped network.
    static func fromSyrmosData() -> AssistantVocabulary {
        var seen = Set<String>()
        var stationVocab: [StationVocab] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id)
                stationVocab.append(
                    StationVocab(
                        id: st.id,
                        names: [st.name, st.nameEl].filter { !$0.isEmpty },
                        lineIds: st.lineIds
                    )
                )
            }
        }
        let lineVocab: [LineVocab] = SyrmosData.lines.map { line in
            var aliases = [line.id, line.name, line.nameEl]
            let suffix = String(line.id.drop { !$0.isNumber })
            if !suffix.isEmpty {
                aliases.append("line \(suffix)")
                aliases.append("γραμμη \(suffix)")
                aliases.append("linja \(suffix)")
                if line.id.first == "M" { aliases.append("metro \(suffix)") }
                if line.id.first == "T" { aliases.append("tram \(suffix)") }
            }
            return LineVocab(id: line.id, aliases: aliases.filter { !$0.isEmpty })
        }
        return AssistantVocabulary(stations: stationVocab, lines: lineVocab)
    }
}

struct AthensTransitParser {
    let vocabulary: AssistantVocabulary

    func parse(_ rawInput: String) -> AssistantIntent {
        let text = Self.fold(rawInput)
        if text.trimmingCharacters(in: .whitespaces).isEmpty { return .outOfScope }

        let stations = matchStations(text)
        let line = matchLine(text)
        let day = matchDay(text)

        if containsAny(text, Self.help) { return .help }

        let strongTransit = !stations.isEmpty || line != nil || containsAny(text, Self.transitNouns)
        let intentSignal = containsAny(text, Self.alertWords) ||
            containsAny(text, Self.mapWords) ||
            containsAny(text, Self.lastTrainPhrases) ||
            containsAny(text, Self.planPhrases) ||
            containsAny(text, Self.findWords) ||
            containsAny(text, Self.fareWords) ||
            containsAny(text, Self.favoriteWords)

        let weather = containsAny(text, Self.weatherWords)
        if weather && !strongTransit { return .outOfScope }
        if !strongTransit && !intentSignal && !weather { return .outOfScope }

        // 0a. Fares (before planning, so "how much to the airport" is a fare).
        if containsAny(text, Self.fareWords) {
            let (from, to) = resolveTripEndpoints(text, stations)
            return .explainFare(airport: containsAny(text, Self.airportWords), fromStationId: from, toStationId: to)
        }

        // 0b. Favorites.
        if containsAny(text, Self.favoriteWords) {
            let base = AssistantIntent.toggleFavorite(stationId: stations.first)
            return stations.first == nil ? .needsClarification(base: base, missing: .station) : base
        }

        // 1. Plan a trip.
        let hasToMarker = Self.toMarkers.contains { text.contains($0) }
        let planning = containsAny(text, Self.planPhrases) || weather ||
            (hasToMarker && !stations.isEmpty) || stations.count >= 2
        if planning {
            let (from, to) = resolveTripEndpoints(text, stations)
            let base = AssistantIntent.planTrip(fromStationId: from, toStationId: to, lowExposure: weather)
            if to == nil { return .needsClarification(base: base, missing: .destinationStation) }
            if from == nil { return .needsClarification(base: base, missing: .originStation) }
            return base
        }

        // 2. Last train.
        if containsAny(text, Self.lastTrainPhrases) {
            let station = stations.first
            let base = AssistantIntent.lastTrain(stationId: station, lineId: line)
            return station == nil ? .needsClarification(base: base, missing: .station) : base
        }

        // 3. Alerts.
        if containsAny(text, Self.alertWords) { return .showAlerts(lineId: line) }

        // 4. Map.
        if containsAny(text, Self.mapWords) { return .openMap(stationId: stations.first) }

        // 5. Explain line.
        if let line, stations.isEmpty, !containsAny(text, Self.departureWords),
           containsAny(text, Self.lineWords) || isBareLineQuery(text) {
            return .explainLine(lineId: line)
        }

        // 6. Departures (default).
        if !stations.isEmpty || containsAny(text, Self.departureWords) || line != nil {
            let station = stations.first
            let base = AssistantIntent.showDepartures(stationId: station, lineId: line, day: day)
            if station == nil && line == nil {
                return .needsClarification(base: base, missing: .station)
            }
            return base
        }

        // 7. Find station.
        if containsAny(text, Self.findWords) && stations.isEmpty {
            return .findStation(query: rawInput.trimmingCharacters(in: .whitespaces))
        }

        return .outOfScope
    }

    // MARK: - Matching

    private func matchStations(_ text: String) -> [String] {
        let ordered = vocabulary.stations
            .flatMap { st in st.names.map { (st.id, Self.fold($0)) } }
            .filter { $0.1.count >= 3 }
            .sorted { $0.1.count > $1.1.count }
        var found: [String] = []
        var scratch = text
        for (id, name) in ordered {
            if found.contains(id) { continue }
            if scratch.contains(name) {
                found.append(id)
                scratch = scratch.replacingOccurrences(of: name, with: String(repeating: " ", count: name.count))
            }
        }
        return found
    }

    private func matchLine(_ text: String) -> String? {
        let ordered = vocabulary.lines
            .flatMap { line in line.aliases.map { (Self.fold($0), line.id) } }
            .filter { $0.0.count >= 2 }
            .sorted { $0.0.count > $1.0.count }
        for (alias, id) in ordered where containsToken(text, alias) { return id }
        return nil
    }

    private func matchDay(_ text: String) -> DayContext {
        if containsAny(text, Self.tomorrowWords) { return .tomorrow }
        if containsAny(text, Self.weekendWords) { return .weekend }
        if containsAny(text, Self.saturdayWords) { return .saturday }
        if containsAny(text, Self.sundayWords) { return .sunday }
        return .today
    }

    private func resolveTripEndpoints(_ text: String, _ stations: [String]) -> (String?, String?) {
        if stations.isEmpty { return (nil, nil) }
        if stations.count == 1 {
            let toMarker = Self.toMarkers.contains { text.contains($0) }
            return toMarker ? (nil, stations[0]) : (stations[0], nil)
        }
        let byPos = stations.sorted { positionOf(text, $0) < positionOf(text, $1) }
        return (byPos[0], byPos[1])
    }

    private func positionOf(_ text: String, _ stationId: String) -> Int {
        guard let names = vocabulary.stations.first(where: { $0.id == stationId })?.names else { return .max }
        let positions = names.compactMap { name -> Int? in
            guard let r = text.range(of: Self.fold(name)) else { return nil }
            return text.distance(from: text.startIndex, to: r.lowerBound)
        }
        return positions.min() ?? .max
    }

    private func isBareLineQuery(_ text: String) -> Bool {
        text.split(whereSeparator: { $0.isWhitespace }).count <= 3
    }

    // MARK: - Tokens

    private func containsAny(_ text: String, _ needles: [String]) -> Bool {
        needles.contains { containsToken(text, Self.fold($0)) }
    }

    private func containsToken(_ text: String, _ needle: String) -> Bool {
        if needle.isEmpty { return false }
        if needle.contains(" ") { return text.contains(needle) }
        var searchStart = text.startIndex
        while let r = text.range(of: needle, range: searchStart..<text.endIndex) {
            let before: Character = r.lowerBound == text.startIndex ? " " : text[text.index(before: r.lowerBound)]
            let after: Character = r.upperBound == text.endIndex ? " " : text[r.upperBound]
            if !before.isLetterOrDigit() && !after.isLetterOrDigit() { return true }
            searchStart = text.index(after: r.lowerBound)
        }
        return false
    }

    // MARK: - Folding

    static func fold(_ input: String) -> String {
        String(input.lowercased().map { foldChar($0) })
    }

    private static func foldChar(_ ch: Character) -> Character {
        switch ch {
        case "ά": return "α"; case "έ": return "ε"; case "ή": return "η"
        case "ί", "ϊ", "ΐ": return "ι"; case "ό": return "ο"
        case "ύ", "ϋ", "ΰ": return "υ"; case "ώ": return "ω"; case "ς": return "σ"
        case "à", "á", "â", "ä", "ã": return "a"
        case "è", "é", "ê", "ë": return "e"
        case "ì", "í", "î", "ï": return "i"
        case "ò", "ó", "ô", "ö", "õ": return "o"
        case "ù", "ú", "û", "ü": return "u"
        case "ç": return "c"
        default: return ch
        }
    }

    // MARK: - Vocabulary (mirrors the KMP lists)

    private static let transitNouns = ["train", "trains", "metro", "tram", "station", "departure", "departures",
        "τρεν", "μετρο", "τραμ", "σταθμ", "δρομολογ", "αναχωρη", "συρμ", "προαστιακ", "tren", "stacion", "nisje"]
    private static let departureWords = ["next", "departure", "departures", "when", "trains", "leave", "leaving", "schedule",
        "επομεν", "αναχωρη", "ποτε", "δρομολογ", "φευγει", "τρεν", "ardhsh", "kur", "nisje", "tren", "trena"]
    private static let lastTrainPhrases = ["last train", "last metro", "last one", "leave by",
        "τελευται", "τελευταιο τρεν", "τελευταιος", "treni i fundit", "fundit", "i fundit", "tren i fundit"]
    private static let planPhrases = ["how do i get", "how to get", "get to", "get me to", "route",
        "πως πα", "πως πη", "πως φτα", "διαδρομη", "για να πα", "si shkoj", "si te shkoj", "rruga", "udhetim"]
    private static let toMarkers = [" to ", " for ", "->", "→", " προς ", " για ", " te ", " per ", " ne "]
    private static let findWords = ["where is", "find", "locate", "nearest", "near me", "closest",
        "που ειναι", "βρες", "κοντιν", "κοντα μου", "πλησιεστερ", "ku eshte", "gjej", "me afert", "afer meje"]
    private static let lineWords = ["line", "about", "tell me about", "γραμμη", "σχετικα", "linja", "rreth"]
    private static let fareWords = ["fare", "fares", "ticket", "tickets", "how much", "price", "cost", "cheap",
        "εισιτηρι", "ποσο κανει", "ποσο κοστιζει", "τιμη", "κοστος", "bilete", "sa kushton", "kushton", "cmim", "cmimi"]
    private static let favoriteWords = ["favorite", "favourite", "save this", "bookmark", "add to favorites", "pin",
        "αγαπημεν", "αποθηκευσ", "προσθεσε στα αγαπημενα", "σημειωσε",
        "i preferuar", "te preferuarat", "ruaj", "shto te te preferuarat"]
    private static let airportWords = ["airport", "αεροδρομιο", "aeroport"]
    private static let alertWords = ["alert", "alerts", "status", "disruption", "delay", "delays", "problem", "closed", "closure",
        "ειδοποι", "κατασταση", "καθυστερη", "προβλημα", "κλειστ", "διακοπη", "njoftim", "vonese", "mbyll"]
    private static let mapWords = ["map", "show on map", "on the map", "χαρτη", "στον χαρτη", "harta", "ne harte"]
    private static let help = ["what can you do", "help", "how do you work", "what do you do", "who are you",
        "τι μπορεις", "βοηθεια", "πως δουλευ", "ποιος εισαι", "si funksionon", "ndihme", "cfare mund", "kush je"]
    private static let weatherWords = ["rain", "raining", "rainy", "weather", "storm", "wet",
        "βροχη", "βρεχει", "καιρο", "κακοκαιρ", "shi", "moti", "stuhi"]
    private static let tomorrowWords = ["tomorrow", "αυριο", "neser"]
    private static let weekendWords = ["weekend", "σαββατοκυριακο", "fundjave"]
    private static let saturdayWords = ["saturday", "σαββατο", "te shtune", "shtune"]
    private static let sundayWords = ["sunday", "κυριακη", "te diel", "diel"]
}

private extension Character {
    func isLetterOrDigit() -> Bool { isLetter || isNumber }
}
