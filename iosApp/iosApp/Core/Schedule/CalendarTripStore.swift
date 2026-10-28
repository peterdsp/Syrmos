import Foundation
import EventKit
import CoreLocation
import UIKit
import UserNotifications

// Syrmos 3.0.2 calendar leave-by for every event with a location.
//
// Generalises the old airport-only calendar read. Reads the next 48 h of events,
// skips all-day and declined ones, resolves a destination station (airport ->
// station name/alias -> geocoder limited to Attica / Thessaloniki; unresolved is
// skipped, never guessed), plans from the current location with the arrive-by
// planner, and schedules the leave-by notifications from `CalendarLeaveBy`.
// Everything stays on the device.

/// A calendar event the airport card shows (kept for the Departures tab).
struct AirportCalendarEvent: Identifiable, Equatable {
    let id: String
    let title: String
    let startDate: Date
    let location: String
}

/// Resolving an event's place to a station. Pure parts are unit tested.
enum CalendarDestinationResolver {
    static let athensAirportStationId = "M3_AER"
    static let thessalonikiAirportStationId = "THS_AIR"

    /// ATH or SKG for a flight; SKG only when the text names Thessaloniki's airport.
    static func airportStationId(title: String, location: String) -> String {
        let text = CalendarLeaveBy.fold("\(title) \(location)")
        let toks = CalendarLeaveBy.tokens(text)
        let skg = toks.contains("skg") || text.contains("makedonia") || text.contains("μακεδονια")
            || text.contains("thessaloniki") || text.contains("θεσσαλονικ")
        return skg ? thessalonikiAirportStationId : athensAirportStationId
    }

    /// The station whose name (English or Greek) appears as whole words in the
    /// event location, longest name first. Names shorter than 4 letters are
    /// ignored so "Ano" or "Agia" alone cannot hijack an address.
    static func stationMatch(location: String, stations: [(id: String, names: [String])]) -> String? {
        let text = " " + CalendarLeaveBy.fold(location).map { isWordChar($0) ? $0 : " " }.reduce("") { $0 + String($1) } + " "
        var best: (id: String, length: Int)?
        for station in stations {
            for name in station.names {
                let folded = CalendarLeaveBy.fold(name).map { isWordChar($0) ? $0 : " " }.reduce("") { $0 + String($1) }
                    .split(separator: " ").joined(separator: " ")
                guard folded.count >= 4 else { continue }
                if text.contains(" \(folded) "), folded.count > (best?.length ?? 0) {
                    best = (station.id, folded.count)
                }
            }
        }
        return best?.id
    }

    private static func isWordChar(_ c: Character) -> Bool {
        c.unicodeScalars.allSatisfy { s in
            switch s.value {
            case 0x30...0x39, 0x61...0x7A, 0x03B1...0x03C9: return true
            default: return false
            }
        }
    }

    /// Attica and Thessaloniki bounding boxes for geocoder results.
    static func isServed(_ c: CLLocationCoordinate2D) -> Bool {
        let attica = (37.60...38.35, 23.30...24.15)
        let thessaloniki = (40.45...40.80, 22.75...23.20)
        return (attica.0.contains(c.latitude) && attica.1.contains(c.longitude))
            || (thessaloniki.0.contains(c.latitude) && thessaloniki.1.contains(c.longitude))
    }
}

/// Which previously planned events lose their alarms after a re-plan. Pure, so
/// the "keep alarms when only the resolution failed" rule is unit tested.
enum CalendarPlanRetention {
    /// `replanned`: events planned again this pass. `retained`: events still in
    /// the calendar and eligible whose destination or route could not be resolved
    /// this time. Everything else that was planned before is cancelled.
    static func keysToCancel(planned: Set<String>, replanned: Set<String>, retained: Set<String>) -> Set<String> {
        planned.subtracting(replanned).subtracting(retained)
    }
}

@MainActor
final class CalendarTripStore: ObservableObject {
    static let shared = CalendarTripStore()

    enum AccessState {
        case unknown
        case connected
        case denied
    }

    @Published private(set) var accessState: AccessState = .unknown
    /// Airport trips for the Departures tab card (the shared classifier says flight).
    @Published private(set) var events: [AirportCalendarEvent] = []

    private let eventStore = EKEventStore()
    private let center = UNUserNotificationCenter.current()
    private let idPrefix = "syrmos.calendar."
    private let plannedKey = "syrmos.calendar.planned.v1"
    private var isPlanning = false

