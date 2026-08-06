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
    @State private var selectedRoute = "M3"
    @State private var airportDepartures: [Departure] = []
    @State private var cityAirportDepartures: [Departure] = []
    @State private var flightTime = TimetablesView.defaultFlightTime
    @State private var nowTick = Date()
    @StateObject private var calendarStore = AirportCalendarStore()

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
                    AirportHeroCard(language: loc.language)

                    AirportCalendarHub(
                        language: loc.language,
                        dayOffset: $dayOffset,
                        flightTime: $flightTime,
                        calendarEvent: selectedCalendarEvent,
                        accessState: calendarStore.accessState,
                        onConnectCalendar: { Task { await calendarStore.connect() } }
                    )

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
                        metroDepartures: airportDepartures
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
                        metroDepartures: airportDepartures
                    )

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
        Task { @MainActor in
            airportDepartures = ScheduleProjector.nextDepartures(
                for: "M3_AER",
                lineIds: ["M3", "M3_AIR", "A1", "A2"],
                limit: 24,
                dayOffset: selectedDay
            )
            cityAirportDepartures = ScheduleProjector.nextDepartures(
                for: "M3_SYN",
                lineIds: ["M3", "M3_AIR"],
                limit: 80,
                dayOffset: selectedDay
            ).filter { AirportData.isAirportBoundDirection($0.direction) || $0.serviceType == "airport" }
        }
    }
}

// MARK: - Airport hub

