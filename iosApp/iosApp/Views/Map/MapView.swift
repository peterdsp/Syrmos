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
        let isInbound = train.direction == "inbound"
        switch train.lineId {
        case "M1": return isInbound ? "metro_m1_left_to_piraeus" : "metro_m1_right_to_kifissia"
        case "M2": return isInbound ? "metro_m2_left_to_anthoupoli" : "metro_m2_right_to_elliniko"
        case "M3":
            if train.isAirportService { return "metro_m3_right_to_airport" }
            return isInbound ? "metro_m3_left_to_dimotiko_theatro" : "metro_m3_right_to_doukissis_plakentias"
        case "T6": return isInbound ? "tram_t6_left_to_syntagma" : "tram_t6_right_to_pikrodafni"
        case "T7": return isInbound ? "tram_t7_left_to_akti_posidonos" : "tram_t7_right_to_asklipiio_voulas"
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
    @State private var position: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.980, longitude: 23.730),
            span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
        )
    )
    @State private var selectedId: String?
    @State private var tappedStation: MapStationNode?
    @State private var showLocationDeniedAlert = false
    /// 0 = country view (huge span), 1 = city, 2 = district, 3 = street.
    /// Mirrors the web buckets so pins look consistent across platforms.
    @State private var zoomBucket: Int = 2
    /// When true, hide all moving train/tram annotations so the user can see
    /// just the lines + stations. Persists across navigation but resets on
    /// cold launch (deliberate: it's a quick toggle, not a setting).
    @State private var vehiclesHidden = false
    /// Force-rebuild key for the SwiftUI Map. iOS 18's Map(position:) has a
    /// CAMetalLayer lifecycle bug where the layer stops rendering after the
    /// system intercepts the app (screenshot, control center, lock/unlock),
    /// leaving the user staring at a black canvas. Incrementing this id on
    /// every screenshot + scenePhase resume forces SwiftUI to discard the
    /// broken view and instantiate a fresh one - hundreds of times cheaper
    /// than the full UIViewRepresentable rewrite.
    @State private var mapRebuildKey: Int = 0
    @Environment(\.scenePhase) private var scenePhase

    private let stations = PreloadedData.stations
    private let routeLines = PreloadedData.routeLines

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                // Wrapping the Map in a Group with `.id()` on the Group (not on
                // Map itself) is what actually forces SwiftUI to dispose of the
                // underlying MKMapView on iOS 18. Putting `.id()` directly on
                // Map(position:) is silently no-op'd by MapKit-for-SwiftUI's
                // internal view reuse, so the dead Metal layer survives. The
                // Group wrapper bypasses that reuse and gives us a guaranteed
                // fresh instance after screenshot or scenePhase resume.
                Group {
                    Map(position: $position, selection: $selectedId) {
                        UserAnnotation()

                        ForEach(routeLines) { route in
                            MapPolyline(coordinates: route.coordinates)
                                .stroke(route.color, lineWidth: route.lineWeight)
                        }

                        ForEach(stations) { station in
                            Annotation(station.displayName, coordinate: station.coordinate) {
                                StationDot(
                                    station: station,
                                    isSelected: selectedId == station.id,
                                    zoomBucket: zoomBucket
                                )
                                    .onTapGesture {
                                        selectedId = station.id
                                    }
                            }
                            .tag(station.id)
                        }

                        if !vehiclesHidden {
                            ForEach(trainSimulator.trains) { train in
                                Annotation(
                                    "\(train.lineName) → \(train.destinationName)",
                                    coordinate: train.coordinate
                                ) {
                                    SimulatedTrainDot(train: train)
                                }
                            }

                            ForEach(liveTrainService.trains) { train in
                                Annotation(train.trainNumber, coordinate: train.coordinate) {
                                    LiveTrainMarker(lineId: train.lineId)
                                }
                            }
                        }
                    }
                    .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll, showsTraffic: false))
                    .mapControls {
                        MapCompass()
                        MapScaleView()
                    }
                    .onMapCameraChange(frequency: .onEnd) { ctx in
                        // Span thresholds match the web's zoom buckets:
                        //   z >= 14 (street)   => bucket 3 = full SVG
                        //   z >= 12 (district) => bucket 2 = SVG slightly smaller
                        //   z >= 10 (city)     => bucket 1 = colored mode-pin
                        //   else (country)     => bucket 0 = tiny colored dot
                        let span = ctx.region.span.latitudeDelta
                        let next: Int
                        switch span {
                        case ..<0.05: next = 3
                        case ..<0.18: next = 2
                        case ..<0.6:  next = 1
                        default:      next = 0
                        }
                        if next != zoomBucket { zoomBucket = next }
                    }
                }
                .id(mapRebuildKey)
                .onChange(of: selectedId) { _, newId in
                    guard let id = newId,
                          let station = stations.first(where: { $0.id == id }) else { return }
                    tappedStation = station
                }
                .onReceive(NotificationCenter.default.publisher(
                    for: UIApplication.userDidTakeScreenshotNotification
                )) { _ in
                    // Screenshot (volume-up + side-button) briefly resigns the
                    // app; the SwiftUI Map's CAMetalLayer can come back dead.
                    // Bumping the rebuild key on the Group wrapper forces a
                    // genuinely fresh MKMapView instead of the silently reused
                    // one MapKit-for-SwiftUI hands back on `.id()` on Map alone.
                    mapRebuildKey &+= 1
                }
                .onChange(of: scenePhase) { oldPhase, newPhase in
                    // Same protection on lock/unlock, control-center swipe, app
                    // switcher, notification banners. Rebuild only on the
                    // background/inactive → active edge so we don't churn the
                    // map every time a `.onChange` fires with the same phase.
                    if oldPhase != .active && newPhase == .active {
                        mapRebuildKey &+= 1
                    }
                }

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
                            ? (loc.language == .greek ? "Εμφάνιση οχημάτων" : "Show vehicles")
                            : (loc.language == .greek ? "Απόκρυψη οχημάτων" : "Hide vehicles")
                    )

                    Button {
                        let result = locationManager.requestOrPrompt()
                        switch result {
                        case .authorized, .promptShown:
                            position = .userLocation(followsHeading: false, fallback: .automatic)
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
            .navigationTitle(loc[.map])
            .sheet(item: $tappedStation, onDismiss: { selectedId = nil }) { station in
                StationSheetView(station: station)
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
            .alert(
                loc.language == .greek ? "Η τοποθεσία είναι απενεργοποιημένη" : "Location is disabled",
                isPresented: $showLocationDeniedAlert
            ) {
                Button(loc.language == .greek ? "Άνοιγμα Ρυθμίσεων" : "Open Settings") {
                    locationManager.openSystemSettings()
                }
                Button(loc.language == .greek ? "Άκυρο" : "Cancel", role: .cancel) {}
            } message: {
                Text(loc.language == .greek
                    ? "Δεν έχετε δώσει άδεια τοποθεσίας στο Syrmos. Θέλετε να ανοίξετε τις Ρυθμίσεις για να την ενεργοποιήσετε;"
                    : "You haven't granted Syrmos location access. Would you like to open Settings to enable it?")
            }
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
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                lineBadges
                stationFactsChips
                if !departures.isEmpty { departuresList }
                directionsButton
            }
            .padding()
        }
        .onAppear(perform: reloadDepartures)
        .onReceive(refreshTimer) { _ in reloadDepartures() }
    }

    private func reloadDepartures() {
        var next = station.lineIds.flatMap { lineId in
            let stationId = station.stationIdByLineId[lineId] ?? station.stationIds.first ?? station.id
            return SyrmosData.sampleDepartures(for: stationId, lineIds: [lineId])
        }
        .sorted { $0.minutesAway < $1.minutesAway }
        if next.count > 6 { next = Array(next.prefix(6)) }
        departures = next
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text(loc.language == .greek ? station.nameEl : station.displayName)
                    .font(.title2)
                    .fontWeight(.bold)
                Text(loc.language == .greek ? station.displayName : station.nameEl)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
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
            .accessibilityLabel(loc.language == .greek ? "Κλείσιμο" : "Close")
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
                         label: loc.language == .greek ? "Ανταπόκριση" : "Interchange")
            }
        }
    }

    private var departuresList: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(loc.language == .greek ? "Επόμενα Δρομολόγια" : "Next departures")
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
                Text(loc.language == .greek ? "Οδηγίες" : "Get directions")
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
                        Text(loc.language == .greek ? "Αεροδρόμιο" : "Airport")
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
                    : "to \(departure.direction)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 1) {
                Text(departure.minutesAway <= 1
                    ? (loc.language == .greek ? "Τώρα" : "Now")
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
