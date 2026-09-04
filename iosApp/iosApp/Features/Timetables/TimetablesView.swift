import SwiftUI
import EventKit
import UIKit

private struct AirportCalendarEvent: Identifiable, Equatable {
    let id: String
    let title: String
    let startDate: Date
    let location: String
}

@MainActor
private final class AirportCalendarStore: ObservableObject {
    enum AccessState {
        case unknown
        case connected
        case denied
    }

    @Published private(set) var accessState: AccessState = .unknown
    @Published private(set) var events: [AirportCalendarEvent] = []

    private let eventStore = EKEventStore()

    func refresh() async {
        let status = EKEventStore.authorizationStatus(for: .event)
        if isAuthorized(status) {
            accessState = .connected
            loadEvents()
        } else if status == .denied || status == .restricted {
            accessState = .denied
            events = []
        } else {
            accessState = .unknown
            events = []
        }
    }

    func connect() async {
        if accessState == .denied {
            guard let settings = URL(string: UIApplication.openSettingsURLString) else { return }
            await UIApplication.shared.open(settings)
            return
        }
        do {
            let granted: Bool
            if #available(iOS 17.0, *) {
                granted = try await eventStore.requestFullAccessToEvents()
            } else {
                granted = try await withCheckedThrowingContinuation { continuation in
                    eventStore.requestAccess(to: .event) { allowed, error in
                        if let error { continuation.resume(throwing: error) }
                        else { continuation.resume(returning: allowed) }
                    }
                }
            }
            accessState = granted ? .connected : .denied
            if granted { loadEvents() }
        } catch {
            accessState = .denied
            events = []
        }
    }

    private func isAuthorized(_ status: EKAuthorizationStatus) -> Bool {
        if #available(iOS 17.0, *) { return status == .fullAccess }
        return status == .authorized
    }

    private func loadEvents() {
        let start = Date()
        let end = Calendar.current.date(byAdding: .day, value: 8, to: start) ?? start
        let predicate = eventStore.predicateForEvents(withStart: start, end: end, calendars: nil)
        events = eventStore.events(matching: predicate)
            .filter { event in
                let text = "\(event.title ?? "") \(event.location ?? "")".lowercased()
                return [
                    "airport", "flight", "ath", "aeroporto", "aeroporti", "volo", "fluturim",
                    "αεροδρο", "πτηση", "πτήση", "m3", "x95", "x93"
                ].contains { text.contains($0) }
            }
            .prefix(20)
            .map { event in
                AirportCalendarEvent(
                    id: event.eventIdentifier ?? UUID().uuidString,
                    title: event.title?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? event.title! : "Airport trip",
                    startDate: event.startDate,
                    location: event.location ?? ""
                )
            }
    }
}

/// Airport travel hub with live context, upcoming services, and a
/// seven-day planning calendar tied to a saved flight time.
struct TimetablesView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var dayOffset: Int = 0
    @State private var selectedCity: AirportCity = .athens
    @State private var selectedRoute = "M3"
    @State private var airportDepartures: [Departure] = []
    @State private var cityAirportDepartures: [Departure] = []
    @State private var liveBuses: AirportBusService.LiveAirportBuses? = nil
    /// Thessaloniki only: next metro departures at each interchange station
    /// (Mikra on L2, Nea Elvetia on L1) that feeds an airport shuttle, keyed by
    /// station id. Athens is fed by direct rail so it uses the two lists above.
    @State private var metroLegDepartures: [String: [Departure]] = [:]
    @State private var flightTime = TimetablesView.defaultFlightTime
    @State private var nowTick = Date()
    @StateObject private var calendarStore = AirportCalendarStore()

    private var hub: AirportHub { AirportHub.hub(selectedCity) }

    private let refreshTimer = Timer.publish(every: 15, on: .main, in: .common).autoconnect()

    private static var defaultFlightTime: Date {
        Calendar.current.date(bySettingHour: 18, minute: 40, second: 0, of: Date()) ?? Date()
    }

    private var selectedCalendarEvent: AirportCalendarEvent? {
        let target = Calendar.current.date(byAdding: .day, value: dayOffset, to: Date()) ?? Date()
        return calendarStore.events.first { Calendar.current.isDate($0.startDate, inSameDayAs: target) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    AirportHeroCard(hub: hub, language: loc.language)

                    AirportCityPicker(selectedCity: $selectedCity, language: loc.language)

                    AirportCalendarHub(
                        language: loc.language,
                        dayOffset: $dayOffset,
                        flightTime: $flightTime,
                        calendarEvent: selectedCalendarEvent,
                        accessState: calendarStore.accessState,
                        onConnectCalendar: { Task { await calendarStore.connect() } }
                    )

                    if hub.hasDirectRail {
                        AirportRouteMapCard(
                            language: loc.language,
                            selectedRoute: $selectedRoute,
                            dayOffset: dayOffset
                        )

                        AirportPredictiveCard(
                            language: loc.language,
                            dayOffset: dayOffset,
                            flightTime: flightTime,
                            airportBoundDepartures: cityAirportDepartures,
                            tripTitle: selectedCalendarEvent?.title
                        )

                        AirportNextServicesCard(
                            language: loc.language,
                            dayOffset: dayOffset,
                            metroDepartures: airportDepartures,
                            liveBuses: liveBuses
                        )

                        airportSectionTitle(
                            airportText(
                                loc.language,
                                "Airport services",
                                "Υπηρεσίες αεροδρομίου",
                                "Shërbimet e aeroportit",
                                "Servizi aeroportuali"
                            )
                        )

                        AirportDepartureList(
                            language: loc.language,
                            dayOffset: dayOffset,
                            metroDepartures: airportDepartures,
                            liveBuses: liveBuses
                        )
                    } else {
                        AirportConnectionsCard(hub: hub, language: loc.language)

                        airportSectionTitle(
                            airportText(
                                loc.language,
                                "Metro departures to the airport shuttle",
                                "Αναχωρήσεις μετρό προς το λεωφορείο αεροδρομίου",
                                "Nisjet e metros drejt autobusit të aeroportit",
                                "Partenze metro verso la navetta aeroporto"
                            )
                        )

                        AirportMetroLegsCard(
                            hub: hub,
                            language: loc.language,
                            dayOffset: dayOffset,
                            departuresByStation: metroLegDepartures
                        )
                    }

                    AirportServiceAlertCard(language: loc.language)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 110)
            }
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 0) {
                Color.clear.frame(height: 8)
            }
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                reload()
                Task { await calendarStore.refresh() }
            }
            .onReceive(refreshTimer) { _ in
                nowTick = Date()
                reload()
            }
            .onChange(of: selectedCity) { _, _ in
                selectedRoute = "M3"
                reload()
            }
            .onChange(of: dayOffset) { _, _ in
                reload()
                if let event = selectedCalendarEvent { flightTime = event.startDate }
            }
            .onChange(of: selectedCalendarEvent) { _, event in
                if let event { flightTime = event.startDate }
            }
        }
    }

    private func reload() {
        let selectedDay = dayOffset
        let currentHub = hub
        Task { @MainActor in
            guard currentHub.hasDirectRail else {
                // Thessaloniki: no direct rail. Surface the next metro departures
                // at each interchange that feeds an airport shuttle.
                airportDepartures = []
                cityAirportDepartures = []
                liveBuses = nil
                var legs: [String: [Departure]] = [:]
                for leg in currentHub.metroLegs {
                    legs[leg.stationId] = ScheduleProjector.nextDepartures(
                        for: leg.stationId,
                        lineIds: leg.lineIds,
                        limit: 6,
                        dayOffset: selectedDay,
                        timeHorizonMinutes: 180
                    )
                }
                metroLegDepartures = legs
                return
            }
            metroLegDepartures = [:]
            airportDepartures = ScheduleProjector.nextDepartures(
                for: currentHub.airportStationId,
                lineIds: currentHub.directRailLineIds,
                limit: 24,
                dayOffset: selectedDay
            )
            cityAirportDepartures = ScheduleProjector.nextDepartures(
                for: "M3_SYN",
                lineIds: ["M3", "M3_AIR"],
                limit: 80,
                dayOffset: selectedDay
            ).filter { AirportData.isAirportBoundDirection($0.direction) || $0.serviceType == "airport" }

            // Live express-bus ETAs are Athens-only (X93/95/96/97 tracked by the
            // Pi). Only the live schedule (today) is meaningful, so skip the fetch
            // when browsing a future day, where "in 5 min" would be misleading.
            if currentHub.city == .athens && selectedDay == 0 {
                liveBuses = await AirportBusService.fetch()
            } else {
                liveBuses = nil
            }
        }
    }
}

