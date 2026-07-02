import WidgetKit
import SwiftUI
import AppIntents
import CoreLocation

// Home-screen widget: the next departures for a station. The user picks the
// mode in the widget's Edit sheet:
//   • Automatic (nearest) — uses the device location to pick the closest
//     station (requires the app's location permission; no prompt in the widget).
//   • Choose station — a fixed station the user selects.
// Departure logic: on an M3 station it shows two regular trains plus the next
// Airport (M3_AIR) train; on any other metro/tram/suburban station it shows the
// next three departures.
//
// Runs fully offline: the timeline provider projects from the bundled seed
// schedules via ScheduleProjector, exactly like the in-app station sheet.
//
// XCODE WIRING (must be done once, cannot be verified headless):
//  1. Add this file to the *SyrmosWidget* target.
//  2. Add these app files to the SyrmosWidget target membership: TransitData.swift,
//     ScheduleProjector.swift, StationCoordinates.swift, SyrmosSchedulesService.swift
//     (and anything they reference: SyrmosStationOffsetsStore, day-type helpers).
//  3. Add the `seed-schedules-v2` resource folder to the SyrmosWidget target's
//     Copy Bundle Resources (scripts/add-ios-resource-folder.py helps).
//  4. For the Automatic (nearest) mode, give the SyrmosWidget target the Location
//     capability so the extension inherits the app's location authorization.
//  5. Register in SyrmosWidgetBundle (done in this commit).

// MARK: - Station picker

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
struct SelectStationIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Next Departures"
    static var description = IntentDescription("Show the next departures from the nearest station, or one you choose.")

    @Parameter(title: "Use nearest station", default: true)
    var useNearestStation: Bool

    @Parameter(title: "Station")
    var station: StationEntity?
}

// MARK: - One-shot widget location (only when the app already has permission)

@available(iOS 17.0, *)
final class WidgetLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation?, Never>?

    func current() async -> CLLocation? {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return nil }
        return await withCheckedContinuation { c in
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

// MARK: - Timeline

@available(iOS 17.0, *)
struct DepartureRow: Identifiable {
    let id = UUID()
    let lineId: String
    let destination: String
    let minutesAway: Int
    let time: String
    let isAirport: Bool
}

@available(iOS 17.0, *)
struct NextDeparturesEntry: TimelineEntry {
    let date: Date
    let stationName: String
    let rows: [DepartureRow]
}

@available(iOS 17.0, *)
struct NextDeparturesProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> NextDeparturesEntry {
        NextDeparturesEntry(date: .now, stationName: "Syntagma", rows: Self.sampleRows)
    }

    func snapshot(for configuration: SelectStationIntent, in context: Context) async -> NextDeparturesEntry {
        await entry(for: configuration)
    }

    func timeline(for configuration: SelectStationIntent, in context: Context) async -> Timeline<NextDeparturesEntry> {
        let entry = await entry(for: configuration)
        let next = Calendar.current.date(byAdding: .minute, value: 5, to: .now) ?? .now.addingTimeInterval(300)
        return Timeline(entries: [entry], policy: .after(next))
    }

    private func entry(for configuration: SelectStationIntent) async -> NextDeparturesEntry {
        let station = await resolveStation(configuration)
        guard let station else {
            return NextDeparturesEntry(date: .now, stationName: "—", rows: [])
        }
        let rows = await MainActor.run { departureRows(for: station) }
        return NextDeparturesEntry(date: .now, stationName: station.name, rows: rows)
    }

    /// Nearest station via GPS (when in automatic mode and permission exists),
    /// else the chosen station, else the nearest, else nil.
    private func resolveStation(_ configuration: SelectStationIntent) async -> TransitStation? {
        if configuration.useNearestStation {
            if let nearest = await nearestStation() { return nearest }
        }
        if let id = configuration.station?.id,
           let picked = StationCoords.allStations.first(where: { $0.id == id }) {
            return picked
        }
        return await nearestStation()
    }

    private func nearestStation() async -> TransitStation? {
        guard let loc = await WidgetLocation().current() else { return nil }
        return StationCoords.allStations.min { a, b in
            let da = CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude).distance(from: loc)
            let db = CLLocation(latitude: b.coordinate.latitude, longitude: b.coordinate.longitude).distance(from: loc)
            return da < db
        }
    }

    @MainActor
    private func departureRows(for station: TransitStation) -> [DepartureRow] {
        let deps = ScheduleProjector.nextDepartures(
            for: station.id,
            lineIds: station.lineIds,
            limit: 20,
            timeHorizonMinutes: 6 * 60
        )
        let isAirport: (Departure) -> Bool = { $0.serviceType == "airport" || $0.lineId == "M3_AIR" }

        // M3 stations: two regular trains + the next Airport train. Every other
        // line: just the next three departures.
        if station.lineIds.contains("M3") {
            var out = deps.filter { !isAirport($0) }.prefix(2).map { dep in
                DepartureRow(lineId: normalizeLine(dep.lineId), destination: dep.direction,
                             minutesAway: dep.minutesAway, time: dep.time, isAirport: false)
            }
            if let air = deps.first(where: isAirport) {
                out.append(DepartureRow(lineId: "M3", destination: air.direction,
                                        minutesAway: air.minutesAway, time: air.time, isAirport: true))
            }
            return Array(out)
        }
        return deps.prefix(3).map { dep in
            DepartureRow(lineId: normalizeLine(dep.lineId), destination: dep.direction,
                         minutesAway: dep.minutesAway, time: dep.time, isAirport: false)
        }
    }

    private func normalizeLine(_ id: String) -> String { id.hasPrefix("M3") ? "M3" : id }

    static let sampleRows: [DepartureRow] = [
        DepartureRow(lineId: "M3", destination: "Doukissis Plakentias", minutesAway: 2, time: "17:47", isAirport: false),
        DepartureRow(lineId: "M3", destination: "Dimotiko Theatro", minutesAway: 4, time: "17:49", isAirport: false),
        DepartureRow(lineId: "M3", destination: "Airport", minutesAway: 10, time: "17:55", isAirport: true),
    ]
}

