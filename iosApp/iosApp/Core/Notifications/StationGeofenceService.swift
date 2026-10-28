import Foundation
import CoreLocation
import UserNotifications
#if canImport(ActivityKit)
import ActivityKit
#endif

// Syrmos 3.0.2 nearby next-train notifications.
//
// Region monitoring watches the 20 nearest stations (1000 m radius) and is
// re-picked on every significant location change. On entering a region the app
// gets a few seconds of background time: it projects the next two departures per
// (line, destination) from the offline schedule, runs the shared coach
// (`NearbyDepartureCoach`) and guards, and posts one local notification. The
// location never leaves the device.

/// One direction at a station, ready for the coach.
struct NearbyDirection: Equatable {
    let lineId: String
    let destination: String
    let departures: [CoachDeparture]
}

/// Builds the notification text from already projected inputs. Pure, so it is
/// unit tested without a schedule, a location or a notification center.
enum NearbyTrainComposer {
    struct Output: Equatable {
        let title: String
        let body: String
        let headlineCase: CoachCase
    }

    static func compose(
        stationName: String,
        stationKey: String,
        walkMinutes: Int,
        directions: [NearbyDirection],
        stationClosed: Bool,
        athensDate: String,
        language lang: AppLanguage
    ) -> Output? {
        let results = directions.map {
            NearbyDepartureCoach.coachCase(walk: walkMinutes, departures: $0.departures, stationClosed: stationClosed)
        }
        let title = NearbyCopy.title(stationName: stationName, walkMinutes: walkMinutes, lang)
        if stationClosed {
            let variant = NearbyDepartureCoach.variantIndex(stationId: stationKey, athensDate: athensDate,
                                                            variantCount: NearbyCopy.variantCount(.stationClosed))
            let line = NearbyCopy.coachLine(.stationClosed, variant: variant, language: lang,
                                            params: .init(line: "", destination: stationName))
            return Output(title: title, body: line, headlineCase: .stationClosed)
        }
        guard let h = NearbyDepartureCoach.headlineIndex(results.map(\.coachCase)) else { return nil }
        // One line per direction: "Airport: 4', 14'".
        var lines: [String] = []
        for d in directions where !d.departures.isEmpty {
            let times = d.departures.prefix(2).map { "\($0.minutes)'" }.joined(separator: ", ")
            lines.append("\(d.destination): \(times)")
        }
        guard !lines.isEmpty else { return nil }
        let headline = results[h]
        let variant = NearbyDepartureCoach.variantIndex(stationId: stationKey, athensDate: athensDate,
                                                        variantCount: NearbyCopy.variantCount(headline.coachCase))
        let catchphrase = NearbyCopy.coachLine(headline.coachCase, variant: variant, language: lang, params: .init(
            next: headline.nextMinutes, following: headline.followingMinutes, margin: headline.marginMinutes,
            wait: headline.waitMinutes, line: directions[h].lineId, destination: directions[h].destination))
        let body = lines.joined(separator: "\n") + " (\(NearbyCopy.scheduledLabel(lang)))\n" + catchphrase
        return Output(title: title, body: body, headlineCase: headline.coachCase)
    }

    /// Next two departures per (line, destination) with last-train flags, from a
    /// full-day departure list (minutes from now, ascending).
    static func directions(from departures: [Departure], overnight: Bool, destinationName: (Departure) -> String) -> [NearbyDirection] {
        var order: [String] = []
        var groups: [String: [Departure]] = [:]
        for d in departures.sorted(by: { $0.minutesAway < $1.minutesAway }) {
            let key = "\(d.lineId)|\(d.direction)"
            if groups[key] == nil { order.append(key) }
            groups[key, default: []].append(d)
        }
        return order.compactMap { key in
            guard let list = groups[key], let first = list.first else { return nil }
            let lastMinute = list.last?.minutesAway
            let next = list.prefix(2).map {
                CoachDeparture(minutes: $0.minutesAway, isLast: !overnight && $0.minutesAway == lastMinute)
            }
            return NearbyDirection(lineId: first.lineId, destination: destinationName(first), departures: next)
        }
    }
}

