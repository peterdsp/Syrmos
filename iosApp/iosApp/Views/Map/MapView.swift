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
        let stations = SyrmosData.stations(for: line.id)
        guard stations.count >= 2 else { return nil }
        let raw = stations.map { $0.coordinate }
        return RouteLine(
            id: line.id,
            color: line.color,
            coordinates: catmullRomSpline(raw),
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
    @State private var mapLoaded = false
    @State private var showLocationDeniedAlert = false

    private let stations = PreloadedData.stations
    private let routeLines = PreloadedData.routeLines

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottomTrailing) {
                Map(position: $position, selection: $selectedId) {
                    UserAnnotation()

                    ForEach(routeLines) { route in
                        MapPolyline(coordinates: route.coordinates)
                            .stroke(route.color, lineWidth: route.lineWeight)
                    }

                    ForEach(stations) { station in
                        Annotation(station.displayName, coordinate: station.coordinate) {
                            StationDot(station: station, isSelected: selectedId == station.id)
                                .onTapGesture {
                                    selectedId = station.id
                                }
                        }
                        .tag(station.id)
                    }

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
                            TrainDot()
                        }
                    }
                }
                .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll, showsTraffic: false))
                .mapControls {
                    MapCompass()
                    MapScaleView()
                }
                .onAppear { mapLoaded = true }
                .onChange(of: selectedId) { _, newId in
                    guard let id = newId,
                          let station = stations.first(where: { $0.id == id }) else { return }
                    tappedStation = station
                }

                if !mapLoaded {
                    Color(.systemBackground)
                        .ignoresSafeArea()
                }

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

    var body: some View {
        Group {
            if let iconName = stationIconName,
               let uiImage = UIImage(named: iconName) {
                Image(uiImage: uiImage)
                    .resizable()
                    .frame(width: isSelected ? 36 : 28, height: isSelected ? 36 : 28)
                    .shadow(color: .black.opacity(isSelected ? 0.3 : 0.15), radius: isSelected ? 4 : 2, y: 1)
            } else if station.isInterchange {
                ZStack {
                    ForEach(Array(station.lineIds.enumerated()), id: \.element) { index, lineId in
                        Circle()
                            .fill(SyrmosData.lineColor(for: lineId))
                            .frame(width: 18, height: 18)
                            .offset(x: CGFloat(index - station.lineIds.count / 2) * 6)
                    }
                    Circle()
                        .fill(.white)
                        .frame(width: 8, height: 8)
                }
                .scaleEffect(isSelected ? 1.3 : 1.0)
            } else {
                ZStack {
                    Circle()
                        .fill(SyrmosData.lineColor(for: station.lineIds.first ?? "M3"))
                        .frame(width: 16, height: 16)
                    Circle()
                        .fill(.white)
                        .frame(width: 6, height: 6)
                }
                .scaleEffect(isSelected ? 1.3 : 1.0)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: isSelected)
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
