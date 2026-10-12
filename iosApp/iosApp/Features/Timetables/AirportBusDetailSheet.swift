import SwiftUI
import CoreLocation

struct AirportBusDetail: Identifiable, Equatable {
    let id: String
    let route: String
    let origin: String
    let destination: String
    let operatorName: String
    let sourceURL: URL
    let confidence: SourceConfidence

    static func from(row: AirportListRow, language: AppLanguage) -> AirportBusDetail? {
        guard ["X93", "X95", "X96", "X97"].contains(row.route) else { return nil }
        let origin: String
        switch row.route {
        case "X93": origin = "Kifisos"
        case "X95": origin = "Syntagma"
        case "X96": origin = detailText(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo")
        default: origin = detailText(language, "Elliniko", "Ελληνικό", "Elliniko", "Elliniko")
        }
        return AirportBusDetail(id: row.route, route: row.route, origin: origin,
                                destination: detailText(language, "Athens Airport", "Αεροδρόμιο Αθηνών", "Aeroporti i Athinës", "Aeroporto di Atene"),
                                operatorName: "OASA", sourceURL: URL(string: "https://www.oasa.gr/en/telematics/")!, confidence: row.confidence)
    }
}

/// Unified airport bus detail with support for all operators and features
struct UnifiedAirportBusDetail: Identifiable, Equatable {
    let id: String
    let route: String
    let routeId: String
    let operatorName: String
    let origin: String
    let destination: String
    let airport: String
    let colorHex: String
    let confidence: SourceConfidence
    let sourceURL: URL?
    let eta: Int?
    let vehiclePositions: [AirportBusVehicles.AirportBusVehicle]
    let alerts: [AirportBusService.ServiceAlert]
    let hasVehicles: Bool
    let direction: String?
    let freshness: TimeInterval?
    let attribution: String?

    var displayEta: String {
        if let eta = eta {
            return eta <= 1 ? "Arriving now" : "\(eta) min"
        }
        return "No live vehicle currently reported"
    }

    var directionLabel: String {
        if let direction = direction {
            switch direction {
            case "to_airport": return "Toward airport"
            case "from_airport": return "Away from airport"
            default: return "Direction unknown"
            }
        }
        return "Direction unknown"
    }

    static func == (lhs: UnifiedAirportBusDetail, rhs: UnifiedAirportBusDetail) -> Bool {
        lhs.id == rhs.id && lhs.route == rhs.route && lhs.operatorName == rhs.operatorName
    }
}

private func detailText(_ language: AppLanguage, _ en: String, _ el: String, _ sq: String, _ it: String) -> String {
    switch language { case .greek: return el; case .albanian: return sq; case .italian: return it; default: return en }
}

/// Simple UIViewRepresentable for displaying MapKit content
import MapKit

struct VehicleMapView: UIViewRepresentable {
    let vehicles: [AirportBusVehicles.AirportBusVehicle]
    let airportCoordinate: CLLocationCoordinate2D?
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.mapType = .standard
        mapView.isRotateEnabled = false
        mapView.isPitchEnabled = false
        return mapView
    }

    func updateUIView(_ mapView: MKMapView, context: Context) {
        // Clear existing annotations
        mapView.removeAnnotations(mapView.annotations)
        
        // Add new annotations for vehicles
        for vehicle in vehicles {
            let annotation = MKPointAnnotation()
            annotation.coordinate = vehicle.coordinate
            annotation.title = vehicle.lineId
            annotation.subtitle = vehicle.toAirport == true ? "To airport" : vehicle.toAirport == false ? "From airport" : "Unknown"
            mapView.addAnnotation(annotation)
        }
        
        // Center on airport if available and there are vehicles
        if let airportCoord = airportCoordinate, !vehicles.isEmpty {
            let region = MKCoordinateRegion(
                center: airportCoord,
                latitudinalMeters: 5000,
                longitudinalMeters: 5000
            )
            mapView.setRegion(region, animated: true)
        } else if !vehicles.isEmpty, let first = vehicles.first {
            let region = MKCoordinateRegion(
                center: first.coordinate,
                latitudinalMeters: 5000,
                longitudinalMeters: 5000
            )
            mapView.setRegion(region, animated: true)
        }
    }
}