private struct AirportHeroCard: View {
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
                    Text("Eleftherios Venizelos")
                        .font(.title2.bold())
                    Text(airportText(
                        language,
                        "Routes, scheduled departures and trip planning",
                        "Διαδρομές, προγραμματισμένες αναχωρήσεις και σχεδιασμός",
                        "Linja, nisje të programuara dhe planifikim udhëtimi",
                        "Percorsi, partenze programmate e pianificazione"
                    ))
                    .font(.caption)
                    .opacity(0.82)
                }
                Spacer(minLength: 0)
            }

            HStack(spacing: 10) {
                airportHeroPill("M3", "tram.fill")
                airportHeroPill("A1", "tram.fill")
                airportHeroPill("X95", "bus.fill")
                airportHeroPill("24/7", "clock.fill")
                Spacer()
                HStack(spacing: 5) {
                    Circle().fill(Color(hex: 0x63E6A6)).frame(width: 8, height: 8)
                    Text(airportText(language, "Schedules", "Ωράρια", "Oraret", "Orari"))
                        .font(.caption.weight(.semibold))
                }
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x0B3D71), Color(hex: 0x155E9F), Color(hex: 0x45398F)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .shadow(color: Color(hex: 0x0B3D71).opacity(0.22), radius: 14, y: 8)
        .accessibilityElement(children: .combine)
    }

    private func airportHeroPill(_ title: String, _ icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(.caption2.weight(.bold))
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(.white.opacity(0.14), in: Capsule())
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
                    Text(calendarEvent?.title ?? airportText(language, "No saved airport trip", "Δεν υπαρχει αποθηκευμενο ταξιδι", "Nuk ka udhetim te ruajtur", "Nessun viaggio salvato"))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
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

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(airportText(language, "Airport route overview", "Επισκόπηση διαδρομών αεροδρομίου", "Pamja e linjave të aeroportit", "Panoramica percorsi aeroporto"))
                        .font(.headline)
                    Text(routeSubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
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

            GeometryReader { proxy in
                ZStack {
                    LinearGradient(
                        colors: [Color(hex: 0xDDEAF2), Color(hex: 0xEDE8F5)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    airportMapGrid(size: proxy.size)
                        .stroke(Color.white.opacity(0.55), lineWidth: 1)
                    airportRoutePath(size: proxy.size)
                        .stroke(routeColor(selectedRoute).opacity(0.85), style: StrokeStyle(lineWidth: 6, lineCap: .round))

                    airportMapStop("S", x: 0.13, y: 0.74, size: proxy.size)
                    airportMapStop("A", x: 0.86, y: 0.25, size: proxy.size)

                    Image(systemName: (selectedRoute == "M3" || selectedRoute == "A1") ? "tram.fill" : "bus.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(routeColor(selectedRoute), in: Circle())
                        .shadow(radius: 5, y: 2)
                        .position(x: proxy.size.width * 0.56, y: proxy.size.height * 0.48)
                }
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .frame(height: 170)
            .accessibilityLabel(routeSubtitle)
        }
        .padding(16)
        .glassCardBackground(cornerRadius: 22)
    }

    private var routeSubtitle: String {
        if dayOffset == 0 {
            return airportText(language, "Route diagram for services to the terminal", "Διάγραμμα διαδρομών προς τον τερματικό σταθμό", "Skema e linjave drejt terminalit", "Schema dei percorsi verso il terminal")
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

    private func airportMapGrid(size: CGSize) -> Path {
        Path { path in
            stride(from: CGFloat(0), through: size.width, by: 34).forEach { x in
                path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x + 58, y: size.height))
            }
            stride(from: CGFloat(16), through: size.height, by: 32).forEach { y in
                path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: size.width, y: y - 18))
            }
        }
    }

    private func airportRoutePath(size: CGSize) -> Path {
        Path { path in
            path.move(to: CGPoint(x: size.width * 0.13, y: size.height * 0.74))
            path.addCurve(
                to: CGPoint(x: size.width * 0.86, y: size.height * 0.25),
                control1: CGPoint(x: size.width * 0.36, y: size.height * 0.85),
                control2: CGPoint(x: size.width * 0.62, y: size.height * 0.18)
            )
        }
    }

    private func airportMapStop(_ label: String, x: CGFloat, y: CGFloat, size: CGSize) -> some View {
        Text(label)
            .font(.caption2.bold())
            .foregroundStyle(routeColor(selectedRoute))
            .frame(width: 26, height: 26)
            .background(.white, in: Circle())
            .overlay(Circle().stroke(routeColor(selectedRoute), lineWidth: 3))
            .position(x: size.width * x, y: size.height * y)
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
                HStack(spacing: 0) {
                    itineraryStep(time: airportClockString(minutes: metroMinutes - 12), title: airportText(language, "Leave", "Αναχώρηση", "Nisja", "Parti"), icon: "figure.walk")
                    itineraryConnector
                    itineraryStep(time: airportClockString(minutes: metroMinutes), title: "M3 Syntagma", icon: "tram.fill")
                    itineraryConnector
                    itineraryStep(time: airportClockString(minutes: metroMinutes + 43), title: airportText(language, "Terminal", "Τερματικός", "Terminali", "Terminal"), icon: "airplane")
                }
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
        VStack(spacing: 5) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.metroBlue)
                .frame(width: 32, height: 32)
                .background(.white.opacity(0.75), in: Circle())
            Text(time).font(.subheadline.bold()).monospacedDigit()
            Text(title).font(.system(size: 9, weight: .medium)).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private var itineraryConnector: some View {
        Rectangle().fill(Color.metroBlue.opacity(0.28)).frame(width: 22, height: 2).offset(y: -12)
    }
}

private struct AirportNextServicesCard: View {
    let language: AppLanguage
    let dayOffset: Int
    let metroDepartures: [Departure]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(airportText(language, "Next services from the airport", "Επόμενα δρομολόγια από το αεροδρόμιο", "Shërbimet e radhës nga aeroporti", "Prossimi servizi dall'aeroporto"))
                .font(.headline)

            HStack(spacing: 10) {
                AirportNextServiceTile(
                    route: "M3",
                    icon: "tram.fill",
                    destination: "Syntagma",
                    primary: primaryMetroLabel,
                    secondary: followingMetroLabel,
                    status: airportText(language, "Scheduled", "Προγραμματισμένο", "Programuar", "Programmato"),
                    color: Color.metroBlue
                )
                AirportNextServiceTile(
                    route: "X95",
                    icon: "bus.fill",
                    destination: "Syntagma",
                    primary: "24/7",
                    secondary: airportText(language, "Check OASA for current bus times", "Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια", "Kontrollo OASA për oraret aktuale", "Controlla OASA per gli orari attuali"),
                    status: airportText(language, "Express", "Express", "Express", "Express"),
                    color: SyrmosTokens.warning
                )
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(route, systemImage: icon).font(.caption.weight(.bold)).foregroundStyle(color)
                Spacer()
                Text(status).font(.caption2.weight(.semibold)).foregroundStyle(color)
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

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(rows.prefix(9).enumerated()), id: \.offset) { _, row in
                HStack(spacing: 12) {
                    Text(row.route)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(row.color, in: Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        Text(row.destination).font(.subheadline.weight(.semibold))
                        Text(row.detail).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(row.time).font(.headline).monospacedDigit().foregroundStyle(row.color)
                }
                .padding(12)
                .glassCardBackground(cornerRadius: 16)
            }
        }
    }

    private var rows: [AirportListRow] {
        let busDetail = airportText(language, "24-hour express bus. Check OASA for current times.", "24ωρο λεωφορείο express. Ελέγξτε τον ΟΑΣΑ για τα τρέχοντα δρομολόγια.", "Autobus express 24 orë. Kontrollo OASA për oraret aktuale.", "Bus express 24 ore. Controlla OASA per gli orari attuali.")
        var output = metroDepartures.prefix(3).map {
            AirportListRow(route: $0.lineId == "M3_AIR" ? "M3" : $0.lineId, destination: "Syntagma", detail: airportText(language, "Scheduled metro departure", "Προγραμματισμένη αναχώρηση μετρό", "Nisje e programuar e metrosë", "Partenza metro programmata"), time: $0.time, color: Color.metroBlue)
        }
        let suburbanDeps = metroDepartures.filter { $0.lineId == "A1" || $0.lineId == "A2" }.prefix(2)
        for dep in suburbanDeps {
            output.append(AirportListRow(route: dep.lineId, destination: airportText(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo"), detail: airportText(language, "Scheduled suburban departure", "Προγραμματισμένη αναχώρηση προαστιακού", "Nisje e programuar e trenit periferik", "Partenza suburbano programmata"), time: dep.time, color: SyrmosTokens.suburban))
        }
        output.append(AirportListRow(route: "X95", destination: "Syntagma", detail: busDetail, time: "24/7", color: SyrmosTokens.warning))
        output.append(AirportListRow(route: "X93", destination: "Kifisos", detail: busDetail, time: "24/7", color: SyrmosTokens.warning))
        output.append(AirportListRow(route: "X96", destination: airportText(language, "Piraeus", "Πειραιάς", "Pireus", "Pireo"), detail: busDetail, time: "24/7", color: SyrmosTokens.warning))
        output.append(AirportListRow(route: "X97", destination: airportText(language, "Elliniko", "Ελληνικό", "Elliniko", "Elliniko"), detail: busDetail, time: "24/7", color: SyrmosTokens.warning))
        return output
    }

}

private struct AirportListRow {
    let route: String
    let destination: String
    let detail: String
    let time: String
    let color: Color
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
        Text(loc.language == .greek ? "Δεν υπαρχουν διαθεσιμα δρομολογια." :
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
