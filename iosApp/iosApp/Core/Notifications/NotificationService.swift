import Foundation
import UserNotifications
import CoreLocation

@MainActor
final class NotificationService: ObservableObject {
    static let shared = NotificationService()

    @Published private(set) var isAuthorized = false

    private let center = UNUserNotificationCenter.current()
    private let seenAlertsKey = "syrmos.notifications.seenAlertIds"
    private let lastWeatherNotifKey = "syrmos.notifications.lastWeatherNotifDate"
    private let lastNearbyNotifKey = "syrmos.notifications.lastNearbyNotifPrefix"

    private init() {
        Task { await refreshAuthorizationStatus() }
    }

    // MARK: - Authorization

    func requestAuthorization() async {
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound, .badge])
            isAuthorized = granted
        } catch {
            isAuthorized = false
        }
    }

    func refreshAuthorizationStatus() async {
        let settings = await center.notificationSettings()
        isAuthorized = settings.authorizationStatus == .authorized
    }

    // MARK: - Morning Digest

    func scheduleMorningDigest() {
        center.removePendingNotificationRequests(withIdentifiers: ["syrmos.morning.digest"])

        guard NotificationPreferences.morningDigestEnabled else { return }

        var dateComponents = DateComponents()
        dateComponents.hour = 7
        dateComponents.minute = 0
        dateComponents.timeZone = TimeZone(identifier: "Europe/Athens")

        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)

        let content = UNMutableNotificationContent()
        content.title = localizedTitle("morningDigest")
        content.body = localizedBody("morningDigestBody")
        content.sound = .default
        content.categoryIdentifier = "MORNING_DIGEST"

        let request = UNNotificationRequest(
            identifier: "syrmos.morning.digest",
            content: content,
            trigger: trigger
        )
        center.add(request)
    }

    // MARK: - New Service Alert Detection

    func checkForNewAlerts(_ alerts: [STASYAnnouncement]) {
        guard NotificationPreferences.serviceAlertsEnabled else { return }
        guard isAuthorized else { return }

        let currentIds = Set(alerts.map(\.id))
        let seenIds = loadSeenAlertIds()
        let newIds = currentIds.subtracting(seenIds)

        if newIds.isEmpty { return }

        let newAlerts = alerts.filter { newIds.contains($0.id) }
        let lang = LocalizationManager.shared.language

        for alert in newAlerts.prefix(3) {
            let content = UNMutableNotificationContent()
            content.title = localizedTitle("serviceAlert")
            content.body = alert.displayTitle(language: lang)
            content.sound = .default
            content.categoryIdentifier = "SERVICE_ALERT"
            content.userInfo = ["alertId": alert.id]

            let request = UNNotificationRequest(
                identifier: "syrmos.alert.\(alert.id.hashValue)",
                content: content,
                trigger: nil
            )
            center.add(request)
        }

        saveSeenAlertIds(currentIds)
    }

    // MARK: - Weather Alerts

    func checkWeather(_ snapshot: WeatherSnapshot?) {
        guard NotificationPreferences.weatherAlertsEnabled else { return }
        guard isAuthorized else { return }
        guard let snapshot, snapshot.current.condition.isSevere else { return }

        let today = Calendar.current.startOfDay(for: Date())
        let lastNotifDate = UserDefaults.standard.object(forKey: lastWeatherNotifKey) as? Date
        if let last = lastNotifDate, Calendar.current.isDate(last, inSameDayAs: today) {
            return
        }

        let content = UNMutableNotificationContent()
        content.title = localizedTitle("weatherAlert")
        content.body = weatherAlertBody(snapshot.current)
        content.sound = .default
        content.categoryIdentifier = "WEATHER_ALERT"

        let request = UNNotificationRequest(
            identifier: "syrmos.weather.\(today.timeIntervalSince1970)",
            content: content,
            trigger: nil
        )
        center.add(request)
        UserDefaults.standard.set(today, forKey: lastWeatherNotifKey)
    }

    // MARK: - Nearby Station Alerts

    func checkNearbyStationAlerts(
        nearbyStations: [LocationService.NearbyStation],
        alerts: [STASYAnnouncement]
    ) {
        guard NotificationPreferences.nearbyAlertsEnabled else { return }
        guard isAuthorized else { return }

        let closeStations = nearbyStations.filter { $0.distanceMeters < 500 }
        guard !closeStations.isEmpty else { return }

        let activeAlerts = alerts.filter { $0.category == .serviceAlert }
        guard !activeAlerts.isEmpty else { return }

        let lang = LocalizationManager.shared.language
        let prefix = closeStations.map(\.id).sorted().joined(separator: ",")
        let lastPrefix = UserDefaults.standard.string(forKey: lastNearbyNotifKey) ?? ""
        if prefix == lastPrefix { return }

        let stationName = closeStations.first?.station.name ?? ""
        let relevantAlert = activeAlerts.first

        guard let alert = relevantAlert else { return }

        let content = UNMutableNotificationContent()
        content.title = "\(localizedTitle("nearbyAlert")) \(stationName)"
        content.body = alert.displayTitle(language: lang)
        content.sound = .default
        content.categoryIdentifier = "NEARBY_ALERT"

        let request = UNNotificationRequest(
            identifier: "syrmos.nearby.\(prefix.hashValue)",
            content: content,
            trigger: nil
        )
        center.add(request)
        UserDefaults.standard.set(prefix, forKey: lastNearbyNotifKey)
    }

    // MARK: - Seen Alert Storage

    private func loadSeenAlertIds() -> Set<String> {
        let array = UserDefaults.standard.stringArray(forKey: seenAlertsKey) ?? []
        return Set(array)
    }

    private func saveSeenAlertIds(_ ids: Set<String>) {
        UserDefaults.standard.set(Array(ids), forKey: seenAlertsKey)
    }

    // MARK: - Localization Helpers

    private func localizedTitle(_ key: String) -> String {
        let lang = LocalizationManager.shared.language
        switch key {
        case "morningDigest":
            switch lang {
            case .greek: return "Ενημερωση Πρωινου"
            case .albanian: return "Perditesimi i Mengjesit"
            case .italian: return "Aggiornamento mattutino"
            default: return "Morning Update"
            }
        case "serviceAlert":
            switch lang {
            case .greek: return "Ειδοποιηση Υπηρεσιας"
            case .albanian: return "Njoftim Sherbimi"
            case .italian: return "Avviso di servizio"
            default: return "Service Alert"
            }
        case "weatherAlert":
            switch lang {
            case .greek: return "Καιρικη Ειδοποιηση"
            case .albanian: return "Njoftim Moti"
            case .italian: return "Avviso meteo"
            default: return "Weather Alert"
            }
        case "nearbyAlert":
            switch lang {
            case .greek: return "Ειδοποιηση κοντα στο"
            case .albanian: return "Njoftim prane"
            case .italian: return "Avviso vicino a"
            default: return "Alert near"
            }
        default:
            return key
        }
    }

    private func localizedBody(_ key: String) -> String {
        let lang = LocalizationManager.shared.language
        switch key {
        case "morningDigestBody":
            switch lang {
            case .greek: return "Δειτε τις ειδοποιησεις υπηρεσιας και τον καιρο για σημερα."
            case .albanian: return "Shikoni njoftimet e sherbimit dhe motin per sot."
            case .italian: return "Controlla gli avvisi di servizio e le condizioni meteo di oggi."
            default: return "Check today's service alerts and weather conditions."
            }
        default:
            return key
        }
    }

    private func weatherAlertBody(_ weather: CurrentWeather) -> String {
        let lang = LocalizationManager.shared.language
        let tempStr = String(format: "%.0f", weather.temperatureC)
        switch weather.condition {
        case .thunderstorm:
            switch lang {
            case .greek: return "Καταιγιδα στην περιοχη. \(tempStr) C. Προσεξτε στις μετακινησεις."
            case .albanian: return "Stuhi ne zone. \(tempStr) C. Kujdes ne udhetim."
            case .italian: return "Temporale nella zona. \(tempStr) C. Attenzione negli spostamenti."
            default: return "Thunderstorm in the area. \(tempStr) C. Take care while traveling."
            }
        case .snow:
            switch lang {
            case .greek: return "Χιονοπτωση στην περιοχη. \(tempStr) C. Πιθανες καθυστερησεις."
            case .albanian: return "Debore ne zone. \(tempStr) C. Vonesa te mundshme."
            case .italian: return "Nevicata nella zona. \(tempStr) C. Possibili ritardi."
            default: return "Snowfall in the area. \(tempStr) C. Possible delays."
            }
        case .showers:
            switch lang {
            case .greek: return "Εντονες βροχοπτωσεις. \(tempStr) C. Πιθανες καθυστερησεις."
            case .albanian: return "Reshje te forta shiu. \(tempStr) C. Vonesa te mundshme."
            case .italian: return "Forti piogge nella zona. \(tempStr) C. Possibili ritardi."
            default: return "Heavy rain in the area. \(tempStr) C. Possible delays."
            }
        default:
            return "\(tempStr) C"
        }
    }
}

// MARK: - Notification Preferences

enum NotificationPreferences {
    private static let serviceAlertsKey = "syrmos.notif.serviceAlerts"
    private static let weatherAlertsKey = "syrmos.notif.weatherAlerts"
    private static let nearbyAlertsKey = "syrmos.notif.nearbyAlerts"
    private static let morningDigestKey = "syrmos.notif.morningDigest"

    static var serviceAlertsEnabled: Bool {
        get { UserDefaults.standard.object(forKey: serviceAlertsKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: serviceAlertsKey) }
    }

    static var weatherAlertsEnabled: Bool {
        get { UserDefaults.standard.object(forKey: weatherAlertsKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: weatherAlertsKey) }
    }

    static var nearbyAlertsEnabled: Bool {
        get { UserDefaults.standard.object(forKey: nearbyAlertsKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: nearbyAlertsKey) }
    }

    static var morningDigestEnabled: Bool {
        get { UserDefaults.standard.object(forKey: morningDigestKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: morningDigestKey) }
    }
}
