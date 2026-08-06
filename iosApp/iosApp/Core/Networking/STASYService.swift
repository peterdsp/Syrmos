import Foundation

struct STASYAnnouncement: Identifiable {
    let id: String
    let title: String
    let titleEn: String
    let titleSq: String
    let titleIt: String
    let date: String
    let summary: String
    let summaryEn: String
    let summarySq: String
    let summaryIt: String
    let url: URL?
    let category: AnnouncementCategory
    /// Lines this notice affects (e.g. ["M3"]), from the feed's `affectedLines`.
    /// Lets Ariadne surface a line-wide advisory to any station on that line,
    /// matching the KMP `STASYAnnouncement`.
    let affectedLines: [String]
    let affectedStationIds: [String]
    /// Raw feed severity: "info" | "warning" | "closure".
    let severity: String
    let validFrom: String?
    let validUntil: String?
    /// "HH:MM" cutoff after which this alert activates
    let serviceUntilTime: String?

    init(
        id: String,
        title: String,
        titleEn: String,
        titleSq: String,
        titleIt: String = "",
        date: String,
        summary: String,
        summaryEn: String,
        summarySq: String,
        summaryIt: String = "",
        url: URL?,
        category: AnnouncementCategory,
        affectedLines: [String] = [],
        affectedStationIds: [String] = [],
        severity: String = "info",
        validFrom: String? = nil,
        validUntil: String? = nil,
        serviceUntilTime: String? = nil
    ) {
        self.id = id
        self.title = title
        self.titleEn = Self.safeTitle(titleEn, source: title, language: .english, category: category)
        self.titleSq = Self.safeTitle(titleSq, source: title, language: .albanian, category: category)
        self.titleIt = Self.safeTitle(titleIt, source: title, language: .italian, category: category)
        self.date = date
        self.summary = summary
        self.summaryEn = Self.safeSummary(summaryEn, source: summary, language: .english)
        self.summarySq = Self.safeSummary(summarySq, source: summary, language: .albanian)
        self.summaryIt = Self.safeSummary(summaryIt, source: summary, language: .italian)
        self.url = url
        self.category = category
        self.affectedLines = affectedLines
        self.affectedStationIds = affectedStationIds
        self.severity = severity
        self.validFrom = validFrom
        self.validUntil = validUntil
        self.serviceUntilTime = serviceUntilTime
    }

    /// Pick the right language-variant title for the active app language.
    /// Albanian falls back to English when titleSq is blank (the synthetic
    /// banner gets translated, but other scraped EN-source articles don't).
    func displayTitle(language: AppLanguage) -> String {
        switch language {
        case .greek: return title
        case .albanian: return titleSq.isEmpty ? (titleEn.isEmpty ? title : titleEn) : titleSq
        case .italian: return titleIt.isEmpty ? (titleEn.isEmpty ? title : titleEn) : titleIt
        case .english: return titleEn.isEmpty ? title : titleEn
        }
    }

    func displaySummary(language: AppLanguage) -> String {
        switch language {
        case .greek: return summary
        case .albanian: return summarySq.isEmpty ? (summaryEn.isEmpty ? summary : summaryEn) : summarySq
        case .italian: return summaryIt.isEmpty ? (summaryEn.isEmpty ? summary : summaryEn) : summaryIt
        case .english: return summaryEn.isEmpty ? summary : summaryEn
        }
    }

    static func isUsableTranslation(_ text: String) -> Bool {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        return !text.unicodeScalars.contains { scalar in
            (0x0370...0x03FF).contains(scalar.value) || (0x1F00...0x1FFF).contains(scalar.value)
        }
    }

    private static func safeTitle(
        _ candidate: String,
        source: String,
        language: AppLanguage,
        category: AnnouncementCategory
    ) -> String {
        if isUsableTranslation(candidate) { return candidate }
        if language == .english, isUsableTranslation(source) { return source }
        let isAlert = category == .serviceAlert
        switch language {
        case .greek: return source
        case .albanian: return isAlert ? "Njoftim për shërbimin" : "Njoftim hekurudhor"
        case .italian: return isAlert ? "Avviso sul servizio" : "Avviso ferroviario"
        case .english: return isAlert ? "Service alert" : "Rail announcement"
        }
    }

    private static func safeSummary(
        _ candidate: String,
        source: String,
        language: AppLanguage
    ) -> String {
        if isUsableTranslation(candidate) { return candidate }
        if language == .english, isUsableTranslation(source) { return source }
        switch language {
        case .greek: return source
        case .albanian: return "Hap njoftimin zyrtar për hollësi të plota."
        case .italian: return "Apri l'avviso ufficiale per tutti i dettagli."
        case .english: return "Open the official notice for full details."
        }
    }
}

enum AnnouncementCategory: String {
    case serviceAlert = "Έκτακτες Ανακοινώσεις"
    case general = "Ανακοινώσεις"
    case other = ""

