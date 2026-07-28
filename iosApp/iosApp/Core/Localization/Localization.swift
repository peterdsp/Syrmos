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
            // Use the device's PRIMARY language only. The previous logic
            // walked the whole preferredLanguages list and picked Greek
            // (or Albanian) if it appeared anywhere, which meant a user
            // with Shqip as their primary language still got Greek as
            // long as Greek sat in slot 2 or 3 — and vice versa, a Greek
            // primary user with Shqip added during translation testing
            // could land on Shqip if Greek had been removed. The product
            // wants device-primary-or-English, full stop.
            let primary = Locale.preferredLanguages.first ?? "en"
            if primary.hasPrefix("el") {
                self.language = .greek
            } else if primary.hasPrefix("sq") {
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
    case explore
    case departures
    case moreTab
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
    case nextTrain
    case to
    case live
    case runningOffline
    case predictedFromSchedule
    case sourceScheduled
    case sourceEstimated
    case sourceOffline
    case lastTrain
    case leaveBy
    case serviceOver
    case enableLocationForNext

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
        case (.explore, .english): return "Explore"
        case (.explore, .greek): return "Εξερεύνηση"
        case (.explore, .albanian): return "Eksploro"
        case (.departures, .english): return "Departures"
        case (.departures, .greek): return "Αναχωρήσεις"
        case (.departures, .albanian): return "Nisjet"
        case (.moreTab, .english): return "More"
        case (.moreTab, .greek): return "Περισσότερα"
        case (.moreTab, .albanian): return "Më shumë"
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
        case (.nextTrain, .english): return "Next train"
        case (.nextTrain, .greek): return "Επόμενος συρμός"
        case (.nextTrain, .albanian): return "Treni i ardhshëm"
        case (.to, .english): return "to"
        case (.to, .greek): return "προς"
        case (.to, .albanian): return "drejt"
        case (.live, .english): return "Live"
        case (.live, .greek): return "Ζωντανά"
        case (.live, .albanian): return "Drejtpërdrejt"
        case (.runningOffline, .english): return "Running offline"
        case (.runningOffline, .greek): return "Εκτός σύνδεσης"
        case (.runningOffline, .albanian): return "Pa internet"
        case (.predictedFromSchedule, .english): return "Predicted from schedule"
        case (.predictedFromSchedule, .greek): return "Πρόβλεψη από το πρόγραμμα"
        case (.predictedFromSchedule, .albanian): return "Parashikuar nga orari"
        case (.sourceScheduled, .english): return "Scheduled"
        case (.sourceScheduled, .greek): return "Πρόγραμμα"
        case (.sourceScheduled, .albanian): return "Orar"
        case (.sourceEstimated, .english): return "Estimated"
        case (.sourceEstimated, .greek): return "Εκτίμηση"
        case (.sourceEstimated, .albanian): return "Vlerësim"
        case (.sourceOffline, .english): return "Offline snapshot"
        case (.sourceOffline, .greek): return "Εκτός σύνδεσης"
        case (.sourceOffline, .albanian): return "Pa internet"
        case (.lastTrain, .english): return "Last train"
        case (.lastTrain, .greek): return "Τελευταίος συρμός"
        case (.lastTrain, .albanian): return "Treni i fundit"
        case (.leaveBy, .english): return "leave by"
        case (.leaveBy, .greek): return "φύγε έως"
        case (.leaveBy, .albanian): return "nisu deri në"
        case (.serviceOver, .english): return "No more trains tonight"
        case (.serviceOver, .greek): return "Δεν υπάρχουν άλλα δρομολόγια απόψε"
        case (.serviceOver, .albanian): return "Nuk ka më trena sonte"
        case (.enableLocationForNext, .english): return "Enable location to see your next train"
        case (.enableLocationForNext, .greek): return "Ενεργοποίησε την τοποθεσία για τον επόμενο συρμό σου"
        case (.enableLocationForNext, .albanian): return "Aktivizo vendndodhjen për trenin tënd të ardhshëm"
        }
    }
}