    private init() {}

    func refresh() async {
        let status = EKEventStore.authorizationStatus(for: .event)
        if isAuthorized(status) {
            accessState = .connected
            loadAirportEvents()
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
            if granted { loadAirportEvents() }
        } catch {
            accessState = .denied
            events = []
        }
    }

    private func isAuthorized(_ status: EKAuthorizationStatus) -> Bool {
        if #available(iOS 17.0, *) { return status == .fullAccess }
        return status == .authorized
    }

    var isAuthorized: Bool { isAuthorized(EKEventStore.authorizationStatus(for: .event)) }

    /// Title for a calendar event that matched as an airport trip but has no title of its own.
    private var fallbackTripTitle: String {
        travelText(LocalizationManager.shared.language, "Airport trip", "Ταξίδι αεροδρομίου", "Udhëtim aeroporti", "Viaggio in aeroporto")
    }

    private func loadAirportEvents() {
        let start = SyrmosClock.now
        let end = Calendar.current.date(byAdding: .day, value: 8, to: start) ?? start
        let predicate = eventStore.predicateForEvents(withStart: start, end: end, calendars: nil)
        events = eventStore.events(matching: predicate)
            .filter { CalendarLeaveBy.classify(title: $0.title ?? "", location: $0.location ?? "") == .flight }
            .prefix(20)
            .map { event in
                AirportCalendarEvent(
                    id: event.eventIdentifier ?? UUID().uuidString,
                    title: event.title?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? event.title! : fallbackTripTitle,
                    startDate: event.startDate,
                    location: event.location ?? ""
                )
            }
    }

    // MARK: - Leave-by planning

    private struct PlannedTrip: Codable {
        var leaveAt: Int64
        var fireIds: [String]
    }

    private var planned: [String: PlannedTrip] {
        get {
            guard let data = UserDefaults.standard.data(forKey: plannedKey) else { return [:] }
            return (try? JSONDecoder().decode([String: PlannedTrip].self, from: data)) ?? [:]
        }
        set { UserDefaults.standard.set(try? JSONEncoder().encode(newValue), forKey: plannedKey) }
    }

    /// Re-plan every eligible event and (re)schedule its notifications. Called on
    /// app open and from the background refresh. A moved leave-at below the
    /// threshold keeps the existing schedule; an unknown location keeps it too.
    func replan() async {
        guard !isPlanning else { return }
        isPlanning = true
        defer { isPlanning = false }
        guard NotificationPreferences.calendarRemindersEnabled, isAuthorized else {
            cancelAll()
            return
        }
        let now = SyrmosClock.now
        let nowEpoch = Int64(now.timeIntervalSince1970)
        let end = now.addingTimeInterval(TimeInterval(CalendarLeaveBy.horizonSeconds))
        let found = eventStore.events(matching: eventStore.predicateForEvents(withStart: now, end: end, calendars: nil))
        guard let origin = CLLocationManager().location else { return } // keep the earlier schedule

        var state = planned
        var seen = Set<String>()
        var retained = Set<String>()
        for event in found {
            let key = Self.key(for: event)
            let declined = event.attendees?.first(where: { $0.isCurrentUser })?.participantStatus == .declined
            let base = CalendarEligibilityInput(startEpochSeconds: Int64(event.startDate.timeIntervalSince1970),
                                                allDay: event.isAllDay, declined: declined)
            guard CalendarLeaveBy.eligibility(base, now: nowEpoch) == "eligible" else { continue }
            let title = event.title ?? ""
            let location = event.location ?? ""
            guard !location.trimmingCharacters(in: .whitespaces).isEmpty
                    || CalendarLeaveBy.classify(title: title, location: "") != .default else { continue }
            // A resolution failure (geocoder offline in a background refresh, no
            // route right now) is not a calendar change: keep any alarms already
            // scheduled for this event instead of cancelling them.
            guard let destination = await resolveDestination(title: title, location: location),
                  let trip = journey(from: origin, to: destination.stationId, lastMileMeters: destination.lastMileMeters) else {
                retained.insert(key)
                continue
            }
            seen.insert(key)

            let input = CalendarTripInput(
                title: title, location: location, startEpochSeconds: base.startEpochSeconds,
                journeyMinutes: trip.minutes, transfers: trip.transfers, firstLegHeadwayMinutes: trip.headway,
                raining: Self.raining, alertOnRoute: trip.alertOnRoute,
                flightBufferMinutes: NotificationPreferences.flightBufferMinutes
            )
            let plan = CalendarLeaveBy.plan(input, now: nowEpoch)
            if let old = state[key], !CalendarLeaveBy.needsReschedule(oldLeaveAt: old.leaveAt, newLeaveAt: plan.leaveAtEpochSeconds) {
                continue
            }
            if let old = state[key] { center.removePendingNotificationRequests(withIdentifiers: old.fireIds) }
            let place = Self.placeName(location: location, title: title, stationId: destination.stationId)
            var ids: [String] = []
            for fire in plan.notifications where NotificationPreferences.calendarTypeEnabled(fire.kind) {
                let id = "\(idPrefix)\(key).\(fire.kind.rawValue)"
                schedule(fire: fire, id: id, kind: plan.kind, place: place, trip: trip, leaveAt: plan.leaveAtEpochSeconds, now: nowEpoch)
                ids.append(id)
            }
            state[key] = PlannedTrip(leaveAt: plan.leaveAtEpochSeconds, fireIds: ids)
        }
        // Events that disappeared, passed, or became declined / all-day: drop
        // their alarms. Unresolved-but-present events keep theirs.
        for key in CalendarPlanRetention.keysToCancel(planned: Set(state.keys), replanned: seen, retained: retained) {
            if let trip = state[key] { center.removePendingNotificationRequests(withIdentifiers: trip.fireIds) }
            state[key] = nil
        }
        planned = state
    }

