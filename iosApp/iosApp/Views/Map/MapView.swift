import SwiftUI
import MapKit
import UIKit

// MARK: - Location manager for map locate button

@MainActor
final class MapLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        authorizationStatus = manager.authorizationStatus
    }

    /// Returns true if the caller should follow up by recentering the map.
    /// Returns false if it should show the "denied" alert instead.
    @discardableResult
    func requestOrPrompt() -> LocationRequestResult {
        let status = manager.authorizationStatus
        switch status {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
            return .promptShown
        case .denied, .restricted:
            return .denied
        case .authorizedAlways, .authorizedWhenInUse:
            return .authorized
        @unknown default:
            return .denied
        }
    }

    func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor [weak self] in
            self?.authorizationStatus = status
        }
    }
}

enum LocationRequestResult {
    case authorized
    case promptShown
    case denied
}

// MARK: - Preloaded station data (computed once at app start)

struct RouteLine: Identifiable {
    let id: String
    let color: Color
    let coordinates: [CLLocationCoordinate2D]
    let lineWeight: CGFloat
    // A line that is built but not open draws greyed and dashed, because the
    // track is real but carries no service. It has no trains or departures
    // either (handled in the projector/simulator).
    var underConstruction: Bool = false
}

private func catmullRomSpline(_ points: [CLLocationCoordinate2D], segments: Int = 5) -> [CLLocationCoordinate2D] {
    guard points.count >= 3 else { return points }
    var result = [points[0]]
    for i in 0..<(points.count - 1) {
        let p0 = points[max(i - 1, 0)]
        let p1 = points[i]
        let p2 = points[i + 1]
        let p3 = points[min(i + 2, points.count - 1)]
        for t in 1...segments {
            let f = Double(t) / Double(segments + 1)
            let lat = cr(p0.latitude, p1.latitude, p2.latitude, p3.latitude, f)
            let lon = cr(p0.longitude, p1.longitude, p2.longitude, p3.longitude, f)
            result.append(CLLocationCoordinate2D(latitude: lat, longitude: lon))
        }
        result.append(p2)
    }
    return result
}

private func cr(_ a: Double, _ b: Double, _ c: Double, _ d: Double, _ t: Double) -> Double {
    0.5 * (2*b + (-a+c)*t + (2*a - 5*b + 4*c - d)*t*t + (-a + 3*b - 3*c + d)*t*t*t)
}

enum PreloadedData {
    static let stations: [MapStationNode] = SyrmosData.mapStations
    static let stationsById: [String: MapStationNode] = Dictionary(
        uniqueKeysWithValues: stations.map { ($0.id, $0) }
    )
    static let routeLines: [RouteLine] = SyrmosData.lines.compactMap { line in
        // Prefer OSM-derived geometry from the bundled shapes.json so the
        // polyline follows the actual rail/tram track (T7 Piraeus loop, M3
        // airport branch, A4 Megara curve). Falls back to a Catmull–Rom
        // spline of station coordinates when no shape is bundled for a
        // line, which keeps the curve smooth instead of zigzagging.
        let stations = SyrmosData.stations(for: line.id)
        let osmCoords = SyrmosRouteShapesStore.shared.coordinates(for: line.id)
        let coords: [CLLocationCoordinate2D]
        if let osm = osmCoords, osm.count >= 2 {
            coords = osm
        } else {
            // No bundled OSM shape: spline through the line's ordered stations.
            // Prefer the curated Athens coordinates; for every other line
            // (national, Thessaloniki, Patras) read the ordered stations
            // straight from lines.json, so the WHOLE network draws - not just
            // Athens. Web does exactly this, which is why national lines showed
            // on web but were blank on iOS.
            let anchors = stations.count >= 2
                ? stations.map { $0.coordinate }
                : SyrmosLineGeometry.orderedCoordinates(for: line.id)
            guard anchors.count >= 2 else { return nil }
            coords = catmullRomSpline(anchors)
        }
        let underConstruction = !line.isOperational
        // Web weights: metro/tram 5, suburban/bus 4, under-construction 3.
        return RouteLine(
            id: line.id,
            color: underConstruction ? Color(white: 0.62) : line.color,
            coordinates: coords,
            lineWeight: underConstruction ? 3 : ((line.type == .suburban || line.type == .bus) ? 4 : 5),
            underConstruction: underConstruction
        )
    }
    static let stationIconMap: [String: String] = {
        var map: [String: String] = [:]
        // Metro + tram lines: index-aligned arrays (station N -> image N).
        let lineImageNames: [(stations: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)], images: [String])] = [
            (StationCoords.line1, StationIconNames.m1),
            (StationCoords.line2, StationIconNames.m2),
            (StationCoords.line3, StationIconNames.m3),
            (StationCoords.tramT6, StationIconNames.t6),
            (StationCoords.tramT7, StationIconNames.t7),
        ]
        for config in lineImageNames {
            for (index, station) in config.stations.enumerated() {
                if map[station.id] != nil { continue }
                if index < config.images.count {
                    map[station.id] = config.images[index]
                }
            }
        }
        // Suburban A1-A4: explicit dictionaries because the asset
        // ordering and the timetable's stop ordering diverge (A1
        // skips Ano Liosia + SKA that exist in the legacy "p1" set,
        // etc). station_id -> asset name.
        for (stationId, assetName) in StationIconNames.a1 where map[stationId] == nil {
            map[stationId] = assetName
        }
        for (stationId, assetName) in StationIconNames.a2 where map[stationId] == nil {
            map[stationId] = assetName
        }
        for (stationId, assetName) in StationIconNames.a3 where map[stationId] == nil {
            map[stationId] = assetName
        }
        for (stationId, assetName) in StationIconNames.a4 where map[stationId] == nil {
            map[stationId] = assetName
        }
        // Per athens_transit_icons_and_rules_package/RULES.md, at major
        // interchanges show the combined icon with every connecting line
        // visible (not a single-mode icon). The package ships these as
        // station_connection_icons; we rastered the truly multi-line ones
        // into the asset catalog.
        let interchangeIcons: [String: String] = [
            // Syntagma: M2 + M3 + T6
            "M2_SYN": "station_syntagma_m2_m3_t6",
            "M3_SYN": "station_syntagma_m2_m3_t6",
            "T6_SYN": "station_syntagma_m2_m3_t6",
            // Monastiraki: M1 + M3
            "M1_MON": "station_monastiraki_m1_m3",
            "M3_MON": "station_monastiraki_m1_m3",
            // Dimotiko Theatro: M3 + T7
            "M3_DIM": "station_dimotiko_theatro_m3_t7",
            "T7_DIM": "station_dimarhio_dimotiko_theatro_m3_t7",
        ]
        for (stationId, iconName) in interchangeIcons {
            map[stationId] = iconName
        }
        return map
    }()
}

enum VehicleIcons {
    static func imageName(for train: SimulatedTrain) -> String? {
        return imageName(lineId: train.lineId, direction: train.direction, isAirportService: train.isAirportService)
    }

