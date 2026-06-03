import Foundation

enum AppLanguage: String, CaseIterable {
    case english = "en"
    case greek = "el"

    var displayName: String {
        switch self {
        case .english: return "English"
        case .greek: return "Ελληνικά"
        }
    }
}

@MainActor
final class LocalizationManager: ObservableObject {
    static let shared = LocalizationManager()

    @Published var language: AppLanguage {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: "app_language")
        }
    }

    private init() {
        if let saved = UserDefaults.standard.string(forKey: "app_language"),
           let lang = AppLanguage(rawValue: saved) {
            self.language = lang
        } else {
            let systemLangs = Locale.preferredLanguages
            let isGreek = systemLangs.first { $0.hasPrefix("el") } != nil
            self.language = isGreek ? .greek : .english
        }
    }

    subscript(_ key: LocalizedKey) -> String {
        key.text(for: language)
    }
}

enum LocalizedKey {
    case appSubtitle
    case metro
    case tram
    case suburban
    case serviceAlerts
    case latestFromSTASY
    case readMore
    case showMore
    case showLess
    case lines
    case settings
    case home
    case map
    case language
    case theme
    case systemDefault
    case preferences
    case data
    case scheduleVersion
    case stations
    case about
    case aboutText
    case couldNotReach

    func text(for lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.appSubtitle, .english): return "Live Athens rail times"
        case (.appSubtitle, .greek): return "Ζωντανοί χρόνοι σιδηροδρόμων Αθήνας"
        case (.metro, .english): return "Metro"
        case (.metro, .greek): return "Μετρό"
        case (.tram, .english): return "Tram"
        case (.tram, .greek): return "Τραμ"
        case (.suburban, .english): return "Suburban"
        case (.suburban, .greek): return "Προαστιακός"
        case (.serviceAlerts, .english): return "Service Alerts"
        case (.serviceAlerts, .greek): return "Έκτακτες Ανακοινώσεις"
        case (.latestFromSTASY, .english): return "Latest from STASY"
        case (.latestFromSTASY, .greek): return "Τελευταία από ΣΤΑΣΥ"
        case (.readMore, .english): return "Read more"
        case (.readMore, .greek): return "Διαβάστε περισσότερα"
        case (.showMore, .english): return "Show more"
        case (.showMore, .greek): return "Δείτε περισσότερα"
        case (.showLess, .english): return "Show less"
        case (.showLess, .greek): return "Δείτε λιγότερα"
        case (.lines, .english): return "Lines"
        case (.lines, .greek): return "Γραμμές"
        case (.settings, .english): return "Settings"
        case (.settings, .greek): return "Ρυθμίσεις"
        case (.home, .english): return "Home"
        case (.home, .greek): return "Αρχική"
        case (.map, .english): return "Map"
        case (.map, .greek): return "Χάρτης"
        case (.language, .english): return "Language"
        case (.language, .greek): return "Γλώσσα"
        case (.theme, .english): return "Theme"
        case (.theme, .greek): return "Θέμα"
        case (.systemDefault, .english): return "System"
        case (.systemDefault, .greek): return "Σύστημα"
        case (.preferences, .english): return "Preferences"
        case (.preferences, .greek): return "Προτιμήσεις"
        case (.data, .english): return "Data"
        case (.data, .greek): return "Δεδομένα"
        case (.scheduleVersion, .english): return "Schedule version"
        case (.scheduleVersion, .greek): return "Έκδοση δρομολογίων"
        case (.stations, .english): return "Stations"
        case (.stations, .greek): return "Σταθμοί"
        case (.about, .english): return "About"
        case (.about, .greek): return "Σχετικά"
        case (.aboutText, .english):
            return "Schedule data from STASY and Hellenic Train official timetables. This app is not affiliated with STASY, Hellenic Train or OASA."
        case (.aboutText, .greek):
            return "Δεδομένα δρομολογίων από τα επίσημα προγράμματα ΣΤΑΣΥ και Hellenic Train. Η εφαρμογή δεν σχετίζεται με ΣΤΑΣΥ, Hellenic Train ή ΟΑΣΑ."
        case (.couldNotReach, .english): return "Could not reach stasy.gr"
        case (.couldNotReach, .greek): return "Δεν ήταν δυνατή η σύνδεση με stasy.gr"
        }
    }
}
