import Foundation

struct STASYAnnouncement: Identifiable {
    let id: String
    let title: String
    let titleEn: String
    let titleSq: String
    let date: String
    let summary: String
    let summaryEn: String
    let summarySq: String
    let url: URL?
    let category: AnnouncementCategory

    /// Pick the right language-variant title for the active app language.
    /// Albanian falls back to English when titleSq is blank (the synthetic
    /// banner gets translated, but other scraped EN-source articles don't).
    func displayTitle(language: AppLanguage) -> String {
        switch language {
        case .greek: return title
        case .albanian: return titleSq.isEmpty ? (titleEn.isEmpty ? title : titleEn) : titleSq
        case .english: return titleEn.isEmpty ? title : titleEn
        }
    }

    func displaySummary(language: AppLanguage) -> String {
        switch language {
        case .greek: return summary
        case .albanian: return summarySq.isEmpty ? (summaryEn.isEmpty ? summary : summaryEn) : summarySq
        case .english: return summaryEn.isEmpty ? summary : summaryEn
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
    @Published var announcements: [STASYAnnouncement] = []
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
        let serviceUntil: String?
        let scrapedAt: String?

        func displayMessage(language: AppLanguage) -> String {
            switch language {
            case .greek:
                return rawMessage
            case .albanian:
                if let sq = rawMessageSq, !sq.isEmpty { return sq }
                return (rawMessageEn?.isEmpty == false ? rawMessageEn! : rawMessage)
            case .english:
                return (rawMessageEn?.isEmpty == false ? rawMessageEn! : rawMessage)
            }
        }
    }

    private struct APIAnnouncement: Decodable {
        let id: String
        let title: String
        let titleEn: String?
        let titleSq: String?
        let date: String
        let summary: String
        let summaryEn: String?
        let summarySq: String?
        let url: String
        let category: String
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
                    date: item.date,
                    summary: item.summary,
                    summaryEn: item.summaryEn ?? "",
                    summarySq: item.summarySq ?? "",
                    url: URL(string: item.url),
                    category: AnnouncementCategory(rawValue: item.category == "serviceAlert" ? "Έκτακτες Ανακοινώσεις" : "Ανακοινώσεις") ?? .general
                )
            }
            announcements = parsed
            lastUpdated = Date()
            // The feed came back from the API, so we're online. Flip the
            // home offline-alive pill to "live".
            LiveDataFreshness.shared.markLive()
            cacheAnnouncements(parsed)
        } catch {
            self.error = "Could not reach Syrmos API"
            // keep showing cached content silently — don't flip back to empty
            if announcements.isEmpty {
                loadCachedAnnouncements()
            }
        }

        isLoading = false
    }

    // MARK: - Cache

    private func cacheAnnouncements(_ announcements: [STASYAnnouncement]) {
        let dicts = announcements.map { ann -> [String: String] in
            [
                "id": ann.id,
                "title": ann.title,
                "titleEn": ann.titleEn,
                "titleSq": ann.titleSq,
                "date": ann.date,
                "summary": ann.summary,
                "summaryEn": ann.summaryEn,
                "summarySq": ann.summarySq,
                "url": ann.url?.absoluteString ?? "",
                "category": ann.category.rawValue,
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
                date: dict["date"] ?? "",
                summary: dict["summary"] ?? "",
                summaryEn: dict["summaryEn"] ?? "",
                summarySq: dict["summarySq"] ?? "",
                url: URL(string: dict["url"] ?? ""),
                category: AnnouncementCategory(rawValue: dict["category"] ?? "") ?? .other
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
                date: item.date,
                summary: item.summary,
                summaryEn: item.summaryEn ?? "",
                summarySq: item.summarySq ?? "",
                url: URL(string: item.url),
                category: AnnouncementCategory(rawValue: item.category == "serviceAlert" ? "Έκτακτες Ανακοινώσεις" : "Ανακοινώσεις") ?? .general
            )
        }
    }
}
