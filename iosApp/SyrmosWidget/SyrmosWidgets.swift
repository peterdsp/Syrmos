import WidgetKit
import SwiftUI
import AppIntents
import CoreLocation
import os

// Home-screen widget families for Syrmos (1.2 "Widgets Everywhere"). Every
// family projects fully offline from the bundled seed schedules via
// ScheduleProjector, exactly like the in-app station sheet, and shares one
// configuration intent (primary station + primary line; nearest-mode overrides
// both). Visual language: the shared DesignSystem/WidgetKit components
// (LinePill, StationStrip*, DepartureRowCompact, LiquidGlassTile, SyrmosLineTokens).
//
// Families:
//   Next Train        systemSmall + systemMedium
//   Live Departures   systemLarge
//   Near Me           systemLarge
//   All Lines Status  systemExtraLarge (iPad)
//   Weather + Alerts  systemExtraLarge (iPad)
//   Trio              systemMedium (iPad-friendly: next train + alerts + near me)

@available(iOS 17.0, *)
private let widgetLog = Logger(subsystem: "com.syrmos.widget", category: "families")

/// Widget localization. Reads the app's selected language from the shared App
/// Group (the app mirrors it there via WidgetBridge) and picks EN / EL / SQ, so
/// the widgets match the in-app language rather than the device language.
enum WLoc {
    static var lang: String {
        UserDefaults(suiteName: "group.com.syrmosApp.ios")?.string(forKey: "app_language") ?? "en"
    }
    /// EN, EL, SQ in that order.
    static func t(_ en: String, _ el: String, _ sq: String) -> String {
        switch lang {
        case "el": return el
        case "sq": return sq
        default: return en
        }
    }
}

// MARK: - Configuration intent

@available(iOS 17.0, *)
struct StationEntity: AppEntity, Identifiable {
    let id: String
    let name: String
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Station")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
    static var defaultQuery = StationQuery()
}

@available(iOS 17.0, *)
struct StationQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [StationEntity] {
        StationCoords.allStations
            .filter { identifiers.contains($0.id) }
            .map { StationEntity(id: $0.id, name: $0.name) }
    }
    func suggestedEntities() async throws -> [StationEntity] {
        StationCoords.allStations
            .sorted { $0.name < $1.name }
            .map { StationEntity(id: $0.id, name: $0.name) }
    }
}

@available(iOS 17.0, *)
struct LineEntity: AppEntity, Identifiable {
    let id: String
    static var typeDisplayRepresentation = TypeDisplayRepresentation(name: "Line")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(id)") }
    static var defaultQuery = LineQuery()
}

@available(iOS 17.0, *)
struct LineQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [LineEntity] {
        SyrmosLineTokens.allLines.filter { identifiers.contains($0) }.map { LineEntity(id: $0) }
    }
    func suggestedEntities() async throws -> [LineEntity] {
        SyrmosLineTokens.allLines.map { LineEntity(id: $0) }
    }
}

@available(iOS 17.0, *)
struct SyrmosWidgetConfigurationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Syrmos"
    static var description = IntentDescription("Pick your primary station and line, or use the nearest station automatically.")

    @Parameter(title: "Use nearest station", default: true)
    var useNearestStation: Bool

    @Parameter(title: "Primary station")
    var station: StationEntity?

    @Parameter(title: "Primary line")
    var line: LineEntity?
}

// MARK: - One-shot widget location (2s hard timeout)

@available(iOS 17.0, *)
final class WidgetLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation?, Never>?

    func current() async -> CLLocation? {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return nil }
        return await withTaskGroup(of: CLLocation?.self) { group in
            group.addTask { await self.requestOnce() }
            group.addTask { try? await Task.sleep(for: .seconds(2)); return nil }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private func requestOnce() async -> CLLocation? {
        await withCheckedContinuation { c in
            continuation = c
            manager.delegate = self
            manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            manager.requestLocation()
        }
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        continuation?.resume(returning: locs.last); continuation = nil
    }
    func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        continuation?.resume(returning: nil); continuation = nil
    }
}