    var displayName: String {
        switch self {
        case .serviceAlert: return "Service Alert"
        case .general: return "Announcement"
        case .other: return "News"
        }
    }
}

@MainActor
final class STASYService: ObservableObject {
    @Published var announcements: [STASYAnnouncement] = [] {
        didSet {
            lineDisruptions = Self.deriveLineDisruptions(from: announcements)
            stationDisruptions = Self.deriveStationDisruptions(from: announcements, status: serviceStatus)
        }
    }
    @Published private(set) var lineDisruptions: [String: String] = [:]
    @Published private(set) var stationDisruptions: [String: String] = [:]
    @Published var isLoading = false
    @Published var lastUpdated: Date?
    @Published var error: String?

    private let apiURL = URL(string: "https://api-syrmos.peterdsp.dev/api/announcements")!
    private let cacheKey = "stasy_announcements_cache"
    private let cacheTimeKey = "stasy_announcements_cache_time"
    private let statusCacheKey = "stasy_service_status_cache"

    init() {
        loadCachedAnnouncements()
        loadCachedStatus()
        // If both the UserDefaults cache and the live API are unavailable
        // (first cold launch with no network), fall back to the bundled
        // snapshot shipped at build time. Keeps the home screen non-empty.
        if announcements.isEmpty && serviceStatus == nil {
            hydrateFromBundleIfNeeded()
        }
    }

    private static func deriveLineDisruptions(from announcements: [STASYAnnouncement]) -> [String: String] {
        let rank = ["info": 0, "warning": 1, "closure": 2]
        var result: [String: String] = [:]
        for announcement in announcements where announcement.category == .serviceAlert {
            let severity = announcement.severity.lowercased()
            for rawLineId in announcement.affectedLines {
                let lineId = rawLineId.uppercased()
                if rank[severity, default: 0] > rank[result[lineId] ?? "info", default: 0] {
                    result[lineId] = severity
                }
                if lineId == "M3_AIR", rank[severity, default: 0] > rank[result["M3"] ?? "info", default: 0] {
                    result["M3"] = severity
                }
                if lineId == "M3", rank[severity, default: 0] > rank[result["M3_AIR"] ?? "info", default: 0] {
                    result["M3_AIR"] = severity
                }
            }
        }
        return result
    }

    private static func deriveStationDisruptions(from announcements: [STASYAnnouncement], status: APIStatus?) -> [String: String] {
        let rank = ["info": 0, "warning": 1, "closure": 2]
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = TimeZone(identifier: "Europe/Athens")
        let nowHHMM = formatter.string(from: Date())

        var result: [String: String] = [:]
        for announcement in announcements where announcement.category == .serviceAlert {
            let severity = announcement.severity.lowercased()
            let cutoff = announcement.serviceUntilTime ?? status?.serviceUntil
            if let cutoff, nowHHMM < cutoff { continue }

            for stationId in announcement.affectedStationIds {
                let sid = stationId.trimmingCharacters(in: .whitespaces)
                guard !sid.isEmpty else { continue }
                if rank[severity, default: 0] > rank[result[sid] ?? "info", default: 0] {
                    result[sid] = severity
                }
            }
        }
        return result
    }

    private struct APIPayload: Decodable {
        let updatedAt: String?
        let count: Int
        let status: APIStatus?
        let announcements: [APIAnnouncement]
    }

    /// Mirrors the `status` object the Pi exposes: `normal` (Κανονική
    /// Λειτουργία), `alert` (with optional `serviceUntil` HH:MM), or
    /// `unknown` when the watcher couldn't detect a badge.
    struct APIStatus: Decodable {
        let status: String
        let rawMessage: String
        let rawMessageEn: String?
        let rawMessageSq: String?
        let rawMessageIt: String?
        let serviceUntil: String?
        let scrapedAt: String?

        func displayMessage(language: AppLanguage) -> String {
            switch language {
            case .greek:
                return rawMessage
            case .albanian:
                if let sq = rawMessageSq, STASYAnnouncement.isUsableTranslation(sq) { return sq }
                return "Njoftim për shërbimin. Hap njoftimin zyrtar për hollësi."
            case .italian:
                if let it = rawMessageIt, STASYAnnouncement.isUsableTranslation(it) { return it }
                return "Avviso sul servizio. Apri l'avviso ufficiale per i dettagli."
            case .english:
                if let en = rawMessageEn, STASYAnnouncement.isUsableTranslation(en) { return en }
                return "Service alert. Open the official notice for details."
            }
        }
    }

    private struct APIAnnouncement: Decodable {
        let id: String
        let title: String
        let titleEn: String?
        let titleSq: String?
        let titleIt: String?
        let date: String
        let summary: String
        let summaryEn: String?
        let summarySq: String?
        let summaryIt: String?
        let url: String
        let category: String
        let affectedLines: [String]?
        let affectedStationIds: [String]?
        let severity: String?
        let validFrom: String?
        let validUntil: String?
        let serviceUntilTime: String?
    }

