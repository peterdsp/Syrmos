import Foundation

// Swift mirrors of the KMP core/domain assistant types that back Ariadne's
// co-pilot behaviours: route preference, session memory, and the STASY service
// advisory matcher. Kept a faithful port of RoutePreference.kt,
// AssistantSessionContext.kt and ServiceAdvisory.kt so iOS and Android/Web
// reason about context, preference, and advisories identically. Pure, offline.

// MARK: - Route preference

/// How the user wants a route optimised, parsed from cues like "faster",
/// "easiest", or "fewer changes". Ariadne stays a co-pilot: she picks a
/// preference, the deterministic planner ranks the options, and she explains the
/// trade-off rather than deciding silently.
///
/// `.fastest` minimises total minutes. `.fewestChanges` minimises transfers even
/// at a small time cost, the right default for luggage / strollers / limited
/// mobility. `.balanced` is the neutral default when the user gave no cue.
enum RoutePreference: Equatable {
    case balanced
    case fastest
    case fewestChanges

    /// Accent-folded, lowercased cue words per preference (EN / EL / SQ).
    private static let fastestCues = [
        "faster", "fastest", "quicker", "quickest", "fast", "quick", "speed",
        "γρηγορα", "γρηγορο", "γρηγορη", "πιο γρηγορα", "ταχυτερ", "ταχυτητα",
        "shpejt", "me shpejt", "shpejte", "shpejtesi",
    ]
    private static let fewestChangesCues = [
        "easiest", "easier", "easy", "simplest", "simpler", "simple", "direct",
        "fewer change", "fewer changes", "no change", "no changes", "less walking",
        "fewest", "without changing", "straight",
        "ευκολ", "απλ", "απευθειας", "χωρις αλλαγ", "λιγοτερες αλλαγ", "λιγοτερο περπατ",
        "lehte", "me lehte", "thjesht", "direkt", "pa nderrim", "me pak nderrim", "me pak ecje",
    ]

    /// Reads a preference out of already-folded text (`AthensTransitParser.fold`).
    /// Fewest-changes wins ties because "easy direct" leans toward simplicity.
    static func fromFolded(_ folded: String) -> RoutePreference {
        if fewestChangesCues.contains(where: { folded.contains($0) }) { return .fewestChanges }
        if fastestCues.contains(where: { folded.contains($0) }) { return .fastest }
        return .balanced
    }
}

// MARK: - Session context

/// The small amount of conversation memory that lets Ariadne feel like a co-pilot
/// instead of a stateless Q&A box. Not chat memory in the LLM sense: just the
/// handful of facts a transit companion needs so follow-ups don't re-ask what
/// the user already said.
///
/// "I'm at Syntagma" sets `currentStation`; a later "go airport faster" then
/// needs no "from where?". Everything is optional and the whole context is
/// replaced each turn, so state is easy to reason about and mirror on Android.
struct AssistantSessionContext: Equatable {
    var currentStation: String?
    var currentLine: String?
    var lastDestination: String?
    var lastRoute: RouteMemory?

    static let empty = AssistantSessionContext()

    func withCurrentStation(_ stationId: String?) -> AssistantSessionContext {
        var copy = self
        copy.currentStation = stationId
        return copy
    }

    /// The best-known origin for a trip the user didn't fully specify.
    func originOr(_ explicit: String?) -> String? { explicit ?? currentStation }
}

/// Just enough of the last route to answer a follow-up ("faster?") without
/// recomputing from scratch or re-asking endpoints.
struct RouteMemory: Equatable {
    let fromStationId: String
    let toStationId: String
    var preference: RoutePreference = .balanced
    var totalMinutes: Int?
    var transferCount: Int?
}

// MARK: - Service advisory

/// STASY severity, mapped from the feed's "info" / "warning" / "closure".
enum AdvisorySeverity: Int, Equatable {
    case info = 0
    case warning = 1
    case closure = 2

    static func fromRaw(_ raw: String) -> AdvisorySeverity {
        switch raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "closure", "closed": return .closure
        case "warning", "warn", "alert": return .warning
        default: return .info
        }
    }
}