// MARK: - Entry model

@available(iOS 17.0, *)
struct WRow: Identifiable {
    let id = UUID()
    let lineId: String
    let destination: String
    let minutesAway: Int
    let time: String
    let isAirport: Bool
}

@available(iOS 17.0, *)
struct WNearby: Identifiable {
    let id: String
    let name: String
    let lineIds: [String]
    let walkMinutes: Int
}

@available(iOS 17.0, *)
struct WLineStatus: Identifiable {
    var id: String { lineId }
    let lineId: String
    let ok: Bool
    let label: String
}

@available(iOS 17.0, *)
struct WWeather {
    let temperature: Int
    let symbol: String
    let high: Int
    let low: Int
}

@available(iOS 17.0, *)
struct SyrmosEntry: TimelineEntry {
    let date: Date
    let stationName: String
    let rows: [WRow]
    let routeStops: [String]
    let lastTrain: String?
    let nearby: [WNearby]
    let statuses: [WLineStatus]
    let weather: WWeather?
    let alerts: [String]
}

// MARK: - Provider

@available(iOS 17.0, *)
struct SyrmosProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> SyrmosEntry { Self.sample }

    func snapshot(for configuration: SyrmosWidgetConfigurationIntent, in context: Context) async -> SyrmosEntry {
        await entry(for: configuration)
    }

    func timeline(for configuration: SyrmosWidgetConfigurationIntent, in context: Context) async -> Timeline<SyrmosEntry> {
        let entry = await entry(for: configuration)
        let next = Calendar.current.date(byAdding: .minute, value: 5, to: .now) ?? .now.addingTimeInterval(300)
        return Timeline(entries: [entry], policy: .after(next))
    }

    private func entry(for configuration: SyrmosWidgetConfigurationIntent) async -> SyrmosEntry {
        await MainActor.run { _ = SyrmosSchedulesStore.shared }
        let location = configuration.useNearestStation ? await WidgetLocation().current() : nil
        let station = await resolveStation(configuration, location: location)
        let nearby = nearbyStations(from: location)

        guard let station else {
            widgetLog.error("no station resolved")
            return SyrmosEntry(date: .now, stationName: "—", rows: [], routeStops: [],
                               lastTrain: nil, nearby: nearby, statuses: lineStatuses(),
                               weather: cachedWeather(), alerts: cachedAlerts())
        }
        let rows = await MainActor.run { departureRows(for: station) }
        let last = await MainActor.run { lastTrainString(for: station) }
        let stops = routeStops(for: station)
        return SyrmosEntry(date: .now, stationName: station.name, rows: rows, routeStops: stops,
                           lastTrain: last, nearby: nearby, statuses: lineStatuses(),
                           weather: cachedWeather(), alerts: cachedAlerts())
    }

    private func resolveStation(_ configuration: SyrmosWidgetConfigurationIntent, location: CLLocation?) async -> TransitStation? {
        if configuration.useNearestStation, let loc = location, let nearest = nearest(to: loc) {
            return nearest
        }
        if let id = configuration.station?.id,
           let picked = StationCoords.allStations.first(where: { $0.id == id }) {
            return picked
        }
        if let loc = location { return nearest(to: loc) }
        return await WidgetLocation().current().flatMap { nearest(to: $0) }
    }

    private func nearest(to loc: CLLocation) -> TransitStation? {
        StationCoords.allStations.min { a, b in
            CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude).distance(from: loc)
                < CLLocation(latitude: b.coordinate.latitude, longitude: b.coordinate.longitude).distance(from: loc)
        }
    }

    private func nearbyStations(from loc: CLLocation?) -> [WNearby] {
        guard let loc else { return [] }
        return StationCoords.allStations
            .map { s -> (TransitStation, Double) in
                let d = CLLocation(latitude: s.coordinate.latitude, longitude: s.coordinate.longitude).distance(from: loc)
                return (s, d)
            }
            .sorted { $0.1 < $1.1 }
            .prefix(3)
            .map { pair in
                // ~80 m/min average walking pace.
                WNearby(id: pair.0.id, name: pair.0.name, lineIds: pair.0.lineIds,
                        walkMinutes: max(1, Int((pair.1 / 80.0).rounded())))
            }
    }

    @MainActor
    private func departureRows(for station: TransitStation, limit: Int = 5) -> [WRow] {
        var deps = ScheduleProjector.nextDepartures(
            for: station.id, lineIds: station.lineIds, limit: 20, timeHorizonMinutes: 6 * 60
        )
        if deps.isEmpty && SyrmosSchedulesStore.shared.service.bundles.isEmpty {
            deps = SyrmosData.sampleDepartures(for: station.id, lineIds: station.lineIds)
        }
        let isAirport: (Departure) -> Bool = { $0.serviceType == "airport" || $0.lineId == "M3_AIR" }
        if station.lineIds.contains("M3") {
            var out = deps.filter { !isAirport($0) }.prefix(limit - 1).map { dep in
                WRow(lineId: SyrmosLineTokens.label(for: dep.lineId), destination: dep.direction,
                     minutesAway: dep.minutesAway, time: dep.time, isAirport: false)
            }
            if let air = deps.first(where: isAirport) {
                out.append(WRow(lineId: "M3", destination: air.direction,
                                minutesAway: air.minutesAway, time: air.time, isAirport: true))
            }
            return Array(out)
        }
        return deps.prefix(limit).map { dep in
            WRow(lineId: SyrmosLineTokens.label(for: dep.lineId), destination: dep.direction,
                 minutesAway: dep.minutesAway, time: dep.time, isAirport: false)
        }
    }

    @MainActor
    private func lastTrainString(for station: TransitStation) -> String? {
        guard let line = station.lineIds.first,
              let last = ScheduleProjector.lastTrainTonight(for: station.id, lineIds: [line]) else { return nil }
        return last.time
    }

    private func routeStops(for station: TransitStation) -> [String] {
        guard let line = station.lineIds.first else { return [] }
        let names = SyrmosData.stations(for: line).map { $0.name }
        guard let idx = names.firstIndex(of: station.name) else { return Array(names.prefix(5)) }
        // A short window centered on the current station, max 5 dots (no scroll).
        let start = max(0, idx - 1)
        let end = min(names.count, start + 5)
        return Array(names[start..<end])
    }

    private func lineStatuses() -> [WLineStatus] {
        // Offline-optimistic: without a live alert feed the honest default is
        // "Good Service". The layout is ready for a real feed via App Group.
        SyrmosLineTokens.allLines.map { WLineStatus(lineId: $0, ok: true, label: "Good Service") }
    }

    /// Cached weather from the shared App Group, written by the app's
    /// WeatherStore; returns nil (graceful offline state) when absent.
    private func cachedWeather() -> WWeather? {
        guard let d = UserDefaults(suiteName: "group.com.syrmosApp.ios"),
              d.object(forKey: "weather.temp") != nil else { return nil }
        return WWeather(
            temperature: d.integer(forKey: "weather.temp"),
            symbol: d.string(forKey: "weather.symbol") ?? "cloud.fill",
            high: d.integer(forKey: "weather.high"),
            low: d.integer(forKey: "weather.low")
        )
    }

    /// Cached service alerts from the shared App Group, written by the app's
    /// STASYService; empty when there are none or nothing has synced yet.
    private func cachedAlerts() -> [String] {
        UserDefaults(suiteName: "group.com.syrmosApp.ios")?.stringArray(forKey: "alerts") ?? []
    }

    static let sample = SyrmosEntry(
        date: .now, stationName: "Syntagma",
        rows: [
            WRow(lineId: "M3", destination: "Doukissis Plakentias", minutesAway: 3, time: "17:47", isAirport: false),
            WRow(lineId: "M3", destination: "Dimotiko Theatro", minutesAway: 6, time: "17:50", isAirport: false),
            WRow(lineId: "M3", destination: "Airport", minutesAway: 11, time: "17:55", isAirport: true),
            WRow(lineId: "M2", destination: "Anthoupoli", minutesAway: 4, time: "17:48", isAirport: false),
            WRow(lineId: "M2", destination: "Elliniko", minutesAway: 8, time: "17:52", isAirport: false),
        ],
        routeStops: ["Monastiraki", "Syntagma", "Evangelismos", "Megaro Moussikis", "Ambelokipi"],
        lastTrain: "01:23",
        nearby: [
            WNearby(id: "syntagma", name: "Syntagma", lineIds: ["M2", "M3"], walkMinutes: 2),
            WNearby(id: "panepistimio", name: "Panepistimio", lineIds: ["M2"], walkMinutes: 6),
            WNearby(id: "monastiraki", name: "Monastiraki", lineIds: ["M1", "M3"], walkMinutes: 8),
        ],
        statuses: SyrmosLineTokens.allLines.map { WLineStatus(lineId: $0, ok: true, label: "Good Service") },
        weather: WWeather(temperature: 27, symbol: "sun.max.fill", high: 31, low: 22),
        alerts: []
    )
}

