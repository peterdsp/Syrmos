import Foundation

// Ariadne, the offline Athens transit assistant on iOS. A faithful Swift mirror
// of the KMP `AthensTransitParser` / `AssistantIntent` so iOS and Android/Web
// behave identically: an intent router over the deterministic projector, never
// a generative chatbot, fully offline, EN / EL / SQ.

enum DayContext: Equatable, Sendable { case today, tomorrow, weekend, saturday, sunday }

enum MissingSlot: Equatable, Sendable { case originStation, destinationStation, station }

indirect enum AssistantIntent: Equatable, Sendable {
    case showDepartures(stationId: String?, lineId: String?, day: DayContext)
    case lastTrain(stationId: String?, lineId: String?)
    /// First / earliest train of the day at a station (mirror of lastTrain).
    case firstTrain(stationId: String?, lineId: String?)
    /// "Is X step-free / wheelchair accessible?". From the bundled flag, never invented.
    case stationAccessibility(stationId: String?)
    /// "and back?" / "return" — reverse the remembered route and re-plan.
    case reverseTrip
    /// "Which lines serve X?" — list the lines calling at a station.
    case whichLines(stationId: String?)
    /// "How many stops / how far from A to B?" — stop count + rough duration.
    case stopsBetween(fromStationId: String?, toStationId: String?)
    case findStation(query: String)
    case planTrip(fromStationId: String?, toStationId: String?, lowExposure: Bool, preference: RoutePreference)
    case travelTime(toStationId: String?, fromStationId: String?)
    case explainLine(lineId: String)
    case explainFare(airport: Bool, fromStationId: String?, toStationId: String?)
    case toggleFavorite(stationId: String?)
    case showAlerts(lineId: String?)
    /// "Is X open / working / closed?". Operational status for one station.
    /// Ariadne has no live per-station status feed, so the honest answer leads
    /// with any matching STASY advisory; absent one it falls back to the
    /// timetable and says so, never asserting "open".
    case stationStatus(stationId: String?)
    /// "I'm at X" / "I'm here" / "I got off at X". Pure context-set: records the
    /// user's current station in the session so later follow-ups ("go airport
    /// faster") need no "from where?". stationId is nil for a bare "I'm here",
    /// which the resolver anchors to GPS / last known station.
    case setCurrentLocation(stationId: String?)
    case wrongTrain(stationId: String?, lineId: String?)
    case missedStop(stationId: String?, targetStationId: String?)
    case canIStillMakeIt(toStationId: String?, fromStationId: String?)
    case openMap(stationId: String?)
    case help
    case needsClarification(base: AssistantIntent, missing: MissingSlot)
    case outOfScope
    /// Direct weather question, optionally anchored to a station.
    case weatherAt(stationId: String?)
    /// "I need to be at X by 21:30" — plan backwards from a target
    /// arrival time. Exactly one of arriveByAthensMinutes /
    /// inMinutesFromNow is set.
    case planTripByArrival(
        fromStationId: String?,
        toStationId: String?,
        arriveByAthensMinutes: Int?,
        inMinutesFromNow: Int?
    )
    /// Easter egg: fires on "liepur" / "λιεπ" / close variants. Ariadne
    /// answers with a random cat joke. See catJoke() in AriadneModel.
    case easterEggLiepur
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
                // Albanian is first-class (large Athens community): the bundled
                // station only has EN + EL names, so augment key stations with
                // Albanian / Latin-Greeklish spellings ("aeroport", "Pireas",
                // "Sintagma") so SQ input resolves as reliably as EN/EL. Mirrors
                // the KMP AssistantVocabularyBuilder.
                let folded = AthensTransitParser.fold("\(st.name) \(st.nameEl)")
                let extra = Self.sqAndLatinAliases
                    .filter { folded.contains($0.key) }
                    .flatMap { $0.value }
                stationVocab.append(
                    StationVocab(
                        id: st.id,
                        names: ([st.name, st.nameEl] + extra).filter { !$0.isEmpty },
                        lineIds: st.lineIds
                    )
                )
            }
        }
        // Operational only: Ariadne answers departures, last trains and routes,
        // all actionable. Station vocabulary above stays complete, so she can
        // still recognise a station on unopened track and say something honest
        // about it rather than pretend not to know the name.
        let lineVocab: [LineVocab] = SyrmosData.operationalLines.map { line in
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

    /// Albanian / Latin-Greeklish station aliases, keyed by an accent-folded
    /// token in the EN or EL name. Confident, real variants only. Mirrors the
    /// KMP `AssistantVocabularyBuilder.SQ_AND_LATIN_ALIASES`. Long-term the
    /// bundled Station should carry a `nameSq`; this closes the top gaps now.
    private static let sqAndLatinAliases: [String: [String]] = [
        "airport": ["Aeroport", "Aeroporti"],
        "aerodromio": ["Aeroport", "Aeroporti"],
        "piraeus": ["Pireas", "Pireu"],
        "syntagma": ["Sintagma"],
        "thessaloniki": ["Selanik", "Selaniku", "Thesaloniki"],
        "athens": ["Athina", "Athine"],
        "acropolis": ["Akropoli", "Akropolis"],
        "omonia": ["Omonoia"],
        "monastiraki": ["Monastiraqi"],
        "nikaia": ["Nikea", "Nikaja"],
        "victoria": ["Viktoria"],
        "attiki": ["Atiki"],
        "kifisia": ["Kifissia"],
        "elliniko": ["Helliniko"],
        "peristeri": ["Peristeri"],
        "aigaleo": ["Egaleo", "Aigaleo"],
        "larisa": ["Larisis"],
        "patra": ["Patra", "Patras"],
        "aghios": ["Agios"],
        "agios": ["Aghios"],
    ]
}

