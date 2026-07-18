import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

// Ariadne "Clever mode" intent classifier for Apple Foundation Models (iOS 26+
// on Apple Intelligence devices). This is the grounded-assistant upgrade from
// the Ultra Clever pack: instead of only normalizing fuzzy text, the on-device
// model now does the understanding step — it classifies the user's message into
// one of Ariadne's approved intents via guided generation (@Generable), and
// pulls out the station / line the user mentioned as plain text.
//
// Grounding is preserved exactly: the model never emits a station id, a time, a
// fare, or any transit fact. It only picks an intent and quotes what the user
// said; Swift then resolves those mentions to canonical ids against the bundled
// vocabulary and hands the intent to the existing deterministic dispatch
// (AriadneModel.resolve), which is the sole source of facts. When the model is
// unavailable or returns something we can't ground, we return nil and the caller
// falls back to the rule parser (AthensTransitParser mirror) with zero change.

/// The intent families the classifier can emit, mirroring AssistantIntent and
/// the pack's ariadne_intent_schema.json (minus the app-action intents, which
/// stay rule-only for safety).
enum GuidedIntentKind: String, CaseIterable {
    case showDepartures, lastTrain, findStation, planTrip, planTripByArrival
    case travelTime, explainLine, explainFare, showAlerts, weatherAt, help, outOfScope
}

enum GuidedDay: String, CaseIterable {
    case today, tomorrow, weekend, saturday, sunday
}

#if canImport(FoundationModels)

/// The structured output the model is forced to produce. Station and line are
/// free text the user used ("Syntagma", "line 2"); Swift resolves them to ids.
@available(iOS 26.0, *)
@Generable
struct GuidedIntentPayload {
    @Guide(description: "The single best intent for the user's message.")
    var intent: GuidedIntentKindGenerable

    @Guide(description: "Origin or primary station exactly as the user named it, or empty if none.")
    var stationText: String

    @Guide(description: "Destination station exactly as the user named it, or empty if none.")
    var toStationText: String

    @Guide(description: "Line the user mentioned like M2, line 3, tram, or empty if none.")
    var lineText: String

    @Guide(description: "True only when the user is clearly asking about the airport.")
    var airport: Bool

    @Guide(description: "True when the user asks for a sheltered or low-exposure route.")
    var lowExposure: Bool

    @Guide(description: "Which day the user means for departures.")
    var day: GuidedDayGenerable

    @Guide(description: "Target clock time in Athens 24h like 21:30 when the user must arrive by a time, else empty.")
    var arriveByClock: String

    @Guide(description: "Minutes from now the user must arrive within, else 0.")
    var arriveInMinutes: Int
}

@available(iOS 26.0, *)
@Generable
enum GuidedIntentKindGenerable: String {
    case showDepartures, lastTrain, findStation, planTrip, planTripByArrival
    case travelTime, explainLine, explainFare, showAlerts, weatherAt, help, outOfScope
}

@available(iOS 26.0, *)
@Generable
enum GuidedDayGenerable: String {
    case today, tomorrow, weekend, saturday, sunday
}

#endif

enum AriadneGuided {