    /// Live train counterpart. LiveTrain only carries destination as
    /// a free-form string ("Airport", "Doukissis Plakentias",
    /// "Piraeus" etc.), so we infer airport flag + direction from it.
    static func imageName(for train: LiveTrain) -> String? {
        let dest = train.destination.lowercased()
        let isAirport = dest.contains("airport") || dest.contains("αεροδρόμιο") || dest.contains("aeroport")
        // Per-line inbound terminals (small / west / south end of line).
        // Anything else is treated as outbound and gets the right-arrow asset.
        let inboundTerminals: [String: [String]] = [
            "M1": ["piraeus", "πειραιάς"],
            "M2": ["anthoupoli", "ανθούπολη"],
            "M3": ["dimotiko theatro", "δημοτικό θέατρο"],
            "T6": ["syntagma", "σύνταγμα"],
            "T7": ["akti posidonos", "ακτή ποσειδώνος"],
        ]
        let isInbound = (inboundTerminals[train.lineId] ?? []).contains(where: { dest.contains($0) })
        return imageName(lineId: train.lineId, direction: isInbound ? "inbound" : "outbound", isAirportService: isAirport)
    }

    private static func imageName(lineId: String, direction: String, isAirportService: Bool) -> String? {
        let isInbound = direction == "inbound"
        switch lineId {
        case "M1": return isInbound ? "metro_m1_left_to_piraeus" : "metro_m1_right_to_kifissia"
        case "M2": return isInbound ? "metro_m2_left_to_anthoupoli" : "metro_m2_right_to_elliniko"
        case "M3":
            if isAirportService { return "metro_m3_right_to_airport" }
            return isInbound ? "metro_m3_left_to_dimotiko_theatro" : "metro_m3_right_to_doukissis_plakentias"
        case "T6": return isInbound ? "tram_t6_left_to_syntagma" : "tram_t6_right_to_pikrodafni"
        case "T7": return isInbound ? "tram_t7_left_to_akti_posidonos" : "tram_t7_right_to_asklipiio_voulas"
        // Suburban sprites ship under OASA's legacy naming:
        //   A1 -> "p1" (Piraeus <-> Airport)
        //   A3 -> "p3" (Athens <-> Chalkida)
        //   A4 -> "p2" (Piraeus <-> Kiato)
        // A2 has no left/right sprite in the asset catalogue yet, so
        // it falls through to the coloured teardrop fallback.
        case "A1": return isInbound ? "train_p1_left_to_piraeus" : "train_p1_right_to_airport"
        case "A3": return isInbound ? "train_p3_left_to_athens" : "train_p3_right_to_chalkida"
        case "A4": return isInbound ? "train_p2_left_to_piraeus" : "train_p2_right_to_kiato"
        default: return nil
        }
    }
}

struct TransitMapView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    // Use the shared singleton instances so we don't run two polling loops
    // and two simulator timers in parallel with HomeView. Reduces background
    // work by ~50% and avoids the multi-view re-render storms that were
    // freezing the UI on iOS.
    @ObservedObject private var liveTrainService = LiveTrainService.shared
    @ObservedObject private var trainSimulator = TrainSimulatorService.shared
    @StateObject private var locationManager = MapLocationManager()
    @State private var tappedStation: MapStationNode?
    @State private var showLocationDeniedAlert = false
    /// When true, hide all moving train/tram annotations so the user can see
    /// just the lines + stations. Persists across navigation but resets on
    /// cold launch (deliberate: it's a quick toggle, not a setting).
    @State private var vehiclesHidden = false
    /// The map asks us to recenter via this trigger. UIViewRepresentable
    /// reads it in update() and calls setRegion on the wrapped MKMapView.
    @State private var recenterToUserPing: Int = 0
    private let stations = PreloadedData.stations
    private let routeLines = PreloadedData.routeLines

    var body: some View {
        NavigationStack {
            mapContent
                .safeAreaInset(edge: .top, spacing: 8) {
                    CompactTabHeader(loc[.map])
                }
                .task(id: locationManager.authorizationStatus) {
                    // Auto-center on the user when the Map tab appears AND
                    // permission is already granted. If the user hasn't
                    // granted (or has denied) we stay on the Athens fallback
                    // set in makeUIView. Wait a beat so MKMapView has time to
                    // start streaming CLLocation updates before we recenter.
                    let status = locationManager.authorizationStatus
                    if status == .authorizedWhenInUse || status == .authorizedAlways {
                        try? await Task.sleep(nanoseconds: 300_000_000)
                        recenterToUserPing &+= 1
                    }
                }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $tappedStation) { station in
                StationSheetView(station: station)
                    .presentationDetents([.medium, .large])
                    .presentationDragIndicator(.visible)
                    .presentationContentInteraction(.scrolls)
            }
            .alert(
                loc.language == .greek ? "Η τοποθεσία είναι απενεργοποιημένη" : loc.language == .albanian ? "Vendndodhja është e çaktivizuar" : "Location is disabled",
                isPresented: $showLocationDeniedAlert
            ) {
                Button(loc.language == .greek ? "Άνοιγμα Ρυθμίσεων" : loc.language == .albanian ? "Hap Cilësimet" : "Open Settings") {
                    locationManager.openSystemSettings()
                }
                Button(loc.language == .greek ? "Άκυρο" : loc.language == .albanian ? "Anulo" : "Cancel", role: .cancel) {}
            } message: {
                Text(loc.language == .greek
                    ? "Δεν έχετε δώσει άδεια τοποθεσίας στο Syrmos. Θέλετε να ανοίξετε τις Ρυθμίσεις για να την ενεργοποιήσετε;"
                    : loc.language == .albanian
                    ? "Nuk i ke dhënë Syrmos leje për vendndodhjen. Dëshiron të hapësh Cilësimet për ta aktivizuar?"
                    : "You haven't granted Syrmos location access. Would you like to open Settings to enable it?")
            }
        }
    }

    private var mapContent: some View {
        ZStack(alignment: .bottomTrailing) {
                // UIViewRepresentable wrapping MKMapView directly. SwiftUI's
                // Map(position:) had a CAMetalLayer lifecycle bug on iOS 18
                // and 26 where the metal layer would come back dead after a
                // screenshot, lock/unlock, control center swipe, or app
                // switcher cycle, leaving the user on a black canvas. Bumping
                // a .id() trigger only papered over some paths; an explicit
                // UIViewRepresentable is the only thing that survives all of
                // them, because we own the MKMapView lifecycle deterministically.
                SyrmosMKMapView(
                    stations: stations,
                    routeLines: routeLines,
                    // Per-line suburban dedupe: railway.gov.gr SSE carries
                    // raw GPS, our projector carries schedule-based positions
                    // for the SAME physical train. Whenever the live feed has
                    // ANY train for a line, hide the projected dots for that
                    // line so we don't draw two markers. If the live feed is
                    // empty (offline mode or feed dropped) the projected
                    // dots keep moving — which is the entire point.
                    simulatedTrains: vehiclesHidden ? [] : {
                        let coveredLines = Set(liveTrainService.trains.map(\.lineId))
                        return trainSimulator.trains.filter { !coveredLines.contains($0.lineId) }
                    }(),
                    liveTrains: vehiclesHidden ? [] : liveTrainService.trains,
                    recenterToUserPing: recenterToUserPing,
                    onStationTap: { stationId in
                        tappedStation = stations.first(where: { $0.id == stationId })
                    }
                )
                // Extend the map underneath the CompactTabHeader at the
                // top and the system tab bar at the bottom. Without this,
                // the safe-area inset paints solid black bars above and
                // below the map, which read as a layout glitch.
                .ignoresSafeArea(.container, edges: [.top, .bottom])

                VStack(spacing: 12) {
                    Button {
                        vehiclesHidden.toggle()
                    } label: {
                        Image(systemName: vehiclesHidden ? "tram.fill" : "tram")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(vehiclesHidden ? .white : Color.accentColor)
                            .frame(width: 50, height: 50)
                            .background(vehiclesHidden ? Color.accentColor : Color(uiColor: .systemBackground))
                            .clipShape(Circle())
                            .overlay(
                                Circle().strokeBorder(
                                    vehiclesHidden ? .clear : Color.accentColor.opacity(0.25),
                                    lineWidth: 1
                                )
                            )
                            .shadow(color: .black.opacity(0.25), radius: 6, y: 3)
                    }
                    .accessibilityLabel(
                        vehiclesHidden
                            ? (loc.language == .greek ? "Εμφάνιση οχημάτων" : loc.language == .albanian ? "Shfaq mjetet" : "Show vehicles")
                            : (loc.language == .greek ? "Απόκρυψη οχημάτων" : loc.language == .albanian ? "Fshih mjetet" : "Hide vehicles")
                    )

                    Button {
                        let result = locationManager.requestOrPrompt()
                        switch result {
                        case .authorized, .promptShown:
                            recenterToUserPing &+= 1
                        case .denied:
                            showLocationDeniedAlert = true
                        }
                    } label: {
                        Image(systemName: "location.fill")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 50, height: 50)
                            .background(Color.accentColor)
                            .clipShape(Circle())
                            .shadow(color: .black.opacity(0.25), radius: 6, y: 3)
                    }
                }
                .padding(.trailing, 16)
                .padding(.bottom, 80)
        }
    }
}

