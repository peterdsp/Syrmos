import Foundation

struct RailNewsItem: Identifiable {
    let id: String
    let title: String
    let titleEn: String
    let titleSq: String
    let titleIt: String
    let summary: String
    let summaryEn: String
    let summarySq: String
    let summaryIt: String
    let url: URL?
    let publishedAt: String
    let thumbnailUrl: String

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

    var formattedDate: String {
        guard !publishedAt.isEmpty else { return "" }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = isoFormatter.date(from: publishedAt) {
            let df = DateFormatter()
            df.dateStyle = .medium
            df.timeStyle = .none
            return df.string(from: date)
        }
        let fallback = ISO8601DateFormatter()
        fallback.formatOptions = [.withInternetDateTime]
        if let date = fallback.date(from: publishedAt) {
            let df = DateFormatter()
            df.dateStyle = .medium
            df.timeStyle = .none
            return df.string(from: date)
        }
        return String(publishedAt.prefix(10))
    }
}

@MainActor
final class RailNewsService: ObservableObject {
    @Published var news: [RailNewsItem] = []
    @Published var isLoading = false

    private let apiURL = URL(string: "https://api-syrmos.peterdsp.dev/api/news")!
    private let cacheKey = "rail_news_cache"

    init() {
        loadCached()
    }

    private struct APIPayload: Decodable {
        let updatedAt: String?
        let count: Int
        let news: [APINewsItem]
    }

    private struct APINewsItem: Decodable {
        let id: String
        let title: String
        let titleEn: String?
        let titleSq: String?
        let titleIt: String?
        let summary: String
        let summaryEn: String?
        let summarySq: String?
        let summaryIt: String?
        let url: String
        let publishedAt: String?
        let thumbnailUrl: String?
    }

    func fetchNews() async {
        if news.isEmpty { isLoading = true }
        do {
            var request = URLRequest(url: apiURL)
            request.timeoutInterval = 10
            request.cachePolicy = .reloadIgnoringLocalCacheData

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                throw URLError(.badServerResponse)
            }

            let payload = try JSONDecoder().decode(APIPayload.self, from: data)
            let parsed = payload.news.map { item in
                RailNewsItem(
                    id: item.id,
                    title: item.title,
                    titleEn: item.titleEn ?? "",
                    titleSq: item.titleSq ?? "",
                    titleIt: item.titleIt ?? "",
                    summary: item.summary,
                    summaryEn: item.summaryEn ?? "",
                    summarySq: item.summarySq ?? "",
                    summaryIt: item.summaryIt ?? "",
                    url: URL(string: item.url),
                    publishedAt: item.publishedAt ?? "",
                    thumbnailUrl: item.thumbnailUrl ?? ""
                )
            }
            news = parsed
            cache(parsed)
        } catch {
            if news.isEmpty { loadCached() }
        }
        isLoading = false
    }

    private func cache(_ items: [RailNewsItem]) {
        let dicts = items.map { item -> [String: String] in
            [
                "id": item.id,
                "title": item.title,
                "titleEn": item.titleEn,
                "titleSq": item.titleSq,
                "titleIt": item.titleIt,
                "summary": item.summary,
                "summaryEn": item.summaryEn,
                "summarySq": item.summarySq,
                "summaryIt": item.summaryIt,
                "url": item.url?.absoluteString ?? "",
                "publishedAt": item.publishedAt,
                "thumbnailUrl": item.thumbnailUrl,
            ]
        }
        UserDefaults.standard.set(dicts, forKey: cacheKey)
    }

    private func loadCached() {
        guard let dicts = UserDefaults.standard.array(forKey: cacheKey) as? [[String: String]] else { return }
        news = dicts.compactMap { d in
            guard let id = d["id"], let title = d["title"] else { return nil }
            return RailNewsItem(
                id: id,
                title: title,
                titleEn: d["titleEn"] ?? "",
                titleSq: d["titleSq"] ?? "",
                titleIt: d["titleIt"] ?? "",
                summary: d["summary"] ?? "",
                summaryEn: d["summaryEn"] ?? "",
                summarySq: d["summarySq"] ?? "",
                summaryIt: d["summaryIt"] ?? "",
                url: URL(string: d["url"] ?? ""),
                publishedAt: d["publishedAt"] ?? "",
                thumbnailUrl: d["thumbnailUrl"] ?? ""
            )
        }
    }
}