/// A service notice relevant to Ariadne, projected from a STASY announcement by
/// the caller so this layer stays free of the network `STASYAnnouncement` type.
/// `text` is the already-localized title/summary; station names commonly appear
/// inside it, which is how the matcher links a notice to a station.
struct ServiceNotice: Equatable {
    let id: String
    /// Localized title/summary Ariadne reads back to the user.
    let text: String
    var affectedLineIds: [String] = []
    var severity: AdvisorySeverity = .info
    var validFrom: String?
    var validUntil: String?
    /// All-language blob the matcher scans for station names, so a Greek-only
    /// announcement still links to a station the user named in English. Defaults
    /// to `text` when the caller has only one language available.
    var searchText: String

    init(
        id: String,
        text: String,
        affectedLineIds: [String] = [],
        severity: AdvisorySeverity = .info,
        validFrom: String? = nil,
        validUntil: String? = nil,
        searchText: String? = nil
    ) {
        self.id = id
        self.text = text
        self.affectedLineIds = affectedLineIds
        self.severity = severity
        self.validFrom = validFrom
        self.validUntil = validUntil
        self.searchText = searchText ?? text
    }
}

/// What Ariadne found relevant to the thing the user asked about: the matching
/// STASY notices plus whether severe weather is in play. `hasAny` is the cue for
/// the resolver to lead with the advisory before reciting the timetable.
struct ServiceAdvisory: Equatable {
    var notices: [ServiceNotice] = []
    var severeWeather: Bool = false

    static let none = ServiceAdvisory()

    var hasAny: Bool { !notices.isEmpty || severeWeather }

    /// Closures first, then warnings, then info, so Ariadne leads with the worst news.
    var ranked: [ServiceNotice] {
        notices.sorted { $0.severity.rawValue > $1.severity.rawValue }
    }

    /// The single most important notice, if any, for a one-line answer.
    var top: ServiceNotice? { ranked.first }
}

/// Links the STASY notices and severe-weather signal we already show on Home to
/// whatever Ariadne is answering about: one station, one line, or a whole route.
///
/// A notice matches a line by id, or a station by the station's name appearing in
/// the notice text. Matching runs on accent-folded, lowercased text
/// (`AthensTransitParser.fold`) so Greek, Albanian, English and Greeklish
/// converge. Pure and offline: the caller supplies the notices, the matcher
/// never fetches anything.
enum ServiceAdvisoryMatcher {

    static func forLine(
        lineId: String,
        notices: [ServiceNotice],
        severeWeather: Bool = false
    ) -> ServiceAdvisory {
        ServiceAdvisory(
            notices: notices.filter { mentionsAnyLine($0, [lineId]) },
            severeWeather: severeWeather
        )
    }

    static func forStation(
        stationNames: [String],
        stationLineIds: [String],
        notices: [ServiceNotice],
        severeWeather: Bool = false
    ) -> ServiceAdvisory {
        ServiceAdvisory(
            notices: notices.filter { mentionsAnyLine($0, stationLineIds) || mentionsAnyName($0, stationNames) },
            severeWeather: severeWeather
        )
    }

    static func forRoute(
        lineIds: [String],
        stationNames: [String],
        notices: [ServiceNotice],
        severeWeather: Bool = false
    ) -> ServiceAdvisory {
        ServiceAdvisory(
            notices: notices.filter { mentionsAnyLine($0, lineIds) || mentionsAnyName($0, stationNames) },
            severeWeather: severeWeather
        )
    }

    private static func mentionsAnyLine(_ notice: ServiceNotice, _ lineIds: [String]) -> Bool {
        if notice.affectedLineIds.isEmpty || lineIds.isEmpty { return false }
        let wanted = Set(lineIds.map { normalizeLine($0) }.filter { !$0.isEmpty })
        if wanted.isEmpty { return false }
        return notice.affectedLineIds.contains { wanted.contains(normalizeLine($0)) }
    }

    private static func mentionsAnyName(_ notice: ServiceNotice, _ names: [String]) -> Bool {
        if names.isEmpty || notice.searchText.trimmingCharacters(in: .whitespaces).isEmpty { return false }
        let folded = AthensTransitParser.fold(notice.searchText)
        return names.contains { name in
            let f = AthensTransitParser.fold(name).trimmingCharacters(in: .whitespaces)
            // Guard against 1-2 char names matching noise; real station names are longer.
            return f.count >= 3 && folded.contains(f)
        }
    }