// MARK: - Station Sheet

struct StationSheetView: View {
    let station: MapStationNode
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var departures: [Departure] = []

    // Live countdown refresh
    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    var body: some View {
        // Pinned bottom layout: everything else scrolls, the Get directions
        // call-to-action stays parked at the bottom edge so the user never
        // has to scroll to find their primary action.
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    lineBadges
                    stationFactsChips
                    if !departures.isEmpty { departuresList }
                }
                .padding()
                .padding(.bottom, 12)
            }
            .scrollIndicators(.automatic)

            // Solid background + top divider so the pinned footer reads as
            // a sticky action bar over the scrolling list rather than
            // floating on top of mid-content text.
            Divider()
            directionsButton
                .padding(.horizontal)
                .padding(.top, 12)
                .padding(.bottom, 16)
                .background(.thinMaterial)
        }
        .onAppear(perform: reloadDepartures)
        .onReceive(refreshTimer) { _ in reloadDepartures() }
    }

    /// Map sheet entry: short list (4 entries) of the next departures
    /// across whichever lines call at this stop. Offline only — the
    /// previous SyrmosDeparturesService remote path was removed because
    /// any time the Pi's clock drifted or the user spoofed their phone
    /// clock the remote answer overwrote the locally-correct offline
    /// answer and the screen reported stale data. Local projector with
    /// a 12 h horizon guarantees we always have at least the next train
    /// even if today's service has wrapped into tomorrow morning.
    private func reloadDepartures() {
        departures = localDepartures(limit: 4)
    }

    private func localDepartures(limit: Int) -> [Departure] {
        var collected: [Departure] = []
        for lineId in station.lineIds {
            let stationId = station.stationIdByLineId[lineId]
                ?? station.stationIds.first
                ?? station.id
            collected.append(contentsOf: ScheduleProjector.nextDepartures(
                for: stationId,
                lineIds: [lineId],
                limit: limit,
                timeHorizonMinutes: 12 * 60
            ))
        }
        return collected
            .sorted { $0.minutesAway < $1.minutesAway }
            .prefix(limit)
            .map { $0 }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text(loc.language == .greek ? station.nameEl : station.displayName)
                    .font(.title2)
                    .fontWeight(.bold)
                // Subtitle only when in Greek mode (Latin underneath the
                // Greek title). Non-Greek modes get a single Latin name —
                // Albanian and English users don't expect a Greek subtitle.
                if loc.language == .greek {
                    Text(station.displayName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 28, weight: .regular))
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(loc.language == .greek ? "Κλείσιμο" : loc.language == .albanian ? "Mbylle" : "Close")
        }
    }

    private var lineBadges: some View {
        // Wrap badges so 4+ line stations like Piraeus don't overflow.
        FlowLayout(spacing: 6) {
            ForEach(station.lineIds, id: \.self) { lineId in
                HStack(spacing: 5) {
                    Circle()
                        .fill(SyrmosData.lineColor(for: lineId))
                        .frame(width: 8, height: 8)
                    Text(SyrmosData.line(for: lineId)?.name ?? lineId)
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(SyrmosData.lineColor(for: lineId).opacity(0.12))
                .clipShape(Capsule())
            }
        }
    }

    @ViewBuilder
    private var stationFactsChips: some View {
        // Only show the chips that are actually useful. Skip "Lines: N" (the
        // badges already say that) and the internal "merged records" detail.
        if station.isInterchange {
            HStack(spacing: 6) {
                FactChip(icon: "arrow.left.arrow.right",
                         label: loc.language == .greek ? "Ανταπόκριση" : loc.language == .albanian ? "Korrespondencë" : "Interchange")
            }
        }
    }

    private var departuresList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(loc.language == .greek ? "Επόμενα Δρομολόγια" : loc.language == .albanian ? "Nisjet e ardhshme" : "Next departures")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            ForEach(departures.prefix(6)) { dep in
                DepartureRowView(departure: dep)
                if dep.id != departures.prefix(6).last?.id {
                    Divider().padding(.leading, 28)
                }
            }
        }
    }

    private var directionsButton: some View {
        Button {
            let dest = MKMapItem(placemark: MKPlacemark(coordinate: station.coordinate))
            dest.name = station.displayName
            dest.openInMaps(launchOptions: [
                MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeTransit,
            ])
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                Text(loc.language == .greek ? "Οδηγίες" : loc.language == .albanian ? "Udhëzime" : "Get directions")
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Color.syrmosPrimary)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Helpers

/// Compact pill that surfaces a single useful station fact (Interchange, etc).
private struct FactChip: View {
    let icon: String
    let label: String
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(label)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(Capsule())
    }
}

/// Simple wrapping layout for line badges so a 4-line station like Piraeus
/// doesn't have to scroll horizontally.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var totalHeight: CGFloat = 0
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > width {
                totalHeight += rowHeight + spacing
                rowWidth = size.width + spacing
                rowHeight = size.height
            } else {
                rowWidth += size.width + spacing
                rowHeight = max(rowHeight, size.height)
            }
        }
        totalHeight += rowHeight
        return CGSize(width: width, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let width = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + width {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: size.width, height: size.height))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Station Dot (map annotation)

struct StationDot: View {
    let station: MapStationNode
    let isSelected: Bool
    /// 0=country, 1=city, 2=district, 3=street. See `TransitMapView.zoomBucket`.
    var zoomBucket: Int = 3

    var body: some View {
        Group {
            switch zoomBucket {
            case 3:
                highZoomBody
            case 2:
                midZoomBody
            default:
                lowZoomBody
            }
        }
        .animation(.easeInOut(duration: 0.15), value: zoomBucket)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
    }

    /// Street-level: the full station_smart_code SVG when we have one.
    @ViewBuilder
    private var highZoomBody: some View {
        if let iconName = stationIconName,
           let uiImage = UIImage(named: iconName) {
            Image(uiImage: uiImage)
                .resizable()
                .frame(width: isSelected ? 30 : 22, height: isSelected ? 30 : 22)
                .shadow(color: .black.opacity(isSelected ? 0.3 : 0.15), radius: isSelected ? 4 : 2, y: 1)
        } else {
            midZoomBody
        }
    }

