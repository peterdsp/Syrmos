import SwiftUI
import CoreLocation

// MARK: - Models

struct TransitLine: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let terminalA: String
    let terminalB: String
    let stationCount: Int
    let color: Color
    let type: TransitType
    var region: TransitRegion = .athens
    var status: TransitLineStatus = .operational

    /// A line that carries scheduled service and may therefore produce a
    /// departure, a train, a last-train answer or a track-picker entry. Seasonal
    /// lines (the Pelion railway) count as operational: they are real boardable
    /// services whose own dated trips gate them to the days they run. Only the
    /// built-but-closed states are excluded. Check this, not the id.
    var isOperational: Bool { status == .operational || status == .seasonal }

    /// Track that exists but carries no service right now: never opened
    /// (`underConstruction`) or a real line temporarily halted (`suspended`).
    /// Drawn greyed but labelled, so it is never mistaken for a line in service.
    var isBuiltButClosed: Bool { status == .underConstruction || status == .suspended }

    /// A real line temporarily not running (rockfalls, works).
    var isSuspended: Bool { status == .suspended }

    /// Runs only part of the year / on some day-types (Pelion railway).
    var isSeasonal: Bool { status == .seasonal }
}

/// The network a line belongs to.
///
/// Deliberately not "city": the Thessaloniki suburban corridors reach Larisa and
/// Florina, and `national` intercity spans the country, so a line legitimately
/// crosses cities. Unknown values fall back to `athens` rather than failing on a
/// newer payload.
enum TransitRegion: String {
    case athens
    case thessaloniki
    case national
    case patras

    init(raw: String?) {
        self = TransitRegion(rawValue: (raw ?? "").lowercased()) ?? .athens
    }
}

/// Whether a line actually carries trains.
///
/// Thessaloniki metro Line 2 opens at the end of July 2026; until then it renders
/// greyed and every prediction path skips it. Opening it is a data change, not a
/// code change. Unknown values fall back to `operational`, matching how the app
/// behaved before the field existed: a stale payload must not make live Athens
/// lines vanish.
enum TransitLineStatus: String {
    case operational
    case underConstruction = "under_construction"
    /// A real line that ran and is temporarily halted (Diakopto-Kalavryta rack
    /// railway, suspended 13 March 2026 after rockfalls). Greyed but labelled
    /// "suspended", never "under construction".
    case suspended
    /// Runs only part of the year and on some day-types (Pelion railway: weekends
    /// and holidays, April to October). Real and boardable in season.
    case seasonal

    init(raw: String?) {
        self = TransitLineStatus(rawValue: (raw ?? "").lowercased()) ?? .operational
    }
}

enum TransitType: String, CaseIterable {
    case metro = "Metro"
    case tram = "Tram"
    case suburban = "Suburban Railway"
    case scenic = "Scenic Railway"
    /// Rail-replacement / connecting bus on a suspended rail corridor. The rail
    /// operator's own bus standing in for the train, never an OASA city bus.
    case bus = "Bus"
}

struct TransitStation: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    var nameSq: String? = nil
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool
    var region: TransitRegion = .athens
    var sourceConfidence: SourceConfidence = .scheduled
}

/// Where an on-screen departure came from, so Syrmos can say how sure it is.
/// Swift mirror of the KMP `SourceConfidence` (core:model) - keep in sync.
enum SourceConfidence: Equatable {
    case live          // a real-time position / arrival
    case scheduled     // a timetabled departure from the live schedules API
    case estimated     // projected from a frequency band, not an exact minute
    case offline       // the bundled offline snapshot
    case operatorLink  // the operator must be checked for live status
    case unknown       // no source known - render nothing

    // Self-contained RGB (no Color.* extension dependency) so this compiles in
    // every target that includes TransitData - the app AND the widget extension,
    // which doesn't link the DesignSystem Color palette. Values match the shared
    // source-state tokens: live #059669, scheduled #2563EB, estimated #B45309,
    // offline #6B7280, brand #2139A1.
    var color: Color {
        switch self {
        case .live: return Color(red: 0.020, green: 0.588, blue: 0.412)
        case .scheduled: return Color(red: 0.145, green: 0.388, blue: 0.922)
        case .estimated: return Color(red: 0.706, green: 0.325, blue: 0.035)
        case .operatorLink: return Color(red: 0.129, green: 0.224, blue: 0.631)
        case .offline, .unknown: return Color(red: 0.420, green: 0.447, blue: 0.502)
        }
    }

    func label(_ language: AppLanguage) -> String {
        switch self {
        case .live: return LocalizedKey.live.text(for: language)
        case .scheduled: return LocalizedKey.sourceScheduled.text(for: language)
        case .estimated: return LocalizedKey.sourceEstimated.text(for: language)
        case .offline: return LocalizedKey.sourceOffline.text(for: language)
        case .operatorLink: return LocalizedKey.sourceOperator.text(for: language)
        case .unknown: return ""
        }
    }
}

/// A calm chip stating how sure a departure is (live / scheduled / estimated /
/// offline). Mirrors the KMP `SourceConfidenceChip`. Renders nothing for
/// `.unknown` so it never adds noise.
struct SourceConfidenceChip: View {
    let confidence: SourceConfidence
    let language: AppLanguage

    var body: some View {
        if confidence != .unknown {
            HStack(spacing: 5) {
                Circle().fill(confidence.color).frame(width: 6, height: 6)
                    .modifier(confidence == .live ? LivePulseOptional(active: true) : LivePulseOptional(active: false))
                Text(confidence.label(language))
                    .font(.caption2).fontWeight(.semibold)
                    .foregroundColor(confidence.color)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Capsule().fill(confidence.color.opacity(0.12)))
        }
    }
}

private struct LivePulseOptional: ViewModifier {
    let active: Bool
    @State private var pulsing = false
    func body(content: Content) -> some View {
        if active {
            content
                .scaleEffect(pulsing ? 1.15 : 1.0)
                .opacity(pulsing ? 0.7 : 1.0)
                .animation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true), value: pulsing)
                .onAppear { pulsing = true }
        } else {
            content
        }
    }
}

struct Departure: Identifiable {
    let id = UUID()
    let time: String
    let lineId: String
    let direction: String
    let minutesAway: Int
    let serviceType: String
    let trainNo: String?
    /// Where this departure came from; drives the source-confidence chip.
    /// Defaults to `.scheduled` because every iOS departure list is built from
    /// timetable data (the offline projector or the server-projected API) - live
    /// suburban positions are a separate model shown as moving map trains, not
    /// rows here. Set explicitly if a genuinely live/estimated source is added.
    var sourceConfidence: SourceConfidence = .scheduled