    /// "M3" / "m3" / "line 3" / "γραμμή 3" ids reduced to a comparable token.
    private static func normalizeLine(_ id: String) -> String {
        AthensTransitParser.fold(id)
            .replacingOccurrences(of: "line", with: "")
            .replacingOccurrences(of: "γραμμη", with: "")
            .replacingOccurrences(of: "metro", with: "")
            .replacingOccurrences(of: " ", with: "")
            .trimmingCharacters(in: .whitespaces)
    }
}

// MARK: - Weather context (Phase 2)

// Swift mirrors of the KMP weather-context types that let Ariadne tilt route
// advice on the weather while staying honest about certainty. Faithful ports of
// core/model/.../weather/WeatherContext.kt and
// core/domain/.../assistant/WeatherContextBuilder.kt so iOS and Android/Web
// reason about live vs seasonal weather identically. Pure, offline.

/// How exposed to the weather a route is, from its transit types. Metro is
/// sheltered (underground), tram is exposed (open-air), suburban is mixed.
/// Mirrors the KMP `Exposure` / `StationComfort`.
enum Exposure: Equatable {
    case sheltered
    case mixed
    case exposed

    /// Worst-case exposure across a route's transit types: any exposed leg makes
    /// the whole route exposed; else any mixed leg makes it mixed; else sheltered.
    static func forRoute(_ types: [TransitType]) -> Exposure {
        func e(_ type: TransitType) -> Exposure {
            switch type {
            case .metro: return .sheltered
            case .tram: return .exposed
            case .suburban: return .mixed
            case .bus: return .exposed
            }
        }
        let es = types.map(e)
        if es.contains(.exposed) { return .exposed }
        if es.contains(.mixed) { return .mixed }
        return .sheltered
    }
}

/// Where a `WeatherContext` came from, so Ariadne can be honest about certainty.
/// `.live` / `.forecast` are real observations; `.seasonalFallback` is
/// climatology for the month ("usually hot this time of year"), never presented
/// as "now"; `.unknown` means we have nothing and must say so.
enum WeatherSource: Equatable {
    case live
    case forecast
    case seasonalFallback
    case unknown
}

/// The dominant advisory Ariadne acts on. Kept deliberately small (the four
/// states that actually change transit advice) rather than a full forecast.
enum WeatherState: Equatable {
    case normal
    case hot
    case rainy
    case windy
}

/// Coarse risk band for a single factor (heat / rain / wind).
enum WeatherRisk: Equatable {
    case low
    case medium
    case high
}

/// A weather signal reduced to what changes transit advice: the source (so
/// phrasing stays honest), the dominant `state`, and per-factor risk bands.
/// `condition`, `temperatureC`, and `windKph` are nil for a seasonal-fallback
/// context, because climatology gives a typical picture, not a live reading.
struct WeatherContext: Equatable {
    let source: WeatherSource
    let state: WeatherState
    var condition: WeatherCondition?
    var temperatureC: Double?
    var windKph: Double?
    var heatRisk: WeatherRisk = .low
    var rainRisk: WeatherRisk = .low
    var windRisk: WeatherRisk = .low
    var placeName: String?
    /// Month (1..12) this context describes; set for seasonal, else nil.
    var month: Int?

    var isLive: Bool { source == .live || source == .forecast }
    var isKnown: Bool { source != .unknown }

    static let unknown = WeatherContext(source: .unknown, state: .normal)
}

/// Typical Athens weather for a calendar month, the honest fallback when there's
/// no live reading. `typicalHighC` and `typicalCondition` describe the norm; the
/// caller phrases it as "usually / this time of year", never "now".
struct SeasonalWeatherProfile: Equatable {
    let month: Int               // 1..12
    let city: String
    let typicalCondition: WeatherCondition
    let typicalHighC: Int
    let typicalState: WeatherState
}

/// Athens climatology: the typical weather per month, used as an honest fallback
/// when there's no live reading. Values are round monthly-high norms for the
/// Athens basin; precision isn't the point, the honest "this time of year"
/// framing is. Summer (Jun–Sep) is hot and dry; winter (Nov–Mar) is cooler with
/// rain possible; spring/autumn are mild.
enum AthensClimate {
    // Jan..Dec typical daily high (°C), Athens.
    private static let typicalHighC = [13, 14, 16, 20, 26, 31, 34, 34, 29, 23, 18, 14]