    /// District-level: a compact modern dot in the line colour with a crisp
    /// white ring (mirrors the web map). Much smaller than the old teardrop; the
    /// mode glyph only appears when the stop is selected so the map stays clean.
    /// Interchanges read as a white-cored "target" ring.
    private var midZoomBody: some View {
        let size: CGFloat = isSelected ? MapDesignTokens.dotSelected : MapDesignTokens.dotCity
        return ZStack {
            if station.isInterchange {
                Circle()
                    .fill(Color.white)
                    .frame(width: size, height: size)
                    .overlay(Circle().stroke(primaryColor, lineWidth: size * MapDesignTokens.interchangeRingRatio))
                    .shadow(color: .black.opacity(0.25), radius: 1.5, y: 0.5)
            } else {
                Circle()
                    .fill(primaryColor)
                    .frame(width: size, height: size)
                    .overlay(Circle().stroke(.white, lineWidth: MapDesignTokens.ringWidth))
                    .shadow(color: .black.opacity(0.25), radius: 1.5, y: 0.5)
            }
            if isSelected {
                Image(systemName: primaryModeSymbol)
                    .font(.system(size: size * 0.5, weight: .bold))
                    .foregroundStyle(station.isInterchange ? primaryColor : .white)
            }
        }
        .scaleEffect(isSelected ? 1.15 : 1.0)
    }

    /// Country-level: tiny solid dot in line color, no glyph (would be unreadable).
    private var lowZoomBody: some View {
        let size: CGFloat = isSelected ? 12 : 9
        return ZStack {
            Circle()
                .fill(primaryColor)
                .frame(width: size, height: size)
                .overlay(
                    Circle()
                        .stroke(.white, lineWidth: size * 0.18)
                )
                .shadow(color: .black.opacity(0.25), radius: 1, y: 0.5)
        }
        .scaleEffect(isSelected ? 1.3 : 1.0)
    }

    private var primaryLineId: String { station.lineIds.first ?? "M3" }
    private var primaryColor: Color { SyrmosData.lineColor(for: primaryLineId) }

    private var primaryModeSymbol: String {
        guard let line = SyrmosData.line(for: primaryLineId) else { return "tram.fill" }
        switch line.type {
        case .metro: return "tram.tunnel.fill"
        case .tram: return "tram.fill"
        case .suburban: return "train.side.front.car"
        case .bus: return "bus.fill"
        }
    }

    private var interchangeRingsBadge: some View {
        HStack(spacing: 1.5) {
            ForEach(Array(station.lineIds.prefix(3).enumerated()), id: \.element) { _, lineId in
                Circle()
                    .fill(SyrmosData.lineColor(for: lineId))
                    .frame(width: 5, height: 5)
                    .overlay(Circle().stroke(.white, lineWidth: 0.8))
            }
        }
        .padding(2)
        .background(
            Capsule()
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.18), radius: 1, y: 0.5)
        )
    }

    private var stationIconName: String? {
        let primaryId = station.stationIds.first ?? station.id
        return PreloadedData.stationIconMap[primaryId]
    }
}

struct SimulatedTrainDot: View {
    let train: SimulatedTrain

    var body: some View {
        if let iconName = VehicleIcons.imageName(for: train),
           let uiImage = UIImage(named: iconName) {
            Image(uiImage: uiImage)
                .resizable()
                .frame(width: 38, height: 38)
                .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
        } else {
            fallbackDot
        }
    }

    @ViewBuilder
    private var fallbackDot: some View {
        VStack(spacing: 2) {
            Text(train.lineId)
                .font(.system(size: 8, weight: .heavy))
                .foregroundStyle(.white)
                .padding(.horizontal, 4)
                .padding(.vertical, 1)
                .background(trainColor, in: Capsule())

            ZStack {
                if train.isAirportService {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.white)
                        .frame(width: 28, height: 24)
                        .shadow(color: .black.opacity(0.15), radius: 2, y: 1)
                    RoundedRectangle(cornerRadius: 5)
                        .fill(Color.metroBlue)
                        .frame(width: 24, height: 20)
                    Image(systemName: "airplane")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(.white)
                } else if train.lineType == .tram {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(.white)
                        .frame(width: 26, height: 18)
                        .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(trainColor)
                        .frame(width: 22, height: 14)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(.white)
                        .frame(width: 26, height: 22)
                        .shadow(color: .black.opacity(0.15), radius: 2, y: 1)
                    RoundedRectangle(cornerRadius: 5)
                        .fill(trainColor)
                        .frame(width: 22, height: 18)
                    RoundedRectangle(cornerRadius: 1.5)
                        .fill(.white.opacity(0.7))
                        .frame(width: 12, height: 3)
                        .offset(y: -2)
                }
            }
        }
    }

    private var trainColor: Color {
        SyrmosData.lineColor(for: train.lineId)
    }
}

struct TrainDot: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.suburbanPurple.opacity(0.22))
                .frame(width: 30, height: 30)
            Circle()
                .fill(Color.suburbanPurple)
                .frame(width: 16, height: 16)
            Circle()
                .fill(.white)
                .frame(width: 6, height: 6)
        }
        .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
    }
}

/// Live suburban train marker: pulsing purple ring + line-id badge so users
/// can spot real trains amid the simulated metro/tram dots. The pulse runs
/// off TimelineView so it animates without forcing the parent map to redraw.
struct LiveTrainMarker: View {
    let lineId: String
    @State private var pulsing = false

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle()
                    .stroke(Color.suburbanPurple.opacity(0.55), lineWidth: 2)
                    .frame(width: pulsing ? 44 : 26, height: pulsing ? 44 : 26)
                    .opacity(pulsing ? 0 : 0.9)
                Circle()
                    .fill(Color.suburbanPurple)
                    .frame(width: 22, height: 22)
                Image(systemName: "tram.fill")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
            }
            Text(lineId)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(Color.suburbanPurple)
                .clipShape(Capsule())
                .shadow(color: .black.opacity(0.18), radius: 2, y: 1)
        }
        .shadow(color: .black.opacity(0.18), radius: 3, y: 2)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: false)) {
                pulsing = true
            }
        }
    }
}

// MARK: - Departure Row

struct DepartureRowView: View {
    let departure: Departure
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 12) {
            // Bigger color indicator that ties the row to its line
            RoundedRectangle(cornerRadius: 3, style: .continuous)
                .fill(SyrmosData.lineColor(for: departure.lineId))
                .frame(width: 4, height: 32)

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(SyrmosData.line(for: departure.lineId)?.name ?? departure.lineId)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    if departure.serviceType == "airport" {
                        Text(loc.language == .greek ? "Αεροδρόμιο" : loc.language == .albanian ? "Aeroporti" : "Airport")
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1)
                            .background(Color.metroBlue.opacity(0.15))
                            .foregroundStyle(Color.metroBlue)
                            .clipShape(Capsule())
                    }
                }
                Text(loc.language == .greek
                    ? "προς \(departure.direction)"
                    : loc.language == .albanian
                    ? "drejt \(departure.direction)"
                    : "to \(departure.direction)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 1) {
                Text(departure.minutesAwayDisplay(language: loc.language))
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundStyle(arrivalColor)
                    .contentTransition(.numericText())
                Text(departure.time)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 4)
    }

    private var arrivalColor: Color {
        if departure.minutesAway <= 2 { return Color.arrivalSoon }
        if departure.minutesAway <= 5 { return Color.arrivalModerate }
        return Color.arrivalFar
    }
}

// MARK: - MKMapView wrapper