    /// Classify [input] into a grounded AssistantIntent using Foundation Models,
    /// or nil to fall back to the rule parser. Station / line mentions are
    /// resolved to canonical ids against [vocabulary]; anything the model quotes
    /// that we can't resolve is dropped (never guessed), so a plan/departures
    /// intent with an unresolved station degrades to a clarification request.
    static func classify(_ input: String, vocabulary: AssistantVocabulary) async -> AssistantIntent? {
        // Preferred: the downloaded on-device model (llama.cpp), same GGUF as
        // Android/Web. Grammar-locked JSON, grounded exactly like the rule parser.
        if let path = AriadneModelStore.readyModelPath(),
           await LlamaSession.shared.load(path: path) {
            let json = await LlamaSession.shared.complete(
                prompt: llamaClassificationPrompt(input), maxTokens: 160, grammar: llamaGrammar)
            if let intent = groundJSON(json, vocabulary: vocabulary) { return intent }
        }
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            guard case .available = SystemLanguageModel.default.availability else { return nil }
            let instructions = Instructions(systemPrompt)
            do {
                let session = LanguageModelSession(instructions: instructions)
                let response = try await session.respond(to: input, generating: GuidedIntentPayload.self)
                return ground(response.content, vocabulary: vocabulary)
            } catch {
                return nil
            }
        }
        #endif
        return nil
    }

    // MARK: - On-device llama.cpp path (grammar-locked JSON)

    /// GBNF that locks output to the flat intent JSON (mirrors
    /// core/common AriadneGrammar.GBNF and the web grammar).
    private static let llamaGrammar = """
    root ::= "{\\"intent\\":" intent ",\\"station\\":" str ",\\"toStation\\":" str ",\\"line\\":" str ",\\"query\\":" str ",\\"airport\\":" bool ",\\"lowExposure\\":" bool ",\\"day\\":" day ",\\"arriveByClock\\":" str ",\\"arriveInMinutes\\":" int "}"
    intent ::= "\\"showDepartures\\"" | "\\"lastTrain\\"" | "\\"firstTrain\\"" | "\\"stationAccessibility\\"" | "\\"reverseTrip\\"" | "\\"findStation\\"" | "\\"planTrip\\"" | "\\"planTripByArrival\\"" | "\\"travelTime\\"" | "\\"explainLine\\"" | "\\"explainFare\\"" | "\\"showAlerts\\"" | "\\"weatherAt\\"" | "\\"help\\"" | "\\"outOfScope\\""
    day ::= "\\"today\\"" | "\\"tomorrow\\"" | "\\"weekend\\"" | "\\"saturday\\"" | "\\"sunday\\""
    bool ::= "true" | "false"
    int ::= "0" | [1-9] [0-9]{0,3}
    str ::= "\\"" schar{0,40} "\\""
    schar ::= [a-zA-Z0-9 .,:/-]
    """

    /// Few-shot prompt, mirrors core/domain IntentGrounder.classificationPrompt.
    private static func llamaClassificationPrompt(_ input: String) -> String {
        """
        Task: classify a message about Athens metro/tram/suburban rail into ONE intent and quote the stations. Output ONLY the JSON object.
        Intents: showDepartures (next trains from a station), lastTrain (last/final train), firstTrain (first/earliest train of the day), stationAccessibility (is a station step-free / wheelchair / lift), reverseTrip (and back / return the last trip), planTrip (how to go from A to B), planTripByArrival (arrive by a time), travelTime (how long), explainFare (ticket price/cost), explainLine (about a line), showAlerts (delays/strikes/closures), findStation (where is a station), weatherAt, help (what can you do), outOfScope (not about Athens transit).

        Message: last train from syntagma
        JSON: {"intent":"lastTrain","station":"syntagma","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: how much is a ticket to the airport
        JSON: {"intent":"explainFare","station":"","toStation":"airport","line":"","query":"","airport":true,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: kur niset treni i fundit per Pire
        JSON: {"intent":"lastTrain","station":"Pire","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: πρωτο τρενο απο το Μοναστηρακι
        JSON: {"intent":"firstTrain","station":"Monastiraki","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: a eshte Sintagma i aksesueshem
        JSON: {"intent":"stationAccessibility","station":"Syntagma","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: and back?
        JSON: {"intent":"reverseTrip","station":"","toStation":"","line":"","query":"","airport":false,"lowExposure":false,"day":"today","arriveByClock":"","arriveInMinutes":0}
        Message: \(input.trimmingCharacters(in: .whitespacesAndNewlines))
        JSON:
        """
    }

    /// Grounds the model's JSON into an intent, resolving quoted station/line to
    /// canonical ids (never trusting the model for an id or a fact).
    private static func groundJSON(_ json: String, vocabulary: AssistantVocabulary) -> AssistantIntent? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        func s(_ k: String) -> String { (obj[k] as? String) ?? "" }
        func b(_ k: String) -> Bool { (obj[k] as? Bool) ?? false }
        func i(_ k: String) -> Int { (obj[k] as? Int) ?? ((obj[k] as? NSNumber)?.intValue ?? 0) }
        let kind = s("intent")
        let station = resolveStation(s("station"), in: vocabulary)
        let toStation = resolveStation(s("toStation"), in: vocabulary)
        let line = resolveLine(s("line"), in: vocabulary)
        let day = mapDayString(s("day"))
        switch kind {
        case "showDepartures": return .showDepartures(stationId: station, lineId: line, day: day)
        case "lastTrain": return .lastTrain(stationId: station, lineId: line)
        case "firstTrain": return .firstTrain(stationId: station, lineId: line)
        case "stationAccessibility": return station == nil ? nil : .stationAccessibility(stationId: station)
        case "reverseTrip": return .reverseTrip
        case "findStation":
            let q = s("query").isEmpty ? s("station") : s("query")
            return q.isEmpty ? nil : .findStation(query: q)
        case "planTrip":
            if station == nil && toStation == nil { return nil }
            return .planTrip(fromStationId: station, toStationId: toStation, lowExposure: b("lowExposure"), preference: .balanced)
        case "planTripByArrival":
            let absMin = clockToAthensMinutes(s("arriveByClock"))
            let relMin = i("arriveInMinutes") > 0 ? i("arriveInMinutes") : nil
            if absMin == nil && relMin == nil { return nil }
            return .planTripByArrival(fromStationId: station, toStationId: toStation,
                                      arriveByAthensMinutes: absMin, inMinutesFromNow: relMin)
        case "travelTime": return .travelTime(toStationId: toStation ?? station, fromStationId: toStation == nil ? nil : station)
        case "explainLine": guard let line else { return nil }; return .explainLine(lineId: line)
        case "explainFare": return .explainFare(airport: b("airport"), fromStationId: station, toStationId: toStation)
        case "showAlerts": return .showAlerts(lineId: line)
        case "weatherAt": return .weatherAt(stationId: station)
        case "help": return .help
        case "outOfScope": return .outOfScope
        default: return nil
        }
    }

    private static func mapDayString(_ d: String) -> DayContext {
        switch d.lowercased() {
        case "tomorrow": return .tomorrow
        case "weekend": return .weekend
        case "saturday": return .saturday
        case "sunday": return .sunday
        default: return .today
        }
    }

    #if canImport(FoundationModels)
    /// Turns the model's structured guess into a grounded AssistantIntent,
    /// resolving station / line text to ids and never trusting the model for a
    /// fact. Returns nil when the intent needs a slot the model didn't give and
    /// the rule parser would do better.
    @available(iOS 26.0, *)
    private static func ground(_ p: GuidedIntentPayload, vocabulary: AssistantVocabulary) -> AssistantIntent? {
        let station = resolveStation(p.stationText, in: vocabulary)
        let toStation = resolveStation(p.toStationText, in: vocabulary)
        let line = resolveLine(p.lineText, in: vocabulary)
        let day = mapDay(p.day)

        switch p.intent {
        case .showDepartures:
            return .showDepartures(stationId: station, lineId: line, day: day)
        case .lastTrain:
            return .lastTrain(stationId: station, lineId: line)
        case .findStation:
            let q = p.stationText.isEmpty ? input(p) : p.stationText
            return q.isEmpty ? nil : .findStation(query: q)
        case .planTrip:
            if station == nil && toStation == nil { return nil }
            return .planTrip(fromStationId: station, toStationId: toStation, lowExposure: p.lowExposure, preference: .balanced)
        case .planTripByArrival:
            let absMin = clockToAthensMinutes(p.arriveByClock)
            let relMin = p.arriveInMinutes > 0 ? p.arriveInMinutes : nil
            if absMin == nil && relMin == nil { return nil }
            return .planTripByArrival(fromStationId: station, toStationId: toStation,
                                      arriveByAthensMinutes: absMin, inMinutesFromNow: relMin)
        case .travelTime:
            return .travelTime(toStationId: toStation ?? station, fromStationId: toStation == nil ? nil : station)
        case .explainLine:
            guard let line else { return nil }
            return .explainLine(lineId: line)
        case .explainFare:
            return .explainFare(airport: p.airport, fromStationId: station, toStationId: toStation)
        case .showAlerts:
            return .showAlerts(lineId: line)
        case .weatherAt:
            return .weatherAt(stationId: station)
        case .help:
            return .help
        case .outOfScope:
            return .outOfScope
        }
    }

    @available(iOS 26.0, *)
    private static func input(_ p: GuidedIntentPayload) -> String { p.stationText }

    @available(iOS 26.0, *)
    private static func mapDay(_ d: GuidedDayGenerable) -> DayContext {
        switch d {
        case .today: return .today
        case .tomorrow: return .tomorrow
        case .weekend: return .weekend
        case .saturday: return .saturday
        case .sunday: return .sunday
        }
    }
    #endif

    // MARK: - Deterministic grounding helpers (no model involved)

    /// Resolve a free-text station mention to a bundled station id, or nil.
    /// Accent- and case-insensitive; prefers an exact name match, then a prefix,
    /// then a contains match, so "sintagma" and "Σύνταγμα" both land on Syntagma.
    static func resolveStation(_ text: String, in vocabulary: AssistantVocabulary) -> String? {
        let needle = fold(text)
        guard needle.count >= 3 else { return nil }
        var prefixHit: String?
        var containsHit: String?
        for st in vocabulary.stations {
            for raw in st.names {
                let name = fold(raw)
                if name == needle { return st.id }
                if prefixHit == nil, name.hasPrefix(needle) || needle.hasPrefix(name) { prefixHit = st.id }
                if containsHit == nil, name.contains(needle) || needle.contains(name) { containsHit = st.id }
            }
        }
        return prefixHit ?? containsHit
    }

    /// Resolve a free-text line mention ("M2", "line 3", "tram") to a line id.
    static func resolveLine(_ text: String, in vocabulary: AssistantVocabulary) -> String? {
        let needle = fold(text)
        guard !needle.isEmpty else { return nil }
        for line in vocabulary.lines {
            if line.aliases.contains(where: { fold($0) == needle }) { return line.id }
        }
        for line in vocabulary.lines {
            if line.aliases.contains(where: { let a = fold($0); return !a.isEmpty && needle.contains(a) }) { return line.id }
        }
        return nil
    }

    /// "21:30" -> Athens minutes-of-day, or nil when not a valid clock.
    static func clockToAthensMinutes(_ clock: String) -> Int? {
        let parts = clock.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]),
              (0..<24).contains(h), (0..<60).contains(m) else { return nil }
        return h * 60 + m
    }

    private static func fold(_ s: String) -> String {
        s.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US"))
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Grounding system prompt, condensed from the pack's ariadne_prompt_pack.md.
    static let systemPrompt = """
    You are Ariadne, the offline assistant inside Syrmos for Athens metro, tram, and
    suburban rail. You are NOT the source of transit facts. Classify the user's
    message into exactly one intent and extract the station and line they mention
    as plain text, quoted as the user said it. Never output a station id, a time, a
    fare, a route, or any fact — the app computes those. If the message is not about
    Athens public transport, use outOfScope. If they ask what you can do, use help.
    Supported languages: English, Greek, Albanian.
    """
}