    /// Latest STASY service-status badge, populated by `fetchAnnouncements`.
    @Published var serviceStatus: APIStatus?

    func fetchAnnouncements() async {
        // Don't flip isLoading if we already have cached content — refresh silently
        if announcements.isEmpty { isLoading = true }
        error = nil

        do {
            var request = URLRequest(url: apiURL)
            request.timeoutInterval = 10
            request.cachePolicy = .reloadIgnoringLocalCacheData

            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }

            let payload = try JSONDecoder().decode(APIPayload.self, from: data)
            serviceStatus = payload.status
            cacheStatus(payload.status)
            let parsed: [STASYAnnouncement] = payload.announcements.map { item in
                STASYAnnouncement(
                    id: item.id,
                    title: item.title,
                    titleEn: item.titleEn ?? "",
                    titleSq: item.titleSq ?? "",
                    titleIt: item.titleIt ?? "",
                    date: item.date,
                    summary: item.summary,
                    summaryEn: item.summaryEn ?? "",
                    summarySq: item.summarySq ?? "",
                    summaryIt: item.summaryIt ?? "",
                    url: URL(string: item.url),
                    category: AnnouncementCategory(rawValue: item.category == "serviceAlert" ? "Έκτακτες Ανακοινώσεις" : "Ανακοινώσεις") ?? .general,
                    affectedLines: item.affectedLines ?? [],
                    affectedStationIds: item.affectedStationIds ?? [],
                    severity: item.severity ?? "info",
                    validFrom: item.validFrom,
                    validUntil: item.validUntil,
                    serviceUntilTime: item.serviceUntilTime
                )
            }
            announcements = parsed
            lastUpdated = Date()
            // The feed came back from the API, so we're online. Flip the
            // home offline-alive pill to "live".
            LiveDataFreshness.shared.markLive()
            cacheAnnouncements(parsed)
            // Surface the current alerts in the Weather + Alerts widget. Prefer
            // real service alerts, then fall back to general announcements.
            let ranked = parsed.sorted { a, _ in a.category == .serviceAlert }
            WidgetBridge.publishAlerts(ranked.map { $0.titleEn.isEmpty ? $0.title : $0.titleEn })
        } catch {
            self.error = "Could not reach Syrmos API"
            // keep showing cached content silently — don't flip back to empty
            if announcements.isEmpty {
                loadCachedAnnouncements()
            }
        }

