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

// The widgets use StaticConfiguration (nearest-station mode) rather than an
// AppIntentConfiguration. An earlier AppIntentConfiguration version failed at
// runtime — WidgetKit could not reconstruct the configuration intent ("No
// AppIntent in timeline(for:with:)"), so the timeline provider was never called
// and every widget showed only the redacted placeholder, on device and in the
// simulator alike. StaticConfiguration removes that intent dependency entirely,
// so the provider always runs and the widget renders real departures.

// MARK: - One-shot widget location (2s hard timeout)

@available(iOS 17.0, *)
final class WidgetLocation: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation?, Never>?
    private var didResume = false
    private let lock = NSLock()

    func current() async -> CLLocation? {
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { return nil }
        return await withCheckedContinuation { c in
            lock.lock()
            continuation = c
            didResume = false
            lock.unlock()
            manager.delegate = self
            manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            manager.requestLocation()
            // Hard 2s timeout: guarantees the continuation resumes even if the
            // location callback never fires (widget process off the main run
            // loop, cold GPS, no fix). Without this the timeline provider hangs
            // forever and the widget is stuck on the redacted placeholder.
            DispatchQueue.global().asyncAfter(deadline: .now() + 2) { [weak self] in
                self?.finish(nil)
            }
        }
    }

    /// Resumes the continuation at most once, whichever of the delegate
    /// callback or the timeout arrives first.
    private func finish(_ location: CLLocation?) {
        lock.lock()
        defer { lock.unlock() }
        guard !didResume, let c = continuation else { return }
        didResume = true
        continuation = nil
        c.resume(returning: location)
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        finish(locs.last)
    }
    func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        finish(nil)
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
struct SyrmosProvider: TimelineProvider {
    func placeholder(in context: Context) -> SyrmosEntry { Self.sample }

    func getSnapshot(in context: Context, completion: @escaping (SyrmosEntry) -> Void) {
        // The widget gallery preview should show something immediately.
        if context.isPreview {
            completion(Self.sample)
        } else {
            Task { completion(await entry()) }
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SyrmosEntry>) -> Void) {
        Task {
            let e = await entry()
            let next = Calendar.current.date(byAdding: .minute, value: 5, to: .now) ?? .now.addingTimeInterval(300)
            completion(Timeline(entries: [e], policy: .after(next)))
        }
    }

    /// Nearest-station entry. No per-widget configuration: the widget always
    /// tracks the station closest to the device (bounded by the 2s location
    /// timeout), falling back to an empty entry when there is no fix.
    private func entry() async -> SyrmosEntry {
        await MainActor.run { _ = SyrmosSchedulesStore.shared }
        // Prefer the widget's own fix, but fall back to the coordinate the app
        // published to the App Group (the extension often gets no fix of its
        // own). This is why the widget renders real departures on device even
        // when its CLLocationManager stays silent.
        let location = await WidgetLocation().current() ?? cachedLocation()
        let station = location.flatMap { nearest(to: $0) }
        let nearby = nearbyStations(from: location)

        guard let station else {
            widgetLog.error("no station resolved (no location fix)")
            return SyrmosEntry(date: .now, stationName: "—", rows: [], routeStops: [],
                               lastTrain: nil, nearby: nearby, statuses: lineStatuses(),
                               weather: cachedWeather(), alerts: cachedAlerts())
        }
        let rows = await MainActor.run { departureRows(for: station) }
        let last = await MainActor.run { lastTrainString(for: station) }
        let stops = routeStops(for: station)
        widgetLog.debug("entry ready: \(station.name, privacy: .public), \(rows.count) rows")
        return SyrmosEntry(date: .now, stationName: station.name, rows: rows, routeStops: stops,
                           lastTrain: last, nearby: nearby, statuses: lineStatuses(),
                           weather: cachedWeather(), alerts: cachedAlerts())
    }

    /// The app's last known coordinate from the shared App Group, or nil if the
    /// app hasn't published one yet. Used as the fallback when the widget's own
    /// CLLocationManager returns no fix.
    private func cachedLocation() -> CLLocation? {
        guard let d = UserDefaults(suiteName: "group.com.syrmosApp.ios"),
              d.object(forKey: "loc.lat") != nil, d.object(forKey: "loc.lon") != nil else { return nil }
        let lat = d.double(forKey: "loc.lat")
        let lon = d.double(forKey: "loc.lon")
        guard lat != 0 || lon != 0 else { return nil }
        return CLLocation(latitude: lat, longitude: lon)
    }

    private func nearest(to loc: CLLocation) -> TransitStation? {
        StationCoords.allStations.min { a, b in
            CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude).distance(from: loc)
                < CLLocation(latitude: b.coordinate.latitude, longitude: b.coordinate.longitude).distance(from: loc)
        }
    }

    private func nearbyStations(from loc: CLLocation?) -> [WNearby] {
        guard let loc else { return [] }
        // StationCoords has one node per line, so an interchange (e.g. Syntagma
        // on M2 + M3) appears several times. Collapse by name, keeping the
        // closest node and unioning its lines, so the list shows three distinct
        // stations rather than the same interchange repeated.
        var byName: [String: (station: TransitStation, distance: Double, lines: [String])] = [:]
        for s in StationCoords.allStations {
            let d = CLLocation(latitude: s.coordinate.latitude, longitude: s.coordinate.longitude).distance(from: loc)
            if let existing = byName[s.name] {
                let mergedLines = existing.lines + s.lineIds.filter { !existing.lines.contains($0) }
                byName[s.name] = (min(existing.distance, d) == d ? s : existing.station,
                                  min(existing.distance, d), mergedLines)
            } else {
                byName[s.name] = (s, d, s.lineIds)
            }
        }
        return byName.values
            .sorted { $0.distance < $1.distance }
            .prefix(3)
            .map { entry in
                // ~80 m/min average walking pace.
                WNearby(id: entry.station.id, name: entry.station.name, lineIds: entry.lines,
                        walkMinutes: max(1, Int((entry.distance / 80.0).rounded())))
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
        StaticConfiguration(kind: "SyrmosNextTrain", provider: SyrmosProvider()) { entry in
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
        StaticConfiguration(kind: "SyrmosLiveDepartures", provider: SyrmosProvider()) { entry in
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
        StaticConfiguration(kind: "SyrmosNearMe", provider: SyrmosProvider()) { entry in
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
        StaticConfiguration(kind: "SyrmosAllLines", provider: SyrmosProvider()) { entry in
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
        StaticConfiguration(kind: "SyrmosWeatherAlerts", provider: SyrmosProvider()) { entry in
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
        StaticConfiguration(kind: "SyrmosTrio", provider: SyrmosProvider()) { entry in
            TrioView(entry: entry).syrmosWidgetContainer(accent: SyrmosLineTokens.color(for: entry.rows.first?.lineId ?? "M3"))
        }
        .configurationDisplayName("Syrmos Trio")
        .description("Next train, alerts, and near me together.")
        .supportedFamilies([.systemMedium])
    }
}