// MARK: - Family views

@available(iOS 17.0, *)
struct NextTrainView: View {
    var entry: SyrmosEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        let lead = entry.rows.first
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                if let lead {
                    LinePill(lineId: lead.lineId, size: family == .systemSmall ? .regular : .large)
                    Text(lead.minutesAway <= 1 ? WLoc.t("now", "τώρα", "tani") : "\(lead.minutesAway) " + WLoc.t("min", "λεπτά", "min"))
                        .font(.system(size: family == .systemSmall ? 30 : 40, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(SyrmosLineTokens.color(for: lead.lineId))
                        .widgetAccentable()
                    Text(lead.destination)
                        .font(.caption).fontWeight(.semibold).lineLimit(1)
                    Text(entry.stationName)
                        .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                } else {
                    Text(entry.stationName).font(.headline).lineLimit(1)
                    Text(WLoc.t("No upcoming departures", "Καμία επόμενη αναχώρηση", "Asnjë nisje e ardhshme")).font(.caption).foregroundStyle(.secondary)
                }
                if family == .systemSmall, !entry.routeStops.isEmpty {
                    StationStripCompact(stops: entry.routeStops, tint: SyrmosLineTokens.color(for: lead?.lineId ?? "M3"), showLabels: false)
                        .frame(height: 16)
                }
            }
            if family == .systemMedium {
                VStack(alignment: .trailing, spacing: 8) {
                    if !entry.routeStops.isEmpty {
                        StationStripFull(stops: Array(entry.routeStops.prefix(4)),
                                         currentIndex: 1,
                                         tint: SyrmosLineTokens.color(for: lead?.lineId ?? "M3"))
                    }
                    Spacer(minLength: 0)
                    if let last = entry.lastTrain {
                        Text(WLoc.t("Last train", "Τελευταίο τρένο", "Treni i fundit") + " \(last) 🌙")
                            .font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .padding(family == .systemSmall ? 12 : 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 17.0, *)
struct LiveDeparturesView: View {
    var entry: SyrmosEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(entry.stationName).font(.headline).lineLimit(1)
                Spacer()
                Image(systemName: "tram.fill").font(.caption).foregroundStyle(.secondary)
            }
            if entry.rows.isEmpty {
                Spacer(); Text("No upcoming departures").font(.caption).foregroundStyle(.secondary); Spacer()
            } else {
                ForEach(entry.rows.prefix(5)) { row in
                    DepartureRowCompact(lineId: row.lineId, destination: row.destination,
                                        minutesAway: row.minutesAway, isAirport: row.isAirport)
                }
                Spacer(minLength: 0)
                if !entry.nearby.isEmpty {
                    Divider()
                    HStack(spacing: 4) {
                        Image(systemName: "location.fill").font(.system(size: 9)).foregroundStyle(.secondary)
                        Text(WLoc.t("Near me", "Κοντά μου", "Pranë meje") + ": " + entry.nearby.prefix(2).map { "\($0.name) \($0.walkMinutes)m" }.joined(separator: " · "))
                            .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 17.0, *)
struct NearMeView: View {
    var entry: SyrmosEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "location.fill").font(.caption).foregroundStyle(.secondary)
                Text(WLoc.t("Near me", "Κοντά μου", "Pranë meje")).font(.headline)
                Spacer()
            }
            if entry.nearby.isEmpty {
                Spacer()
                Text(WLoc.t("Enable location to see the nearest stations.",
                            "Ενεργοποιήστε την τοποθεσία για να δείτε τους πλησιέστερους σταθμούς.",
                            "Aktivizo vendndodhjen për të parë stacionet më të afërta."))
                    .font(.caption).foregroundStyle(.secondary)
                Spacer()
            } else {
                ForEach(entry.nearby) { s in
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(s.name).font(.subheadline).fontWeight(.semibold).lineLimit(1)
                            HStack(spacing: 4) {
                                ForEach(s.lineIds.prefix(3), id: \.self) { LinePill(lineId: $0, size: .small) }
                            }
                        }
                        Spacer(minLength: 0)
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("\(s.walkMinutes) " + WLoc.t("min", "λεπτά", "min")).font(.subheadline).fontWeight(.semibold).monospacedDigit()
                            Text(WLoc.t("walk", "με τα πόδια", "në këmbë")).font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 17.0, *)
struct AllLinesStatusView: View {
    var entry: SyrmosEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(WLoc.t("Network status", "Κατάσταση δικτύου", "Statusi i rrjetit")).font(.headline)
            let cols = [GridItem(.adaptive(minimum: 150), spacing: 10)]
            LazyVGrid(columns: cols, alignment: .leading, spacing: 8) {
                ForEach(entry.statuses) { st in
                    HStack(spacing: 8) {
                        LinePill(lineId: st.lineId, size: .regular)
                        Text(st.ok ? WLoc.t("Good Service", "Κανονική λειτουργία", "Shërbim normal") : st.label)
                            .font(.caption).foregroundStyle(st.ok ? .secondary : .primary).lineLimit(1)
                        Spacer(minLength: 0)
                        Image(systemName: st.ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(st.ok ? .green : .orange)
                    }
                    .padding(8)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 17.0, *)
struct WeatherAlertsView: View {
    var entry: SyrmosEntry
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // Weather column.
            LiquidGlassTile(accent: .blue) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(WLoc.t("Athens", "Αθήνα", "Athinë")).font(.caption).foregroundStyle(.secondary)
                    if let w = entry.weather {
                        Image(systemName: w.symbol).font(.title2).foregroundStyle(.yellow)
                        Text("\(w.temperature)°").font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("H:\(w.high)°  L:\(w.low)°").font(.caption2).foregroundStyle(.secondary)
                    } else {
                        Image(systemName: "cloud.fill").font(.title2).foregroundStyle(.secondary)
                        Text(WLoc.t("Weather offline", "Καιρός εκτός σύνδεσης", "Moti jashtë linje")).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                }
                .padding(12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            .frame(width: 150)

            // Alerts column.
            VStack(alignment: .leading, spacing: 6) {
                Text(WLoc.t("Service alerts", "Ειδοποιήσεις", "Njoftime shërbimi")).font(.caption).fontWeight(.semibold)
                if entry.alerts.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                        Text(WLoc.t("No active alerts", "Καμία ενεργή ειδοποίηση", "Asnjë njoftim aktiv")).font(.caption).foregroundStyle(.secondary)
                    }
                } else {
                    ForEach(entry.alerts.prefix(4), id: \.self) { a in
                        Text("• \(a)").font(.caption).lineLimit(2)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)

            // Next-train column.
            VStack(alignment: .leading, spacing: 6) {
                Text(entry.stationName).font(.caption).fontWeight(.semibold).lineLimit(1)
                ForEach(entry.rows.prefix(3)) { row in
                    DepartureRowCompact(lineId: row.lineId, destination: row.destination,
                                        minutesAway: row.minutesAway, isAirport: row.isAirport)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

@available(iOS 17.0, *)
struct TrioView: View {
    var entry: SyrmosEntry
    var body: some View {
        HStack(spacing: 10) {
            // Next train.
            VStack(alignment: .leading, spacing: 4) {
                if let lead = entry.rows.first {
                    LinePill(lineId: lead.lineId, size: .regular)
                    Text(lead.minutesAway <= 1 ? WLoc.t("now", "τώρα", "tani") : "\(lead.minutesAway)m")
                        .font(.system(size: 24, weight: .bold, design: .rounded)).monospacedDigit()
                        .foregroundStyle(SyrmosLineTokens.color(for: lead.lineId))
                    Text(entry.stationName).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            Divider()
            // Alerts.
            VStack(alignment: .leading, spacing: 4) {
                Text(WLoc.t("Alerts", "Ειδοποιήσεις", "Njoftime")).font(.caption2).fontWeight(.semibold).foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.seal.fill").font(.caption2).foregroundStyle(.green)
                    Text(WLoc.t("All clear", "Όλα καλά", "Gjithçka në rregull")).font(.caption2)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            Divider()
            // Near me.
            VStack(alignment: .leading, spacing: 4) {
                Text(WLoc.t("Near me", "Κοντά μου", "Pranë meje")).font(.caption2).fontWeight(.semibold).foregroundStyle(.secondary)
                ForEach(entry.nearby.prefix(2)) { s in
                    Text("\(s.name) · \(s.walkMinutes)m").font(.caption2).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - Widgets

@available(iOS 17.0, *)
struct NextTrainWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosNextTrain", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            NextTrainView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: entry.rows.first?.lineId ?? "M3"))
        }
        .configurationDisplayName("Next Train")
        .description("The next train from your station, or the nearest one.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@available(iOS 17.0, *)
struct LiveDeparturesWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosLiveDepartures", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            LiveDeparturesView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: entry.rows.first?.lineId ?? "M3"))
        }
        .configurationDisplayName("Live Departures")
        .description("The next five departures, plus what's near you.")
        .supportedFamilies([.systemLarge])
    }
}

@available(iOS 17.0, *)
struct NearMeWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosNearMe", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            NearMeView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: "M3"))
        }
        .configurationDisplayName("Near Me")
        .description("The three nearest stations with walking distance.")
        .supportedFamilies([.systemLarge])
    }
}

@available(iOS 17.0, *)
struct AllLinesStatusWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosAllLines", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            AllLinesStatusView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: "M2"))
        }
        .configurationDisplayName("All Lines Status")
        .description("Service status for every metro, tram, and suburban line.")
        .supportedFamilies([.systemExtraLarge])
    }
}

@available(iOS 17.0, *)
struct WeatherAlertsWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosWeatherAlerts", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            WeatherAlertsView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: "M3"))
        }
        .configurationDisplayName("Weather + Alerts")
        .description("Weather, service alerts, and your next train at a glance.")
        .supportedFamilies([.systemExtraLarge])
    }
}

@available(iOS 17.0, *)
struct TrioWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "SyrmosTrio", intent: SyrmosWidgetConfigurationIntent.self, provider: SyrmosProvider()) { entry in
            TrioView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: entry.rows.first?.lineId ?? "M3"))
        }
        .configurationDisplayName("Syrmos Trio")
        .description("Next train, alerts, and near me together.")
        .supportedFamilies([.systemMedium])
    }
}