    /// Human-friendly arrival countdown. "Now" for the train that's
    /// already at the platform, "5 min" for the close ones, and
    /// "3h 21min" when the next service is hours away (typical for
    /// late-night views or stations far downstream of a terminus).
    func secondsAway(from now: Date) -> Int {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return minutesAway * 60 }
        let cal = Calendar(identifier: .gregorian)
        let tz = TimeZone(identifier: "Europe/Athens")!
        var comps = cal.dateComponents(in: tz, from: now)
        let nowSecs = (comps.hour ?? 0) * 3600 + (comps.minute ?? 0) * 60 + (comps.second ?? 0)
        let depSecs = parts[0] * 3600 + parts[1] * 60
        var diff = depSecs - nowSecs
        if diff < -60 { diff += 86400 }
        return max(diff, 0)
    }

    func minutesAwayDisplay(language: AppLanguage) -> String {
        if minutesAway <= 1 {
            switch language {
            case .greek: return "Τώρα"
            case .albanian: return "Tani"
            case .italian: return "Ora"
            default: return "Now"
            }
        }
        let minAbbr: String
        let hAbbr: String
        switch language {
        case .greek: minAbbr = "λεπ"; hAbbr = "ω"
        case .albanian: minAbbr = "min"; hAbbr = "o"
        case .italian: minAbbr = "min"; hAbbr = "h"
        default: minAbbr = "min"; hAbbr = "h"
        }
        if minutesAway < 60 {
            return "\(minutesAway) \(minAbbr)"
        }
        let h = minutesAway / 60
        let m = minutesAway % 60
        if m == 0 { return "\(h)\(hAbbr)" }
        return "\(h)\(hAbbr) \(m)\(minAbbr)"
    }
}

// MARK: - Service patterns from official STASY/Hellenic Train timetables

struct ServicePattern {
    let lineId: String
    let direction: String
    let frequencyMinutes: Int
    let serviceType: String
}

// MARK: - Static Data

/// Decodes the generator's `seed-schedules-v2/lines.json`, the single source of
/// truth for lines.
///
/// The old path was a hardcoded Swift array here, which a JS script transcribed
/// into the KMP seed. That script broke in June 2026 when this file moved, so the
/// two copies silently drifted (86 of 201 station ids diverged) and neither could
/// carry region or status. See
/// docs/plans/2026-07-17-server-as-single-source-for-lines.md.
private struct SeedLinesPayload: Decodable {
    struct SeedLine: Decodable {
        let id: String
        let name: String
        let nameEl: String
        let type: String
        let color: String
        let terminalA: String
        let terminalB: String
        let stationCount: Int
        let region: String?
        let status: String?
    }
    let lines: [SeedLine]
}

/// Ordered station coordinates per line, read from `lines.json`'s nested
/// `stations[]` (which carry lat/lng for every line, including national,
/// Thessaloniki and Patras corridors). Used by the map to spline a route
/// polyline for any line that has no bundled OSM shape - so the whole network
/// draws, exactly like the web map, instead of only the Athens M/T/A lines.
enum SyrmosLineGeometry {
    private struct Payload: Decodable {
        struct Line: Decodable {
            let id: String
            let stations: [Station]?
        }
        struct Station: Decodable {
            let lat: Double
            let lng: Double
            let stopSequence: Int?
        }
        let lines: [Line]
    }

    private static let byLine: [String: [CLLocationCoordinate2D]] = {
        guard let url = Bundle.main.url(
            forResource: "lines",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data)
        else { return [:] }
        var out: [String: [CLLocationCoordinate2D]] = [:]
        for line in payload.lines {
            guard let stations = line.stations, !stations.isEmpty else { continue }
            let ordered = stations.enumerated().sorted { a, b in
                (a.element.stopSequence ?? a.offset) < (b.element.stopSequence ?? b.offset)
            }
            out[line.id] = ordered.map {
                CLLocationCoordinate2D(latitude: $0.element.lat, longitude: $0.element.lng)
            }
        }
        return out
    }()

    static func orderedCoordinates(for lineId: String) -> [CLLocationCoordinate2D] {
        byLine[lineId] ?? []
    }
}

enum SyrmosData {

    /// Lines, loaded from the bundled payload.
    ///
    /// Falls back to `hardcodedLines` if the bundle is missing or unreadable. That
    /// fallback is deliberate belt-and-braces, not a second source of truth: it
    /// keeps the app working rather than launching with an empty network, and it
    /// goes away once the payload has proven itself in the field.
    static let lines: [TransitLine] = loadLines() ?? hardcodedLines