/// UIKit-backed map. Wraps MKMapView in UIViewRepresentable so the
/// CAMetalLayer is owned by a stable UIView whose lifecycle SwiftUI can't
/// destabilise on screenshot / scenePhase transitions. Same approach the
/// StationMapSheet already uses successfully — see that file's header
/// comment for the rationale.
struct SyrmosMKMapView: UIViewRepresentable {
    let stations: [MapStationNode]
    let routeLines: [RouteLine]
    let simulatedTrains: [SimulatedTrain]
    let liveTrains: [LiveTrain]
    /// Bumped from the parent's "Locate me" button to re-center on the
    /// user. Reading it in updateUIView() lets us tell a fresh request
    /// apart from the no-op redraws triggered by annotation churn.
    let recenterToUserPing: Int
    let onStationTap: (String) -> Void

    /// CARTO label-free minimal base tiles that replace Apple's map content.
    static func makeCartoOverlay(dark: Bool) -> MKTileOverlay {
        let style = dark ? "dark_nolabels" : "light_nolabels"
        let overlay = MKTileOverlay(urlTemplate: "https://a.basemaps.cartocdn.com/\(style)/{z}/{x}/{y}@2x.png")
        overlay.canReplaceMapContent = true
        overlay.maximumZ = 20
        return overlay
    }