struct AthensTransitParser {
    let vocabulary: AssistantVocabulary

    func parse(_ rawInput: String) -> AssistantIntent {
        let text = Self.fold(rawInput)
        if text.trimmingCharacters(in: .whitespaces).isEmpty { return .outOfScope }

        // Easter egg check runs first so a legitimate transit intent that
        // happens to share substrings never masks the trigger.
        if Self.liepurTriggers.contains(where: { text.contains($0) }) {
            return .easterEggLiepur
        }

        let stations = matchStations(text)
        let line = matchLine(text)
        let day = matchDay(text)

        if containsAny(text, Self.help) { return .help }

        // Reverse-trip follow-up: a bare "and back?" / "return" / "the other
        // way" / "kthimi" with no newly-named station. The resolver flips the
        // remembered route; an explicit "X to Y and back" keeps the stations.
        if stations.isEmpty && containsAny(text, Self.reversePhrases) {
            return .reverseTrip
        }

        let strongTransit = !stations.isEmpty || line != nil || containsAny(text, Self.transitNouns)
        let intentSignal = containsAny(text, Self.alertWords) ||
            containsAny(text, Self.mapWords) ||
            containsAny(text, Self.lastTrainPhrases) ||
            containsAny(text, Self.planPhrases) ||
            containsAny(text, Self.findWords) ||
            containsAny(text, Self.fareWords) ||
            containsAny(text, Self.favoriteWords) ||
            containsAny(text, Self.timePhrases) ||
            containsAny(text, Self.locationPhrases)

        let weather = containsAny(text, Self.weatherWords)
        if weather {
            let planning = containsAny(text, Self.planPhrases) ||
                Self.toMarkers.contains(where: { text.contains($0) }) ||
                stations.count >= 2
            if !planning {
                // "weather in London": a place we don't serve resolved to no
                // Athens station, yet a location was clearly named, so decline
                // instead of answering with Athens weather. Mirrors KMP.
                if stations.isEmpty && namesUnservedPlace(text) {
                    return .outOfScope
                }
                return .weatherAt(stationId: stations.first)
            }
        }
        if !strongTransit && !intentSignal && !weather { return .outOfScope }

        // Pure context-set "I'm at X" is checked before planning, because the
        // Albanian "jam te X" contains " te ", which would otherwise read as a
        // "to" marker and turn the statement into a trip. Only fires with at
        // most one station, no plan cue, and no routing preference, so
        // "I'm at X, go to Y faster" still plans normally below.
        if containsAny(text, Self.locationPhrases) &&
            stations.count <= 1 &&
            !containsAny(text, Self.planPhrases) &&
            RoutePreference.fromFolded(text) == .balanced &&
            !containsAny(text, Self.timePhrases) &&
            !containsAny(text, Self.fareWords) &&
            !containsAny(text, Self.lastTrainPhrases) {
            return .setCurrentLocation(stationId: stations.first)
        }

        // Recovery: wrong train
        if containsAny(text, Self.wrongTrainPhrases) {
            return .wrongTrain(stationId: stations.first, lineId: line)
        }

        // Recovery: missed stop
        if containsAny(text, Self.missedStopPhrases) {
            let missed = stations.count >= 2 ? stations[1] : stations.first
            let current = stations.count >= 2 ? stations[0] : nil
            return .missedStop(stationId: current, targetStationId: missed)
        }

        // Recovery: can I still make it
        if containsAny(text, Self.canIStillMakeItPhrases) {
            let (from, to) = resolveTripEndpoints(text, stations)
            return .canIStillMakeIt(toStationId: to ?? from, fromStationId: stations.count >= 2 ? from : nil)
        }

        // 0. Travel time / ETA ("how long to X"). Before fares ("how much time"
        //    shares the fare cue) and planning ("how long to get to X" shares
        //    the plan cue). Origin defaults to the user's location, resolved by
        //    the caller; only an explicit origin fills fromStationId.
        if containsAny(text, Self.timePhrases) {
            let (from, to) = resolveTripEndpoints(text, stations)
            let destination = to ?? from
            let origin = stations.count >= 2 ? from : nil
            if let destination {
                return .travelTime(toStationId: destination, fromStationId: origin)
            }
            return .needsClarification(base: .travelTime(toStationId: nil, fromStationId: nil), missing: .destinationStation)
        }

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

        // 0c. Station accessibility: "is X step-free?", "does X have a lift?".
        //     Before planning because the Greek "για ΑμεΑ" contains the " για "
        //     to-marker, which would otherwise turn the question into a trip.
        if containsAny(text, Self.accessibilityWords) &&
            (!stations.isEmpty || containsAny(text, Self.stationNounWords)) {
            let station = stations.first
            let base = AssistantIntent.stationAccessibility(stationId: station)
            return station == nil ? .needsClarification(base: base, missing: .station) : base
        }

        // 0d. Stop count / "how far" between two stations. Before planning.
        if containsAny(text, Self.stopsBetweenWords) {
            let (from, to) = resolveTripEndpoints(text, stations)
            let base = AssistantIntent.stopsBetween(fromStationId: from, toStationId: to)
            if to == nil { return .needsClarification(base: base, missing: .destinationStation) }
            if from == nil { return .needsClarification(base: base, missing: .originStation) }
            return base
        }

        // 1. Plan a trip. Triggered by an explicit "how do I get" phrase, an
        //    explicit "to" frame with a station, weather routing, a non-balanced
        //    preference with a station, or two distinct stations named.
        let hasToMarker = Self.toMarkers.contains { text.contains($0) }
        let preference = RoutePreference.fromFolded(text)
        let planning = containsAny(text, Self.planPhrases) || weather ||
            (preference != .balanced && !stations.isEmpty) ||
            (hasToMarker && !stations.isEmpty) || stations.count >= 2
        if planning {
            // Before the standard PlanTrip, see if the user pinned an
            // arrival time. "airport by 21:30", "in 45 min to Piraeus".
            if let target = extractTargetTime(text) {
                let (from, to) = resolveTripEndpoints(text, stations)
                let base: AssistantIntent = .planTripByArrival(
                    fromStationId: from,
                    toStationId: to,
                    arriveByAthensMinutes: target.absoluteMinutes,
                    inMinutesFromNow: target.relativeMinutes
                )
                if to == nil { return .needsClarification(base: base, missing: .destinationStation) }
                if from == nil { return .needsClarification(base: base, missing: .originStation) }
                return base
            }
            // A single named station reached through a plan cue or a routing
            // preference ("how do I go airport faster") is the destination; the
            // origin comes from session context or a follow-up question. Without
            // such a cue, a lone station keeps the position-based reading.
            let singleDestination = stations.count == 1 && !hasToMarker &&
                (containsAny(text, Self.planPhrases) || preference != .balanced)
            let from: String?, to: String?
            if singleDestination {
                (from, to) = (nil, stations[0])
            } else {
                (from, to) = resolveTripEndpoints(text, stations)
            }
            let base = AssistantIntent.planTrip(fromStationId: from, toStationId: to, lowExposure: weather, preference: preference)
            if to == nil { return .needsClarification(base: base, missing: .destinationStation) }
            if from == nil { return .needsClarification(base: base, missing: .originStation) }
            return base
        }

        // 1c. First / earliest train of the day (mirror of last train). A bare
        //     position word ("first" / "πρώτο" / "parë") counts only alongside a
        //     named station or line, so "first M2 train" resolves while "first"
        //     alone does not.
        let firstCue = containsAny(text, Self.firstTrainPhrases) ||
            (Self.firstTokens.contains(where: { text.contains($0) }) && (!stations.isEmpty || line != nil))
        if firstCue {
            let station = stations.first
            let base = AssistantIntent.firstTrain(stationId: station, lineId: line)
            if station == nil && line == nil {
                return .needsClarification(base: base, missing: .station)
            }
            return base
        }

        // 2. Last train.
        if containsAny(text, Self.lastTrainPhrases) {
            let station = stations.first
            let base = AssistantIntent.lastTrain(stationId: station, lineId: line)
            return station == nil ? .needsClarification(base: base, missing: .station) : base
        }

        // 2b. Station operational status: "is X open / working / closed?".
        //     Needs either a named station or the word "station", so a general
        //     "any closures today?" still falls through to the alerts branch.
        //     Placed before Alerts so "is Syntagma closed" is a station-status
        //     question, not a network-wide alerts query.
        if containsAny(text, Self.stationStatusWords) &&
            (!stations.isEmpty || containsAny(text, Self.stationNounWords)) {
            let station = stations.first
            let base = AssistantIntent.stationStatus(stationId: station)
            return station == nil ? .needsClarification(base: base, missing: .station) : base
        }

        // 3. Alerts.
        if containsAny(text, Self.alertWords) { return .showAlerts(lineId: line) }

        // 4. Map.
        if containsAny(text, Self.mapWords) { return .openMap(stationId: stations.first) }

        // 4b. Which lines serve a station.
        if containsAny(text, Self.whichLinesWords) &&
            (!stations.isEmpty || containsAny(text, Self.stationNounWords)) {
            let station = stations.first
            let base = AssistantIntent.whichLines(stationId: station)
            return station == nil ? .needsClarification(base: base, missing: .station) : base
        }

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
        // Typo fallback: only when nothing matched exactly, so a clean query is
        // never overridden. Resolves "nikea" / "nkiea" / "sintagma".
        if found.isEmpty, let fuzzy = fuzzyMatchStation(text) {
            found.append(fuzzy)
        }
        return found
    }