// MARK: - Airport hub

private struct AirportHeroCard: View {
    let hub: AirportHub
    let language: AppLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "airplane.departure")
                    .font(.title2.weight(.semibold))
                    .frame(width: 48, height: 48)
                    .background(.white.opacity(0.16), in: Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(airportText(language, "AIRPORT", "ΑΕΡΟΔΡΟΜΙΟ", "AEROPORTI", "AEROPORTO"))
                        .font(.caption.weight(.bold))
                        .tracking(1.2)
                        .opacity(0.82)
                    Text(hub.name)
                        .font(.title2.bold())
                    Text(hub.subtitle.text(language))
                        .font(.caption)
                        .opacity(0.82)
                }
                Spacer(minLength: 0)
                Text(hub.code)
                    .font(.caption.weight(.bold))
                    .tracking(1)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.white.opacity(0.16), in: Capsule())
            }

            // Mode pills wrap as complete units, so a label like "M3" or "24/7"
            // can never be compressed and split across two lines. The schedule
            // status chip sits on its own trailing line, so it never competes
            // with the pills for width and can never clip at the card edge.
            VStack(alignment: .leading, spacing: 10) {
                FlowLayout(spacing: 8) {
                    ForEach(Array(hub.pills.enumerated()), id: \.offset) { _, pill in
                        airportHeroPill(pill.title, pill.icon)
                    }
                }
                HStack(spacing: 5) {
                    Spacer(minLength: 0)
                    Circle().fill(Color(hex: 0x63E6A6)).frame(width: 8, height: 8)
                    Text(airportText(language, "Schedules", "Ωράρια", "Oraret", "Orari"))
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize()
                }
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(
            LinearGradient(
                colors: hub.gradient.map { Color(hex: $0) },
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .shadow(color: Color(hex: hub.gradient.first ?? 0x0B3D71).opacity(0.22), radius: 14, y: 8)
        .accessibilityElement(children: .combine)
    }

    private func airportHeroPill(_ title: String, _ icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(.caption2.weight(.bold))
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(.white.opacity(0.14), in: Capsule())
    }
}

/// Lays out chips left to right, wrapping each one to the next line as a
/// complete unit instead of compressing it. Keeps short transport labels such
/// as "M3", "X95" or "24/7" atomic, so they can never be split across lines.
private struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        let maxX = bounds.maxX
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxX, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Airport city switcher

private struct AirportCityPicker: View {
    @Binding var selectedCity: AirportCity
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 8) {
            ForEach(AirportHub.all) { hub in
                let isSelected = hub.city == selectedCity
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { selectedCity = hub.city }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "airplane")
                            .font(.caption2.weight(.bold))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(hub.cityName.text(language))
                                .font(.caption.weight(.bold))
                            Text(hub.code)
                                .font(.system(size: 9, weight: .semibold))
                                .opacity(0.7)
                        }
                    }
                    .foregroundStyle(isSelected ? .white : Color.primary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        isSelected
                            ? AnyShapeStyle(LinearGradient(colors: hub.gradient.map { Color(hex: $0) }, startPoint: .leading, endPoint: .trailing))
                            : AnyShapeStyle(Color.primary.opacity(0.06)),
                        in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(hub.cityName.text(language))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }
}

