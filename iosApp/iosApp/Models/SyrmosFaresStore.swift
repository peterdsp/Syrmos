import Foundation

/// Fetches and caches `/api/fares` — the structured OASA ticket catalogue
/// scraped from oasa.gr/en/tickets/prices-of-products/ and refreshed by
/// the daily watcher. Drives the native FaresView (no more punting users
/// to the OASA web page for the regular case).
@MainActor
final class SyrmosFaresStore: ObservableObject {
    static let shared = SyrmosFaresStore()

    @Published private(set) var products: [Product] = []
    @Published private(set) var infoLinks: [InfoLink] = []
    @Published private(set) var updatedAt: String = ""

    private let base = "https://api-syrmos.peterdsp.dev"
    private let session: URLSession

    private init(session: URLSession = .shared) {
        self.session = session
        hydrateFromBundleIfNeeded()
    }

    func products(in section: String) -> [Product] {
        products.filter { $0.section == section }
    }

    func refresh() async {
        guard let url = URL(string: base + "/api/fares") else { return }
        var req = URLRequest(url: url)
        req.timeoutInterval = 10
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return }
            let payload = try JSONDecoder().decode(Payload.self, from: data)
            self.products = payload.products
            self.infoLinks = payload.infoLinks ?? []
            self.updatedAt = payload.updatedAt
        } catch {
            // Silent: keep whatever the bundle hydrated.
        }
    }

    private func hydrateFromBundleIfNeeded() {
        guard products.isEmpty else { return }
        guard let url = Bundle.main.url(
            forResource: "fares",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) else { return }
        guard let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data)
        else { return }
        self.products = payload.products
        self.infoLinks = payload.infoLinks ?? []
        self.updatedAt = payload.updatedAt
    }

    struct Payload: Decodable {
        let updatedAt: String
        let products: [Product]
        let infoLinks: [InfoLink]?
    }

    struct InfoLink: Decodable, Identifiable {
        let id: String
        let operator_: String
        let icon: String
        let titleEn: String
        let titleEl: String
        let urlEn: String
        let urlEl: String

        enum CodingKeys: String, CodingKey {
            case id, icon, titleEn, titleEl, urlEn, urlEl
            case operator_ = "operator"
        }
    }

    struct Product: Decodable, Identifiable {
        let section: String
        let titleEn: String
        let titleEl: String
        let fullPriceEur: Double?
        let discountedPriceEur: Double?
        let validity: String
        let notes: String
        let tags: [String]
        let sourceUrl: String

        var id: String { "\(section)-\(titleEn)" }
    }
}