    private static func loadLines() -> [TransitLine]? {
        guard let url = Bundle.main.url(
            forResource: "lines",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(SeedLinesPayload.self, from: data),
              !payload.lines.isEmpty
        else { return nil }

        return payload.lines.map { seed in
            TransitLine(
                id: seed.id,
                name: seed.name,
                nameEl: seed.nameEl,
                terminalA: seed.terminalA,
                terminalB: seed.terminalB,
                stationCount: seed.stationCount,
                color: colorFor(hex: seed.color, type: seed.type),
                type: transitType(for: seed.type),
                region: TransitRegion(raw: seed.region),
                status: TransitLineStatus(raw: seed.status)
            )
        }
    }

    /// Every station in the network, built from `lines.json`'s nested
    /// `stations[]` across ALL networks (Athens, Thessaloniki, Patras,
    /// national) - the same 389-station set the web map reads from
    /// `stations.json`. The map builds its dots from this so iOS draws every
    /// station, not just the hardcoded Athens M/T/A lines. Empty only if the
    /// bundle is missing, in which case the map falls back to `StationCoords`.
    static let bundleStations: [TransitStation] = loadBundleStations()

    private static func loadBundleStations() -> [TransitStation] {
        struct P: Decodable {
            struct L: Decodable { let id: String; let stations: [S]? }
            struct S: Decodable { let id: String; let name: String; let nameEl: String; let lat: Double; let lng: Double }
            let lines: [L]
        }
        guard let url = Bundle.main.url(
            forResource: "lines", withExtension: "json", subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(P.self, from: data)
        else { return [] }
        // A station on several lines appears in each line's nested list; merge
        // by id and collect every line it belongs to (so interchanges are right).
        var firstSeen: [String: P.S] = [:]
        var lineIdsById: [String: Set<String>] = [:]
        for line in payload.lines {
            for s in line.stations ?? [] {
                if firstSeen[s.id] == nil { firstSeen[s.id] = s }
                lineIdsById[s.id, default: []].insert(line.id)
            }
        }
        return firstSeen.map { id, s in
            // Union the same-id membership with the network-wide proximity hub
            // membership, so co-located stations that use different ids (e.g.
            // Larissa: M2_STA / GR_ATH / A1_ATH) share the full line set in map
            // pills and Browse All Stations, matching the interchange section.
            let lineIds = (lineIdsById[id] ?? []).union(hubLineIds[id] ?? []).sorted()
            return TransitStation(
                id: id, name: s.name, nameEl: s.nameEl,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lng),
                lineIds: lineIds, isInterchange: lineIds.count > 1
            )
        }
    }

    private static func transitType(for raw: String) -> TransitType {
        switch raw.lowercased() {
        case "metro": return .metro
        case "tram": return .tram
        case "bus": return .bus
        case "scenic": return .scenic
        default: return .suburban
        }
    }

    /// Prefer the payload's own hex, so a colour correction ships without an app
    /// release. Falls back to the mode's house colour when the hex is unusable.
    /// Parses the hex to a UInt and uses the unambiguous `Color(hex: UInt)`
    /// initialiser directly (the `Color(hex: String)` overload mis-resolves under
    /// Swift 6 overload rules here, picking the UInt init and rejecting a String).
    private static func colorFor(hex: String, type: String) -> Color {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 6, let v = UInt(s, radix: 16) { return Color(hex: v) }
        switch type.lowercased() {
        case "metro": return .metroBlue
        case "tram": return .tramOrange
        case "scenic": return .scenic
        default: return .suburbanPurple
        }
    }

    private static let greekToEnglish: [String: String] = {
        struct P: Decodable {
            struct L: Decodable { let stations: [S]? }
            struct S: Decodable { let name: String; let nameEl: String }
            let lines: [L]
        }
        var map: [String: String] = [:]
        guard let url = Bundle.main.url(
            forResource: "lines", withExtension: "json", subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(P.self, from: data)
        else { return [:] }
        for line in payload.lines {
            for s in line.stations ?? [] {
                let key = s.nameEl.lowercased()
                if s.name != s.nameEl { map[key] = s.name }
                else if map[key] == nil { map[key] = s.name }
            }
        }
        return map
    }()

    private static let englishToItalian: [String: String] = [
        "athens": "Atene",
        "athina": "Atene",
        "piraeus": "Pireo",
        "pireas": "Pireo",
        "thessaloniki": "Salonicco",
        "larisa": "Larissa",
        "larissa": "Larissa",
        "patra": "Patrasso",
        "patras": "Patrasso",
    ]

    static func localizedStationName(_ englishName: String, language: AppLanguage) -> String {
        let trimmed = englishName.trimmingCharacters(in: .whitespaces)
        guard language == .italian else { return trimmed }
        return englishToItalian[trimmed.lowercased()] ?? trimmed
    }

    static func translatedStationName(_ greekName: String, language: AppLanguage) -> String {
        let trimmed = greekName.trimmingCharacters(in: .whitespaces)
        if language == .greek { return trimmed }
        let english = greekToEnglish[trimmed.lowercased()] ?? trimmed
        return localizedStationName(english, language: language)
    }

    static func resolveStation(_ greek: String, en: String, language: AppLanguage) -> String {
        let trimmed = greek.trimmingCharacters(in: .whitespaces)
        if language == .greek { return trimmed }
        let enTrimmed = en.trimmingCharacters(in: .whitespaces)
        let english = enTrimmed.isEmpty ? (greekToEnglish[trimmed.lowercased()] ?? trimmed) : enTrimmed
        return localizedStationName(english, language: language)
    }

    /// Lines that actually carry trains. The default for anything the user acts on.
    static var operationalLines: [TransitLine] { lines.filter(\.isOperational) }

    private static let hardcodedLines: [TransitLine] = [
        .init(id: "M1", name: "Line 1", nameEl: "Γραμμή 1", terminalA: "Piraeus", terminalB: "Kifissia", stationCount: 24, color: .metroGreen, type: .metro),
        .init(id: "M2", name: "Line 2", nameEl: "Γραμμή 2", terminalA: "Anthoupoli", terminalB: "Elliniko", stationCount: 20, color: .metroRed, type: .metro),
        .init(id: "M3", name: "Line 3", nameEl: "Γραμμή 3", terminalA: "Dimotiko Theatro", terminalB: "Doukissis Plakentias", stationCount: 27, color: .metroBlue, type: .metro),
        .init(id: "T6", name: "Tram T6", nameEl: "Τραμ Τ6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        .init(id: "T7", name: "Tram T7", nameEl: "Τραμ Τ7", terminalA: "Akti Poseidonos", terminalB: "Asklipiio Voulas", stationCount: 43, color: .tramOrange, type: .tram),
        .init(id: "A1", name: "A1 Piraeus-Airport", nameEl: "Α1 Πειραιάς-Αεροδρόμιο", terminalA: "Piraeus", terminalB: "Airport", stationCount: 19, color: .suburbanPurple, type: .suburban),
        .init(id: "A2", name: "A2 Ano Liosia-Airport", nameEl: "Α2 Άνω Λιόσια-Αεροδρόμιο", terminalA: "Ano Liosia", terminalB: "Airport", stationCount: 12, color: .suburbanPurple, type: .suburban),
        .init(id: "A3", name: "A3 Athens-Chalcis", nameEl: "Α3 Αθήνα-Χαλκίδα", terminalA: "Athens", terminalB: "Chalcis", stationCount: 17, color: .suburbanPurple, type: .suburban),
        .init(id: "A4", name: "A4 Piraeus-Kiato", nameEl: "Α4 Πειραιάς-Κιάτο", terminalA: "Piraeus", terminalB: "Kiato", stationCount: 20, color: .suburbanPurple, type: .suburban),
    ]

    static func line(for id: String) -> TransitLine? {
        lines.first { $0.id == id }
    }

    static func lineColor(for id: String) -> Color {
        switch id {
        case "M1": return .metroGreen
        case "M2": return .metroRed
        case "M3": return .metroBlue
        case "T6", "T7": return .tramOrange
        // National/bus/regional lines carry their own hex in lines.json, so a
        // Thessaloniki suburban or intercity triangle isn't drawn Athens-purple.
        default: return line(for: id)?.color ?? .suburbanPurple
        }
    }

    // MARK: - Stations per Line (uses StationCoords for map data)

    static func stations(for lineId: String) -> [TransitStation] {
        switch lineId {
        case "M1": return StationCoords.line1.map { makeStation($0, primaryLine: "M1") }
        case "M2": return StationCoords.line2.map { makeStation($0, primaryLine: "M2") }
        case "M3": return StationCoords.line3.map { makeStation($0, primaryLine: "M3") }
        case "T6": return StationCoords.tramT6.map { makeStation($0, primaryLine: "T6") }
        case "T7": return StationCoords.tramT7.map { makeStation($0, primaryLine: "T7") }
        case "A1": return StationCoords.suburbanA1.map { makeStation($0, primaryLine: "A1") }
        case "A2": return StationCoords.suburbanA2.map { makeStation($0, primaryLine: "A2") }
        case "A3": return StationCoords.suburbanA3.map { makeStation($0, primaryLine: "A3") }
        case "A4": return StationCoords.suburbanA4.map { makeStation($0, primaryLine: "A4") }
        default: return bundleStationsPerLine[lineId] ?? []
        }
    }

    private struct RawBundleStation {
        let id: String
        let lineId: String
        let name: String
        let nameEl: String
        let coordinate: CLLocationCoordinate2D
    }

    private static let rawBundleStations: [RawBundleStation] = loadRawBundleStations()

    private static func loadRawBundleStations() -> [RawBundleStation] {
        struct P: Decodable {
            struct L: Decodable { let id: String; let stations: [S]? }
            struct S: Decodable { let id: String; let name: String; let nameEl: String; let lat: Double; let lng: Double }
            let lines: [L]
        }
        guard let url = Bundle.main.url(
            forResource: "lines", withExtension: "json", subdirectory: "seed-schedules-v2"
        ) ?? Bundle.main.url(forResource: "lines", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(P.self, from: data)
        else { return [] }
        var out: [RawBundleStation] = []
        for line in payload.lines {
            for s in line.stations ?? [] {
                out.append(RawBundleStation(
                    id: s.id, lineId: line.id, name: s.name, nameEl: s.nameEl,
                    coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lng)
                ))
            }
        }
        return out
    }

    /// Hub membership derived ONCE for the whole network: every line with a
    /// stop within the interchange radius of a station id, unioned. Curated
    /// (StationCoords) and bundled stations share ids, so this drives station
    /// lineIds/pills AND the interchange section consistently for every region
    /// (Athens, Thessaloniki, Patras, national) with no hand-maintained table
    /// to drift.
    static let hubLineIds: [String: [String]] = {
        let pts = rawBundleStations
        var result: [String: Set<String>] = [:]
        for p in pts { result[p.id, default: []].insert(p.lineId) }
        let n = pts.count
        for i in 0..<n {
            let a = pts[i]
            for j in (i + 1)..<n {
                let b = pts[j]
                if distanceMeters(a.coordinate.latitude, a.coordinate.longitude,
                                  b.coordinate.latitude, b.coordinate.longitude) <= interchangeRadiusMeters {
                    result[a.id]?.insert(b.lineId)
                    result[b.id]?.insert(a.lineId)
                }
            }
        }
        return result.mapValues { $0.sorted() }
    }()

    private static let bundleStationsPerLine: [String: [TransitStation]] = {
        var result: [String: [TransitStation]] = [:]
        for s in rawBundleStations {
            let lineIds = hubLineIds[s.id] ?? [s.lineId]
            result[s.lineId, default: []].append(TransitStation(
                id: s.id, name: s.name, nameEl: s.nameEl,
                coordinate: s.coordinate, lineIds: lineIds, isInterchange: lineIds.count > 1
            ))
        }
        return result
    }()

    private static func makeStation(_ s: (id: String, name: String, nameEl: String, lat: Double, lon: Double), primaryLine: String) -> TransitStation {
        // Prefer the network-wide computed hub membership; fall back to the
        // curated table then the primary line. Primary line first so the
        // header and pills lead with the line being viewed.
        let computed = hubLineIds[s.id] ?? StationCoords.lineAssociations[s.id] ?? [primaryLine]
        let allLines = [primaryLine] + computed.filter { $0 != primaryLine }
        return TransitStation(
            id: s.id,
            name: s.name,
            nameEl: s.nameEl,
            coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
            lineIds: allLines,
            isInterchange: allLines.count > 1
        )
    }

    /// The other lines serving the same physical hub as `station`, each paired
    /// with the station id to open on that line, nearest hub first.
    ///
    /// Computed purely by PROXIMITY across every line (any line with a stop
    /// within ~150 m is a real transfer), not from the station's stored
    /// `lineIds`. That keeps it complete for every region, Athens, Thessaloniki,
    /// Patras and the national corridors, with no hand-maintained interchange
    /// table to drift, and it resolves correctly even when a hub's per-line ids
    /// use different suffixes (e.g. M3_AER vs A1_AIR). Used to make an
    /// interchange actionable: tap a line to see its timetable at this hub.
    static let interchangeRadiusMeters = 150.0

    @MainActor
    static func interchangeTargets(
        from station: TransitStation, currentLineId: String
    ) -> [(line: TransitLine, stationId: String)] {
        func metersToHub(_ s: TransitStation) -> Double {
            distanceMeters(
                s.coordinate.latitude, s.coordinate.longitude,
                station.coordinate.latitude, station.coordinate.longitude
            )
        }
        var targets: [(line: TransitLine, stationId: String, meters: Double)] = []
        // Only offer a transfer the rider can actually board: operational and
        // with a usable timetable. This drops suspended lines (e.g. DK1) and
        // lines with no bundled schedule (e.g. the X3/2X airport shuttles),
        // which would otherwise lead to an empty timetable.
        for line in lines where line.id != currentLineId && line.isOperational && hasSchedule(line.id) {
            guard let nearest = stations(for: line.id).min(by: { metersToHub($0) < metersToHub($1) }) else { continue }
            let d = metersToHub(nearest)
            if d <= interchangeRadiusMeters {
                targets.append((line, nearest.id, d))
            }
        }
        return targets.sorted { $0.meters < $1.meters }.map { ($0.line, $0.stationId) }
    }

    /// Whether a line has a bundled timetable (frequency bands or scheduled
    /// trips). Metro/tram use bands, suburban/intercity use trips.
    @MainActor
    static func hasSchedule(_ lineId: String) -> Bool {
        guard let bundle = SyrmosSchedulesStore.shared.service.bundles[lineId] else { return false }
        return !bundle.trips.isEmpty || !bundle.bands.isEmpty
    }

    // MARK: - Departures (with correct service patterns)

    // Line 3 airport section: stations past Douk. Plakentias
    static let line3AirportOnlyStations: Set<String> = [
        "M3_PAL", "M3_PEA", "M3_KO2", "M3_AER"
    ]

    static func sampleDepartures(for stationId: String, lineIds: [String]) -> [Departure] {
        // Anchor next departures to clock-aligned slots so the countdown
        // actually ticks down between refreshes instead of always reporting
        // "5 min / 10 min / 15 min / 20 min" from the moment of call.
        // e.g. on a 5-minute frequency at 14:31 the next departures are
        // 14:35 (4 min), 14:40 (9 min), 14:45 (14 min), 14:50 (19 min);
        // 30 seconds later they become 14:35 (3 min), and so on.
        let date = Date()
        let calendar = Calendar.current
        let nowComponents = calendar.dateComponents([.hour, .minute, .second], from: date)
        let nowMinutes = (nowComponents.hour ?? 0) * 60 + (nowComponents.minute ?? 0)
        let secondOffset = (nowComponents.second ?? 0) >= 30 ? 1 : 0
        var departures: [Departure] = []

        for lineId in lineIds {
            let patterns = servicePatterns(for: lineId, stationId: stationId)
            for pattern in patterns {
                let freq = max(pattern.frequencyMinutes, 1)
                // The next clock-aligned slot in the future for this line.
                // We treat slot t such that t % freq == 0 since midnight.
                var nextSlot = ((nowMinutes / freq) + 1) * freq
                for _ in 0..<4 {
                    let mins = nextSlot - nowMinutes - secondOffset
                    let depTime = nextSlot % (24 * 60)
                    let h = depTime / 60
                    let m = depTime % 60
                    departures.append(Departure(
                        time: String(format: "%02d:%02d", h, m),
                        lineId: pattern.lineId,
                        direction: pattern.direction,
                        minutesAway: max(mins, 0),
                        serviceType: pattern.serviceType,
                        trainNo: nil
                    ))
                    nextSlot += freq
                }
            }
        }

        return departures.sorted { $0.minutesAway < $1.minutesAway }
    }

    private static func servicePatterns(for lineId: String, stationId: String) -> [ServicePattern] {
        switch lineId {
        case "M1":
            return [
                ServicePattern(lineId: "M1", direction: "Kifisia", frequencyMinutes: 5, serviceType: "regular"),
                ServicePattern(lineId: "M1", direction: "Piraeus", frequencyMinutes: 5, serviceType: "regular"),
            ]
        case "M2":
            return [
                ServicePattern(lineId: "M2", direction: "Elliniko", frequencyMinutes: 4, serviceType: "regular"),
                ServicePattern(lineId: "M2", direction: "Anthoupoli", frequencyMinutes: 4, serviceType: "regular"),
            ]
        case "M3", "M3A":
            if line3AirportOnlyStations.contains(stationId) {
                // Past Douk. Plakentias: only airport trains, every 36 min
                return [
                    ServicePattern(lineId: "M3", direction: "Airport", frequencyMinutes: 36, serviceType: "airport"),
                    ServicePattern(lineId: "M3", direction: "Dimotiko Theatro", frequencyMinutes: 36, serviceType: "airport"),
                ]
            } else {
                // Regular service to Douk. Plakentias + airport trains
                return [
                    ServicePattern(lineId: "M3", direction: "Douk. Plakentias", frequencyMinutes: 5, serviceType: "regular"),
                    ServicePattern(lineId: "M3", direction: "Dimotiko Theatro", frequencyMinutes: 5, serviceType: "regular"),
                    ServicePattern(lineId: "M3", direction: "Airport", frequencyMinutes: 36, serviceType: "airport"),
                ]
            }
        case "T6":
            return [
                ServicePattern(lineId: "T6", direction: "Pikrodafni", frequencyMinutes: 9, serviceType: "regular"),
                ServicePattern(lineId: "T6", direction: "Syntagma", frequencyMinutes: 9, serviceType: "regular"),
            ]
        case "T7":
            return [
                ServicePattern(lineId: "T7", direction: "Asklipiio Voulas", frequencyMinutes: 12, serviceType: "regular"),
                ServicePattern(lineId: "T7", direction: "Akti Posidonos", frequencyMinutes: 12, serviceType: "regular"),
            ]
        case "A1":
            return [
                ServicePattern(lineId: "A1", direction: "Airport", frequencyMinutes: 30, serviceType: "suburban"),
                ServicePattern(lineId: "A1", direction: "Piraeus", frequencyMinutes: 30, serviceType: "suburban"),
            ]
        case "A2":
            return [
                ServicePattern(lineId: "A2", direction: "Airport", frequencyMinutes: 60, serviceType: "suburban"),
                ServicePattern(lineId: "A2", direction: "Ano Liosia", frequencyMinutes: 60, serviceType: "suburban"),
            ]
        case "A3":
            return [
                ServicePattern(lineId: "A3", direction: "Chalcis", frequencyMinutes: 90, serviceType: "regional"),
                ServicePattern(lineId: "A3", direction: "Athens", frequencyMinutes: 90, serviceType: "regional"),
            ]
        case "A4":
            return [
                ServicePattern(lineId: "A4", direction: "Kiato", frequencyMinutes: 60, serviceType: "regional"),
                ServicePattern(lineId: "A4", direction: "Piraeus", frequencyMinutes: 60, serviceType: "regional"),
            ]
        default:
            return []
        }
    }
}

extension SyrmosData {
    static var mapStations: [MapStationNode] {
        // Draw every station from the bundle (all networks), exactly like the
        // web map; fall back to the hardcoded Athens list only if the bundle is
        // unreadable.
        let source = bundleStations.isEmpty ? StationCoords.allStations : bundleStations
        let grouped = Dictionary(grouping: source.sorted {
            if $0.coordinate.latitude != $1.coordinate.latitude {
                return $0.coordinate.latitude < $1.coordinate.latitude
            }
            if $0.coordinate.longitude != $1.coordinate.longitude {
                return $0.coordinate.longitude < $1.coordinate.longitude
            }
            return $0.id < $1.id
        }, by: { $0.clusterKey })

        let nodes: [MapStationNode] = grouped.flatMap { _, group in
            group.clusterByProximity().enumerated().map { index, cluster in
                let primary = cluster.first!
                let lineIds = Array(Set(cluster.flatMap { $0.lineIds })).sorted()
                var stationIdByLineId: [String: String] = [:]

                for station in cluster {
                    for lineId in station.lineIds where stationIdByLineId[lineId] == nil {
                        stationIdByLineId[lineId] = station.id
                    }
                }

                return MapStationNode(
                    id: "\(primary.clusterKey)_\(index)_\(cluster.latitudeBucket)_\(cluster.longitudeBucket)",
                    stationIds: cluster.map { $0.id },
                    stationIdByLineId: stationIdByLineId,
                    name: primary.name,
                    nameEl: primary.nameEl,
                    coordinate: CLLocationCoordinate2D(
                        latitude: cluster.map(\.coordinate.latitude).reduce(0, +) / Double(cluster.count),
                        longitude: cluster.map(\.coordinate.longitude).reduce(0, +) / Double(cluster.count)
                    ),
                    lineIds: lineIds,
                    isInterchange: lineIds.count > 1 || cluster.contains(where: { $0.isInterchange })
                )
            }
        }
        return mergeColocatedNodes(initial: nodes)
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
    }

    /// Second-pass merge for stations that share a physical location but
    /// have different names. The primary clustering groups stations by
    /// normalized name first, then by proximity — so M3 "Dimotiko Theatro"
    /// and T7 "Dimarhio / Dimotiko Theatro" sit ~32 m apart but never
    /// compare against each other because their names differ. This pass
    /// runs a final 60 m distance sweep over the produced nodes and
    /// folds any two that are clearly the same hub. The threshold is
    /// tight enough to never collapse adjacent-but-separate stops.
    private static func mergeColocatedNodes(initial: [MapStationNode]) -> [MapStationNode] {
        let radiusMeters = 60.0
        var merged: [MapStationNode] = []
        for node in initial {
            if let idx = merged.firstIndex(where: {
                distanceMeters(
                    $0.coordinate.latitude, $0.coordinate.longitude,
                    node.coordinate.latitude, node.coordinate.longitude
                ) <= radiusMeters
            }) {
                let existing = merged[idx]
                let combinedLineIds = Array(Set(existing.lineIds + node.lineIds)).sorted()
                var combinedMap = existing.stationIdByLineId
                for (lineId, stationId) in node.stationIdByLineId where combinedMap[lineId] == nil {
                    combinedMap[lineId] = stationId
                }
                let count = 2.0
                let lat = (existing.coordinate.latitude + node.coordinate.latitude) / count
                let lon = (existing.coordinate.longitude + node.coordinate.longitude) / count
                // Pick the more descriptive name for the merged node — the
                // longer one is usually the "Dimarhio / Dimotiko Theatro"
                // style dual label rather than a single mode's shorthand.
                let pickName = node.name.count > existing.name.count ? node : existing
                merged[idx] = MapStationNode(
                    id: existing.id,
                    stationIds: existing.stationIds + node.stationIds,
                    stationIdByLineId: combinedMap,
                    name: pickName.name,
                    nameEl: pickName.nameEl,
                    coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                    lineIds: combinedLineIds,
                    isInterchange: combinedLineIds.count > 1 || existing.isInterchange || node.isInterchange
                )
            } else {
                merged.append(node)
            }
        }
        return merged
    }
}

final class LiveTrainService: ObservableObject, @unchecked Sendable {
    /// Shared instance so the whole app uses a single polling task — avoids
    /// duplicate work that was contributing to UI freezes on iOS.
    static let shared = LiveTrainService()

    @MainActor @Published var trains: [LiveTrain] = []

    @MainActor static var onLiveDataRefreshed: ((Int) -> Void)?

    private var task: Task<Void, Never>?

    private struct TrainsPayload: Decodable {
        let updatedAt: String?
        let count: Int
        let trains: [TrainItem]
    }

    private struct LiveStreamInfo: Decodable {
        let playlistUrl: String
        let streamingStatus: String?
    }

    private struct TrainItem: Decodable {
        let id: String
        let lineId: String
        let trainNumber: String
        let origin: String
        let originEn: String?
        let destination: String
        let destinationEn: String?
        let nextStation: String
        let nextStationEn: String?
        let delayMinutes: Int
        let serviceType: String
        let lat: Double
        let lng: Double
        let speed: Double?
        let course: Double?
        let altitude: Double?
        let progress: Double?
        let locomotiveNumber: String?
        let distanceToDestination: Int?
        let distanceToNextStation: Int?
        let signalStatus: String?
        let corridor: String?
        let trainType: String?
        let scheduledDeparture: String?
        let scheduledArrival: String?
        let scheduleStatus: String?
        let trainId: String?
        let liveStream: LiveStreamInfo?
        // Honest service status from the server (vehicle_status.py). Optional so
        // an older Pi that omits them still decodes; defaults treat a vehicle as
        // a normal boardable service.
        let status: String?
        let inService: Bool?
    }

    init() {
        startPolling()
    }

    deinit {
        task?.cancel()
    }

    func startPolling() {
        guard task == nil else { return }
        task = Task { [weak self] in
            while !Task.isCancelled {
                await LiveTrainService.fetchOnce(self)
                try? await Task.sleep(nanoseconds: 10_000_000_000)
            }
        }
    }

    /// Public single-shot refresh — Settings -> Check now wires this up.
    func refresh() async {
        await LiveTrainService.fetchOnce(self)
    }

    private static func fetchOnce(_ instance: LiveTrainService?) async {
        let url = URL(string: "https://api-syrmos.peterdsp.dev/api/trains")!
        do {
            var req = URLRequest(url: url)
            req.timeoutInterval = 10
            req.cachePolicy = .reloadIgnoringLocalCacheData
            let (data, response) = try await URLSession.shared.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }
            let payload = try JSONDecoder().decode(TrainsPayload.self, from: data)
            let parsed: [LiveTrain] = payload.trains.map { t in
                LiveTrain(
                    id: t.id,
                    lineId: t.lineId,
                    trainNumber: t.trainNumber,
                    origin: t.origin,
                    originEn: t.originEn ?? "",
                    destination: t.destination,
                    destinationEn: t.destinationEn ?? "",
                    nextStation: t.nextStation,
                    nextStationEn: t.nextStationEn ?? "",
                    delayMinutes: t.delayMinutes,
                    serviceType: t.serviceType,
                    coordinate: CLLocationCoordinate2D(latitude: t.lat, longitude: t.lng),
                    speed: t.speed,
                    course: t.course,
                    altitude: t.altitude,
                    progress: t.progress,
                    locomotiveNumber: t.locomotiveNumber,
                    distanceToDestination: t.distanceToDestination,
                    distanceToNextStation: t.distanceToNextStation,
                    signalStatus: t.signalStatus,
                    corridor: t.corridor,
                    trainType: t.trainType,
                    scheduledDeparture: t.scheduledDeparture,
                    scheduledArrival: t.scheduledArrival,
                    scheduleStatus: t.scheduleStatus,
                    trainId: t.trainId,
                    liveStreamUrl: t.liveStream?.playlistUrl,
                    status: t.status ?? "in_service",
                    inService: t.inService ?? true
                )
            }
            let trainCount = parsed.count
            await MainActor.run {
                instance?.trains = parsed
                LiveDataFreshness.shared.markLive()
                LiveTrainService.onLiveDataRefreshed?(trainCount)
            }
        } catch {
            // Silent — the user's previous Check now still has whatever
            // the last poll returned. Map keeps rendering bundled
            // polylines + stations regardless.
        }
    }
}

struct LiveTrain: Identifiable {
    let id: String
    let lineId: String
    let trainNumber: String
    let origin: String
    let originEn: String
    let destination: String
    let destinationEn: String
    let nextStation: String
    let nextStationEn: String
    let delayMinutes: Int
    let serviceType: String
    let coordinate: CLLocationCoordinate2D
    let speed: Double?
    let course: Double?
    let altitude: Double?
    let progress: Double?
    let locomotiveNumber: String?
    let distanceToDestination: Int?
    let distanceToNextStation: Int?
    let signalStatus: String?
    let corridor: String?
    let trainType: String?
    let scheduledDeparture: String?
    let scheduledArrival: String?
    let scheduleStatus: String?
    let trainId: String?
    let liveStreamUrl: String?
    // Honest service status: "in_service" | "position_only" | "parked_yard" |
    // "not_in_service". Parked/yard vehicles are withheld server-side, so a
    // non-boardable train that still reaches the client is "position_only".
    var status: String = "in_service"
    var inService: Bool = true
}

struct MapStationNode: Identifiable {
    let id: String
    let stationIds: [String]
    let stationIdByLineId: [String: String]
    let name: String
    let nameEl: String
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool

    var displayName: String {
        name.isEmpty ? nameEl : name
    }
}

private extension TransitStation {
    var clusterKey: String {
        [name.normalizeStationText(), nameEl.normalizeStationText()]
            .filter { !$0.isEmpty }
            .sorted()
            .joined(separator: "|")
    }

    var displayKey: String { clusterKey }
}

private extension Array where Element == TransitStation {
    func clusterByProximity(radiusMeters: Double = 300.0) -> [[TransitStation]] {
        var clusters: [[TransitStation]] = []
        for station in self {
            if let index = clusters.firstIndex(where: { cluster in
                cluster.contains(where: {
                    distanceMeters(
                        $0.coordinate.latitude,
                        $0.coordinate.longitude,
                        station.coordinate.latitude,
                        station.coordinate.longitude
                    ) <= radiusMeters
                })
            }) {
                clusters[index].append(station)
            } else {
                clusters.append([station])
            }
        }
        return clusters
    }

    var latitudeBucket: Int {
        Int((map(\.coordinate.latitude).reduce(0, +) / Double(count)) * 10000)
    }

    var longitudeBucket: Int {
        Int((map(\.coordinate.longitude).reduce(0, +) / Double(count)) * 10000)
    }
}

private extension String {
    func normalizeStationText() -> String {
        lowercased()
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: ".", with: "")
    }
}

private func distanceMeters(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    let earthRadius = 6_371_000.0
    let dLat = (lat2 - lat1) * .pi / 180
    let dLon = (lon2 - lon1) * .pi / 180
    let a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
}

private struct TrainPositionsPayload: Decodable {
    let positions: [TrainPositionPayload]
}

private struct TrainPositionPayload: Decodable {
    let id: String?
    let trainId: String?
    let name: String?
    let trainNumber: String?
    let origin: String?
    let destination: String?
    let nextStation: String?
    let delay: Int?
    let lat: Double?
    let lng: Double?
    let locomotiveNumber: String?
    let locomotiveId: String?
    let corridor: String?
}

// MARK: - Simulated Train

struct SimulatedTrain: Identifiable {
    let id: String
    let lineId: String
    let lineName: String
    let lineType: TransitType
    let direction: String
    let destinationName: String
    let currentStationName: String
    let nextStationName: String
    /// Last-known coordinate snapshot. Useful for the bottom sheet
    /// distance display. The Map view IGNORES this field and recomputes
    /// the live coordinate every frame from `originEpoch + stops` so
    /// the train moves continuously instead of jumping between
    /// simulator ticks.
    let coordinate: CLLocationCoordinate2D
    let isAirportService: Bool
    // Per-frame position inputs. Filled by TrainSimulatorService when
    // the train is built. Empty stops + 0 epoch are treated as "not
    // enough info, fall back to coordinate snapshot".
    let originEpoch: TimeInterval
    let totalTravelMinutes: Int
    /// Pre-resolved (stationId, minutesFromOrigin) for the train's
    /// direction. Already ordered by stop_sequence.
    let stops: [(stationId: String, minutesFromOrigin: Double)]
    /// Compass heading (0 = north) of travel along the current segment, used to
    /// rotate the directional triangle for suburban/national/bus vehicles.
    var bearing: Double = 0.0
}

/// Compass bearing (0 = north) from one coordinate to another.
func compassBearing(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) -> Double {
    let rad = { (d: Double) in d * .pi / 180.0 }
    let y = sin(rad(toLon - fromLon)) * cos(rad(toLat))
    let x = cos(rad(fromLat)) * sin(rad(toLat)) -
        sin(rad(fromLat)) * cos(rad(toLat)) * cos(rad(toLon - fromLon))
    return (atan2(y, x) * 180.0 / .pi + 360.0).truncatingRemainder(dividingBy: 360.0)
}

// MARK: - Train Simulator Service

final class TrainSimulatorService: ObservableObject, @unchecked Sendable {
    /// Shared instance — single timer powers both Map and any other view.
    static let shared = TrainSimulatorService()

