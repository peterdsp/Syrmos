import SwiftUI

enum HomeSection: String, CaseIterable, Codable, Identifiable {
    case nextTrain
    case serviceAlerts
    case railNews
    case networkOverview
    case nearMe
    case liveTrains

    var id: String { rawValue }

    func displayName(language: AppLanguage) -> String {
        switch self {
        case .nextTrain:
            switch language {
            case .greek: return "Επομενο τρενο"
            case .albanian: return "Treni i radhes"
            case .italian: return "Prossimo treno"
            case .english: return "Next train"
            }
        case .serviceAlerts:
            switch language {
            case .greek: return "Ειδοποιησεις υπηρεσιας"
            case .albanian: return "Njoftimet e sherbimit"
            case .italian: return "Avvisi di servizio"
            case .english: return "Service alerts"
            }
        case .railNews:
            switch language {
            case .greek: return "Σιδηροδρομικα Νεα"
            case .albanian: return "Lajme Hekurudhore"
            case .italian: return "Notizie ferroviarie"
            case .english: return "Rail News"
            }
        case .networkOverview:
            switch language {
            case .greek: return "Δικτυο"
            case .albanian: return "Rrjeti"
            case .italian: return "Panoramica rete"
            case .english: return "Network overview"
            }
        case .nearMe:
            switch language {
            case .greek: return "Κοντα μου"
            case .albanian: return "Prane meje"
            case .italian: return "Vicino a me"
            case .english: return "Near me"
            }
        case .liveTrains:
            switch language {
            case .greek: return "Ζωντανα τρενα"
            case .albanian: return "Trenat aktiv"
            case .italian: return "Treni in tempo reale"
            case .english: return "Live trains"
            }
        }
    }

    var iconName: String {
        switch self {
        case .nextTrain: return "clock.fill"
        case .serviceAlerts: return "exclamationmark.triangle.fill"
        case .railNews: return "newspaper.fill"
        case .networkOverview: return "square.grid.2x2.fill"
        case .nearMe: return "location.fill"
        case .liveTrains: return "tram.fill"
        }
    }

    var iconColor: Color {
        switch self {
        case .nextTrain: return .orange
        case .serviceAlerts: return .orange
        case .railNews: return .blue
        case .networkOverview: return .purple
        case .nearMe: return .blue
        case .liveTrains: return .purple
        }
    }

    static let defaultOrder: [HomeSection] = allCases
}

struct HomeSectionEntry: Codable, Identifiable {
    let section: HomeSection
    var isVisible: Bool

    var id: String { section.id }
}

@MainActor
final class HomeSectionStore: ObservableObject {
    static let shared = HomeSectionStore()

    @Published var entries: [HomeSectionEntry] {
        didSet { save() }
    }

    private let key = "syrmos.home.sectionOrder"

    var visibleSections: [HomeSection] {
        entries.filter(\.isVisible).map(\.section)
    }

    private init() {
        if let data = UserDefaults.standard.data(forKey: key),
           let decoded = try? JSONDecoder().decode([HomeSectionEntry].self, from: data),
           !decoded.isEmpty {
            var existing = decoded
            let knownIds = Set(existing.map(\.section))
            for section in HomeSection.allCases where !knownIds.contains(section) {
                existing.append(HomeSectionEntry(section: section, isVisible: true))
            }
            self.entries = existing
        } else {
            self.entries = HomeSection.defaultOrder.map {
                HomeSectionEntry(section: $0, isVisible: true)
            }
        }
    }

    func move(from source: IndexSet, to destination: Int) {
        entries.move(fromOffsets: source, toOffset: destination)
    }

    func toggle(_ section: HomeSection) {
        guard let idx = entries.firstIndex(where: { $0.section == section }) else { return }
        entries[idx] = HomeSectionEntry(section: section, isVisible: !entries[idx].isVisible)
    }

    func reset() {
        entries = HomeSection.defaultOrder.map {
            HomeSectionEntry(section: $0, isVisible: true)
        }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(entries) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
}
