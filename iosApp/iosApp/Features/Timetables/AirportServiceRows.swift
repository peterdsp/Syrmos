import SwiftUI

/// One row in the airport "Airport services" list. `stationId` is the airport
/// side station a metro/suburban row opens; buses have none. `confidence` drives
/// how honestly the time reads: live ETAs vs a neutral 24/7 badge.
struct AirportListRow: Identifiable {
    let id = UUID()
    let route: String
    let destination: String
    let detail: String
    /// The next few departure times for this (line, destination). The first is
    /// the dominant countdown; the rest render as a "Then …" tail, so one grouped
    /// row replaces the old stack of same-line rows.
    let times: [String]
    let color: Color
    var stationId: String? = nil
    var confidence: SourceConfidence = .scheduled
}

/// Airport-side station a metro/suburban row opens in StationDetailView. Buses
/// (X..) have no per-stop timetable, so nil keeps their row non-navigable.
func airportSideStationId(forRoute route: String) -> String? {
    switch route {
    case "M3", "M3_AIR": return "M3_AER"
    case "A1": return "A1_AIR"
    case "A2": return "A2_AIR"
    default: return nil
    }
}

/// Pure builder for the airport-departures list. Kept free of SwiftUI plumbing
/// and network so ordering, de-duplication, labeling and the live/fallback bus
/// treatment are all unit-testable (see AirportServiceRowsTests).
///
/// Three defects this fixes versus the old inline `rows`:
///   1. M3 and M3_AIR project the same physical train, so the raw list held two
///      identical "M3 Syntagma 06:39" rows. We de-dup metro by departure time.
///   2. The old code took the first 3 departures regardless of mode and always
///      labeled them "M3 / Syntagma / metro departure", so an A1 slot showed up
///      as a metro to Syntagma. We now section by real mode.
///   3. Buses showed a passive "24/7 - check OASA" even though the Pi tracks
///      their live ETA to the airport. We surface that ETA as `.live` when present.
enum AirportServiceRows {
    static func build(
        metroDepartures: [Departure],
        buses: AirportBusService.LiveAirportBuses?,
        language: AppLanguage
    ) -> [AirportListRow] {
        var output: [AirportListRow] = []

        // Metro (M3 / M3_AIR) -> Syntagma. De-dup by time, next 3, collapsed into
        // ONE grouped row (was three identical "M3 · Syntagma · Scheduled" rows
        // differing only by the clock time). Mirrors the grouped station board.
        var metroTimes: [String] = []
        var seenMetro = Set<String>()
        for dep in metroDepartures where dep.lineId == "M3" || dep.lineId == "M3_AIR" {
            guard seenMetro.insert(dep.time).inserted else { continue }
            metroTimes.append(dep.time)
            if metroTimes.count >= 3 { break }
        }
        if !metroTimes.isEmpty {
            output.append(AirportListRow(
                route: "M3",
                destination: "Syntagma",
                detail: t(language,
                          "Scheduled metro departure",
                          "Προγραμματισμένη αναχώρηση μετρό",
                          "Nisje e programuar e metrosë",
                          "Partenza metro programmata"),
                times: metroTimes,
                color: Color.metroBlue,
                stationId: airportSideStationId(forRoute: "M3"),
                confidence: .scheduled
            ))
        }

        // Suburban (A1 / A2) -> Piraeus. Group by the REAL line (never relabel to
        // A1), de-dup by time, next 2 each -> one grouped row per line.
        var suburbanTimes: [String: [String]] = [:]
        var suburbanOrder: [String] = []
        var seenSuburban = Set<String>()
        for dep in metroDepartures where dep.lineId == "A1" || dep.lineId == "A2" {
            guard seenSuburban.insert(dep.lineId + dep.time).inserted else { continue }
            if suburbanTimes[dep.lineId] == nil {
                suburbanTimes[dep.lineId] = []
                suburbanOrder.append(dep.lineId)
            }
            if suburbanTimes[dep.lineId]!.count < 2 {
                suburbanTimes[dep.lineId]!.append(dep.time)
            }
        }
        for line in suburbanOrder {
            output.append(AirportListRow(
                route: line,
                destination: t(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo"),
                detail: t(language,
                          "Scheduled suburban departure",
                          "Προγραμματισμένη αναχώρηση προαστιακού",
                          "Nisje e programuar e trenit periferik",
                          "Partenza suburbano programmata"),
                times: suburbanTimes[line] ?? [],
                color: SyrmosTokens.suburban,
                stationId: airportSideStationId(forRoute: line),
                confidence: .scheduled
            ))
        }

        // Express buses. Live ETA when the Pi is tracking one, else a neutral 24/7.
        for bus in busLines(language) {
            if let mins = buses?.soonest(bus.line) {
                output.append(AirportListRow(
                    route: bus.line,
                    destination: bus.destination,
                    detail: t(language,
                              "Live arrival at the airport stop",
                              "Ζωντανή άφιξη στη στάση του αεροδρομίου",
                              "Mbërritje e drejtpërdrejtë në stacionin e aeroportit",
                              "Arrivo in tempo reale alla fermata dell'aeroporto"),
                    times: [etaLabel(minutes: mins, language: language)],
                    color: SyrmosTokens.warning,
                    stationId: nil,
                    confidence: .live
                ))
            } else {
                output.append(AirportListRow(
                    route: bus.line,
                    destination: bus.destination,
                    detail: t(language,
                              "24-hour express bus",
                              "24ωρο λεωφορείο express",
                              "Autobus express 24 orë",
                              "Bus express 24 ore"),
                    times: ["24/7"],
                    color: SyrmosTokens.warning,
                    stationId: nil,
                    confidence: .operatorLink
                ))
            }
        }
        return output
    }

    /// Localized minutes badge for a live bus ETA. "Now" when it is at the stop.
    static func etaLabel(minutes: Int, language: AppLanguage) -> String {
        if minutes <= 1 {
            switch language {
            case .greek: return "Τώρα"
            case .albanian: return "Tani"
            case .italian: return "Ora"
            default: return "Now"
            }
        }
        let minAbbr: String
        switch language {
        case .greek: minAbbr = "λεπ"
        default: minAbbr = "min"
        }
        if minutes < 60 { return "\(minutes) \(minAbbr)" }
        let h = minutes / 60
        let m = minutes % 60
        let hAbbr = language == .greek ? "ω" : "h"
        return m == 0 ? "\(h)\(hAbbr)" : "\(h)\(hAbbr) \(m) \(minAbbr)"
    }

    private struct BusLine { let line: String; let destination: String }

    private static func busLines(_ language: AppLanguage) -> [BusLine] {
        [
            BusLine(line: "X95", destination: "Syntagma"),
            BusLine(line: "X93", destination: "Kifisos"),
            BusLine(line: "X96", destination: t(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo")),
            BusLine(line: "X97", destination: t(language, "Elliniko", "Ελληνικό", "Elliniko", "Elliniko")),
        ]
    }

    private static func t(_ language: AppLanguage, _ en: String, _ el: String, _ al: String, _ it: String) -> String {
        switch language {
        case .greek: return el
        case .albanian: return al
        case .italian: return it
        default: return en
        }
    }
}