struct AirportBusDetailSheet: View {
    let bus: AirportBusDetail
    let live: AirportBusService.LiveAirportBuses?
    let language: AppLanguage
    @Environment(\.openURL) private var openURL

    private var eta: Int? { live?.soonest(bus.route) }
    private var freshness: String {
        if let date = live?.updatedAt {
            return "Updated \(date.formatted(date: .omitted, time: .shortened)) · OASA live feed via Syrmos API"
        }
        return "Live data unavailable · route information from OASA"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(spacing: 12) {
                        Text(bus.route).font(.title3.bold()).foregroundStyle(.white)
                            .frame(width: 58, height: 42).background(SyrmosTokens.warning, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading) {
                            Text("OASA airport express").font(.headline)
                            Text("\(bus.origin) ↔ \(bus.destination)").font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    VStack(alignment: .leading, spacing: 6) {
                        Text(eta.map { $0 <= 1 ? "Arriving now" : "\($0) min" } ?? "No live vehicle currently reported")
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                        Text(eta == nil ? "This is not a live departure. Check the official operator information for current service." : "Direction at the airport stop: toward the airport")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    Text(freshness).font(.caption).foregroundStyle(.secondary)
                    Label("24-hour airport express service is shown as operator information, not as a guaranteed current departure.", systemImage: "info.circle")
                        .font(.caption).foregroundStyle(.secondary)
                    Button { openURL(bus.sourceURL) } label: {
                        Label("Open official OASA information", systemImage: "arrow.up.right.square")
                            .frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent)
                }.padding(20)
            }
            .navigationTitle(bus.route)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// New unified detail sheet with support for all features
struct UnifiedAirportBusDetailSheet: View {
    let detail: UnifiedAirportBusDetail
    let language: AppLanguage
    @Environment(\.openURL) private var openURL

    // Compute airport coordinate based on operator
    private var airportCoordinate: CLLocationCoordinate2D? {
        if detail.airport == "AIA" {
            return CLLocationCoordinate2D(latitude: 37.9364, longitude: 23.9045)
        } else if detail.airport == "SKG" {
            return CLLocationCoordinate2D(latitude: 40.51972, longitude: 22.97083)
        }
        return nil
    }

    private var color: Color {
        Color(hex: detail.colorHex)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    // Route header
                    HStack(spacing: 12) {
                        Text(detail.route).font(.title3.bold()).foregroundStyle(.white)
                            .frame(width: 58, height: 42).background(color, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading) {
                            Text(detail.operatorName).font(.headline)
                                .foregroundStyle(detail.operatorName == "OASA" ? .primary : .primary)
                            Text("\(detail.origin) ↔ \(detail.destination)").font(.subheadline).foregroundStyle(.secondary)
                        }
                    }

                    // ETA display
                    VStack(alignment: .leading, spacing: 6) {
                        Text(detail.displayEta)
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                        Text(detail.directionLabel)
                            .font(.subheadline).foregroundStyle(.secondary)
                    }

                    // Confidence indicator
                    if detail.confidence != .unknown {
                        HStack(spacing: 8) {
                            SourceConfidenceChip(confidence: detail.confidence, language: language)
                            Spacer()
                        }
                    }

                    // Vehicle positions map (if available)
                    if detail.hasVehicles && !detail.vehiclePositions.isEmpty && airportCoordinate != nil {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Live vehicle positions".localized(language))
                                .font(.subheadline).foregroundStyle(.secondary)
                            VehicleMapView(
                                vehicles: detail.vehiclePositions,
                                airportCoordinate: airportCoordinate
                            )
                            .frame(height: 300)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color(.systemGray4), lineWidth: 1)
                            )
                            Text("\(detail.vehiclePositions.count) vehicle(s) on route")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }

                    // Alerts section
                    if !detail.alerts.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Service alerts".localized(language))
                                .font(.headline)
                            ForEach(detail.alerts) { alert in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(alert.title)
                                        .font(.subheadline).bold()
                                    Text(alert.summary)
                                        .font(.caption).foregroundStyle(.secondary)
                                    if let url = alert.url {
                                        Button {
                                            openURL(url)
                                        } label: {
                                            Text("More information".localized(language))
                                                .font(.caption)
                                        }
                                    }
                                }
                                .padding(12)
                                .background(Color(.systemBackground))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(severityColor(alert.severity), lineWidth: 2)
                                )
                            }
                        }
                    }

                    // Freshness and attribution
                    VStack(alignment: .leading, spacing: 4) {
                        if let freshness = detail.freshness {
                            Text("Updated \(TimeInterval formatting)".localized(language))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        if let attribution = detail.attribution {
                            Text(attribution)
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }

                    // Official information button
                    if let sourceURL = detail.sourceURL {
                        Button {
                            openURL(sourceURL)
                        } label: {
                            Label("Open official operator information", systemImage: "arrow.up.right.square")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                }
                .padding(20)
            }
            .navigationTitle(detail.route)
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func severityColor(_ severity: String) -> Color {
        switch severity.lowercased() {
        case "high", "critical", "danger": return .red
        case "medium", "warning": return .orange
        case "low", "info": return .blue
        default: return .gray
        }
    }
}

// MARK: - Helper for creating unified detail from various sources

extension UnifiedAirportBusDetail {
    static func fromUnified(
        operator: String,
        route: String,
        routeData: AirportBusService.UnifiedRoute?,
        operatorData: AirportBusService.OperatorData?,
        unifiedBuses: AirportBusService.UnifiedAirportBuses?,
        language: AppLanguage
    ) -> UnifiedAirportBusDetail? {
        guard let operatorData = operatorData else { return nil }
        
        let eta = operatorData.etasByLine[route]?.first
        let vehicles = operatorData.vehicles.filter { $0.lineId == route }
        let alerts = operatorData.alerts
        let routeMeta = routeData ?? operatorData.routes.first { $0.route_id == route }
        
        let colorHex = routeMeta?.color ?? (operator == "oasa" ? "#E8792B" : "#0E7490")
        let confidence = confidenceLevel(from: routeMeta?.confidence ?? "unknown")
        
        return UnifiedAirportBusDetail(
            id: "\(operator)_\(route)",
            route: route,
            routeId: route,
            operatorName: operator.capitalized,
            origin: routeMeta?.origin ?? "",
            destination: routeMeta?.destination ?? "",
            airport: routeMeta?.airport ?? (operator == "oasa" ? "AIA" : "SKG"),
            colorHex: colorHex,
            confidence: confidence,
            sourceURL: URL(string: routeMeta?.source_url ?? ""),
            eta: eta,
            vehiclePositions: vehicles,
            alerts: alerts,
            hasVehicles: !vehicles.isEmpty,
            direction: routeMeta?.direction,
            freshness: nil,
            attribution: routeMeta?.attribution
        )
    }

    private static func confidenceLevel(from string: String) -> SourceConfidence {
        switch string.lowercased() {
        case "live": return .live
        case "scheduled": return .scheduled
        case "estimated": return .estimated
        case "operator-link", "operator_link": return .operatorLink
        case "offline": return .offline
        case "stale": return .offline
        default: return .unknown
        }
    }
}

// MARK: - Preview support

#Preview("OASA X95 Detail") {
    let mockDetail = UnifiedAirportBusDetail(
        id: "oasa_X95",
        route: "X95",
        routeId: "X95",
        operatorName: "OASA",
        origin: "Syntagma",
        destination: "Athens Airport",
        airport: "AIA",
        colorHex: "#E8792B",
        confidence: .live,
        sourceURL: URL(string: "https://www.oasa.gr/en/telematics/"),
        eta: 5,
        vehiclePositions: [],
        alerts: [],
        hasVehicles: false,
        direction: "to_airport",
        freshness: nil,
        attribution: "Live bus data from OASA Telematics"
    )
    return UnifiedAirportBusDetailSheet(detail: mockDetail, language: .english)
}
