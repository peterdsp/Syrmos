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
        let titleSq: String?
        let titleIt: String?
        let summaryEn: String?
        let summaryEl: String?
        let summarySq: String?
        let summaryIt: String?
        let bullets: [Bullet]?
        let urlEn: String
        let urlEl: String
        let urlSq: String?
        let urlIt: String?

        enum CodingKeys: String, CodingKey {
            case id, icon
            case titleEn, titleEl, titleSq, titleIt
            case summaryEn, summaryEl, summarySq, summaryIt
            case bullets
            case urlEn, urlEl, urlSq, urlIt
            case operator_ = "operator"
        }

        func localizedTitle(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return titleEl.isEmpty ? titleEn : titleEl
            case .albanian: return (titleSq?.isEmpty == false) ? titleSq! : titleEn
            case .italian: return (titleIt?.isEmpty == false) ? titleIt! : titleEn
            case .english: return titleEn
            }
        }

        func localizedSummary(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return summaryEl ?? summaryEn ?? ""
            case .albanian: return summarySq ?? summaryEn ?? ""
            case .italian: return (summaryIt?.isEmpty == false) ? summaryIt! : summaryEn ?? ""
            case .english: return summaryEn ?? ""
            }
        }

        func localizedUrl(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return urlEl.isEmpty ? urlEn : urlEl
            case .albanian: return (urlSq?.isEmpty == false) ? urlSq! : urlEn
            case .italian: return (urlIt?.isEmpty == false) ? urlIt! : urlEn
            case .english: return urlEn
            }
        }
    }

    struct Bullet: Decodable, Identifiable {
        let en: String
        let el: String
        let sq: String?
        let it: String?
        var id: String { en }

        func localized(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return el.isEmpty ? en : el
            case .albanian: return (sq?.isEmpty == false) ? sq! : en
            case .italian: return (it?.isEmpty == false) ? it! : en
            case .english: return en
            }
        }
    }

    struct Product: Decodable, Identifiable {
        let section: String
        let titleEn: String
        let titleEl: String
        let titleSq: String?
        let titleIt: String?
        let fullPriceEur: Double?
        let discountedPriceEur: Double?
        let validity: String
        let validitySq: String?
        let validityIt: String?
        let notes: String
        let notesEl: String?
        let notesSq: String?
        let notesIt: String?
        let tags: [String]
        let sourceUrl: String

        var id: String { "\(section)-\(titleEn)" }

        func localizedTitle(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return titleEl.isEmpty ? titleEn : titleEl
            case .albanian: return (titleSq?.isEmpty == false) ? titleSq! : titleEn
            case .italian: return (titleIt?.isEmpty == false) ? titleIt! : titleEn
            case .english: return titleEn
            }
        }

        func localizedValidity(_ lang: AppLanguage) -> String {
            switch lang {
            case .albanian: return (validitySq?.isEmpty == false) ? validitySq! : validity
            case .italian: return (validityIt?.isEmpty == false) ? validityIt! : validity
            default: return validity
            }
        }

        func localizedNotes(_ lang: AppLanguage) -> String {
            switch lang {
            case .greek: return (notesEl?.isEmpty == false) ? notesEl! : notes
            case .albanian: return (notesSq?.isEmpty == false) ? notesSq! : notes
            case .italian: return (notesIt?.isEmpty == false) ? notesIt! : notes
            default: return notes
            }
        }
    }
}