        isLoading = false
    }

    static func cachedAlert(byId id: String) -> STASYAnnouncement? {
        guard let dicts = UserDefaults.standard.array(forKey: "stasy_announcements_cache") as? [[String: String]] else { return nil }
        guard let dict = dicts.first(where: { $0["id"] == id }) else { return nil }
        guard let title = dict["title"] else { return nil }
        return STASYAnnouncement(
            id: id,
            title: title,
            titleEn: dict["titleEn"] ?? "",
            titleSq: dict["titleSq"] ?? "",
            titleIt: dict["titleIt"] ?? "",
            date: dict["date"] ?? "",
            summary: dict["summary"] ?? "",
            summaryEn: dict["summaryEn"] ?? "",
            summarySq: dict["summarySq"] ?? "",
            summaryIt: dict["summaryIt"] ?? "",
            url: URL(string: dict["url"] ?? ""),
            category: AnnouncementCategory(rawValue: dict["category"] ?? "") ?? .other,
            affectedLines: (dict["affectedLines"] ?? "").split(separator: ",").map(String.init),
            affectedStationIds: (dict["affectedStationIds"] ?? "").split(separator: ",").map(String.init),
            severity: dict["severity"] ?? "info",
            validFrom: (dict["validFrom"]?.isEmpty == false) ? dict["validFrom"] : nil,
            validUntil: (dict["validUntil"]?.isEmpty == false) ? dict["validUntil"] : nil,
            serviceUntilTime: (dict["serviceUntilTime"]?.isEmpty == false) ? dict["serviceUntilTime"] : nil
        )
    }

    // MARK: - Cache

    private func cacheAnnouncements(_ announcements: [STASYAnnouncement]) {
        let dicts = announcements.map { ann -> [String: String] in
            [
                "id": ann.id,
                "title": ann.title,
                "titleEn": ann.titleEn,
                "titleSq": ann.titleSq,
                "titleIt": ann.titleIt,
                "date": ann.date,
                "summary": ann.summary,
                "summaryEn": ann.summaryEn,
                "summarySq": ann.summarySq,
                "summaryIt": ann.summaryIt,
                "url": ann.url?.absoluteString ?? "",
                "category": ann.category.rawValue,
                "affectedLines": ann.affectedLines.joined(separator: ","),
                "affectedStationIds": ann.affectedStationIds.joined(separator: ","),
                "severity": ann.severity,
                "validFrom": ann.validFrom ?? "",
                "validUntil": ann.validUntil ?? "",
                "serviceUntilTime": ann.serviceUntilTime ?? "",
            ]
        }
        UserDefaults.standard.set(dicts, forKey: cacheKey)
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: cacheTimeKey)
    }

    private func loadCachedAnnouncements() {
        guard let dicts = UserDefaults.standard.array(forKey: cacheKey) as? [[String: String]] else { return }
        announcements = dicts.compactMap { dict in
            guard let id = dict["id"], let title = dict["title"] else { return nil }
            return STASYAnnouncement(
                id: id,
                title: title,
                titleEn: dict["titleEn"] ?? "",
                titleSq: dict["titleSq"] ?? "",
                titleIt: dict["titleIt"] ?? "",
                date: dict["date"] ?? "",
                summary: dict["summary"] ?? "",
                summaryEn: dict["summaryEn"] ?? "",
                summarySq: dict["summarySq"] ?? "",
                summaryIt: dict["summaryIt"] ?? "",
                url: URL(string: dict["url"] ?? ""),
                category: AnnouncementCategory(rawValue: dict["category"] ?? "") ?? .other,
                affectedLines: (dict["affectedLines"] ?? "").split(separator: ",").map(String.init),
                affectedStationIds: (dict["affectedStationIds"] ?? "").split(separator: ",").map(String.init),
                severity: dict["severity"] ?? "info",
                validFrom: (dict["validFrom"]?.isEmpty == false) ? dict["validFrom"] : nil,
                validUntil: (dict["validUntil"]?.isEmpty == false) ? dict["validUntil"] : nil,
                serviceUntilTime: (dict["serviceUntilTime"]?.isEmpty == false) ? dict["serviceUntilTime"] : nil
            )
        }
        if let cached = UserDefaults.standard.object(forKey: cacheTimeKey) as? TimeInterval {
            lastUpdated = Date(timeIntervalSince1970: cached)
        }
    }

    private func cacheStatus(_ status: APIStatus?) {
        guard let status else {
            UserDefaults.standard.removeObject(forKey: statusCacheKey)
            return
        }
        let dict: [String: String] = [
            "status": status.status,
            "rawMessage": status.rawMessage,
            "rawMessageEn": status.rawMessageEn ?? "",
            "rawMessageSq": status.rawMessageSq ?? "",
            "rawMessageIt": status.rawMessageIt ?? "",
            "serviceUntil": status.serviceUntil ?? "",
            "scrapedAt": status.scrapedAt ?? "",
        ]
        UserDefaults.standard.set(dict, forKey: statusCacheKey)
    }

    private func loadCachedStatus() {
        guard let dict = UserDefaults.standard.dictionary(forKey: statusCacheKey) as? [String: String],
              let status = dict["status"], let rawMessage = dict["rawMessage"]
        else { return }
        serviceStatus = APIStatus(
            status: status,
            rawMessage: rawMessage,
            rawMessageEn: dict["rawMessageEn"],
            rawMessageSq: dict["rawMessageSq"],
            rawMessageIt: dict["rawMessageIt"],
            serviceUntil: (dict["serviceUntil"]?.isEmpty == false) ? dict["serviceUntil"] : nil,
            scrapedAt: dict["scrapedAt"]
        )
    }

    /// First-cold-launch fallback: load the snapshot baked at build time
    /// (`Resources/seed-schedules-v2/announcements.json`). Matches the
    /// same offline-first pattern used for SyrmosSchedulesStore.
    private func hydrateFromBundleIfNeeded() {
        guard let url = Bundle.main.url(
            forResource: "announcements",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ),
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(APIPayload.self, from: data)
        else { return }
        serviceStatus = payload.status
        announcements = payload.announcements.map { item in
            STASYAnnouncement(
                id: item.id,
                title: item.title,
                titleEn: item.titleEn ?? "",
                titleSq: item.titleSq ?? "",
                titleIt: item.titleIt ?? "",
                date: item.date,
                summary: item.summary,
                summaryEn: item.summaryEn ?? "",
                summarySq: item.summarySq ?? "",
                summaryIt: item.summaryIt ?? "",
                url: URL(string: item.url),
                category: AnnouncementCategory(rawValue: item.category == "serviceAlert" ? "Έκτακτες Ανακοινώσεις" : "Ανακοινώσεις") ?? .general,
                affectedLines: item.affectedLines ?? [],
                affectedStationIds: item.affectedStationIds ?? [],
                severity: item.severity ?? "info",
                validFrom: item.validFrom,
                validUntil: item.validUntil,
                serviceUntilTime: item.serviceUntilTime
            )
        }
    }
}