    static func profile(_ month: Int) -> SeasonalWeatherProfile {
        let m = ((month - 1) % 12 + 12) % 12 + 1
        let highC = typicalHighC[m - 1]
        let condition: WeatherCondition
        let state: WeatherState
        switch m {
        case 6, 7, 8, 9:
            condition = .clear; state = .hot
        case 12, 1, 2:
            condition = .rain; state = .normal
        case 3, 11:
            condition = .partlyCloudy; state = .normal
        default: // 4, 5, 10: mild
            condition = .clear; state = .normal
        }
        return SeasonalWeatherProfile(
            month: m,
            city: "Athens",
            typicalCondition: condition,
            typicalHighC: highC,
            typicalState: state
        )
    }
}

/// Builds a `WeatherContext` from what we actually have: a live `WeatherSnapshot`
/// when one is cached, otherwise the Athens seasonal profile for the month, and
/// `.unknown` only when even the month is unavailable. The source is stamped so
/// the answer composer can phrase live vs typical honestly.
enum WeatherContextBuilder {

    static func fromSnapshot(_ snap: WeatherSnapshot) -> WeatherContext {
        let temp = snap.current.temperatureC
        let wind = snap.current.windKph
        let condition = snap.current.condition
        let heat = heatRisk(temp)
        let rain = rainRisk(condition)
        let windR = windRisk(wind)
        return WeatherContext(
            source: .live,
            state: dominantState(rain: rain, heat: heat, wind: windR),
            condition: condition,
            temperatureC: temp,
            windKph: wind,
            heatRisk: heat,
            rainRisk: rain,
            windRisk: windR,
            placeName: snap.placeName,
            month: nil
        )
    }

    static func fromSeasonal(_ profile: SeasonalWeatherProfile) -> WeatherContext {
        let heat = heatRisk(Double(profile.typicalHighC))
        let rain = rainRisk(profile.typicalCondition)
        return WeatherContext(
            source: .seasonalFallback,
            // Trust the curated seasonal state, but never below what the typical
            // temperature implies (a 34° "HOT" month stays HOT).
            state: heat != .low ? .hot : profile.typicalState,
            condition: nil,
            temperatureC: nil,
            windKph: nil,
            heatRisk: heat,
            rainRisk: rain,
            windRisk: .low,
            placeName: profile.city,
            month: profile.month
        )
    }

    /// Live snapshot wins; else seasonal for `month`; else unknown.
    static func resolve(snapshot: WeatherSnapshot?, month: Int?) -> WeatherContext {
        if let snapshot { return fromSnapshot(snapshot) }
        if let month { return fromSeasonal(AthensClimate.profile(month)) }
        return .unknown
    }

    // Athens-tuned thresholds. Heat matters for exposed waits and long walks;
    // wind matters for coastal/elevated tram sections; rain risk tracks the
    // condition's wetness/severity.
    static func heatRisk(_ tempC: Double) -> WeatherRisk {
        if tempC >= 38 { return .high }
        if tempC >= 32 { return .medium }
        return .low
    }

    static func windRisk(_ windKph: Double) -> WeatherRisk {
        if windKph >= 45 { return .high }
        if windKph >= 30 { return .medium }
        return .low
    }

    static func rainRisk(_ condition: WeatherCondition) -> WeatherRisk {
        if condition.isSevere { return .high }
        if condition == .rain { return .medium }
        if condition == .drizzle { return .medium }
        return .low
    }

    /// Rain beats heat beats wind when picking the single dominant advisory.
    static func dominantState(rain: WeatherRisk, heat: WeatherRisk, wind: WeatherRisk) -> WeatherState {
        if rain != .low { return .rainy }
        if heat != .low { return .hot }
        if wind != .low { return .windy }
        return .normal
    }
}

// MARK: - Route ranking (Phase 3)

// Swift mirrors of the KMP core/domain assistant route-ranking types that let
// Ariadne pick the route that fits the moment, not just the fastest one. Faithful
// ports of core/domain/.../assistant/RouteRanker.kt so iOS and Android/Web score
// and order candidates identically. Pure, offline. The candidate holds the Swift
// `JourneyPlanner.Plan`, whose `totalMinutes` and `transfers` are the two facts
// the ranker reads (mirror of KMP `JourneyResult.totalMinutes` / `transferCount`).