// MARK: - View

@available(iOS 17.0, *)
struct NextDeparturesView: View {
    var entry: NextDeparturesEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 6) {
                Text(entry.stationName)
                    .font(.headline).lineLimit(1).minimumScaleFactor(0.8)
                Spacer(minLength: 0)
                Image(systemName: "tram.fill").font(.caption).foregroundStyle(.secondary)
            }
            if entry.rows.isEmpty {
                Spacer(minLength: 0)
                Text("No upcoming departures")
                    .font(.caption).foregroundStyle(.secondary)
                Spacer(minLength: 0)
            } else {
                ForEach(entry.rows) { row in
                    HStack(spacing: 8) {
                        Text(row.lineId)
                            .font(.caption2).fontWeight(.bold).foregroundStyle(.white)
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(lineColor(row.lineId), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        HStack(spacing: 4) {
                            Text(row.destination).font(.caption).lineLimit(1)
                            if row.isAirport {
                                Image(systemName: "airplane").font(.caption2).foregroundStyle(.blue)
                            }
                        }
                        Spacer(minLength: 0)
                        Text(row.minutesAway <= 1 ? "now" : "\(row.minutesAway)m")
                            .font(.caption).fontWeight(.semibold).monospacedDigit()
                            .foregroundStyle(row.minutesAway <= 2 ? .red : .primary)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func lineColor(_ id: String) -> Color {
        switch id {
        case "M1": return Color(red: 0.13, green: 0.65, blue: 0.35)
        case "M2": return Color(red: 0.85, green: 0.20, blue: 0.20)
        case "M3": return Color(red: 0.10, green: 0.45, blue: 0.85)
        case "T6", "T7": return Color(red: 0.95, green: 0.55, blue: 0.10)
        default: return Color(red: 0.20, green: 0.60, blue: 0.60)
        }
    }
}

// MARK: - Widget

@available(iOS 17.0, *)
struct NextDeparturesWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: "SyrmosNextDepartures",
            intent: SelectStationIntent.self,
            provider: NextDeparturesProvider()
        ) { entry in
            NextDeparturesView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Next Departures")
        .description("Next trains from the nearest station, or one you pick.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