    func makeUIView(context: Context) -> MKMapView {
        let mv = MKMapView()
        mv.delegate = context.coordinator
        mv.pointOfInterestFilter = .excludingAll
        mv.showsUserLocation = true
        mv.isPitchEnabled = false
        // The default MKMapView compass floats to the very top-right corner,
        // and because the map ignores the top safe area it landed under the
        // CompactTabHeader - a visible overlap on the Map tab. This is a flat,
        // north-up transit map (pitch disabled, web + Android carry no compass
        // either), so hide the system compass for a clean, cross-platform-
        // consistent header. Rotation still works; it just has no ornament.
        mv.showsCompass = false

        // Flat, label-free minimal base like the railway.gov.gr live tracker: a
        // CARTO Positron (light) / Dark-Matter (dark) tile overlay that REPLACES
        // Apple's detailed base map, so there is no street clutter or POI noise -
        // our coloured line network + station/train dots are the whole picture.
        // The base tiles MUST go at `.aboveRoads` (the LOWEST overlay level).
        // They are opaque (`canReplaceMapContent`), so at `.aboveLabels` (the
        // highest level) they painted straight over every route polyline and the
        // whole coloured network vanished - the "no lines on the map" bug. Only
        // the station/train annotation views survived, because annotations always
        // draw above overlays regardless of level. Base at the bottom, polylines
        // pinned to `.aboveLabels` below, so the lines sit on the flat base.
        let dark = mv.traitCollection.userInterfaceStyle == .dark
        let baseTiles = Self.makeCartoOverlay(dark: dark)
        mv.addOverlay(baseTiles, level: .aboveRoads)
        context.coordinator.baseTileOverlay = baseTiles
        context.coordinator.baseTileDark = dark
        // Open framed on the whole Athens network (Kifissia -> Elliniko / Piraeus),
        // not just the central 6km. The app is nationwide now, but launching on
        // Athens keeps metro + tram + suburban + trains on screen; the user zooms
        // out for the country and GPS "locate me" recenters on them.
        mv.region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.970, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.24, longitudeDelta: 0.30)
        )

        // Route polylines. ColoredPolyline carries colour + weight so the
        // renderer can pick them without an external lookup table.
        for route in routeLines {
            let poly = SyrmosColoredPolyline(coordinates: route.coordinates, count: route.coordinates.count)
            poly.color = UIColor(route.color)
            poly.weight = route.lineWeight
            poly.dashed = route.underConstruction
            mv.addOverlay(poly, level: .aboveLabels)
        }

        for station in stations {
            mv.addAnnotation(SyrmosStationAnnotation(station: station))
        }

        // On-device audit: warn if any station the app renders falls outside the
        // Attica box (37.7-38.55 N, 22.65-24.15 E; A4 Kiato W to A3 Chalkida N).
        // This is the definitive check for
        // the "dots in the sea" question - it reads the real coordinate, so if the
        // console stays quiet the bundled data is clean. Athens-only by design; the
        // rest of Greece is expected outside the box, so scope the audit to the
        // athens-region stations via their line ids.
        let athensLineIds: Set<String> = ["M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4"]
        var offshoreCount = 0
        for s in stations {
            var onAthensLine = false
            for lid in s.lineIds where athensLineIds.contains(lid) {
                onAthensLine = true
                break
            }
            if !onAthensLine { continue }
            let lat = s.coordinate.latitude
            let lon = s.coordinate.longitude
            // Box covers the full Athens-line extent: north to the A3 Chalkida
            // line (~38.46), west to the A4 Kiato terminus (~22.73). National lines
            // (already excluded above) span all of Greece and aren't audited.
            let outside = lat < 37.70 || lat > 38.55 || lon < 22.65 || lon > 24.15
            if outside {
                offshoreCount += 1
                print("[syrmos] station \(s.id) OUTSIDE Attica: \(lat), \(lon)")
            }
        }
        if offshoreCount == 0 {
            print("[syrmos] station audit: all Athens stations inside Attica box (\(stations.count) total)")
        }

        // Hook up the CADisplayLink that smoothly animates train
        // annotations between simulator snapshots.
        context.coordinator.attach(to: mv)

        return mv
    }

    func updateUIView(_ mv: MKMapView, context: Context) {
        // Swap the flat base tiles when the app theme flips (light <-> dark).
        let dark = mv.traitCollection.userInterfaceStyle == .dark
        if context.coordinator.baseTileDark != dark {
            context.coordinator.baseTileDark = dark
            if let old = context.coordinator.baseTileOverlay { mv.removeOverlay(old) }
            let overlay = SyrmosMKMapView.makeCartoOverlay(dark: dark)
            mv.addOverlay(overlay, level: .aboveRoads)
            context.coordinator.baseTileOverlay = overlay
        }

        // Re-center on user when the ping changes. MKMapView already
        // tracks userLocation when showsUserLocation = true so we just
        // animate to it.
        if context.coordinator.lastRecenterPing != recenterToUserPing {
            context.coordinator.lastRecenterPing = recenterToUserPing
            // Only jump to the user with a real fix; a nil or (0,0) coordinate at
            // startup would fling the map into the Atlantic. Otherwise stay put.
            let userLoc = mv.userLocation.location?.coordinate
            let hasValidFix = userLoc.map {
                CLLocationCoordinate2DIsValid($0) && (abs($0.latitude) > 0.001 || abs($0.longitude) > 0.001)
            } ?? false
            let loc = hasValidFix ? userLoc! : mv.region.center
            mv.setRegion(
                MKCoordinateRegion(
                    center: loc,
                    span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
                ),
                animated: true
            )
        }

        // Sync simulated + live trains. Build a desired set and reconcile
        // against existing annotations so we move existing markers rather
        // than tearing them down each tick (smooth motion, less GPU churn).
        let wantSim = Dictionary(uniqueKeysWithValues: simulatedTrains.map { ($0.id, $0) })
        let wantLive = Dictionary(uniqueKeysWithValues: liveTrains.map { ($0.id, $0) })
        let existing = mv.annotations.compactMap { $0 as? SyrmosTrainAnnotation }
        var seenSim: Set<String> = []
        var seenLive: Set<String> = []
        // Refresh the Coordinator's per-train descriptor cache. The
        // displayLink reads these every frame; we only write here when
        // the simulator pushes a new snapshot. Position itself is
        // recomputed live, not tweened from the snapshot's coordinate.
        for ann in existing {
            switch ann.kind {
            case .simulated:
                if let train = wantSim[ann.id] {
                    context.coordinator.updateDescriptor(for: ann.id, train: train)
                    seenSim.insert(ann.id)
                } else {
                    context.coordinator.dropDescriptor(id: ann.id)
                    mv.removeAnnotation(ann)
                }
            case .live:
                if !wantLive.keys.contains(ann.id) {
                    mv.removeAnnotation(ann)
                } else {
                    seenLive.insert(ann.id)
                }
            }
        }
        for train in simulatedTrains where !seenSim.contains(train.id) {
            let ann = SyrmosTrainAnnotation(simulated: train)
            mv.addAnnotation(ann)
            ann.coordinate = train.coordinate
            context.coordinator.updateDescriptor(for: ann.id, train: train)
        }
        for train in liveTrains where !seenLive.contains(train.id) {
            let ann = SyrmosTrainAnnotation(live: train)
            mv.addAnnotation(ann)
            ann.coordinate = train.coordinate
        }
    }

    static func dismantleUIView(_ mv: MKMapView, coordinator: Coordinator) {
        coordinator.detachDisplayLink()
        mv.delegate = nil
        mv.removeOverlays(mv.overlays)
        mv.removeAnnotations(mv.annotations)
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, MKMapViewDelegate {
        let parent: SyrmosMKMapView
        var lastRecenterPing: Int = -1
        /// The active flat base-map tile overlay + its theme, so a light/dark
        /// flip can swap it without rebuilding the map.
        var baseTileOverlay: MKTileOverlay?
        var baseTileDark: Bool?
        /// Live train descriptor cache keyed by annotation id. The
        /// CADisplayLink ticks at the device's native refresh rate
        /// (60 / 120 Hz) and recomputes each train's position from
        /// `Date().timeIntervalSince1970 - originEpoch` against its
        /// stops table on every frame. No tween, no interpolation
        /// between discrete simulator snapshots — the on-screen
        /// position IS the timetable position at this exact moment.
        /// Simulator only refreshes the cache when the set of active
        /// trains changes; per-frame motion is driven by the clock.
        private struct LiveDescriptor {
            let lineId: String
            let originEpoch: TimeInterval
            let totalTravelMinutes: Int
            let stops: [(stationId: String, minutesFromOrigin: Double)]
        }
        private var descriptors: [String: LiveDescriptor] = [:]

        /// Per-line cached polyline + cumulative distances. Computed
        /// once on first use (haversine across ~200 vertices is
        /// pennies). We keep the cumulative array alongside so the
        /// per-frame lookup is a binary-walk + linear interp inside
        /// one tiny segment instead of re-summing every frame.
        private struct PolylineCache {
            let polyline: [CLLocationCoordinate2D]
            let cumulative: [Double]
            var total: Double { cumulative.last ?? 0 }
        }
        private var polylineCache: [String: PolylineCache] = [:]

        private weak var mapView: MKMapView?
        private var displayLink: CADisplayLink?

        /// Zoom-tiered decluttering, four bands (mirrors web + Android):
        /// 0 = country (lines only, no dots), 1 = near-country (major cross-modal
        /// hubs), 2 = regional (all interchanges), 3 = city (every stop). Starts
        /// at city (the initial Athens region), corrected on the first region change.
        private var currentBand = 3

        /// The visible zoom of an MKMapView isn't exposed directly, so we derive
        /// it from the longitude span and the view's pixel width using the same
        /// slippy-map maths the web build uses, keeping the thresholds consistent
        /// with MapDesignTokens (web tile zooms).
        private func approxZoom(_ mapView: MKMapView) -> Double {
            let widthPx = max(mapView.bounds.width, 1)
            let lonDelta = max(mapView.region.span.longitudeDelta, 0.0001)
            return log2(360.0 * Double(widthPx) / 256.0 / lonDelta)
        }

        private func band(for mapView: MKMapView) -> Int {
            let z = approxZoom(mapView)
            if z >= MapDesignTokens.minorStopMinZoom { return 3 }   // city: all stops
            if z >= MapDesignTokens.majorHubMinZoom { return 2 }    // regional: interchanges
            if z <= MapDesignTokens.linesOnlyMaxZoom { return 0 }   // country: lines only
            return 1                                                // near-country: major hubs
        }

        /// A major hub is a genuine cross-modal transfer: its lines span 2+
        /// distinct types. The is_interchange flag is over-applied, so this
        /// tighter rule is what the country band shows. Same rule on web + Android.
        private func isMajorHub(_ station: MapStationNode) -> Bool {
            let types = Set(station.lineIds.compactMap { SyrmosData.line(for: $0)?.type })
            return types.count >= 2
        }

        private func shouldShow(_ station: MapStationNode, band: Int) -> Bool {
            switch band {
            case 3: return true
            case 2: return station.isInterchange
            case 1: return isMajorHub(station)
            default: return false   // country: lines only
            }
        }

        init(_ parent: SyrmosMKMapView) { self.parent = parent }

        // displayLink lives for the lifetime of the Coordinator. The
        // MainActor isolation on the link prevents a non-isolated
        // deinit cleanup, but since the parent dismantles the map
        // (and nils out our reference) before the Coordinator goes
        // away in practice, we let the runtime release it on its own
        // tear-down rather than racing it from deinit.

        /// Wire up the per-frame ticker once we have a real MKMapView
        /// instance. Called from makeUIView right after the map is
        /// constructed and the delegate is hooked up.
        func attach(to mv: MKMapView) {
            mapView = mv
            if displayLink == nil {
                let link = CADisplayLink(target: self, selector: #selector(tick))
                link.preferredFrameRateRange = CAFrameRateRange(minimum: 15, maximum: 60, preferred: 30)
                link.add(to: .main, forMode: .common)
                displayLink = link
            }
        }

        /// Cache (or refresh) the descriptor for a train annotation.
        /// Stable across simulator ticks because origin epoch + stops
        /// table don't change for a given train; we only really need
        /// to write here when a new train id appears.
        func updateDescriptor(for id: String, train: SimulatedTrain) {
            descriptors[id] = LiveDescriptor(
                lineId: train.lineId,
                originEpoch: train.originEpoch,
                totalTravelMinutes: train.totalTravelMinutes,
                stops: train.stops
            )
        }

        func dropDescriptor(id: String) {
            descriptors.removeValue(forKey: id)
        }

        func detachDisplayLink() {
            displayLink?.invalidate()
            displayLink = nil
            mapView = nil
        }

        @objc private func tick() {
            guard let mv = mapView, !descriptors.isEmpty else { return }
            let now = Date().timeIntervalSince1970
            let trainAnns = mv.annotations.compactMap { $0 as? SyrmosTrainAnnotation }
            for ann in trainAnns {
                guard let d = descriptors[ann.id] else { continue }
                guard let next = livePosition(descriptor: d, nowEpoch: now) else { continue }
                if next.latitude != ann.coordinate.latitude || next.longitude != ann.coordinate.longitude {
                    ann.coordinate = next
                }
            }
        }

        /// Resolve the train's exact position on the polyline at the
        /// requested wall-clock instant. Walks the stops table to find
        /// the segment containing the elapsed minutes-from-origin,
        /// resolves the from/to station coords, then arc-interpolates
        /// inside the polyline between them. Returns nil when the
        /// train has finished its run (we let the simulator drop it
        /// on its next tick).
        private func livePosition(descriptor d: LiveDescriptor, nowEpoch: TimeInterval) -> CLLocationCoordinate2D? {
            let elapsedMin = (nowEpoch - d.originEpoch) / 60.0
            if elapsedMin < 0 { return nil }
            if elapsedMin > Double(d.totalTravelMinutes) + 0.5 { return nil }
            guard d.stops.count >= 2 else { return nil }
            var segIdx = d.stops.count - 2
            for i in 0..<(d.stops.count - 1) {
                if d.stops[i].minutesFromOrigin <= elapsedMin && elapsedMin < d.stops[i + 1].minutesFromOrigin {
                    segIdx = i
                    break
                }
            }
            let from = d.stops[segIdx]
            let to = d.stops[segIdx + 1]
            let segDur = to.minutesFromOrigin - from.minutesFromOrigin
            let frac = segDur > 0 ? min(max((elapsedMin - from.minutesFromOrigin) / segDur, 0), 1) : 0

            let coords = StationCoordinateLookup.shared
            guard let fc = coords.coordinate(for: from.stationId),
                  let tc = coords.coordinate(for: to.stationId) else { return nil }
            let fromCoord = CLLocationCoordinate2D(latitude: fc.lat, longitude: fc.lon)
            let toCoord = CLLocationCoordinate2D(latitude: tc.lat, longitude: tc.lon)

            if let cache = cachedPolyline(for: d.lineId) {
                let startDist = arcDistance(to: fromCoord, cache: cache)
                let endDist = arcDistance(to: toCoord, cache: cache)
                let target = startDist + (endDist - startDist) * frac
                let p = coordAt(distance: target, polyline: cache.polyline, cumulative: cache.cumulative)
                // Guard against a wrong arc mapping (a station snapping to a far vertex
                // on a looping / past-the-terminus shape, e.g. the T7 tram past Voula)
                // flinging the dot into the sea: a point between two stations should
                // sit within ~the segment length of BOTH. If it overshoots that (plus
                // a curve margin), fall back to the honest chord.
                let fromLoc = CLLocation(latitude: fromCoord.latitude, longitude: fromCoord.longitude)
                let toLoc = CLLocation(latitude: toCoord.latitude, longitude: toCoord.longitude)
                let pLoc = CLLocation(latitude: p.latitude, longitude: p.longitude)
                let segLen = fromLoc.distance(from: toLoc)
                if pLoc.distance(from: fromLoc) <= segLen + 600, pLoc.distance(from: toLoc) <= segLen + 600 {
                    return p
                }
            }
            // Chord fallback for lines without an OSM shape (A1-A4).
            return CLLocationCoordinate2D(
                latitude: fromCoord.latitude + (toCoord.latitude - fromCoord.latitude) * frac,
                longitude: fromCoord.longitude + (toCoord.longitude - fromCoord.longitude) * frac
            )
        }

        // MARK: - Polyline distance helpers

        private func cachedPolyline(for lineId: String) -> PolylineCache? {
            if let hit = polylineCache[lineId] { return hit }
            // M3_AIR shares the M3 OSM shape west of Doukissis Plakentias;
            // try the canonical id first and fall back to M3.
            let candidates: [String] = (lineId == "M3_AIR") ? ["M3_AIR", "M3"] : [lineId]
            for candidate in candidates {
                if let line = SyrmosRouteShapesStore.shared.coordinates(for: candidate), line.count >= 2 {
                    var cum: [Double] = [0]
                    cum.reserveCapacity(line.count)
                    for i in 0..<(line.count - 1) {
                        cum.append(cum.last! + haversineMeters(line[i], line[i + 1]))
                    }
                    let entry = PolylineCache(polyline: line, cumulative: cum)
                    polylineCache[lineId] = entry
                    return entry
                }
            }
            return nil
        }

        private func arcDistance(to coord: CLLocationCoordinate2D, cache: PolylineCache) -> Double {
            // Snap to closest polyline vertex by haversine. Good enough
            // because the polyline is dense — using closest-segment
            // perpendicular projection would be more accurate but the
            // gain is sub-metre and not visually relevant at city zoom.
            var bestIdx = 0
            var bestDist = Double.greatestFiniteMagnitude
            for (i, p) in cache.polyline.enumerated() {
                let d = haversineMeters(p, coord)
                if d < bestDist { bestDist = d; bestIdx = i }
            }
            return cache.cumulative[bestIdx]
        }

        private func coordAt(
            distance: Double,
            polyline: [CLLocationCoordinate2D],
            cumulative: [Double]
        ) -> CLLocationCoordinate2D {
            guard polyline.count >= 2, !cumulative.isEmpty else {
                return polyline.first ?? CLLocationCoordinate2D(latitude: 0, longitude: 0)
            }
            let clamped = min(max(distance, 0), cumulative.last ?? 0)
            // Linear scan; polylines are short (<300 vertices typically)
            // and the search runs at most ~30 trains × 60fps = 1800/s.
            for i in 0..<(cumulative.count - 1) {
                if cumulative[i + 1] >= clamped {
                    let segLen = cumulative[i + 1] - cumulative[i]
                    let segFrac = segLen > 0 ? (clamped - cumulative[i]) / segLen : 0
                    let a = polyline[i]
                    let b = polyline[i + 1]
                    return CLLocationCoordinate2D(
                        latitude: a.latitude + (b.latitude - a.latitude) * segFrac,
                        longitude: a.longitude + (b.longitude - a.longitude) * segFrac
                    )
                }
            }
            return polyline.last ?? polyline[0]
        }

        private func haversineMeters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
            let r = 6371000.0
            let dLat = (b.latitude - a.latitude) * .pi / 180
            let dLon = (b.longitude - a.longitude) * .pi / 180
            let lat1 = a.latitude * .pi / 180
            let lat2 = b.latitude * .pi / 180
            let h = sin(dLat / 2) * sin(dLat / 2)
                + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(min(1, sqrt(h)))
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tile)
            }
            if let p = overlay as? SyrmosColoredPolyline {
                let r = MKPolylineRenderer(polyline: p)
                // Web draws in-service lines at 0.9 opacity, round caps/joins.
                r.strokeColor = p.color.withAlphaComponent(0.9)
                r.lineWidth = p.weight
                r.lineCap = .round
                r.lineJoin = .round
                if p.dashed {
                    // Dashed + translucent so track that is built but not open
                    // reads as inert and can never be mistaken for live service.
                    r.lineDashPattern = [6, 8]
                    r.strokeColor = p.color.withAlphaComponent(0.6)
                }
                return r
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            if let station = annotation as? SyrmosStationAnnotation {
                let id = "station"
                let v = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                    ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
                v.annotation = annotation
                v.canShowCallout = false
                let primary = station.station.lineIds.first ?? "M3"
                let image = stationImage(for: station.station, primaryLineId: primary)
                v.image = image
                v.frame.size = image.size
                v.centerOffset = .zero
                // Three-tier decluttering: hide stops not in the current zoom band.
                v.isHidden = !shouldShow(station.station, band: currentBand)
                return v
            }
            if let train = annotation as? SyrmosTrainAnnotation {
                let id = "train"
                let v = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                    ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
                v.annotation = annotation
                v.canShowCallout = false
                v.image = trainImage(for: train)
                v.frame.size = CGSize(width: 28, height: 22)
                // Same decluttering rule as stations: below the regional band the
                // whole fleet would pile into one blob on the coastline, so hide
                // vehicles until the map is zoomed in far enough to place them on
                // their lines (band >= 2 == MapDesignTokens.majorHubMinZoom).
                v.isHidden = currentBand < 2
                return v
            }
            return nil
        }

        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            let b = band(for: mapView)
            currentBand = b
            // Padded visible region: cull stations outside it so the now-nationwide
            // network (389 stops) stays light and distant coastal lines (Katakolo,
            // Corinth-Patras) don't render at the edges over the sea. Runs on every
            // region change (pan + zoom), not just band changes.
            let region = mapView.region
            let padLat = region.span.latitudeDelta * 0.7
            let padLon = region.span.longitudeDelta * 0.7
            let minLat = region.center.latitude - region.span.latitudeDelta / 2 - padLat
            let maxLat = region.center.latitude + region.span.latitudeDelta / 2 + padLat
            let minLon = region.center.longitude - region.span.longitudeDelta / 2 - padLon
            let maxLon = region.center.longitude + region.span.longitudeDelta / 2 + padLon
            func inView(_ c: CLLocationCoordinate2D) -> Bool {
                c.latitude >= minLat && c.latitude <= maxLat && c.longitude >= minLon && c.longitude <= maxLon
            }
            for annotation in mapView.annotations {
                guard let view = mapView.view(for: annotation) else { continue }
                if let station = annotation as? SyrmosStationAnnotation {
                    view.isHidden = !inView(station.coordinate) || !shouldShow(station.station, band: b)
                } else if annotation is SyrmosTrainAnnotation {
                    // Vehicles follow the station decluttering rule: hidden below
                    // the regional band so the fleet never piles into a coastal blob.
                    view.isHidden = b < 2
                }
            }
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let station = view.annotation as? SyrmosStationAnnotation else { return }
            mapView.deselectAnnotation(view.annotation, animated: false)
            parent.onStationTap(station.station.id)
        }

        private func stationImage(for station: MapStationNode, primaryLineId: String) -> UIImage {
            // A clean line-coloured disc with a crisp white ring - a pixel
            // mirror of the web Leaflet `circleMarker` (stationStyle). We used
            // to draw bundled per-station artwork here (the ISAP/Syntagma icons
            // with baked-in "M3 AM" text), which is why iOS looked nothing like
            // web; web draws none of that, only these dots, with the per-station
            // glyph appearing on selection. Colour is the primary line's raw hex
            // from lines.json (same source web reads), so dots match the
            // polylines exactly - not the hardcoded metro* theme constants.
            let color = UIColor(SyrmosData.line(for: primaryLineId)?.color
                ?? SyrmosData.lineColor(for: primaryLineId))
            // Web radii: 4.5 default / 5.5 interchange (px, non-retina). Draw at
            // 2x and let the annotation view show it crisp. Interchange reads as
            // a white-cored "target" ring; a plain stop is a filled dot.
            let diameter: CGFloat = 15
            let ringWidth: CGFloat = 3
            let size = CGSize(width: diameter, height: diameter)
            let renderer = UIGraphicsImageRenderer(size: size)
            return renderer.image { ctx in
                let cg = ctx.cgContext
                let outer = CGRect(origin: .zero, size: size)
                cg.setFillColor(UIColor.white.cgColor)
                cg.fillEllipse(in: outer)
                if station.isInterchange {
                    cg.setFillColor(color.cgColor)
                    cg.fillEllipse(in: outer.insetBy(dx: ringWidth * 0.5, dy: ringWidth * 0.5))
                    cg.setFillColor(UIColor.white.cgColor)
                    cg.fillEllipse(in: outer.insetBy(dx: ringWidth * 1.8, dy: ringWidth * 1.8))
                } else {
                    cg.setFillColor(color.cgColor)
                    cg.fillEllipse(in: outer.insetBy(dx: ringWidth, dy: ringWidth))
                }
            }
        }

        /// A directional triangle for national rail + rail-replacement buses +
        /// suburban A-lines (no per-line sprite). Rotated to the travel heading
        /// (compass bearing, 0 = north), coloured by line with a white outline -
        /// the iOS mirror of the web + Android triangle markers.
        static func triangleTrainImage(color: UIColor, bearing: Double) -> UIImage {
            let size = CGSize(width: 22, height: 22)
            let renderer = UIGraphicsImageRenderer(size: size)
            return renderer.image { ctx in
                let cg = ctx.cgContext
                cg.translateBy(x: size.width / 2, y: size.height / 2)
                cg.rotate(by: CGFloat(bearing) * .pi / 180) // clockwise, 0 = up = north
                let r = size.width * 0.42
                let path = UIBezierPath()
                path.move(to: CGPoint(x: 0, y: -r))
                path.addLine(to: CGPoint(x: r * 0.82, y: r * 0.72))
                path.addLine(to: CGPoint(x: -r * 0.82, y: r * 0.72))
                path.close()
                color.setFill(); path.fill()
                path.lineWidth = size.width * 0.09
                path.lineJoinStyle = .round
                UIColor.white.setStroke(); path.stroke()
            }
        }

        private func trainImage(for train: SyrmosTrainAnnotation) -> UIImage {
            // Every moving vehicle is a single heading-rotated directional
            // triangle in the line colour - a pixel mirror of web's one
            // `trainMarkerIcon` for the whole fleet. Native used to hand metro
            // and tram their own per-line sprite artwork, which is exactly why
            // the trains looked nothing like web; web draws only triangles.
            // Colour is the primary line's raw hex from lines.json (same source
            // web reads), so a moving train matches its own line.
            let lineId: String
            let bearing: Double
            switch train.kind {
            case .simulated(let t): lineId = t.lineId; bearing = t.bearing
            case .live(let t):      lineId = t.lineId; bearing = 0
            }
            let color = UIColor(SyrmosData.line(for: lineId)?.color
                ?? SyrmosData.lineColor(for: lineId))
            return Self.triangleTrainImage(color: color, bearing: bearing)
        }
    }
}