private struct AirportCalendarHub: View {
    let language: AppLanguage
    @Binding var dayOffset: Int
    @Binding var flightTime: Date
    let calendarEvent: AirportCalendarEvent?
    let accessState: AirportCalendarStore.AccessState
    let onConnectCalendar: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(
                    airportText(language, "Calendar Hub", "Κέντρο ημερολογίου", "Qendra e kalendarit", "Centro calendario"),
                    systemImage: "calendar.badge.clock"
                )
                .font(.headline)
                Spacer()
                Text(selectedDateLabel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.metroBlue)
            }

            DayPickerRow(selectedOffset: $dayOffset)

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(calendarEvent == nil
                        ? airportText(language, "PLANNED DEPARTURE", "ΠΡΟΓΡΑΜΜΑΤΙΣΜΕΝΗ ΑΝΑΧΩΡΗΣΗ", "NISJE E PLANIFIKUAR", "PARTENZA PIANIFICATA")
                        : airportText(language, "SAVED AIRPORT TRIP", "ΑΠΟΘΗΚΕΥΜΕΝΟ ΤΑΞΙΔΙ", "UDHETIM I RUAJTUR", "VIAGGIO SALVATO"))
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.secondary)
                    Text(calendarEvent?.title ?? airportText(language, "No saved airport trip", "Δεν υπάρχει αποθηκευμένο ταξίδι", "Nuk ka udhëtim të ruajtur", "Nessun viaggio salvato"))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        // Longer locales (EL/SQ/IT) shrink to fit one line
                        // instead of truncating next to the time picker.
                        .minimumScaleFactor(0.8)
                    Button(action: onConnectCalendar) {
                        Label(calendarStatusText, systemImage: calendarStatusIcon)
                            .font(.caption2)
                            .foregroundStyle(accessState == .connected ? SyrmosTokens.live : Color.metroBlue)
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                DatePicker("", selection: $flightTime, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .tint(Color.metroBlue)
            }
            .padding(12)
            .background(Color.metroBlue.opacity(0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .padding(16)
        .glassCardBackground(cornerRadius: 20)
    }

    private var selectedDateLabel: String {
        let date = Calendar.current.date(byAdding: .day, value: dayOffset, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: airportLocaleCode(language))
        formatter.setLocalizedDateFormatFromTemplate("EEE d MMM")
        return formatter.string(from: date)
    }

    private var calendarStatusText: String {
        if calendarEvent != nil {
            return airportText(language, "From device calendar", "Απο το ημερολογιο συσκευης", "Nga kalendari i pajisjes", "Dal calendario del dispositivo")
        }
        switch accessState {
        case .connected:
            return airportText(language, "Calendar connected", "Το ημερολογιο συνδεθηκε", "Kalendari u lidh", "Calendario collegato")
        case .denied:
            return airportText(language, "Allow calendar access in Settings", "Επιτρεψε προσβαση ημερολογιου στις Ρυθμισεις", "Lejo kalendarin te Cilësimet", "Consenti il calendario nelle Impostazioni")
        case .unknown:
            return airportText(language, "Connect device calendar", "Συνδεση ημερολογιου συσκευης", "Lidh kalendarin e pajisjes", "Collega il calendario")
        }
    }

    private var calendarStatusIcon: String {
        accessState == .connected ? "checkmark.icloud.fill" : "calendar.badge.plus"
    }
}

private struct AirportRouteMapCard: View {
    let language: AppLanguage
    @Binding var selectedRoute: String
    let dayOffset: Int

    private let routes = ["M3", "A1", "X95", "X93", "X96", "X97"]

    private struct RouteStop: Identifiable {
        let id = UUID()
        let label: String
        let isAirport: Bool
        let isInterchange: Bool
    }

    var body: some View {
        let stops = routeStops
        let color = routeColor(selectedRoute)
        return VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(airportText(language, "Airport route overview", "Επισκόπηση διαδρομών αεροδρομίου", "Pamja e linjave të aeroportit", "Panoramica percorsi aeroporto"))
                    .font(.headline)
                Text(routeSubtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                ForEach(routes, id: \.self) { route in
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) { selectedRoute = route }
                    } label: {
                        Text(route)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(selectedRoute == route ? .white : routeColor(route))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(selectedRoute == route ? routeColor(route) : routeColor(route).opacity(0.12), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }

            // Endpoints line: real origin -> Airport, so the strip below reads
            // as an actual line, not a decorative squiggle.
            if let first = stops.first, let last = stops.last, stops.count > 1 {
                HStack(spacing: 6) {
                    Text(first.label)
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                    Image(systemName: "arrow.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(color)
                    Text(last.label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(color)
                        .lineLimit(1)
                }
            }

            // Real station strip. Rail routes render every stop from the line
            // data (interchanges ringed, terminal = airplane); express buses have
            // no per-stop data, so they show origin and terminal as a dashed hop.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(Array(stops.enumerated()), id: \.element.id) { index, stop in
                        stopCell(index: index, count: stops.count, stop: stop, color: color)
                    }
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 4)
            }
            .frame(height: 64)
            .background(color.opacity(0.05), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

            HStack(spacing: 6) {
                Image(systemName: isBusRoute ? "bus.fill" : "tram.fill")
                    .font(.caption2)
                    .foregroundStyle(color)
                Text(serviceNote)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .glassCardBackground(cornerRadius: 22)
    }

    @ViewBuilder
    private func stopCell(index: Int, count: Int, stop: RouteStop, color: Color) -> some View {
        VStack(spacing: 7) {
            HStack(spacing: 0) {
                connectorSegment(color: color, hidden: index == 0)
                stopDot(stop, color: color)
                connectorSegment(color: color, hidden: index == count - 1)
            }
            .frame(height: 26)
            Text(stop.label)
                .font(.system(size: 9, weight: stop.isAirport ? .bold : .regular))
                .foregroundStyle(stop.isAirport ? color : Color.secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.85)
                .frame(width: 60, height: 24, alignment: .top)
        }
        .frame(width: 60)
    }

    private func connectorSegment(color: Color, hidden: Bool) -> some View {
        Rectangle()
            .fill(hidden ? Color.clear : (isBusRoute ? color.opacity(0.35) : color.opacity(0.8)))
            .frame(height: 3)
    }

    @ViewBuilder
    private func stopDot(_ stop: RouteStop, color: Color) -> some View {
        if stop.isAirport {
            Image(systemName: "airplane")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 24, height: 24)
                .background(color, in: Circle())
        } else if stop.isInterchange {
            Circle()
                .fill(Color.syrmosBackground)
                .frame(width: 13, height: 13)
                .overlay(Circle().stroke(color, lineWidth: 3))
        } else {
            Circle()
                .fill(color)
                .frame(width: 11, height: 11)
        }
    }

    private var isBusRoute: Bool { selectedRoute.hasPrefix("X") }

    private var routeStops: [RouteStop] {
        let airportLabel = airportText(language, "Airport", "Αεροδρόμιο", "Aeroporti", "Aeroporto")
        switch selectedRoute {
        case "M3", "A1":
            let stations = AirportData.stations(for: selectedRoute)
            guard !stations.isEmpty else { return [] }
            return stations.enumerated().map { index, station in
                let isAirport = index == stations.count - 1
                return RouteStop(
                    label: isAirport ? airportLabel : stationName(station),
                    isAirport: isAirport,
                    isInterchange: !isAirport && station.lineIds.count > 1
                )
            }
        default:
            // Express buses: no per-stop timetable, so show the city anchor and
            // the terminal as a two-node dashed hop.
            let origin: String
            switch selectedRoute {
            case "X95": origin = "Syntagma"
            case "X93": origin = airportText(language, "Kifisos B Station", "ΚΤΕΛ Κηφισού", "Stacioni Kifisos", "Stazione Kifisos")
            case "X96": origin = airportText(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo")
            case "X97": origin = "Elliniko"
            default: origin = airportText(language, "City", "Πόλη", "Qyteti", "Città")
            }
            return [
                RouteStop(label: origin, isAirport: false, isInterchange: false),
                RouteStop(label: airportLabel, isAirport: true, isInterchange: false),
            ]
        }
    }

    private func stationName(_ station: AirportData.Station) -> String {
        if language == .greek { return station.nameEl.isEmpty ? station.name : station.nameEl }
        return station.name
    }

    private var serviceNote: String {
        switch selectedRoute {
        case "M3": return airportText(language, "Metro Line 3, direct to the terminal", "Μετρό Γραμμή 3, απευθείας στον τερματικό", "Metro Linja 3, direkt te terminali", "Metro Linea 3, diretto al terminal")
        case "A1": return airportText(language, "Suburban A1, direct to the terminal", "Προαστιακός Α1, απευθείας στον τερματικό", "Suburban A1, direkt te terminali", "Suburbano A1, diretto al terminal")
        default: return airportText(language, "24-hour express bus. Times set by OASA.", "24ωρο λεωφορείο express. Ωράρια από τον ΟΑΣΑ.", "Autobus express 24 orë. Oraret nga OASA.", "Bus express 24 ore. Orari da OASA.")
        }
    }

    private var routeSubtitle: String {
        if dayOffset == 0 {
            return airportText(language, "Every stop on the way to the terminal", "Κάθε στάση στη διαδρομή προς τον τερματικό", "Çdo stacion drejt terminalit", "Ogni fermata verso il terminal")
        }
        return airportText(language, "Planned service for the selected day", "Προγραμματισμένη υπηρεσία για την επιλεγμένη ημέρα", "Shërbimi i planifikuar për ditën e zgjedhur", "Servizio previsto per il giorno selezionato")
    }

    private func routeColor(_ route: String) -> Color {
        switch route {
        case "M3": return Color.metroBlue
        case "A1": return SyrmosTokens.suburban
        case "X95", "X93", "X96", "X97": return SyrmosTokens.warning
        default: return SyrmosTokens.suburban
        }
    }
}

private struct AirportPredictiveCard: View {
    let language: AppLanguage
    let dayOffset: Int
    let flightTime: Date
    let airportBoundDepartures: [Departure]
    let tripTitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(
                    airportText(language, "Smart trip plan", "Έξυπνο πλάνο ταξιδιού", "Plani i mençur i udhëtimit", "Piano di viaggio intelligente"),
                    systemImage: "sparkles"
                )
                .font(.headline)
                Spacer()
                Text(tripTitle.map { airportText(language, "For \($0)", "Για \($0)", "Per \($0)", "Per \($0)") }
                    ?? airportText(language, "Manual plan", "Χειροκινητο πλανο", "Plan manual", "Piano manuale"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(SyrmosTokens.suburban)
                    .lineLimit(1)
            }

            if let metroMinutes {
                HStack(alignment: .top, spacing: 0) {
                    itineraryStep(time: airportClockString(minutes: metroMinutes - 12), title: airportText(language, "Leave", "Αναχώρηση", "Nisja", "Parti"), icon: "figure.walk")
                    itineraryStep(time: airportClockString(minutes: metroMinutes), title: "M3 · Syntagma", icon: "tram.fill")
                    itineraryStep(time: airportClockString(minutes: metroMinutes + 43), title: airportText(language, "Terminal", "Τερματικός", "Terminali", "Terminal"), icon: "airplane")
                }
                .background(alignment: .top) { itineraryTrack }
            } else {
                Text(airportText(
                    language,
                    "No scheduled M3 departure was found for this date. Choose another day or check official operator information.",
                    "Δεν βρέθηκε προγραμματισμένη αναχώρηση M3 για αυτή την ημερομηνία. Επιλέξτε άλλη ημέρα ή ελέγξτε τις επίσημες πληροφορίες.",
                    "Nuk u gjet nisje e programuar M3 për këtë datë. Zgjidh një ditë tjetër ose kontrollo informacionin zyrtar.",
                    "Nessuna partenza M3 programmata trovata per questa data. Scegli un altro giorno o controlla le informazioni ufficiali."
                ))
                .font(.caption)
                .foregroundStyle(SyrmosTokens.disruption)
            }

            Text(airportText(
                language,
                "Includes the selected day's timetable and a 90 minute airport buffer.",
                "Περιλαμβάνει το ωράριο της επιλεγμένης ημέρας και περιθώριο 90 λεπτών.",
                "Përfshin orarin e ditës së zgjedhur dhe 90 minuta rezervë në aeroport.",
                "Include l'orario del giorno selezionato e 90 minuti di margine in aeroporto."
            ))
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [Color.metroBlue.opacity(0.12), SyrmosTokens.suburban.opacity(0.09)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.metroBlue.opacity(0.15)))
    }

    private var metroMinutes: Int? {
        let target = flightMinutes - 133
        let candidates = airportBoundDepartures.compactMap { departure -> (Departure, Int)? in
            guard let minutes = airportClockMinutes(departure.time) else { return nil }
            return (departure, minutes)
        }
        return candidates.last(where: { $0.1 <= target })?.1 ?? candidates.first?.1
    }

    private var flightMinutes: Int {
        let components = Calendar.current.dateComponents([.hour, .minute], from: flightTime)
        return (components.hour ?? 18) * 60 + (components.minute ?? 40)
    }

    private func itineraryStep(time: String, title: String, icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.callout.weight(.semibold))
                .foregroundStyle(Color.metroBlue)
                .frame(width: 36, height: 36)
                .background(Color.syrmosBackground, in: Circle())
                .overlay(Circle().stroke(Color.metroBlue.opacity(0.85), lineWidth: 2))
            Text(time).font(.subheadline.bold()).monospacedDigit()
            Text(title)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }

    // A continuous track joining the three node centers (each step is equal
    // width, so centers land at 1/6, 1/2, 5/6), with the leg durations pinned
    // to the midpoints. Replaces the old floating 22pt dashes that read as
    // broken disconnected lines.
    private var itineraryTrack: some View {
        GeometryReader { geo in
            let y: CGFloat = 18
            ZStack {
                Path { path in
                    path.move(to: CGPoint(x: geo.size.width / 6, y: y))
                    path.addLine(to: CGPoint(x: geo.size.width * 5 / 6, y: y))
                }
                .stroke(Color.metroBlue.opacity(0.35), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                durationChip("12 min").position(x: geo.size.width / 3, y: y)
                durationChip("43 min").position(x: geo.size.width * 2 / 3, y: y)
            }
        }
        .allowsHitTesting(false)
    }

    private func durationChip(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 8, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(Color.metroBlue)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color.syrmosBackground, in: Capsule())
            .overlay(Capsule().stroke(Color.metroBlue.opacity(0.2), lineWidth: 1))
    }
}

private struct AirportNextServicesCard: View {
    let language: AppLanguage
    let dayOffset: Int
    let metroDepartures: [Departure]
    var liveBuses: AirportBusService.LiveAirportBuses? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(airportText(language, "Next services from the airport", "Επόμενα δρομολόγια από το αεροδρόμιο", "Shërbimet e radhës nga aeroporti", "Prossimi servizi dall'aeroporto"))
                .font(.headline)

            HStack(spacing: 10) {
                let m3Tile = AirportNextServiceTile(
                    route: "M3",
                    icon: "tram.fill",
                    destination: "Syntagma",
                    primary: primaryMetroLabel,
                    secondary: followingMetroLabel,
                    status: airportText(language, "Scheduled", "Προγραμματισμένο", "Programuar", "Programmato"),
                    color: Color.metroBlue,
                    navigable: airportStation(id: "M3_AER") != nil
                )
                if let station = airportStation(id: "M3_AER") {
                    NavigationLink { StationDetailView(station: station) } label: { m3Tile }
                        .buttonStyle(.plain)
                } else {
                    m3Tile
                }
                if let mins = liveBuses?.soonest("X95") {
                    AirportNextServiceTile(
                        route: "X95",
                        icon: "bus.fill",
                        destination: "Syntagma",
                        primary: AirportServiceRows.etaLabel(minutes: mins, language: language),
                        secondary: airportText(language, "Live to the airport stop", "Ζωντανά στη στάση του αεροδρομίου", "Drejtpërdrejt te stacioni i aeroportit", "In tempo reale alla fermata dell'aeroporto"),
                        status: SourceConfidence.live.label(language),
                        color: SyrmosTokens.warning
                    )
                } else {
                    AirportNextServiceTile(
                        route: "X95",
                        icon: "bus.fill",
                        destination: "Syntagma",
                        primary: "24/7",
                        secondary: airportText(language, "24-hour express bus", "24ωρο λεωφορείο express", "Autobus express 24 orë", "Bus express 24 ore"),
                        status: airportText(language, "Express", "Express", "Express", "Express"),
                        color: SyrmosTokens.warning
                    )
                }
            }
        }
    }

    private var primaryMetroLabel: String {
        guard let departure = metroDepartures.first else { return "-" }
        return dayOffset == 0 ? departure.minutesAwayDisplay(language: language) : departure.time
    }

    private var followingMetroLabel: String {
        guard metroDepartures.count > 1 else { return airportText(language, "No later departure in the current schedule", "Δεν υπάρχει επόμενη αναχώρηση στο τρέχον ωράριο", "Nuk ka nisje tjetër në orarin aktual", "Nessuna partenza successiva nell'orario attuale") }
        return airportText(language, "Then", "Έπειτα", "Pastaj", "Poi") + " " + metroDepartures[1].time
    }
}

