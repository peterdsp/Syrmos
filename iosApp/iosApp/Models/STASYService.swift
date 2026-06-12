import Foundation

struct STASYAnnouncement: Identifiable {
    let id: String
    let title: String
    let titleEn: String
    let date: String
    let summary: String
    let summaryEn: String
    let url: URL?
    let category: AnnouncementCategory

    /// Pick Greek original or English translation based on app language.
    /// Falls back to Greek if the EN field is empty (older API cache).
    func displayTitle(language: AppLanguage) -> String {
        if language == .greek { return title }
        return titleEn.isEmpty ? title : titleEn
    }

    func displaySummary(language: AppLanguage) -> String {
        if language == .greek { return summary }
        return summaryEn.isEmpty ? summary : summaryEn
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

    init() {
        loadCachedAnnouncements()
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
        let serviceUntil: String?
        let scrapedAt: String?

        func displayMessage(language: AppLanguage) -> String {
            if language == .greek { return rawMessage }
            return (rawMessageEn?.isEmpty == false ? rawMessageEn : nil) ?? rawMessage
        }
    }

    private struct APIAnnouncement: Decodable {
        let id: String
        let title: String
        let titleEn: String?
        let date: String
        let summary: String
        let summaryEn: String?
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
            let parsed: [STASYAnnouncement] = payload.announcements.map { item in
                STASYAnnouncement(
                    id: item.id,
                    title: item.title,
                    titleEn: item.titleEn ?? "",
                    date: item.date,
                    summary: item.summary,
                    summaryEn: item.summaryEn ?? "",
                    url: URL(string: item.url),
                    category: AnnouncementCategory(rawValue: item.category == "serviceAlert" ? "Έκτακτες Ανακοινώσεις" : "Ανακοινώσεις") ?? .general
                )
            }
            announcements = parsed
            lastUpdated = Date()
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
                "date": ann.date,
                "summary": ann.summary,
                "summaryEn": ann.summaryEn,
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
                date: dict["date"] ?? "",
                summary: dict["summary"] ?? "",
                summaryEn: dict["summaryEn"] ?? "",
                url: URL(string: dict["url"] ?? ""),
                category: AnnouncementCategory(rawValue: dict["category"] ?? "") ?? .other
            )
        }
        if let cached = UserDefaults.standard.object(forKey: cacheTimeKey) as? TimeInterval {
            lastUpdated = Date(timeIntervalSince1970: cached)
        }
    }
}