/// Station and destination names for the nearby notification, through the
/// app's own localized-name data: the reviewed station-complex registry (the
/// name the station board shows, with Albanian and Italian forms), then the
/// coordinate table's English/Greek pair. Airport stops read as the app's
/// airport label in every language.
enum NearbyPlaceNames {
    static let airportStopIds: Set<String> = ["M3_AER", "A1_AIR", "A2_AIR", "THS_AIR"]

    static func airportLabel(_ lang: AppLanguage) -> String {
        travelText(lang, "Airport", "Αεροδρόμιο", "Aeroporti", "Aeroporto")
    }

    /// The localized name for a stop id, or nil when the id is unknown.
    static func stopName(_ stopId: String, _ lang: AppLanguage) -> String? {
        if airportStopIds.contains(stopId) { return airportLabel(lang) }
        if let complex = StationComplexRegistry.complex(forStopId: stopId) {
            return complex.localizedName(lang)
        }
        guard let name = StationCoords.localizedStationName(for: stopId, lang) else { return nil }
        return lang == .greek ? name : SyrmosData.localizedStationName(name, language: lang)
    }

    /// The station title: the first of the node's stops the app can name.
    static func stationName(_ node: MapStationNode, _ lang: AppLanguage) -> String {
        let ids = node.stationIds.isEmpty ? [node.id] : node.stationIds
        for id in ids { if let name = stopName(id, lang) { return name } }
        return lang == .greek ? node.nameEl : SyrmosData.localizedStationName(node.displayName, language: lang)
    }

    /// A departure's direction string resolved to a stop on its line, then named
    /// in the reader's language. Matching is exact on the folded name (either
    /// language) first, then by abbreviation ("Douk. Plakentias" for "Doukissis
    /// Plakentias": every token of the stop name is a prefix of a direction
    /// token), accepted only when exactly one stop matches. Unknown directions
    /// stay as given rather than being guessed.
    static func destination(lineId: String, direction: String, _ lang: AppLanguage) -> String {
        guard let stopId = resolveStopId(lineId: lineId, direction: direction),
              let name = stopName(stopId, lang) else {
            return DirectionL10n.localized(lineId: lineId, direction: direction, language: lang)
        }
        return name
    }

    static func resolveStopId(lineId: String, direction: String) -> String? {
        let folded = fold(direction)
        guard !folded.isEmpty else { return nil }
        if ["airport", "aerodromio", "αεροδρομιο"].contains(folded) {
            let stops = SyrmosData.stations(for: lineId).filter { airportStopIds.contains($0.id) }
            if let id = stops.first?.id { return id }
        }
        let stops = SyrmosData.stations(for: lineId)
        if let exact = stops.first(where: { fold($0.name) == folded || fold($0.nameEl) == folded }) {
            return exact.id
        }
        let dirTokens = tokens(folded)
        let abbreviated = stops.filter { stop in
            [stop.name, stop.nameEl].contains { name in
                let t = tokens(fold(name))
                return !t.isEmpty && t.allSatisfy { st in dirTokens.contains { $0.hasPrefix(st) } }
            }
        }
        return abbreviated.count == 1 ? abbreviated[0].id : nil
    }

    private static func fold(_ s: String) -> String {
        s.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .trimmingCharacters(in: .whitespaces)
    }

    private static func tokens(_ s: String) -> [String] {
        s.split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map(String.init).filter { $0.count >= 2 }
    }
}

/// Shared per-station cooldown and daily count, so the nearby next-train
/// notification and the older nearby service-alert notification never both fire
/// for the same station within the cooldown.
enum NearbyNotificationLedger {
    private static let lastKey = "syrmos.nearby.lastByStation"
    private static let dayKey = "syrmos.nearby.dayKey"
    private static let countKey = "syrmos.nearby.dayCount"

    static func lastNotified(stationIds: [String]) -> Int64? {
        let map = UserDefaults.standard.dictionary(forKey: lastKey) as? [String: Int64] ?? [:]
        return stationIds.compactMap { map[$0] }.max()
    }