private struct AirportNextServiceTile: View {
    let route: String
    let icon: String
    let destination: String
    let primary: String
    let secondary: String
    let status: String
    let color: Color
    var navigable: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(route, systemImage: icon).font(.caption.weight(.bold)).foregroundStyle(color)
                Spacer()
                Text(status).font(.caption2.weight(.semibold)).foregroundStyle(color)
                if navigable {
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
            Text(primary)
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(color == Color.metroBlue ? SyrmosTokens.warning : color)
                .monospacedDigit()
                .minimumScaleFactor(0.75)
                .lineLimit(1)
            Text(destination).font(.subheadline.weight(.semibold))
            Text(secondary).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCardBackground(cornerRadius: 18)
    }
}

private struct AirportDepartureList: View {
    let language: AppLanguage
    let dayOffset: Int
    let metroDepartures: [Departure]
    var liveBuses: AirportBusService.LiveAirportBuses? = nil

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(rows.prefix(9).enumerated()), id: \.offset) { _, row in
                if let station = airportStation(id: row.stationId) {
                    NavigationLink {
                        StationDetailView(station: station)
                    } label: {
                        rowBody(row, navigable: true)
                    }
                    .buttonStyle(.plain)
                } else {
                    rowBody(row, navigable: false)
                }
            }
        }
    }

    private func rowBody(_ row: AirportListRow, navigable: Bool) -> some View {
        HStack(spacing: 12) {
            Text(row.route)
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .background(row.color, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(row.destination).font(.subheadline.weight(.semibold))
                // Live bus rows carry a pulsing Live chip instead of a static
                // detail line, so the source reads the same as every other
                // departure surface. Scheduled/operator rows keep their text.
                if row.confidence == .live {
                    HStack(spacing: 6) {
                        SourceConfidenceChip(confidence: .live, language: language)
                        Text(row.detail).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                } else {
                    Text(row.detail).font(.caption).foregroundStyle(.secondary)
                }
                // Grouped tail: the next departures after the soonest, so one card
                // replaces the old stack of same-line rows (matches the featured
                // tile's "Then …" pattern above).
                if row.times.count > 1 {
                    Text(airportText(language, "Then", "Έπειτα", "Pastaj", "Poi") + " " + row.times.dropFirst().joined(separator: " · "))
                        .font(.caption2)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Text(row.times.first ?? "-").font(.headline).monospacedDigit().foregroundStyle(row.color)
            // A chevron signals the row opens the station's full departures.
            if navigable {
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(12)
        .glassCardBackground(cornerRadius: 16)
    }

    private var rows: [AirportListRow] {
        AirportServiceRows.build(
            metroDepartures: metroDepartures,
            buses: liveBuses,
            language: language
        )
    }

}

// MARK: - Thessaloniki connections (metro + shuttle, or direct bus)

private struct AirportConnectionsCard: View {
    let hub: AirportHub
    let language: AppLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(airportText(language, "How to reach the airport", "Πώς να φτάσετε στο αεροδρόμιο", "Si të shkoni në aeroport", "Come raggiungere l'aeroporto"))
                    .font(.headline)
                Text(airportText(language, "The metro does not reach the terminal yet, so finish on a shuttle bus.", "Το μετρό δεν φτάνει ακόμη στον τερματικό, οπότε ολοκληρώστε με λεωφορείο.", "Metroja nuk arrin ende te terminali, prandaj përfundoni me autobus.", "La metro non arriva ancora al terminal, quindi si prosegue in navetta."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            VStack(spacing: 10) {
                ForEach(hub.connections) { connection in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: icon(for: connection.mode))
                            .font(.callout.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(Color(hex: connection.colorHex), in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text(connection.badge)
                                    .font(.caption2.weight(.bold))
                                    .foregroundStyle(Color(hex: connection.colorHex))
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(Color(hex: connection.colorHex).opacity(0.12), in: Capsule())
                                Text(connection.title.text(language))
                                    .font(.subheadline.weight(.semibold))
                                Spacer(minLength: 0)
                            }
                            Text(connection.detail.text(language))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .glassCardBackground(cornerRadius: 16)
                }
            }

            Text(airportText(
                language,
                "Shuttle and city bus times are set by OASTH/OSETH. The metro departures below are live from the timetable.",
                "Τα δρομολόγια λεωφορείων ορίζονται από τον ΟΑΣΘ/ΟΣΕΘ. Οι αναχωρήσεις μετρό παρακάτω είναι από το ωράριο.",
                "Oraret e autobusëve caktohen nga OASTH/OSETH. Nisjet e metros më poshtë janë nga orari.",
                "Gli orari dei bus sono fissati da OASTH/OSETH. Le partenze metro qui sotto sono da orario."
            ))
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(16)
        .glassCardBackground(cornerRadius: 22)
    }

    private func icon(for mode: AirportConnection.Mode) -> String {
        switch mode {
        case .metro, .rail, .metroBus: return "tram.fill"
        case .bus: return "bus.fill"
        }
    }
}

private struct AirportMetroLegsCard: View {
    let hub: AirportHub
    let language: AppLanguage
    let dayOffset: Int
    let departuresByStation: [String: [Departure]]

    var body: some View {
        VStack(spacing: 10) {
            ForEach(hub.metroLegs) { leg in
                let deps = departuresByStation[leg.stationId] ?? []
                if let station = airportStation(id: leg.stationId) {
                    NavigationLink {
                        StationDetailView(station: station)
                    } label: {
                        legCard(leg: leg, deps: deps, navigable: true)
                    }
                    .buttonStyle(.plain)
                } else {
                    legCard(leg: leg, deps: deps, navigable: false)
                }
            }
        }
    }

    private func legCard(leg: AirportMetroLeg, deps: [Departure], navigable: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Text(leg.badge)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(Color(hex: leg.colorHex), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(leg.stationName.text(language))
                        .font(.subheadline.weight(.semibold))
                    Text(leg.towards.text(language))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                // Chevron signals the card opens the interchange station's
                // full departures board.
                if navigable {
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }

            if deps.isEmpty {
                Text(airportText(
                    language,
                    "No scheduled metro departure in the current window.",
                    "Καμία προγραμματισμένη αναχώρηση μετρό αυτή τη στιγμή.",
                    "Asnjë nisje e programuar e metros tani.",
                    "Nessuna partenza metro programmata al momento."
                ))
                .font(.caption)
                .foregroundStyle(.secondary)
            } else {
                HStack(spacing: 8) {
                    ForEach(Array(deps.prefix(4).enumerated()), id: \.offset) { _, dep in
                        Text(dayOffset == 0 ? dep.minutesAwayDisplay(language: language) : dep.time)
                            .font(.caption.weight(.bold))
                            .monospacedDigit()
                            .foregroundStyle(Color(hex: leg.colorHex))
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(Color(hex: leg.colorHex).opacity(0.12), in: Capsule())
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .glassCardBackground(cornerRadius: 16)
    }
}

private struct AirportServiceAlertCard: View {
    let language: AppLanguage

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title3)
                .foregroundStyle(SyrmosTokens.disruption)
                .frame(width: 40, height: 40)
                .background(SyrmosTokens.disruption.opacity(0.11), in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(airportText(language, "Service alerts", "Ειδοποιήσεις υπηρεσίας", "Njoftime shërbimi", "Avvisi di servizio"))
                    .font(.subheadline.weight(.semibold))
                Text(airportText(
                    language,
                    "Check official operator notices before leaving. Syrmos does not infer a clear status when no alert feed is available.",
                    "Ελέγξτε τις επίσημες ανακοινώσεις πριν φύγετε. Το Syrmos δεν συμπεραίνει ότι όλα λειτουργούν κανονικά όταν δεν υπάρχει ροή ειδοποιήσεων.",
                    "Kontrollo njoftimet zyrtare para nisjes. Syrmos nuk supozon se gjithçka është në rregull kur nuk ka burim njoftimesh.",
                    "Controlla gli avvisi ufficiali prima di partire. Syrmos non presume che tutto sia regolare quando non è disponibile un feed di avvisi."
                ))
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(SyrmosTokens.disruption.opacity(0.06), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(SyrmosTokens.disruption.opacity(0.16)))
    }
}

private func airportSectionTitle(_ title: String) -> some View {
    Text(title).font(.title3.bold()).padding(.top, 2)
}

private func airportStation(id: String?) -> TransitStation? {
    guard let id, !id.isEmpty else { return nil }
    return StationCoords.allStations.first { $0.id == id }
}

private func airportText(_ language: AppLanguage, _ english: String, _ greek: String, _ albanian: String, _ italian: String) -> String {
    switch language {
    case .english: return english
    case .greek: return greek
    case .albanian: return albanian
    case .italian: return italian
    }
}

private func airportLocaleCode(_ language: AppLanguage) -> String {
    switch language {
    case .english: return "en_US"
    case .greek: return "el_GR"
    case .albanian: return "sq_AL"
    case .italian: return "it_IT"
    }
}

private func airportClockMinutes(_ value: String) -> Int? {
    let parts = value.split(separator: ":").compactMap { Int($0) }
    guard parts.count == 2 else { return nil }
    return parts[0] * 60 + parts[1]
}

private func airportClockString(minutes: Int) -> String {
    let normalized = ((minutes % 1440) + 1440) % 1440
    return String(format: "%02d:%02d", normalized / 60, normalized % 60)
}

// MARK: - Glass cards

private struct LinePickerCard: View {
    @Binding var selectedLineId: String
    let lines: [TransitLine]
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(lines, id: \.id) { line in
                Button {
                    selectedLineId = line.id
                } label: {
                    HStack {
                        Text(lineLabel(line))
                        if line.id == selectedLineId {
                            Spacer()
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: iconName)
                    .font(.subheadline)
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(tint.opacity(0.15), in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(lineHeaderLabel.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(currentLineLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCardBackground(cornerRadius: 16)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var currentLineLabel: String {
        guard let line = lines.first(where: { $0.id == selectedLineId }) else { return selectedLineId }
        return lineLabel(line)
    }

    private func lineLabel(_ line: TransitLine) -> String {
        line.localizedName(loc.language)
    }

    private var iconName: String {
        let line = lines.first { $0.id == selectedLineId }
        switch line?.type {
        case .metro: return "tram.tunnel.fill"
        case .tram: return "tram.fill"
        case .bus: return "bus.fill"
        case .scenic: return "mountain.2.fill"
        default: return "train.side.front.car"
        }
    }

    private var tint: Color {
        SyrmosData.lineColor(for: selectedLineId)
    }

    private var lineHeaderLabel: String {
        switch loc.language {
        case .greek: return "Γραμμη"
        case .albanian: return "Linja"
        case .italian: return "Linea"
        case .english: return "Line"
        }
    }
}

private struct StationPickerCard: View {
    let stations: [TransitStation]
    @Binding var selectedStationId: String
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Menu {
            ForEach(stations, id: \.id) { st in
                Button {
                    selectedStationId = st.id
                } label: {
                    HStack {
                        Text(loc.language == .greek ? st.nameEl : st.name)
                        if st.id == selectedStationId {
                            Spacer()
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            let current = stations.first { $0.id == selectedStationId }
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: "mappin.circle.fill")
                    .font(.subheadline)
                    .foregroundStyle(.tint)
                    .frame(width: 28, height: 28)
                    .background(.tint.opacity(0.12), in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(stationLabel.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .tracking(0.6)
                    Text(loc.language == .greek ? (current?.nameEl ?? "") : (current?.name ?? ""))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                }

                Spacer()

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCardBackground(cornerRadius: 16)
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private var stationLabel: String {
        switch loc.language {
        case .greek: return "Σταθμος"
        case .albanian: return "Stacioni"
        case .italian: return "Stazione"
        case .english: return "Station"
        }
    }
}

private struct DirectionSection: View {
    enum Kind { case outbound, inbound }

    let kind: Kind
    let departures: [Departure]
    let isToday: Bool
    let destinationLabel: String
    let tint: Color

    @ObservedObject private var loc = LocalizationManager.shared
    @State private var mode: Mode = .featured
    enum Mode { case featured, showPast, showAll }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header

            if let featured = featuredDeparture {
                FeaturedRow(departure: featured, isToday: isToday, tint: tint)
                    .transition(.opacity)
            } else {
                EmptyRow()
            }

            if mode != .featured, !expandedDepartures.isEmpty {
                Divider()
                    .opacity(0.3)
                VStack(spacing: 0) {
                    ForEach(Array(expandedDepartures.enumerated()), id: \.offset) { idx, dep in
                        ExpandedRow(departure: dep, isToday: isToday, tint: tint)
                        if idx < expandedDepartures.count - 1 {
                            Divider().opacity(0.18).padding(.leading, 36)
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            HStack(spacing: 8) {
                if isToday, !pastDepartures.isEmpty {
                    GlassPill(
                        label: earlierLabel,
                        systemImage: "clock.arrow.circlepath",
                        isActive: mode == .showPast,
                        tint: tint
                    ) {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            mode = (mode == .showPast) ? .featured : .showPast
                        }
                    }
                }
                if upcomingDepartures.count > 1 {
                    GlassPill(
                        label: allUpcomingLabel,
                        systemImage: "list.bullet",
                        isActive: mode == .showAll,
                        tint: tint
                    ) {
                        withAnimation(.easeInOut(duration: 0.22)) {
                            mode = (mode == .showAll) ? .featured : .showAll
                        }
                    }
                }
            }
        }
        .padding(12)
        .glassCardBackground(cornerRadius: 16)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .background(tint.opacity(0.15), in: Circle())
            VStack(alignment: .leading, spacing: 0) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                if let subtitle = subtitle {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
    }

    private var icon: String {
        switch kind {
        case .outbound: return "arrow.right"
        case .inbound: return "arrow.left"
        }
    }

    private var title: String {
        let prefix: String
        switch loc.language {
        case .greek: prefix = "Προς"
        case .albanian: prefix = "Drejt"
        case .italian: prefix = "Verso"
        case .english: prefix = "Towards"
        }
        return "\(prefix) \(destinationLabel)"
    }

    private var subtitle: String? {
        let count = upcomingDepartures.count
        guard count > 0 else { return nil }
        switch loc.language {
        case .greek: return "\(count) επομενα δρομολογια"
        case .albanian: return "\(count) nisje te radhes"
        case .italian: return "\(count) partenze imminenti"
        case .english: return "\(count) upcoming departures"
        }
    }

    private var earlierLabel: String {
        switch loc.language {
        case .greek: return "Προηγουμενα"
        case .albanian: return "Me pare"
        case .italian: return "Precedenti"
        case .english: return "Earlier"
        }
    }

    private var allUpcomingLabel: String {
        switch loc.language {
        case .greek: return "Ολα τα επομενα"
        case .albanian: return "Te gjitha"
        case .italian: return "Tutte le prossime"
        case .english: return "All upcoming"
        }
    }

    private var pastDepartures: [Departure] {
        departures.filter { $0.minutesAway == 0 && $0.time < currentTimeString }
    }

    private var upcomingDepartures: [Departure] {
        if isToday {
            return departures.filter { $0.minutesAway > 0 || $0.time >= currentTimeString }
        } else {
            return departures
        }
    }

    private var featuredDeparture: Departure? {
        upcomingDepartures.first
    }

    private var expandedDepartures: [Departure] {
        switch mode {
        case .featured: return []
        case .showPast: return Array(pastDepartures.reversed())
        case .showAll: return Array(upcomingDepartures.dropFirst())
        }
    }

    private var currentTimeString: String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }
}

private struct FeaturedRow: View {
    let departure: Departure
    let isToday: Bool
    let tint: Color
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Circle()
                .fill(SyrmosData.lineColor(for: departure.lineId))
                .frame(width: 28, height: 28)
                .overlay(
                    Image(systemName: "train.side.front.car")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(SyrmosData.line(for: departure.lineId)?.localizedName(loc.language) ?? departure.lineId)
                    .font(.subheadline.weight(.semibold))
                Text(directionLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if departure.sourceConfidence != .unknown {
                    SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 1) {
                if isToday {
                    Text(minutesLabel)
                        .font(.system(size: 22, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                    Text(departure.time)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                } else {
                    Text(departure.time)
                        .font(.system(size: 22, weight: .semibold, design: .rounded))
                        .foregroundStyle(tint)
                        .monospacedDigit()
                }
            }
        }
        .padding(.vertical, 4)
    }

    private var minutesLabel: String {
        if departure.minutesAway <= 1 {
            switch loc.language {
            case .greek: return "Τωρα"
            case .albanian: return "Tani"
            case .italian: return "Ora"
            case .english: return "Now"
            }
        }
        return departure.minutesAwayDisplay(language: loc.language)
    }

    private var directionLabel: String {
        switch loc.language {
        case .greek: return "προς \(departure.direction)"
        case .albanian: return "drejt \(departure.direction)"
        case .italian: return "verso \(departure.direction)"
        case .english: return "towards \(departure.direction)"
        }
    }
}

private struct ExpandedRow: View {
    let departure: Departure
    let isToday: Bool
    let tint: Color
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(tint.opacity(0.18))
                .overlay(Circle().fill(tint).frame(width: 5, height: 5))
                .frame(width: 12, height: 12)
                .padding(.leading, 18)

            VStack(alignment: .leading, spacing: 1) {
                Text(SyrmosData.line(for: departure.lineId)?.localizedName(loc.language) ?? departure.lineId)
                    .font(.caption)
                    .foregroundStyle(.primary)
                if departure.sourceConfidence != .unknown {
                    SourceConfidenceChip(confidence: departure.sourceConfidence, language: loc.language)
                }
            }

            Spacer()

            HStack(spacing: 4) {
                if isToday, departure.minutesAway > 0 {
                    Text(departure.minutesAwayDisplay(language: loc.language))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                }
                Text(departure.time)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 6)
        .padding(.trailing, 4)
    }
}

private struct EmptyRow: View {
    @ObservedObject private var loc = LocalizationManager.shared
    var body: some View {
        Text(loc.language == .greek ? "Δεν υπάρχουν διαθέσιμα δρομολόγια." :
             loc.language == .albanian ? "Nuk ka nisje te disponueshme." :
             loc.language == .italian ? "Nessuna partenza disponibile." :
             "No departures available.")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.vertical, 8)
    }
}

private struct GlassPill: View {
    let label: String
    let systemImage: String
    let isActive: Bool
    let tint: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: systemImage)
                    .font(.caption2.weight(.semibold))
                Text(label)
                    .font(.caption.weight(.medium))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .foregroundStyle(isActive ? .white : tint)
            .background(
                Capsule()
                    .fill(isActive ? AnyShapeStyle(tint) : AnyShapeStyle(.thinMaterial))
            )
            .overlay(
                Capsule().strokeBorder(tint.opacity(isActive ? 0 : 0.25), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Glass card background helper

private extension View {
    @ViewBuilder
    func glassCardBackground(cornerRadius: CGFloat = 20) -> some View {
        self.background(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: .black.opacity(0.06), radius: 6, x: 0, y: 3)
        )
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(Color.white.opacity(0.08), lineWidth: 0.5)
        )
    }
}

// MARK: - Day picker

private struct DayPickerRow: View {
    @Binding var selectedOffset: Int
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<7, id: \.self) { offset in
                    let isSelected = selectedOffset == offset
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            selectedOffset = offset
                        }
                    } label: {
                        VStack(spacing: 1) {
                            Text(dayName(offset))
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundStyle(isSelected ? .white : .secondary)
                            Text(dayNumber(offset))
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(isSelected ? .white : .primary)
                        }
                        .frame(width: 44, height: 44)
                        .background(
                            Circle()
                                .fill(isSelected ? AnyShapeStyle(Color.metroBlue) : AnyShapeStyle(.thinMaterial))
                        )
                        .overlay(
                            Circle().strokeBorder(Color.metroBlue.opacity(isSelected ? 0 : 0.18), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 2)
            .padding(.vertical, 1)
        }
    }

    private func dayName(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        if offset == 0 {
            switch loc.language {
            case .greek: return "ΣΗΜ"
            case .albanian: return "SOT"
            case .italian: return "OGGI"
            case .english: return "TODAY"
            }
        }
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: localeCode)
        fmt.dateFormat = "EEE"
        return fmt.string(from: date).uppercased()
    }

    private func dayNumber(_ offset: Int) -> String {
        let date = Calendar.current.date(byAdding: .day, value: offset, to: Date()) ?? Date()
        let fmt = DateFormatter()
        fmt.dateFormat = "d"
        return fmt.string(from: date)
    }

    private var localeCode: String {
        switch loc.language {
        case .greek: return "el_GR"
        case .albanian: return "sq_AL"
        case .italian: return "it_IT"
        case .english: return "en_US"
        }
    }
}