    /// Best-effort typo correction of a query to a single station. Compares each
    /// 4+ char input token (that isn't a known vocabulary word) against every
    /// station-name word and returns the closest station id, or nil. Kept tight
    /// to avoid mapping gibberish onto a stop: up to 2 edits always, a 3rd only
    /// when the first letter matches on a 6+ char word (the "nkiea" case).
    /// A best-effort station-name suggestion for the recovery path ("did you
    /// mean X?"), or nil. Mirrors the KMP `suggestStation`; the parser itself
    /// still returns `.outOfScope`, this is only consulted on a dead-end.
    func suggestStation(_ text: String) -> String? {
        guard let id = fuzzyMatchStation(text) else { return nil }
        return vocabulary.stations.first(where: { $0.id == id })?.names.first
    }

    private func fuzzyMatchStation(_ text: String) -> String? {
        let tokens = text
            .split { !$0.isLetter && !$0.isNumber }
            .map(String.init)
            .filter { $0.count >= 4 && !Self.stopwords.contains($0) }
        if tokens.isEmpty { return nil }

        var bestId: String?
        var bestDist = Int.max
        for token in tokens {
            let tokenChars = Array(token)
            for st in vocabulary.stations {
                for rawName in st.names {
                    for word in Self.fold(rawName).split(separator: " ").map(String.init)
                    where word.count >= 4 && !Self.stopwords.contains(word) {
                        if abs(word.count - token.count) > 3 { continue }
                        let dist = Self.editDistance(tokenChars, Array(word))
                        let maxLen = max(token.count, word.count)
                        let accept = dist <= 2 ||
                            (dist == 3 && token.first == word.first && maxLen >= 6)
                        if accept && dist < bestDist {
                            bestDist = dist
                            bestId = st.id
                        }
                    }
                }
            }
        }
        return bestId
    }