private extension WeatherRisk {
    /// Ordinal matching the KMP `WeatherRisk` enum (LOW=0, MEDIUM=1, HIGH=2), so
    /// the weather-penalty severity math stays identical across platforms.
    var ordinal: Int {
        switch self {
        case .low: return 0
        case .medium: return 1
        case .high: return 2
        }
    }
}

/// A candidate route plus its exposure (how sheltered it is), the two facts the
/// ranker needs. Exposure is computed by the caller from the route's line types
/// via `Exposure.forRoute`, keeping the ranker pure and trivially testable.
struct RouteCandidate: Equatable {
    let result: JourneyPlanner.Plan
    let exposure: Exposure
}

/// A scored candidate. Lower `score` is better; `weatherPenalty` is the part of
/// the score that came from adverse weather meeting exposure, so the answer can
/// explain "slower but drier".
struct ScoredRoute: Equatable {
    let candidate: RouteCandidate
    let score: Double
    let weatherPenalty: Double
}

/// Ranks route candidates by the user's `RoutePreference`, with an adverse-weather
/// tilt: on a hot / rainy / windy day an exposed (tram / surface) route is
/// penalised, so a slightly slower but sheltered option can win a close call. This
/// is the "genuinely clever" bit — Ariadne doesn't just report the fastest route,
/// she picks the one that fits the moment and can say why. Pure and offline.
enum RouteRanker {

    static func rank(
        _ candidates: [RouteCandidate],
        preference: RoutePreference,
        weather: WeatherContext?
    ) -> [ScoredRoute] {
        candidates
            .map { c -> ScoredRoute in
                let penalty = weatherPenalty(exposure: c.exposure, weather: weather)
                return ScoredRoute(candidate: c, score: baseScore(c.result, preference) + penalty, weatherPenalty: penalty)
            }
            // Stable sort keeps input order for exact ties, so the planner's
            // primary (fastest) route wins when nothing separates them.
            .enumerated()
            .sorted { lhs, rhs in
                lhs.element.score != rhs.element.score ? lhs.element.score < rhs.element.score : lhs.offset < rhs.offset
            }
            .map { $0.element }
    }

    static func best(
        _ candidates: [RouteCandidate],
        preference: RoutePreference,
        weather: WeatherContext?
    ) -> ScoredRoute? {
        rank(candidates, preference: preference, weather: weather).first
    }

    /// Base cost before weather. `.fastest` is pure minutes; `.fewestChanges`
    /// makes a transfer dominate everything (each change worth ~1000 "minutes");
    /// `.balanced` charges a modest ~5 min per change so a small time win doesn't
    /// justify an extra transfer.
    private static func baseScore(_ result: JourneyPlanner.Plan, _ preference: RoutePreference) -> Double {
        let minutes = Double(result.totalMinutes)
        let transfers = Double(result.transfers)
        switch preference {
        case .fastest: return minutes
        case .fewestChanges: return transfers * 1000.0 + minutes
        case .balanced: return minutes + transfers * 5.0
        }
    }

    /// Extra cost when adverse weather meets an exposed route. Nothing in calm
    /// weather; a bigger hit when the risk is HIGH. Sheltered routes are never
    /// penalised (that's their advantage).
    private static func weatherPenalty(exposure: Exposure, weather: WeatherContext?) -> Double {
        guard let weather, weather.state != .normal else { return 0.0 }
        let exposureFactor: Double
        switch exposure {
        case .exposed: exposureFactor = 1.0
        case .mixed: exposureFactor = 0.5
        case .sheltered: exposureFactor = 0.0
        }
        if exposureFactor == 0.0 { return 0.0 }
        // A HIGH-risk day hurts an exposed route more than a MEDIUM one.
        let severity = max(weather.heatRisk.ordinal, weather.rainRisk.ordinal, weather.windRisk.ordinal)
        let base: Double
        switch severity {
        case 2: base = 12.0 // HIGH
        case 1: base = 8.0  // MEDIUM
        default: base = 6.0
        }
        return base * exposureFactor
    }
}
