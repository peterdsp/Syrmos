import Foundation

enum AppLanguage: String, CaseIterable {
    case english = "en"
    case greek = "el"
    case albanian = "sq"

    var displayName: String {
        switch self {
        case .english: return "English"
        case .greek: return "Ελληνικά"
        case .albanian: return "Shqip"
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
            if systemLangs.first(where: { $0.hasPrefix("el") }) != nil {
                self.language = .greek
            } else if systemLangs.first(where: { $0.hasPrefix("sq") }) != nil {
                self.language = .albanian
            } else {
                self.language = .english
            }
        }
    }

    subscript(_ key: LocalizedKey) -> String {
        key.text(for: language)
    }
}

extension TransitLine {
    func localizedName(_ lang: AppLanguage) -> String {
        lang == .greek ? nameEl : name
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
    case onboardWelcomeTitle
    case onboardWelcomeBody
    case onboardLiveTitle
    case onboardLiveBody
    case onboardLocationTitle
    case onboardLocationBody
    case onboardLocationCta
    case onboardPrivacyTitle
    case onboardPrivacyBody
    case onboardContinue
    case onboardGetStarted
    case onboardSkip

    func text(for lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.appSubtitle, .english): return "Live Athens rail times"
        case (.appSubtitle, .greek): return "Ζωντανοί χρόνοι σιδηροδρόμων Αθήνας"
        case (.appSubtitle, .albanian): return "Oraret e drejtpërdrejta të hekurudhave të Athinës"
        case (.metro, .english): return "Metro"
        case (.metro, .greek): return "Μετρό"
        case (.metro, .albanian): return "Metro"
        case (.tram, .english): return "Tram"
        case (.tram, .greek): return "Τραμ"
        case (.tram, .albanian): return "Tramvaj"
        case (.suburban, .english): return "Suburban"
        case (.suburban, .greek): return "Προαστιακός"
        case (.suburban, .albanian): return "Treni periferik"
        case (.serviceAlerts, .english): return "Service Alerts"
        case (.serviceAlerts, .greek): return "Έκτακτες Ανακοινώσεις"
        case (.serviceAlerts, .albanian): return "Njoftime urgjente"
        case (.latestFromSTASY, .english): return "Metro & Tram updates"
        case (.latestFromSTASY, .greek): return "Ενημέρωση Μετρό & Τραμ"
        case (.latestFromSTASY, .albanian): return "Përditësime Metro & Tramvaj"
        case (.readMore, .english): return "Read more"
        case (.readMore, .greek): return "Διαβάστε περισσότερα"
        case (.readMore, .albanian): return "Lexo më shumë"
        case (.showMore, .english): return "Show more"
        case (.showMore, .greek): return "Δείτε περισσότερα"
        case (.showMore, .albanian): return "Trego më shumë"
        case (.showLess, .english): return "Show less"
        case (.showLess, .greek): return "Δείτε λιγότερα"
        case (.showLess, .albanian): return "Trego më pak"
        case (.lines, .english): return "Lines"
        case (.lines, .greek): return "Γραμμές"
        case (.lines, .albanian): return "Linjat"
        case (.settings, .english): return "Settings"
        case (.settings, .greek): return "Ρυθμίσεις"
        case (.settings, .albanian): return "Cilësimet"
        case (.home, .english): return "Home"
        case (.home, .greek): return "Αρχική"
        case (.home, .albanian): return "Kryesore"
        case (.map, .english): return "Map"
        case (.map, .greek): return "Χάρτης"
        case (.map, .albanian): return "Harta"
        case (.language, .english): return "Language"
        case (.language, .greek): return "Γλώσσα"
        case (.language, .albanian): return "Gjuha"
        case (.theme, .english): return "Theme"
        case (.theme, .greek): return "Θέμα"
        case (.theme, .albanian): return "Tema"
        case (.systemDefault, .english): return "System"
        case (.systemDefault, .greek): return "Σύστημα"
        case (.systemDefault, .albanian): return "Sistemi"
        case (.preferences, .english): return "Preferences"
        case (.preferences, .greek): return "Προτιμήσεις"
        case (.preferences, .albanian): return "Preferencat"
        case (.data, .english): return "Data"
        case (.data, .greek): return "Δεδομένα"
        case (.data, .albanian): return "Të dhënat"
        case (.scheduleVersion, .english): return "Schedule version"
        case (.scheduleVersion, .greek): return "Έκδοση δρομολογίων"
        case (.scheduleVersion, .albanian): return "Versioni i orarit"
        case (.stations, .english): return "Stations"
        case (.stations, .greek): return "Σταθμοί"
        case (.stations, .albanian): return "Stacionet"
        case (.about, .english): return "About"
        case (.about, .greek): return "Σχετικά"
        case (.about, .albanian): return "Rreth"
        case (.aboutText, .english):
            return "Schedule data from STASY and Hellenic Train official timetables. This app is not affiliated with STASY, Hellenic Train or OASA."
        case (.aboutText, .greek):
            return "Δεδομένα δρομολογίων από τα επίσημα προγράμματα ΣΤΑΣΥ και Hellenic Train. Η εφαρμογή δεν σχετίζεται με ΣΤΑΣΥ, Hellenic Train ή ΟΑΣΑ."
        case (.aboutText, .albanian):
            return "Të dhënat e orareve nga oraret zyrtare STASY dhe Hellenic Train. Ky aplikacion nuk është i lidhur me STASY, Hellenic Train ose OASA."
        case (.couldNotReach, .english): return "Could not reach stasy.gr"
        case (.couldNotReach, .greek): return "Δεν ήταν δυνατή η σύνδεση με stasy.gr"
        case (.couldNotReach, .albanian): return "Nuk arritëm të lidhemi me stasy.gr"
        case (.onboardWelcomeTitle, .english): return "Welcome to Syrmos"
        case (.onboardWelcomeTitle, .greek): return "Καλώς ήρθες στο Syrmos"
        case (.onboardWelcomeTitle, .albanian): return "Mirëserdhe në Syrmos"
        case (.onboardWelcomeBody, .english): return "Live arrivals for the Athens Metro, Tram and Suburban network, in your pocket."
        case (.onboardWelcomeBody, .greek): return "Ζωντανές αφίξεις για Μετρό, Τραμ και Προαστιακό της Αθήνας, στην τσέπη σου."
        case (.onboardWelcomeBody, .albanian): return "Mbërritjet e drejtpërdrejta për Metron, Tramvajin dhe Trenin periferik të Athinës, në xhepin tënd."
        case (.onboardLiveTitle, .english): return "Trains in real time"
        case (.onboardLiveTitle, .greek): return "Συρμοί σε πραγματικό χρόνο"
        case (.onboardLiveTitle, .albanian): return "Trena në kohë reale"
        case (.onboardLiveBody, .english): return "See the next departures and where every train is on the map, refreshed from STASY and Hellenic Train."
        case (.onboardLiveBody, .greek): return "Δες τις επόμενες αναχωρήσεις και πού βρίσκεται κάθε συρμός στον χάρτη, με δεδομένα από ΣΤΑΣΥ και Hellenic Train."
        case (.onboardLiveBody, .albanian): return "Shih nisjet e ardhshme dhe ku ndodhet çdo tren në hartë, përditësuar nga STASY dhe Hellenic Train."
        case (.onboardLocationTitle, .english): return "Closest to you"
        case (.onboardLocationTitle, .greek): return "Πιο κοντά σε σένα"
        case (.onboardLocationTitle, .albanian): return "Më pranë teje"
        case (.onboardLocationBody, .english): return "Allow location so we can show the nearest stations and arrivals first. Used only on device."
        case (.onboardLocationBody, .greek): return "Επίτρεψε την τοποθεσία για να βλέπεις τους πιο κοντινούς σταθμούς. Χρησιμοποιείται μόνο στη συσκευή."
        case (.onboardLocationBody, .albanian): return "Lejo vendndodhjen që të shohësh stacionet më të afërta dhe mbërritjet të parat. Përdoret vetëm në pajisje."
        case (.onboardLocationCta, .english): return "Allow location"
        case (.onboardLocationCta, .greek): return "Επίτρεψε την τοποθεσία"
        case (.onboardLocationCta, .albanian): return "Lejo vendndodhjen"
        case (.onboardPrivacyTitle, .english): return "No accounts. No tracking."
        case (.onboardPrivacyTitle, .greek): return "Χωρίς λογαριασμό. Χωρίς παρακολούθηση."
        case (.onboardPrivacyTitle, .albanian): return "Pa llogari. Pa gjurmim."
        case (.onboardPrivacyBody, .english): return "Syrmos doesn't ask you to sign in and doesn't store personal data. Just trains."
        case (.onboardPrivacyBody, .greek): return "Το Syrmos δεν ζητάει σύνδεση και δεν αποθηκεύει προσωπικά δεδομένα. Μόνο συρμούς."
        case (.onboardPrivacyBody, .albanian): return "Syrmos nuk kërkon hyrje dhe nuk ruan të dhëna personale. Vetëm trena."
        case (.onboardContinue, .english): return "Continue"
        case (.onboardContinue, .greek): return "Συνέχεια"
        case (.onboardContinue, .albanian): return "Vazhdo"
        case (.onboardGetStarted, .english): return "Get started"
        case (.onboardGetStarted, .greek): return "Ξεκίνα"
        case (.onboardGetStarted, .albanian): return "Fillo"
        case (.onboardSkip, .english): return "Skip"
        case (.onboardSkip, .greek): return "Παράλειψη"
        case (.onboardSkip, .albanian): return "Anashkalo"
        }
    }
}