    /// Optimal string alignment (Damerau-Levenshtein with adjacent
    /// transpositions), so a swap like "nkiea" counts as a single edit.
    static func editDistance(_ a: [Character], _ b: [Character]) -> Int {
        let al = a.count, bl = b.count
        if al == 0 { return bl }
        if bl == 0 { return al }
        var d = Array(repeating: Array(repeating: 0, count: bl + 1), count: al + 1)
        for i in 0...al { d[i][0] = i }
        for j in 0...bl { d[0][j] = j }
        for i in 1...al {
            for j in 1...bl {
                let cost = a[i - 1] == b[j - 1] ? 0 : 1
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if i > 1, j > 1, a[i - 1] == b[j - 2], a[i - 2] == b[j - 1] {
                    d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[al][bl]
    }

    private func matchLine(_ text: String) -> String? {
        let ordered = vocabulary.lines
            .flatMap { line in line.aliases.map { (Self.fold($0), line.id) } }
            .filter { $0.0.count >= 2 }
            .sorted { $0.0.count > $1.0.count }
        for (alias, id) in ordered where containsToken(text, alias) { return id }
        return nil
    }

    /// Public day-context probe for follow-ups ("what about tomorrow?").
    /// Mirrors the KMP `dayOf`.
    func dayOf(_ rawInput: String) -> DayContext { matchDay(Self.fold(rawInput)) }

    private func matchDay(_ text: String) -> DayContext {
        if containsAny(text, Self.tomorrowWords) { return .tomorrow }
        if containsAny(text, Self.weekendWords) { return .weekend }
        if containsAny(text, Self.saturdayWords) { return .saturday }
        if containsAny(text, Self.sundayWords) { return .sunday }
        return .today
    }

    struct TargetTime { let absoluteMinutes: Int?; let relativeMinutes: Int? }

    private func extractTargetTime(_ text: String) -> TargetTime? {
        if let m = text.range(of: #"(\d{1,2})[:.](\d{2})"#, options: .regularExpression) {
            let s = String(text[m])
            let parts = s.split(whereSeparator: { $0 == ":" || $0 == "." })
            if parts.count == 2, let h = Int(parts[0]), let mm = Int(parts[1]),
               (0...23).contains(h), (0...59).contains(mm) {
                return TargetTime(absoluteMinutes: h * 60 + mm, relativeMinutes: nil)
            }
        }
        if let m = text.range(of: #"(\d{1,2})\s*(am|pm|μμ|πμ)"#, options: .regularExpression) {
            let s = String(text[m])
            var h = 0; var mark = ""
            for c in s {
                if c.isNumber { h = h * 10 + Int(String(c))! }
                else if c.isLetter || "μπ".contains(c) { mark.append(c) }
            }
            if (1...12).contains(h) {
                if mark == "pm" || mark == "μμ" { if h < 12 { h += 12 } }
                else if mark == "am" || mark == "πμ" { if h == 12 { h = 0 } }
                return TargetTime(absoluteMinutes: h * 60, relativeMinutes: nil)
            }
        }
        if let m = text.range(of: #"(\d+)\s*(min|minute|minutes|λεπτ|minut)"#, options: .regularExpression) {
            let s = String(text[m])
            var n = 0
            for c in s where c.isNumber { n = n * 10 + Int(String(c))! }
            if (1...(24 * 60)).contains(n) {
                return TargetTime(absoluteMinutes: nil, relativeMinutes: n)
            }
        }
        if let m = text.range(of: #"(\d+)\s*(hour|hours|hr|h |ωρα|ωρε|ore |orë)"#, options: .regularExpression) {
            let s = String(text[m])
            var n = 0
            for c in s where c.isNumber { n = n * 10 + Int(String(c))! }
            if (1...12).contains(n) {
                return TargetTime(absoluteMinutes: nil, relativeMinutes: n * 60)
            }
        }
        return nil
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

    /// True when the text introduces a place with a location preposition
    /// ("weather IN London"). Used by the weather branch to decline a question
    /// about a place that resolved to no Athens station. Mirrors KMP.
    private func namesUnservedPlace(_ text: String) -> Bool {
        let markers = [" in ", " at ", " στο ", " στη ", " στην ", " σε "]
        return markers.contains { text.contains(Self.fold($0)) }
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
        "arrivals", "arriving", "next one", "how soon", "timetable",
        "επομεν", "αναχωρη", "ποτε", "δρομολογ", "φευγει", "τρεν", "ερχεται", "ερχονται", "ωραριο", "επομενο",
        "ardhsh", "kur", "nisje", "tren", "trena", "vjen", "orari", "ardhja"]
    private static let lastTrainPhrases = ["last train", "last metro", "last one", "leave by", "final train", "last departure",
        "τελευται", "τελευταιο τρεν", "τελευταιος", "τελευταιο δρομολογιο",
        "treni i fundit", "fundit", "i fundit", "tren i fundit", "nisja e fundit"]
    // First / earliest service of the day. Mirror of lastTrainPhrases.
    private static let firstTrainPhrases = ["first train", "first metro", "first tram", "first one",
        "earliest train", "earliest metro", "first departure", "when does it start",
        "when does service start", "start of service", "when do trains start", "first service",
        "πρωτο τρεν", "πρωτο δρομολογιο", "πρωτος συρμος", "ποτε ξεκινα", "ποτε ξεκινουν",
        "εναρξη δρομολογιων", "πρωτο μετρο", "πρωτη αναχωρηση",
        "treni i pare", "nisja e pare", "tren i pare", "kur fillon", "kur fillojne",
        "sherbimi i pare", "metroja e pare"]
    // Bare "first / earliest" position tokens (folded substrings). Count only
    // alongside a named station or line.
    private static let firstTokens = ["first", "earliest", "πρωτ", "i pare", "e pare", "me heret"]
    // Step-free / wheelchair / lift accessibility cues.
    private static let accessibilityWords = ["accessible", "accessibility", "wheelchair", "step free", "step-free", "stepfree",
        "lift", "elevator", "disabled access", "disability access", "amea",
        "προσβασιμ", "προσβαση αμεα", "για αμεα", "αναπηρικ", "αμαξιδι", "ασανσερ", "αναβατοριο", "αναπηρια",
        "i aksesueshem", "aksesueshem", "aksesi", "karrige me rrota", "ashensor",
        "per personat me aftesi", "personat me aftesi te kufizuara"]
    // "which line(s) serve X" — list the lines at a station.
    private static let whichLinesWords = ["which line", "which lines", "what line", "what lines",
        "which metro", "what metro", "lines serve", "lines serving", "lines at", "lines through",
        "lines that stop", "served by",
        "ποια γραμμη", "ποιες γραμμες", "τι γραμμη", "τι γραμμες", "ποιες γραμμ", "γραμμες περνανε",
        "cila linje", "cilat linja", "cilat linje", "linjat qe", "cila metro"]
    // "how many stops / how far from A to B" — stop count.
    private static let stopsBetweenWords = ["how many stops", "how many stations", "number of stops",
        "number of stations", "how many stops away", "stops away", "stops between", "stations between",
        "how far apart",
        "ποσες στασεις", "ποσοι σταθμοι", "ποσους σταθμους", "ποσες σταθμοι", "ποσα στοπ",
        "sa stacione", "sa ndalesa", "sa stacione ka", "sa ndalesa ka"]
    // "and back" / "return" / "the other way" — reverse the last route.
    private static let reversePhrases = ["and back", "way back", "the other way", "return trip", "round trip",
        "return journey", "reverse", "reverse trip", "back again", "other direction", "opposite direction",
        "coming back", "on the way back", "return the same way",
        "και πισω", "επιστροφη", "αντιστροφ", "το αναποδο", "το αντιθετο", "πισω παλι",
        "αναποδη διαδρομη", "για επιστροφη",
        "kthimi", "kthimin", "e kunderta", "rruga e kthimit", "anasjelltas",
        "kthimi mbrapa", "kthej mbrapsht", "dhe kthimi"]
    private static let planPhrases = ["how do i get", "how to get", "get to", "get me to", "route",
        "how do i go", "how to go", "go to", "can i go", "can i still", "can i reach",
        "take me to", "best way", "fastest way", "quickest way", "how can i get",
        "i want to go", "i need to go", "navigate to", "way to reach", "getting to",
        "πως πα", "πως πη", "πως φτα", "διαδρομη", "για να πα", "προλαβαινω", "μπορω να παω",
        "πως θα παω", "καλυτερος τροπος", "πιο γρηγορα", "θελω να παω", "πως μπορω να παω",
        "si shkoj", "si te shkoj", "rruga", "udhetim", "a mund te shkoj", "a arrij",
        "si te vij", "rruga me e mire", "rruga me e shpejte", "dua te shkoj", "si mund te shkoj"]
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
    private static let help = ["what can you do", "help", "how do you work", "what do you do",
        // Identity: "who is she" answers with the intro instead of declining.
        "who are you", "who are u", "who r u", "who ru", "who is ariadne", "whos ariadne",
        "what is ariadne", "what are you",
        // Greetings: a bare hello opens Ariadne with her intro.
        "hello", "hi", "hey", "hiya", "hey there", "good morning", "good evening",
        "τι μπορεις", "βοηθεια", "πως δουλευ", "ποιος εισαι", "τι εισαι", "τι ειναι η αριαδνη",
        "ποια εισαι", "γεια", "γεια σου", "γεια σας", "καλημερα",
        "si funksionon", "ndihme", "cfare mund", "kush je", "cfare je", "cfare eshte ariadne",
        "kush eshte ariadne", "pershendetje", "tung", "ckemi"]
    private static let weatherWords = ["rain", "raining", "rainy", "weather", "storm", "wet",
        "βροχη", "βρεχει", "καιρο", "κακοκαιρ", "shi", "moti", "stuhi"]
    private static let timePhrases = ["how long", "how many minutes", "how many hours",
        "how long does it take", "how long to get", "how much time", "minutes away", "how far",
        "ποση ωρα", "ποσα λεπτα", "ποσες ωρες", "ποσο θελει", "ποση ωρα κανει",
        "sa gjate", "sa minuta", "sa ore", "sa larg", "sa kohe"]
    // Station operational-status cues. "closed"/"κλειστ"/"mbyll" overlap
    // alertWords on purpose: with a named station they mean "is this stop open?",
    // which the status branch (ordered first) handles.
    private static let stationStatusWords = ["open", "working", "operating", "operational", "running", "closed", "shut",
        "ανοιχτ", "λειτουργει", "δουλευει", "κλειστ", "ανοιξ",
        "hapur", "punon", "funksionon", "mbyllur", "mbyll"]
    // Words meaning "station", so "is the station open?" resolves even before the
    // specific stop is named.
    private static let stationNounWords = ["station", "σταθμ", "stacion"]
    // "I'm at / here / got off" context-set cues. Kept to multi-word or
    // distinctive tokens so a lone "in"/"on" can't trigger a location set.
    private static let locationPhrases = ["i'm at", "im at", "i am at", "i'm in", "im in", "i'm on", "im on",
        "i'm here", "im here", "i am here", "i'm there", "im there", "i am there",
        "i reached", "i just reached", "i arrived", "i got off", "i just got off",
        "currently at", "i'm inside", "im inside",
        "ειμαι στο", "ειμαι στη", "ειμαι στον", "ειμαι εδω", "ειμαι μεσα",
        "εφτασα", "μολις εφτασα", "κατεβηκα", "μολις κατεβηκα",
        "jam te", "jam ne", "jam ketu", "jam brenda", "arrita", "zbrita", "sapo zbrita"]
    private static let tomorrowWords = ["tomorrow", "αυριο", "neser"]
    private static let weekendWords = ["weekend", "σαββατοκυριακο", "fundjave"]
    private static let saturdayWords = ["saturday", "σαββατο", "te shtune", "shtune"]
    private static let sundayWords = ["sunday", "κυριακη", "te diel", "diel"]

    // Recovery: wrong train / wrong line / wrong direction.
    private static let wrongTrainPhrases = [
        "wrong train", "wrong line", "wrong metro", "wrong tram", "wrong direction",
        "took the wrong", "got on the wrong", "i'm on the wrong", "im on the wrong",
        "this isn't my train", "this isnt my train", "not my train", "not my line",
        "λαθος τρεν", "λαθος γραμμ", "λαθος κατευθυνση", "λαθος συρμ",
        "πηρα λαθος", "μπηκα σε λαθος", "δεν ειναι δικο μου",
        "tren i gabuar", "linja e gabuar", "gabim", "hyra ne trenin e gabuar",
        "nuk eshte treni im", "drejtim i gabuar",
    ]
    // Recovery: missed stop / went past.
    private static let missedStopPhrases = [
        "missed my stop", "missed my station", "missed the stop", "went past",
        "passed my stop", "passed my station", "overshot", "went too far",
        "i didn't get off", "i didnt get off", "forgot to get off",
        "εχασα τη σταση", "εχασα τον σταθμο", "περασα τη σταση", "περασα τον σταθμο",
        "ξεχασα να κατεβω", "δεν κατεβηκα", "προσπερασα",
        "humba stacionin", "humba ndalesan", "kalova stacionin", "shkova larg",
        "nuk zbrita", "harrova te zbris",
    ]
    // Recovery: can I still make it / will I get there in time.
    private static let canIStillMakeItPhrases = [
        "can i still make it", "can i still get there", "will i make it",
        "am i going to make it", "is there still time", "can i reach",
        "do i have time", "will i get there", "am i too late",
        "προλαβαινω", "θα προλαβω", "εχω χρονο", "θα φτασω", "ειναι αργα",
        "a do ia dal", "a do arrij", "a kam kohe", "a eshte vone",
        "a mund te arrij", "a do ta kap",
    ]

    // Easter egg triggers. Substring match on folded text so "Liepuras",
    // "λιεπουρας", "λιεπ", "liepurashi" all resolve.
    private static let liepurTriggers = ["liepur", "λιεπ"]

    // Single-word vocabulary tokens the fuzzy matcher must never "correct" into
    // a station (so "trains" stays a departures cue, not a nearby-sounding stop).
    private static let stopwords: Set<String> = Set(
        (transitNouns + departureWords + findWords + lineWords + fareWords + favoriteWords +
         airportWords + alertWords + mapWords + weatherWords +
         accessibilityWords + reversePhrases + firstTrainPhrases +
         whichLinesWords + stopsBetweenWords +
         wrongTrainPhrases + missedStopPhrases + canIStillMakeItPhrases +
         tomorrowWords + weekendWords + saturdayWords + sundayWords)
            .map { fold($0) }
            .filter { $0.count >= 4 && !$0.contains(" ") }
    )
}

private extension Character {
    func isLetterOrDigit() -> Bool { isLetter || isNumber }
}