    static func notificationsToday(now: Date) -> Int {
        guard UserDefaults.standard.string(forKey: dayKey) == athensDateKey(now) else { return 0 }
        return UserDefaults.standard.integer(forKey: countKey)
    }

    static func record(stationIds: [String], now: Date, countsTowardDailyCap: Bool = true) {
        var map = UserDefaults.standard.dictionary(forKey: lastKey) as? [String: Int64] ?? [:]
        let epoch = Int64(now.timeIntervalSince1970)
        for sid in stationIds { map[sid] = epoch }
        // Forget entries older than a day so the map stays small.
        map = map.filter { epoch - $0.value < 24 * 3600 }
        UserDefaults.standard.set(map, forKey: lastKey)
        guard countsTowardDailyCap else { return }
        let today = athensDateKey(now)
        let count = UserDefaults.standard.string(forKey: dayKey) == today ? UserDefaults.standard.integer(forKey: countKey) : 0
        UserDefaults.standard.set(today, forKey: dayKey)
        UserDefaults.standard.set(count + 1, forKey: countKey)
    }

    static func inCooldown(stationIds: [String], now: Date) -> Bool {
        guard let last = lastNotified(stationIds: stationIds) else { return false }
        return Int64(now.timeIntervalSince1970) - last < NearbyDepartureCoach.cooldownSeconds
    }
}