/// Polyline that carries its render colour + weight so the
/// MKOverlayRenderer can pick them without an extra lookup. Mirrors the
/// helper StationMapSheet uses; we keep it local to MapView.swift to avoid
/// reaching across files for a 4-line class.
final class SyrmosColoredPolyline: MKPolyline {
    var color: UIColor = .systemBlue
    var weight: CGFloat = 4
    var dashed: Bool = false
}

final class SyrmosStationAnnotation: NSObject, MKAnnotation {
    let station: MapStationNode
    init(station: MapStationNode) { self.station = station }
    var coordinate: CLLocationCoordinate2D { station.coordinate }
    var title: String? { station.displayName }
}

final class SyrmosTrainAnnotation: NSObject, MKAnnotation {
    enum Kind { case simulated(SimulatedTrain); case live(LiveTrain) }
    let kind: Kind
    @objc dynamic var coordinate: CLLocationCoordinate2D
    var id: String {
        switch kind {
        case .simulated(let t): return "sim:\(t.id)"
        case .live(let t):      return "live:\(t.id)"
        }
    }
    /// Line the train rides, used by the Coordinator's tween system to
    /// look up the matching polyline cache entry. M3_AIR shares the
    /// M3 polyline west of Doukissis Plakentias, so we keep the raw id
    /// here and let the cache lookup decide on the fallback.
    var lineId: String {
        switch kind {
        case .simulated(let t): return t.lineId
        case .live(let t):      return t.lineId
        }
    }
    init(simulated: SimulatedTrain) {
        self.kind = .simulated(simulated)
        self.coordinate = simulated.coordinate
    }
    init(live: LiveTrain) {
        self.kind = .live(live)
        self.coordinate = live.coordinate
    }
}
