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
        } else if stations.count >= 2 {
            coords = catmullRomSpline(stations.map { $0.coordinate })
        } else {
            return nil
        }
        return RouteLine(
            id: line.id,
            color: line.color,
            coordinates: coords,
            lineWeight: line.type == .suburban ? 3 : 4
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
                    simulatedTrains: vehiclesHidden ? [] : trainSimulator.trains,
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
                .frame(width: isSelected ? 36 : 28, height: isSelected ? 36 : 28)
                .shadow(color: .black.opacity(isSelected ? 0.3 : 0.15), radius: isSelected ? 4 : 2, y: 1)
        } else {
            midZoomBody
        }
    }

    /// District-level: colored teardrop pin with SF Symbol mode glyph.
    private var midZoomBody: some View {
        let size: CGFloat = isSelected ? 30 : 24
        return ZStack {
            Image(systemName: "mappin.circle.fill")
                .resizable()
                .symbolRenderingMode(.palette)
                .foregroundStyle(.white, primaryColor)
                .frame(width: size, height: size)
                .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
            Image(systemName: primaryModeSymbol)
                .font(.system(size: size * 0.42, weight: .bold))
                .foregroundStyle(primaryColor)
                .offset(y: -size * 0.04)
            if station.isInterchange {
                interchangeRingsBadge
                    .offset(x: size * 0.34, y: -size * 0.34)
            }
        }
        .scaleEffect(isSelected ? 1.1 : 1.0)
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
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 1) {
                Text(departure.minutesAway <= 1
                    ? (loc.language == .greek ? "Τώρα" : loc.language == .albanian ? "Tani" : "Now")
                    : "\(departure.minutesAway) min")
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

    func makeUIView(context: Context) -> MKMapView {
        let mv = MKMapView()
        mv.delegate = context.coordinator
        mv.pointOfInterestFilter = .excludingAll
        mv.showsUserLocation = true
        mv.isPitchEnabled = false
        mv.region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
        )

        // Route polylines. ColoredPolyline carries colour + weight so the
        // renderer can pick them without an external lookup table.
        for route in routeLines {
            let poly = SyrmosColoredPolyline(coordinates: route.coordinates, count: route.coordinates.count)
            poly.color = UIColor(route.color)
            poly.weight = route.lineWeight
            mv.addOverlay(poly)
        }

        for station in stations {
            mv.addAnnotation(SyrmosStationAnnotation(station: station))
        }

        // Hook up the CADisplayLink that smoothly animates train
        // annotations between simulator snapshots.
        context.coordinator.attach(to: mv)

        return mv
    }

    func updateUIView(_ mv: MKMapView, context: Context) {
        // Re-center on user when the ping changes. MKMapView already
        // tracks userLocation when showsUserLocation = true so we just
        // animate to it.
        if context.coordinator.lastRecenterPing != recenterToUserPing {
            context.coordinator.lastRecenterPing = recenterToUserPing
            let loc = mv.userLocation.location?.coordinate
                ?? mv.region.center
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
                return coordAt(distance: target, polyline: cache.polyline, cumulative: cache.cumulative)
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
            if let p = overlay as? SyrmosColoredPolyline {
                let r = MKPolylineRenderer(polyline: p)
                r.strokeColor = p.color
                r.lineWidth = p.weight
                r.lineCap = .round
                r.lineJoin = .round
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
                return v
            }
            return nil
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            guard let station = view.annotation as? SyrmosStationAnnotation else { return }
            mapView.deselectAnnotation(view.annotation, animated: false)
            parent.onStationTap(station.station.id)
        }

        private func stationImage(for station: MapStationNode, primaryLineId: String) -> UIImage {
            // Try the bundled per-station artwork first (M1_PIR -> the
            // ISAP Piraeus icon, M3_SYN -> the Syntagma multi-line icon,
            // T7_DIM -> the Dimotiko Theatro tram artwork etc). The
            // mapping table lives on PreloadedData.stationIconMap and
            // covers every M1/M2/M3/T6/T7 stop plus the curated multi-
            // mode interchanges. Asset catalog supplies @1x/@2x/@3x.
            let primaryStationId = station.stationIds.first ?? station.id
            if let assetName = PreloadedData.stationIconMap[primaryStationId],
               let raw = UIImage(named: assetName) {
                let target = CGSize(width: 28, height: 28)
                let renderer = UIGraphicsImageRenderer(size: target)
                return renderer.image { _ in
                    raw.draw(in: CGRect(origin: .zero, size: target))
                }
            }
            // Fallback for suburban (A1-A4) and any station without a
            // bundled SVG: a coloured teardrop pin with the line tint
            // and a white outline so it reads on light + dark map tiles.
            let size = CGSize(width: 22, height: 22)
            let renderer = UIGraphicsImageRenderer(size: size)
            let color = UIColor(SyrmosData.lineColor(for: primaryLineId))
            return renderer.image { ctx in
                let cg = ctx.cgContext
                let outer = CGRect(origin: .zero, size: size)
                let inner = outer.insetBy(dx: 3, dy: 3)
                cg.setFillColor(UIColor.white.cgColor)
                cg.fillEllipse(in: outer)
                cg.setFillColor(color.cgColor)
                cg.fillEllipse(in: inner)
                if station.isInterchange {
                    cg.setStrokeColor(UIColor.white.cgColor)
                    cg.setLineWidth(1.5)
                    cg.strokeEllipse(in: inner.insetBy(dx: 3, dy: 3))
                }
            }
        }

        private func trainImage(for train: SyrmosTrainAnnotation) -> UIImage {
            // Prefer the bundled per-line, per-direction vehicle artwork
            // (metro_m1_left_to_piraeus, tram_t7_right_to_asklipiio_voulas
            // etc). Same path that VehicleIcons.imageName(for:) drives in
            // the SwiftUI dot fallback. Only when no asset is bundled
            // (e.g. suburban A1-A4 don't ship train sprites yet) do we
            // fall back to the coloured rounded rect.
            let iconName: String?
            switch train.kind {
            case .simulated(let t): iconName = VehicleIcons.imageName(for: t)
            case .live(let t):      iconName = VehicleIcons.imageName(for: t)
            }
            if let n = iconName, let raw = UIImage(named: n) {
                let target = CGSize(width: 44, height: 30)
                let renderer = UIGraphicsImageRenderer(size: target)
                return renderer.image { _ in
                    raw.draw(in: CGRect(origin: .zero, size: target))
                }
            }
            let size = CGSize(width: 28, height: 22)
            let color: UIColor
            switch train.kind {
            case .simulated(let t): color = UIColor(SyrmosData.lineColor(for: t.lineId))
            case .live(let t):      color = UIColor(SyrmosData.lineColor(for: t.lineId))
            }
            let renderer = UIGraphicsImageRenderer(size: size)
            return renderer.image { ctx in
                let cg = ctx.cgContext
                let outer = CGRect(origin: .zero, size: size)
                let path = UIBezierPath(roundedRect: outer, cornerRadius: 6)
                cg.setFillColor(UIColor.white.cgColor)
                cg.addPath(path.cgPath); cg.fillPath()
                let inner = outer.insetBy(dx: 2, dy: 2)
                let innerPath = UIBezierPath(roundedRect: inner, cornerRadius: 5)
                cg.setFillColor(color.cgColor)
                cg.addPath(innerPath.cgPath); cg.fillPath()
            }
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