@MainActor
final class StationGeofenceService: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = StationGeofenceService()

    @Published private(set) var authorization: CLAuthorizationStatus
    private let manager = CLLocationManager()
    private let regionPrefix = "syrmos.geo."
    private var latestLocation: CLLocation?

    private override init() {
        authorization = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var hasAlways: Bool { authorization == .authorizedAlways }

    /// Called on launch and whenever the toggle changes.
    func start() {
        guard NotificationPreferences.nearbyTrainsEnabled else { stop(); return }
        guard hasAlways else { return }
        manager.startMonitoringSignificantLocationChanges()
        if let loc = manager.location { repick(around: loc) }
    }

    /// The rider turned the toggle on: ask for Always (iOS first grants While
    /// Using, then offers the upgrade), then start.
    func enable() {
        NotificationPreferences.nearbyTrainsEnabled = true
        switch manager.authorizationStatus {
        case .notDetermined, .authorizedWhenInUse:
            manager.requestAlwaysAuthorization()
        default:
            break
        }
        start()
    }

    func stop() {
        manager.stopMonitoringSignificantLocationChanges()
        for region in manager.monitoredRegions where region.identifier.hasPrefix(regionPrefix) {
            manager.stopMonitoring(for: region)
        }
    }

    /// Monitor the nearest 20 stations, replacing the previous set.
    private func repick(around location: CLLocation) {
        latestLocation = location
        guard CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self) else { return }
        let radius = min(NearbyDepartureCoach.geofenceRadiusMeters, manager.maximumRegionMonitoringDistance)
        let nearest = PreloadedData.stations
            .map { ($0, location.distance(from: CLLocation(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude))) }
            .sorted { $0.1 < $1.1 }
            .prefix(NearbyDepartureCoach.monitoredStationCount)
        let wanted = Set(nearest.map { regionPrefix + $0.0.id })
        for region in manager.monitoredRegions where region.identifier.hasPrefix(regionPrefix) && !wanted.contains(region.identifier) {
            manager.stopMonitoring(for: region)
        }
        let existing = Set(manager.monitoredRegions.map(\.identifier))
        for (node, _) in nearest where !existing.contains(regionPrefix + node.id) {
            let region = CLCircularRegion(center: node.coordinate, radius: radius, identifier: regionPrefix + node.id)
            region.notifyOnEntry = true
            region.notifyOnExit = false
            manager.startMonitoring(for: region)
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
            self.start()
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        Task { @MainActor in self.repick(around: loc) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        let id = region.identifier
        let loc = manager.location
        Task { @MainActor in
            guard id.hasPrefix(self.regionPrefix) else { return }
            let nodeId = String(id.dropFirst(self.regionPrefix.count))
            guard let node = PreloadedData.stations.first(where: { $0.id == nodeId }) else { return }
            await self.notifyArrival(near: node, location: loc ?? self.latestLocation)
        }
    }

    /// Compose and post the notification for `node`, honouring every guard.
    /// Internal so the debug entry in Settings and the simulator check can drive it.
    func notifyArrival(near node: MapStationNode, location: CLLocation?, now: Date = SyrmosClock.now, force: Bool = false) async {
        let stationIds = node.stationIds.isEmpty ? [node.id] : node.stationIds
        let speedKmh: Double? = location.flatMap { $0.speed >= 0 ? $0.speed * 3.6 : nil }
        let guardInput = CoachGuardInput(
            enabled: NotificationPreferences.nearbyTrainsEnabled,
            goJourneyActive: Self.goJourneyActive,
            athensHour: athensHour(now),
            speedKmh: speedKmh,
            lastNotifiedForStationEpochSeconds: NearbyNotificationLedger.lastNotified(stationIds: stationIds),
            notificationsToday: NearbyNotificationLedger.notificationsToday(now: now),
            now: Int64(now.timeIntervalSince1970)
        )
        guard force || NearbyDepartureCoach.guardReason(guardInput) == "allow" else { return }

        let lang = LocalizationManager.shared.language
        let distance = location.map {
            $0.distance(from: CLLocation(latitude: node.coordinate.latitude, longitude: node.coordinate.longitude))
        } ?? NearbyDepartureCoach.geofenceRadiusMeters
        let walk = NearbyDepartureCoach.walkMinutes(distanceMeters: distance)
        let effect = ClosuresStore.shared.effect(stationIds: stationIds)
        var departures: [Departure] = []
        for lineId in node.lineIds {
            let sid = node.stationIdByLineId[lineId] ?? stationIds.first ?? node.id
            departures += ScheduleProjector.nextDepartures(for: sid, lineIds: [lineId], limit: 400)
        }
        let overnight = ScheduleProjector.isOvernightServiceToday(lineIds: node.lineIds)
        let directions = NearbyTrainComposer.directions(from: departures, overnight: overnight) {
            NearbyPlaceNames.destination(lineId: $0.lineId, direction: $0.direction, lang)
        }
        let name = NearbyPlaceNames.stationName(node, lang)
        guard let output = NearbyTrainComposer.compose(
            stationName: name, stationKey: stationIds.first ?? node.id, walkMinutes: walk,
            directions: directions, stationClosed: effect.active && !effect.canBoard,
            athensDate: athensDateKey(now), language: lang
        ) else { return }

        let content = UNMutableNotificationContent()
        content.title = output.title
        content.body = output.body
        content.sound = .default
        content.threadIdentifier = "syrmos.nearby"
        content.categoryIdentifier = "NEARBY_TRAINS"
        content.userInfo = ["stationId": node.stationIdByLineId[node.lineIds.first ?? ""] ?? stationIds.first ?? ""]
        try? await UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: "syrmos.nearby.trains.\(node.id)", content: content, trigger: nil)
        )
        NearbyNotificationLedger.record(stationIds: stationIds, now: now)
    }

    /// QA entry (Settings > Developer): notify for the station closest to the
    /// last known location right now, skipping the guards.
    func debugNotifyNearest() async {
        guard let loc = manager.location ?? latestLocation else { return }
        let nearest = PreloadedData.stations.min {
            loc.distance(from: CLLocation(latitude: $0.coordinate.latitude, longitude: $0.coordinate.longitude))
                < loc.distance(from: CLLocation(latitude: $1.coordinate.latitude, longitude: $1.coordinate.longitude))
        }
        guard let nearest else { return }
        await notifyArrival(near: nearest, location: loc, force: true)
    }

    static var goJourneyActive: Bool {
        if GoActiveJourneyStore.shared.active != nil { return true }
        #if canImport(ActivityKit)
        if #available(iOS 16.2, *) {
            return Activity<GoJourneyActivityAttributes>.activities.contains { $0.activityState == .active }
        }
        #endif
        return false
    }
}