    func cancelAll() {
        let ids = planned.values.flatMap(\.fireIds)
        if !ids.isEmpty { center.removePendingNotificationRequests(withIdentifiers: ids) }
        planned = [:]
    }

    private static func key(for event: EKEvent) -> String {
        let id = event.calendarItemIdentifier
        return "\(id)@\(Int64(event.startDate.timeIntervalSince1970))"
    }

    private static var raining: Bool {
        guard let c = WeatherStore.shared.snapshot?.current.condition else { return false }
        return c.isWet
    }

    private struct ResolvedDestination {
        let stationId: String
        let lastMileMeters: Double
    }

    private func resolveDestination(title: String, location: String) async -> ResolvedDestination? {
        if CalendarLeaveBy.classify(title: title, location: location) == .flight {
            return ResolvedDestination(stationId: CalendarDestinationResolver.airportStationId(title: title, location: location), lastMileMeters: 0)
        }
        let index = SyrmosData.bundleStations.map { (id: $0.id, names: [$0.name, $0.nameEl]) }
        if let id = CalendarDestinationResolver.stationMatch(location: location, stations: index)
            ?? CalendarDestinationResolver.stationMatch(location: title, stations: index) {
            return ResolvedDestination(stationId: id, lastMileMeters: 0)
        }
        guard !location.isEmpty else { return nil }
        let region = CLCircularRegion(center: CLLocationCoordinate2D(latitude: 38.0, longitude: 23.73), radius: 60_000, identifier: "attica")
        guard let placemarks = try? await CLGeocoder().geocodeAddressString(location, in: region),
              let coordinate = placemarks.first?.location?.coordinate,
              CalendarDestinationResolver.isServed(coordinate) else { return nil }
        let target = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        guard let nearest = nearestStation(to: target), nearest.distance <= 3_000 else { return nil }
        return ResolvedDestination(stationId: nearest.id, lastMileMeters: nearest.distance)
    }

    private func nearestStation(to location: CLLocation) -> (id: String, distance: Double)? {
        // Only stations the planner can route (lines with a station list).
        let routable = SyrmosData.operationalLines.flatMap { SyrmosData.stations(for: $0.id) }
        return routable
            .map { ($0.id, location.distance(from: CLLocation(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude))) }
            .min { $0.1 < $1.1 }
    }

    struct Trip {
        let minutes: Int
        let transfers: Int
        let headway: Int
        let firstLineId: String?
        let boardStationId: String
        let boardName: String
        let walkToBoardMinutes: Int
        let alertOnRoute: Bool
    }