    @MainActor @Published var trains: [SimulatedTrain] = []
    @MainActor var closedStationIds: Set<String> = []

    private var task: Task<Void, Never>?

    init() {
        task = Task.detached(priority: .utility) { @Sendable [weak self] in
            await TrainSimulatorService.runLoop(self)
        }
    }

    deinit {
        task?.cancel()
    }

    private static func runLoop(_ instance: TrainSimulatorService?) async {
        let closedIds = await MainActor.run { instance?.closedStationIds ?? [] }
        let first = await projectTrains(closedStationIds: closedIds)
        await MainActor.run { instance?.trains = first }
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if Task.isCancelled { return }
            let ids = await MainActor.run { instance?.closedStationIds ?? [] }
            let next = await projectTrains(closedStationIds: ids)
            await MainActor.run { instance?.trains = next }
        }
    }

    /// Build SimulatedTrain values by interpolating each API-reported active
    /// train along its line's station_offsets table. The map dot lands on
    /// the same minute the bottom sheet projects, because both are derived
    /// from the same projector.
    private static func projectTrains(closedStationIds: Set<String> = []) async -> [SimulatedTrain] {
        let service = await LivePositionsService.shared
        let activeTrains = await service.trains
        let offsetsByLine = await service.offsets

        let stationCoords = StationCoordinateLookup.shared
        let nowEpoch = Date().timeIntervalSince1970

        var result: [SimulatedTrain] = []

        // Metro / tram / A1-A4: interpolated from the live feed + offsets. The
        // national/bus projection below is independent of this, so an empty
        // live feed must not skip it.
        if !activeTrains.isEmpty && !offsetsByLine.isEmpty {
        let lineMeta: [String: (name: String, type: TransitType, terminalA: String, terminalB: String)] = [
            "M1":     ("Line 1",      .metro, "Piraeus",          "Kifissia"),
            "M2":     ("Line 2",      .metro, "Anthoupoli",       "Elliniko"),
            "M3":     ("Line 3",      .metro, "Dimotiko Theatro", "Doukissis Plakentias"),
            "M3_AIR": ("Line 3",      .metro, "Doukissis Plakentias", "Airport"),
            "T6":     ("Tram T6",     .tram,  "Syntagma",         "Pikrodafni"),
            "T7":     ("Tram T7",     .tram,  "Akti Poseidonos",  "Asklipiio Voulas"),
        ]

        for train in activeTrains {
            guard let meta = lineMeta[train.lineId] else { continue }
            // station_offsets keys M3_AIR under M3; the projector uses that
            // remap internally so do the same here.
            let offsetsLineKey = (train.lineId == "M3_AIR") ? "M3" : train.lineId
            guard let stops = offsetsByLine[offsetsLineKey]?[train.directionKey], stops.count >= 2 else { continue }

            let elapsed = (nowEpoch - train.originDepartureEpoch) / 60.0
            if elapsed < 0 || elapsed > Double(train.totalTravelMinutes) + 0.5 { continue }

            var segIdx = 0
            for i in 0..<(stops.count - 1) {
                if stops[i].minutesFromOrigin <= elapsed && elapsed < stops[i + 1].minutesFromOrigin {
                    segIdx = i
                    break
                }
                if i == stops.count - 2 { segIdx = i }
            }

            let from = stops[segIdx]
            let to = stops[segIdx + 1]
            if closedStationIds.contains(from.stationId) || closedStationIds.contains(to.stationId) { continue }
            guard let fromCoord = stationCoords.coordinate(for: from.stationId),
                  let toCoord = stationCoords.coordinate(for: to.stationId) else { continue }

            let segDuration = to.minutesFromOrigin - from.minutesFromOrigin
            let frac = segDuration > 0 ? min(max((elapsed - from.minutesFromOrigin) / segDuration, 0), 1) : 0

            // Walk along the OSM-derived polyline arc between the two
            // stations instead of straight-lerping their coordinates.
            // The straight lerp cuts the chord of every track curve,
            // which is exactly why trains were drawn alongside the
            // blue M3 line rather than on it. The polyline already
            // hugs the real track so a polyline-relative interpolation
            // keeps the moving icon on the rails.
            let polylineLineId = train.lineId == "M3_AIR" ? "M3_AIR" : train.lineId
            let polyline = SyrmosRouteShapesStore.shared.coordinates(for: polylineLineId)
                ?? SyrmosRouteShapesStore.shared.coordinates(for: offsetsLineKey)
            let arcPosition = pointOnPolylineArc(
                polyline: polyline,
                from: CLLocationCoordinate2D(latitude: fromCoord.lat, longitude: fromCoord.lon),
                to: CLLocationCoordinate2D(latitude: toCoord.lat, longitude: toCoord.lon),
                fraction: frac
            )
            let lat = arcPosition.latitude
            let lon = arcPosition.longitude

            let destination = train.directionKey == "outbound" ? meta.terminalB : meta.terminalA
            let displayLineId = train.lineId == "M3_AIR" ? "M3" : train.lineId
            let fromName = stationCoords.englishName(for: from.stationId) ?? from.stationId
            let toName = stationCoords.englishName(for: to.stationId) ?? to.stationId
            let isAirport = train.lineId == "M3_AIR"

            let stopTuples = stops.map { (stationId: $0.stationId, minutesFromOrigin: $0.minutesFromOrigin) }
            result.append(SimulatedTrain(
                id: train.id,
                lineId: displayLineId,
                lineName: meta.name,
                lineType: meta.type,
                direction: train.directionKey,
                destinationName: destination,
                currentStationName: fromName,
                nextStationName: toName,
                coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                isAirportService: isAirport,
                originEpoch: train.originDepartureEpoch,
                totalTravelMinutes: train.totalTravelMinutes,
                stops: stopTuples,
                bearing: compassBearing(
                    fromLat: fromCoord.lat, fromLon: fromCoord.lon,
                    toLat: toCoord.lat, toLon: toCoord.lon
                )
            ))
        }
        } // end live-feed projection

        // National rail + Thessaloniki/Patras suburban + rail-replacement buses:
        // no live feed and no offsets on the Pi, so these vehicles are projected
        // offline from the bundled trip timetables, mirroring the web + Android
        // projectors. Rendered as directional triangles by the same triangle
        // path metro/tram-free lines take in the map.
        result.append(contentsOf: NationalVehicleProjector.shared.project(nowEpoch: nowEpoch))

        return result
    }

