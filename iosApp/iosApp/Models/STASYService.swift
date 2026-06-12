import Foundation

struct STASYAnnouncement: Identifiable {
    let id: String
    let title: String
    let date: String
    let summary: String
    let url: URL?
    let category: AnnouncementCategory
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
        let serviceUntil: String?
        let scrapedAt: String?
    }

    private struct APIAnnouncement: Decodable {
        let id: String
        let title: String
        let date: String
        let summary: String
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
                    date: item.date,
                    summary: item.summary,
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

    private func parseAnnouncements(from html: String) -> [STASYAnnouncement] {
        var results: [STASYAnnouncement] = []

        // The STASY homepage has a ticker/marquee with "Έκτακτες Ανακοινώσεις"
        // Parse anchor tags near the announcements section
        // Pattern: links with text content that are announcements
        let patterns = [
            // WordPress post links with Greek text
            "href=\"(https://www\\.stasy\\.gr/[^\"]+)\"[^>]*>([^<]+)</a>",
            // Also try catching links near announcement sections
            "<a[^>]+href=\"(https://www\\.stasy\\.gr/\\?p=\\d+)\"[^>]*>([^<]+)</a>",
        ]

        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else { continue }
            let range = NSRange(html.startIndex..., in: html)
            let matches = regex.matches(in: html, options: [], range: range)

            for match in matches {
                guard match.numberOfRanges >= 3,
                      let urlRange = Range(match.range(at: 1), in: html),
                      let titleRange = Range(match.range(at: 2), in: html) else { continue }

                let urlString = String(html[urlRange])
                let title = String(html[titleRange])
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .replacingOccurrences(of: "&#8211;", with: "–")
                    .replacingOccurrences(of: "&#8230;", with: "…")
                    .replacingOccurrences(of: "&amp;", with: "&")

                guard !title.isEmpty,
                      title.count > 10,
                      !title.contains("menu"),
                      !title.contains("skip"),
                      !urlString.contains("#") else { continue }

                let isServiceAlert = html.range(of: "Έκτακτες", range: html.index(urlRange.lowerBound, offsetBy: -200, limitedBy: html.startIndex).map { $0..<urlRange.lowerBound } ?? urlRange) != nil

                let announcement = STASYAnnouncement(
                    id: urlString,
                    title: title,
                    date: extractDate(near: urlString, in: html),
                    summary: "",
                    url: URL(string: urlString),
                    category: isServiceAlert ? .serviceAlert : .general
                )

                if !results.contains(where: { $0.id == announcement.id }) {
                    results.append(announcement)
                }
            }
        }

        // If regex parsing didn't get good results, try a simpler approach
        // targeting the marquee/ticker text
        if results.isEmpty {
            let tickerAnnouncement = parseTickerText(from: html)
            if let ann = tickerAnnouncement {
                results.append(ann)
            }
        }

        return results
    }

    private func parseTickerText(from html: String) -> STASYAnnouncement? {
        // Look for the scrolling ticker content
        let tickerPatterns = [
            "marquee[^>]*>([^<]+)<",
            "ticker[^>]*>([^<]+)<",
            "class=\"[^\"]*announcement[^\"]*\"[^>]*>([^<]+)<",
            "class=\"[^\"]*alert[^\"]*\"[^>]*>([^<]+)<",
        ]

        for pattern in tickerPatterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { continue }
            let range = NSRange(html.startIndex..., in: html)
            if let match = regex.firstMatch(in: html, options: [], range: range),
               let textRange = Range(match.range(at: 1), in: html) {
                let text = String(html[textRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                if text.count > 20 {
                    return STASYAnnouncement(
                        id: "ticker-\(text.hashValue)",
                        title: text,
                        date: "",
                        summary: "",
                        url: URL(string: "https://www.stasy.gr"),
                        category: .serviceAlert
                    )
                }
            }
        }
        return nil
    }

    private func extractDate(near urlString: String, in html: String) -> String {
        guard let urlRange = html.range(of: urlString) else { return "" }
        let searchStart = html.index(urlRange.lowerBound, offsetBy: -300, limitedBy: html.startIndex) ?? html.startIndex
        let searchEnd = html.index(urlRange.upperBound, offsetBy: 300, limitedBy: html.endIndex) ?? html.endIndex
        let context = String(html[searchStart..<searchEnd])

        // Match Greek dates like "16 Μαΐου, 2026" or "16 Μαΐου 2026"
        let datePattern = "(\\d{1,2}\\s+(?:Ιανουαρίου|Φεβρουαρίου|Μαρτίου|Απριλίου|Μαΐου|Ιουνίου|Ιουλίου|Αυγούστου|Σεπτεμβρίου|Οκτωβρίου|Νοεμβρίου|Δεκεμβρίου),?\\s*\\d{4})"
        guard let dateRegex = try? NSRegularExpression(pattern: datePattern),
              let dateMatch = dateRegex.firstMatch(in: context, range: NSRange(context.startIndex..., in: context)),
              let dateRange = Range(dateMatch.range(at: 1), in: context) else { return "" }

        return String(context[dateRange])
    }

    // MARK: - Cache

    private func cacheAnnouncements(_ announcements: [STASYAnnouncement]) {
        let dicts = announcements.map { ann -> [String: String] in
            [
                "id": ann.id,
                "title": ann.title,
                "date": ann.date,
                "summary": ann.summary,
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
                date: dict["date"] ?? "",
                summary: dict["summary"] ?? "",
                url: URL(string: dict["url"] ?? ""),
                category: AnnouncementCategory(rawValue: dict["category"] ?? "") ?? .other
            )
        }
        if let cached = UserDefaults.standard.object(forKey: cacheTimeKey) as? TimeInterval {
            lastUpdated = Date(timeIntervalSince1970: cached)
        }
    }
}