    /// Door to door minutes via the arrive-by planner: walk to the nearest
    /// station, ride, then the last-mile walk.
    private func journey(from origin: CLLocation, to destinationId: String, lastMileMeters: Double) -> Trip? {
        guard let board = nearestStation(to: origin) else { return nil }
        let lang = LocalizationManager.shared.language
        let walkIn = NearbyDepartureCoach.walkMinutes(distanceMeters: board.distance)
        let walkOut = lastMileMeters > 0 ? NearbyDepartureCoach.walkMinutes(distanceMeters: lastMileMeters) : 0
        if board.id == destinationId {
            return Trip(minutes: walkIn + walkOut, transfers: 0, headway: 0, firstLineId: nil, boardStationId: board.id,
                        boardName: stationName(board.id, lang), walkToBoardMinutes: walkIn, alertOnRoute: false)
        }
        guard let plan = JourneyPlanner.plan(from: board.id, to: destinationId, language: lang) else { return nil }
        let firstLine = plan.legs.first?.lineId
        var headway = 10
        if let firstLine {
            let deps = ScheduleProjector.nextDepartures(for: board.id, lineIds: [firstLine], limit: 6)
            let byDirection = Dictionary(grouping: deps, by: \.direction).values.first { $0.count >= 2 }
            if let d = byDirection, d.count >= 2 { headway = max(1, d[1].minutesAway - d[0].minutesAway) }
        }
        let disruptions = STASYService().lineDisruptions
        let alert = plan.legs.contains { disruptions[$0.lineId] != nil && disruptions[$0.lineId] != "info" }
        return Trip(minutes: walkIn + plan.totalMinutes + walkOut, transfers: plan.transfers, headway: headway,
                    firstLineId: firstLine, boardStationId: board.id, boardName: stationName(board.id, lang),
                    walkToBoardMinutes: walkIn, alertOnRoute: alert)
    }

    private func stationName(_ id: String, _ lang: AppLanguage) -> String {
        guard let s = SyrmosData.bundleStations.first(where: { $0.id == id }) else { return id }
        return lang == .greek ? s.nameEl : s.name
    }

    private static func placeName(location: String, title: String, stationId: String) -> String {
        let first = location.split(separator: ",").first.map(String.init)?.trimmingCharacters(in: .whitespaces) ?? ""
        if !first.isEmpty { return first }
        return title.isEmpty ? stationId : title
    }

    private func schedule(fire: CalendarFire, id: String, kind: CalendarEventKind, place: String, trip: Trip, leaveAt: Int64, now: Int64) {
        let lang = LocalizationManager.shared.language
        let content = UNMutableNotificationContent()
        let lineLabel = trip.firstLineId.map { SyrmosData.line(for: $0)?.id ?? $0 }
        switch fire.kind {
        case .flightChecklist:
            let c = CalendarCopy.flightChecklist(lang, airportFare: nil)
            content.title = c.title; content.body = c.body
        case .headsUp:
            let c = CalendarCopy.headsUp(kind: kind, place: place, line: lineLabel, board: trip.boardName, lang)
            content.title = c.title; content.body = c.body
        case .leaveNow:
            let c = CalendarCopy.leaveNow(kind: kind, place: place, line: lineLabel,
                                          nextMinutes: nextTrainMinutes(trip: trip, leaveAt: leaveAt), lang)
            content.title = c.title; content.body = c.body
        }
        content.sound = .default
        content.threadIdentifier = "syrmos.calendar"
        content.categoryIdentifier = "CALENDAR_LEAVE_BY"
        content.userInfo = ["stationId": trip.boardStationId]
        let interval = max(1, Double(fire.atEpochSeconds - now))
        center.add(UNNotificationRequest(identifier: id, content: content,
                                         trigger: UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)))
    }

    /// Minutes from leave-at to the first scheduled train the rider can catch
    /// after walking to the station; nil when the schedule has nothing.
    private func nextTrainMinutes(trip: Trip, leaveAt: Int64) -> Int? {
        guard let line = trip.firstLineId else { return nil }
        let now = Int64(SyrmosClock.now.timeIntervalSince1970)
        let reachAt = leaveAt + Int64(trip.walkToBoardMinutes) * 60
        let deps = ScheduleProjector.nextDepartures(for: trip.boardStationId, lineIds: [line], limit: 300, timeHorizonMinutes: 12 * 60)
        guard let dep = deps.first(where: { now + Int64($0.minutesAway) * 60 >= reachAt }) else { return nil }
        return Int((now + Int64(dep.minutesAway) * 60 - leaveAt) / 60)
    }
}