    /// Interpolate a point along the polyline arc between two station
    /// anchors. Finds the closest polyline vertex to each anchor,
    /// measures cumulative haversine distance along the polyline
    /// between those vertices, then walks `fraction` of that arc
    /// distance from the `from` end. Falls back to a straight chord
    /// lerp when the polyline is empty or both anchors snap to the
    /// same vertex (very short inter-station spacing).
    fileprivate static func pointOnPolylineArc(
        polyline: [CLLocationCoordinate2D]?,
        from: CLLocationCoordinate2D,
        to: CLLocationCoordinate2D,
        fraction: Double
    ) -> CLLocationCoordinate2D {
        let f = min(max(fraction, 0), 1)
        guard let line = polyline, line.count >= 2 else {
            return CLLocationCoordinate2D(
                latitude: from.latitude + (to.latitude - from.latitude) * f,
                longitude: from.longitude + (to.longitude - from.longitude) * f
            )
        }
        let fromIdx = closestPolylineIndex(line, to: from)
        let toIdx = closestPolylineIndex(line, to: to)
        if fromIdx == toIdx {
            return CLLocationCoordinate2D(
                latitude: from.latitude + (to.latitude - from.latitude) * f,
                longitude: from.longitude + (to.longitude - from.longitude) * f
            )
        }
        let reversed = fromIdx > toIdx
        let start = min(fromIdx, toIdx)
        let end = max(fromIdx, toIdx)
        var cumulative: [Double] = [0]
        cumulative.reserveCapacity(end - start + 1)
        for i in start..<end {
            cumulative.append(cumulative.last! + haversineMeters(line[i], line[i + 1]))
        }
        guard let total = cumulative.last, total > 0 else { return line[start] }
        let target = total * (reversed ? (1 - f) : f)
        for i in 0..<(cumulative.count - 1) {
            if cumulative[i + 1] >= target {
                let segLen = cumulative[i + 1] - cumulative[i]
                let segFrac = segLen > 0 ? (target - cumulative[i]) / segLen : 0
                let a = line[start + i]
                let b = line[start + i + 1]
                return CLLocationCoordinate2D(
                    latitude: a.latitude + (b.latitude - a.latitude) * segFrac,
                    longitude: a.longitude + (b.longitude - a.longitude) * segFrac
                )
            }
        }
        return line[end]
    }

    private static func closestPolylineIndex(_ line: [CLLocationCoordinate2D], to point: CLLocationCoordinate2D) -> Int {
        var bestIdx = 0
        var bestDist = Double.greatestFiniteMagnitude
        for (i, p) in line.enumerated() {
            let d = haversineMeters(p, point)
            if d < bestDist { bestDist = d; bestIdx = i }
        }
        return bestIdx
    }

    private static func haversineMeters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        let r = 6371000.0
        let dLat = (b.latitude - a.latitude) * .pi / 180
        let dLon = (b.longitude - a.longitude) * .pi / 180
        let lat1 = a.latitude * .pi / 180
        let lat2 = b.latitude * .pi / 180
        let h = sin(dLat / 2) * sin(dLat / 2)
            + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1, sqrt(h)))
    }
}
