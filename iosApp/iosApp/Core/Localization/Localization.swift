import Foundation

enum AppLanguage: String, CaseIterable {
    case english = "en"
    case greek = "el"
    case albanian = "sq"
    case italian = "it"

    var displayName: String {
        switch self {
        case .english: return "English"
        case .greek: return "Ελληνικά"
        case .albanian: return "Shqip"
        case .italian: return "Italiano"
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
            } else if primary.hasPrefix("it") {
                self.language = .italian
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
        if lang == .greek { return nameEl }
        if lang == .italian {
            return name
                .replacingOccurrences(of: "Line ", with: "Linea ")
                .replacingOccurrences(of: "Suburban ", with: "Suburbano ")
        }
        if lang == .albanian {
            return name
                .replacingOccurrences(of: "Line ", with: "Linja ")
                .replacingOccurrences(of: "Suburban ", with: "Periferik ")
        }
        return name
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
    case onboardNotifTitle
    case onboardNotifBody
    case onboardNotifCta
    case onboardMapToolsTitle
    case onboardMapToolsBody
    case onboardPrivacyTitle
    case onboardPrivacyBody
    case onboardContinue
    case onboardGetStarted
    case onboardSkip
    case nextTrain
    case to
    case live
    case runningOnline
    case runningOffline
    case predictedFromSchedule
    case retry
    case sourceScheduled
    case sourceEstimated
    case sourceOffline
    case sourceOperator
    case lastTrain
    case leaveBy
    case serviceOver
    case enableLocationForNext
    case serviceAlertAffectsLine

    func text(for lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.appSubtitle, .english): return "Live Greece rail times"
        case (.appSubtitle, .greek): return "Ζωντανοί χρόνοι σιδηροδρόμων Ελλάδας"
        case (.appSubtitle, .albanian): return "Oraret e drejtpërdrejta te hekurudhave te Greqise"
        case (.appSubtitle, .italian): return "Orari ferroviari della Grecia in tempo reale"
        case (.metro, .english): return "Metro"
        case (.metro, .greek): return "Μετρό"
        case (.metro, .albanian): return "Metro"
        case (.metro, .italian): return "Metro"
        case (.tram, .english): return "Tram"
        case (.tram, .greek): return "Τραμ"
        case (.tram, .albanian): return "Tramvaj"
        case (.tram, .italian): return "Tram"
        case (.suburban, .english): return "Suburban"
        case (.suburban, .greek): return "Προαστιακός"
        case (.suburban, .albanian): return "Treni periferik"
        case (.suburban, .italian): return "Suburbano"
        case (.serviceAlerts, .english): return "Service Alerts"
        case (.serviceAlerts, .greek): return "Έκτακτες Ανακοινώσεις"
        case (.serviceAlerts, .albanian): return "Njoftime urgjente"
        case (.serviceAlerts, .italian): return "Avvisi di servizio"
        case (.latestFromSTASY, .english): return "Rail network updates"
        case (.latestFromSTASY, .greek): return "Ενημερωσεις σιδηροδρομικου δικτυου"
        case (.latestFromSTASY, .albanian): return "Perditesime te rrjetit hekurudhor"
        case (.latestFromSTASY, .italian): return "Aggiornamenti rete ferroviaria"
        case (.readMore, .english): return "Read more"
        case (.readMore, .greek): return "Διαβάστε περισσότερα"
        case (.readMore, .albanian): return "Lexo më shumë"
        case (.readMore, .italian): return "Leggi di più"
        case (.showMore, .english): return "Show more"
        case (.showMore, .greek): return "Δείτε περισσότερα"
        case (.showMore, .albanian): return "Trego më shumë"
        case (.showMore, .italian): return "Mostra di più"
        case (.showLess, .english): return "Show less"
        case (.showLess, .greek): return "Δείτε λιγότερα"
        case (.showLess, .albanian): return "Trego më pak"
        case (.showLess, .italian): return "Mostra di meno"
        case (.lines, .english): return "Lines"
        case (.lines, .greek): return "Γραμμές"
        case (.lines, .albanian): return "Linjat"
        case (.lines, .italian): return "Linee"
        case (.settings, .english): return "Settings"
        case (.settings, .greek): return "Ρυθμίσεις"
        case (.settings, .albanian): return "Cilësimet"
        case (.settings, .italian): return "Impostazioni"
        case (.home, .english): return "Home"
        case (.home, .greek): return "Αρχική"
        case (.home, .albanian): return "Kryesore"
        case (.home, .italian): return "Home"
        case (.map, .english): return "Map"
        case (.map, .greek): return "Χάρτης"
        case (.map, .albanian): return "Harta"
        case (.map, .italian): return "Mappa"
        case (.explore, .english): return "Explore"
        case (.explore, .greek): return "Εξερεύνηση"
        case (.explore, .albanian): return "Eksploro"
        case (.explore, .italian): return "Esplora"
        case (.departures, .english): return "Airport"
        case (.departures, .greek): return "Αεροδρόμιο"
        case (.departures, .albanian): return "Aeroporti"
        case (.departures, .italian): return "Aeroporto"
        case (.moreTab, .english): return "More"
        case (.moreTab, .greek): return "Περισσότερα"
        case (.moreTab, .albanian): return "Më shumë"
        case (.moreTab, .italian): return "Altro"
        case (.language, .english): return "Language"
        case (.language, .greek): return "Γλώσσα"
        case (.language, .albanian): return "Gjuha"
        case (.language, .italian): return "Lingua"
        case (.theme, .english): return "Theme"
        case (.theme, .greek): return "Θέμα"
        case (.theme, .albanian): return "Tema"
        case (.theme, .italian): return "Tema"
        case (.systemDefault, .english): return "System"
        case (.systemDefault, .greek): return "Σύστημα"
        case (.systemDefault, .albanian): return "Sistemi"
        case (.systemDefault, .italian): return "Sistema"
        case (.preferences, .english): return "Preferences"
        case (.preferences, .greek): return "Προτιμήσεις"
        case (.preferences, .albanian): return "Preferencat"
        case (.preferences, .italian): return "Preferenze"
        case (.data, .english): return "Data"
        case (.data, .greek): return "Δεδομένα"
        case (.data, .albanian): return "Të dhënat"
        case (.data, .italian): return "Dati"
        case (.scheduleVersion, .english): return "Schedule version"
        case (.scheduleVersion, .greek): return "Έκδοση δρομολογίων"
        case (.scheduleVersion, .albanian): return "Versioni i orarit"
        case (.scheduleVersion, .italian): return "Versione orario"
        case (.stations, .english): return "Stations"
        case (.stations, .greek): return "Σταθμοί"
        case (.stations, .albanian): return "Stacionet"
        case (.stations, .italian): return "Stazioni"
        case (.about, .english): return "About"
        case (.about, .greek): return "Σχετικά"
        case (.about, .albanian): return "Rreth"
        case (.about, .italian): return "Informazioni"
        case (.aboutText, .english):
            return "Transport information may include public or official data from STASY, OASA, OSETH, OASTH, Thessaloniki Metro, OSE and Hellenic Train, covering metro, tram, InterCity, regional and suburban services, including Athens, Thessaloniki and Patras Suburban Railway. Syrmos is independent and is not affiliated with, endorsed by or operated by any of these authorities or operators."
        case (.aboutText, .greek):
            return "Οι πληροφορίες μεταφορών μπορεί να περιλαμβάνουν δημόσια ή επίσημα δεδομένα από ΣΤΑΣΥ, ΟΑΣΑ, ΟΣΕΘ, ΟΑΣΘ, Μετρό Θεσσαλονίκης, ΟΣΕ και Hellenic Train, για μετρό, τραμ, InterCity, περιφερειακές και προαστιακές υπηρεσίες, συμπεριλαμβανομένων των Προαστιακών Αθήνας, Θεσσαλονίκης και Πάτρας. Το Syrmos είναι ανεξάρτητο και δεν συνδέεται, δεν υποστηρίζεται και δεν λειτουργεί από κανέναν από αυτούς τους φορείς ή παρόχους."
        case (.aboutText, .albanian):
            return "Informacioni i transportit mund të përfshijë të dhëna publike ose zyrtare nga STASY, OASA, OSETH, OASTH, Thessaloniki Metro, OSE dhe Hellenic Train, për shërbimet e metrosë, tramvajit, InterCity, rajonale dhe periferike, përfshirë hekurudhat periferike të Athinës, Selanikut dhe Patrës. Syrmos është i pavarur dhe nuk është i lidhur, miratuar apo operuar nga asnjë prej këtyre autoriteteve ose operatorëve."
        case (.aboutText, .italian):
            return "Le informazioni sui trasporti possono includere dati pubblici o ufficiali di STASY, OASA, OSETH, OASTH, Metropolitana di Salonicco, OSE e Hellenic Train, relativi a metro, tram, InterCity, servizi regionali e suburbani, comprese le ferrovie suburbane di Atene, Salonicco e Patrasso. Syrmos è indipendente e non è affiliata, approvata o gestita da nessuna di queste autorità o operatori."
        case (.couldNotReach, .english): return "Could not reach stasy.gr"
        case (.couldNotReach, .greek): return "Δεν ήταν δυνατή η σύνδεση με stasy.gr"
        case (.couldNotReach, .albanian): return "Nuk arritëm të lidhemi me stasy.gr"
        case (.couldNotReach, .italian): return "Impossibile raggiungere stasy.gr"
        case (.onboardWelcomeTitle, .english): return "Welcome to Syrmos"
        case (.onboardWelcomeTitle, .greek): return "Καλώς ήρθες στο Syrmos"
        case (.onboardWelcomeTitle, .albanian): return "Mirëserdhe në Syrmos"
        case (.onboardWelcomeTitle, .italian): return "Benvenuto su Syrmos"
        case (.onboardWelcomeBody, .english): return "Live arrivals, GPS tracking, train telemetry and onboard livestream for the entire Greek rail network."
        case (.onboardWelcomeBody, .greek): return "Ζωντανες αφιξεις, GPS, τηλεμετρια τρενων και ζωντανη μεταδοση απο ολο το ελληνικο σιδηροδρομικο δικτυο."
        case (.onboardWelcomeBody, .albanian): return "Mberritje live, GPS, telemetri trenash dhe transmetim live nga i gjithe rrjeti hekurudhor grek."
        case (.onboardWelcomeBody, .italian): return "Arrivi in tempo reale, GPS, telemetria treni e streaming live dall'intera rete ferroviaria greca."
        case (.onboardLiveTitle, .english): return "Trains in real time"
        case (.onboardLiveTitle, .greek): return "Συρμοί σε πραγματικό χρόνο"
        case (.onboardLiveTitle, .albanian): return "Trena në kohë reale"
        case (.onboardLiveTitle, .italian): return "Treni in tempo reale"
        case (.onboardLiveBody, .english): return "See departures, track trains on the map with speed and signal data, and watch onboard cameras live."
        case (.onboardLiveBody, .greek): return "Δες αναχωρησεις, παρακολουθησε τρενα στο χαρτη με ταχυτητα και σημα, και δες ζωντανη εικονα απο καμερες."
        case (.onboardLiveBody, .albanian): return "Shih nisjet, ndiq trenat ne harte me shpejtesi dhe sinjal, dhe shiko kamerat live ne bord."
        case (.onboardLiveBody, .italian): return "Vedi le partenze, traccia i treni sulla mappa con velocità e segnale, e guarda le telecamere a bordo in diretta."
        case (.onboardLocationTitle, .english): return "Closest to you"
        case (.onboardLocationTitle, .greek): return "Πιο κοντά σε σένα"
        case (.onboardLocationTitle, .albanian): return "Më pranë teje"
        case (.onboardLocationTitle, .italian): return "Più vicino a te"
        case (.onboardLocationBody, .english): return "Allow location so we can show the nearest stations and arrivals first. Used only on device."
        case (.onboardLocationBody, .greek): return "Επίτρεψε την τοποθεσία για να βλέπεις τους πιο κοντινούς σταθμούς. Χρησιμοποιείται μόνο στη συσκευή."
        case (.onboardLocationBody, .albanian): return "Lejo vendndodhjen që të shohësh stacionet më të afërta dhe mbërritjet të parat. Përdoret vetëm në pajisje."
        case (.onboardLocationBody, .italian): return "Consenti la posizione per vedere le stazioni e gli arrivi più vicini. Usata solo sul dispositivo."
        case (.onboardLocationCta, .english): return "Allow location"
        case (.onboardLocationCta, .greek): return "Επίτρεψε την τοποθεσία"
        case (.onboardLocationCta, .albanian): return "Lejo vendndodhjen"
        case (.onboardLocationCta, .italian): return "Consenti posizione"
        case (.onboardNotifTitle, .english): return "Stay informed"
        case (.onboardNotifTitle, .greek): return "Μεινε ενημερος"
        case (.onboardNotifTitle, .albanian): return "Qendro i informuar"
        case (.onboardNotifTitle, .italian): return "Resta informato"
        case (.onboardNotifBody, .english): return "Get alerts for service disruptions near you, weather warnings that may affect your commute, and a morning briefing with the latest updates."
        case (.onboardNotifBody, .greek): return "Λαβε ειδοποιησεις για διακοπες υπηρεσιων κοντα σου, καιρικες προειδοποιησεις που μπορει να επηρεασουν τη μετακινηση σου, και πρωινη ενημερωση με τα τελευταια νεα."
        case (.onboardNotifBody, .albanian): return "Merr njoftime per nderprerje sherbimesh prane teje, paralajmerime moti qe mund te ndikojne udhetimin tend, dhe informim mengjesit me perditesimet me te fundit."
        case (.onboardNotifBody, .italian): return "Ricevi avvisi per interruzioni di servizio vicino a te, avvisi meteo che possono influire sul tuo tragitto e un briefing mattutino."
        case (.onboardNotifCta, .english): return "Allow notifications"
        case (.onboardNotifCta, .greek): return "Επιτρεψε τις ειδοποιησεις"
        case (.onboardNotifCta, .albanian): return "Lejo njoftimet"
        case (.onboardNotifCta, .italian): return "Consenti notifiche"
        case (.onboardMapToolsTitle, .english): return "Your Map Tools"
        case (.onboardMapToolsTitle, .greek): return "Τα εργαλεια του χαρτη"
        case (.onboardMapToolsTitle, .albanian): return "Mjetet e hartes"
        case (.onboardMapToolsTitle, .italian): return "I tuoi strumenti mappa"
        case (.onboardMapToolsBody, .english): return "Tap the purple owl to ask Ariadne, the train icon to browse live trains, and the blue arrow to find yourself on the map."
        case (.onboardMapToolsBody, .greek): return "Πατησε την μωβ κουκουβαγια για την Αριαδνη, το τρενο για ζωντανα δρομολογια, και το μπλε βελος για την τοποθεσια σου."
        case (.onboardMapToolsBody, .albanian): return "Shtyp bufen vjollce per Ariadnen, ikonen e trenit per trenat live, dhe shigjeten blu per vendndodhjen tende."
        case (.onboardMapToolsBody, .italian): return "Tocca il gufo viola per chiedere ad Ariadne, l'icona del treno per i treni in tempo reale e la freccia blu per trovarti sulla mappa."
        case (.onboardPrivacyTitle, .english): return "No accounts. No tracking."
        case (.onboardPrivacyTitle, .greek): return "Χωρίς λογαριασμό. Χωρίς παρακολούθηση."
        case (.onboardPrivacyTitle, .albanian): return "Pa llogari. Pa gjurmim."
        case (.onboardPrivacyTitle, .italian): return "Nessun account. Nessun tracciamento."
        case (.onboardPrivacyBody, .english): return "Syrmos doesn't ask you to sign in and doesn't store personal data. Just trains."
        case (.onboardPrivacyBody, .greek): return "Το Syrmos δεν ζητάει σύνδεση και δεν αποθηκεύει προσωπικά δεδομένα. Μόνο συρμούς."
        case (.onboardPrivacyBody, .albanian): return "Syrmos nuk kërkon hyrje dhe nuk ruan të dhëna personale. Vetëm trena."
        case (.onboardPrivacyBody, .italian): return "Syrmos non richiede l'accesso e non memorizza dati personali. Solo treni."
        case (.onboardContinue, .english): return "Continue"
        case (.onboardContinue, .greek): return "Συνέχεια"
        case (.onboardContinue, .albanian): return "Vazhdo"
        case (.onboardContinue, .italian): return "Continua"
        case (.onboardGetStarted, .english): return "Get started"
        case (.onboardGetStarted, .greek): return "Ξεκίνα"
        case (.onboardGetStarted, .albanian): return "Fillo"
        case (.onboardGetStarted, .italian): return "Inizia"
        case (.onboardSkip, .english): return "Skip"
        case (.onboardSkip, .greek): return "Παράλειψη"
        case (.onboardSkip, .albanian): return "Anashkalo"
        case (.onboardSkip, .italian): return "Salta"
        case (.nextTrain, .english): return "Next train"
        case (.nextTrain, .greek): return "Επόμενος συρμός"
        case (.nextTrain, .albanian): return "Treni i ardhshëm"
        case (.nextTrain, .italian): return "Prossimo treno"
        case (.to, .english): return "to"
        case (.to, .greek): return "προς"
        case (.to, .albanian): return "drejt"
        case (.to, .italian): return "verso"
        case (.live, .english): return "Live"
        case (.live, .greek): return "Ζωντανά"
        case (.live, .albanian): return "Drejtpërdrejt"
        case (.live, .italian): return "Live"
        case (.runningOnline, .english): return "Running online"
        case (.runningOnline, .greek): return "Σε σύνδεση"
        case (.runningOnline, .albanian): return "Me internet"
        case (.runningOnline, .italian): return "Online"
        case (.runningOffline, .english): return "Running offline"
        case (.runningOffline, .greek): return "Εκτός σύνδεσης"
        case (.runningOffline, .albanian): return "Pa internet"
        case (.runningOffline, .italian): return "Offline"
        case (.retry, .english): return "Retry"
        case (.retry, .greek): return "Δοκιμή"
        case (.retry, .albanian): return "Riprovo"
        case (.retry, .italian): return "Riprova"
        case (.predictedFromSchedule, .english): return "Predicted from schedule"
        case (.predictedFromSchedule, .greek): return "Πρόβλεψη από το πρόγραμμα"
        case (.predictedFromSchedule, .albanian): return "Parashikuar nga orari"
        case (.predictedFromSchedule, .italian): return "Previsto dall'orario"
        case (.sourceScheduled, .english): return "Scheduled"
        case (.sourceScheduled, .greek): return "Πρόγραμμα"
        case (.sourceScheduled, .albanian): return "Orar"
        case (.sourceScheduled, .italian): return "Programmato"
        case (.sourceEstimated, .english): return "Estimated"
        case (.sourceEstimated, .greek): return "Εκτίμηση"
        case (.sourceEstimated, .albanian): return "Vlerësim"
        case (.sourceEstimated, .italian): return "Stimato"
        case (.sourceOffline, .english): return "Offline snapshot"
        case (.sourceOffline, .greek): return "Εκτός σύνδεσης"
        case (.sourceOffline, .albanian): return "Pa internet"
        case (.sourceOffline, .italian): return "Snapshot offline"
        case (.sourceOperator, .english): return "Check operator"
        case (.sourceOperator, .greek): return "Δείτε πάροχο"
        case (.sourceOperator, .albanian): return "Kontrolloni operatorin"
        case (.sourceOperator, .italian): return "Controlla operatore"
        case (.lastTrain, .english): return "Last train"
        case (.lastTrain, .greek): return "Τελευταίος συρμός"
        case (.lastTrain, .albanian): return "Treni i fundit"
        case (.lastTrain, .italian): return "Ultimo treno"
        case (.leaveBy, .english): return "leave by"
        case (.leaveBy, .greek): return "φύγε έως"
        case (.leaveBy, .albanian): return "nisu deri në"
        case (.leaveBy, .italian): return "parti entro"
        case (.serviceOver, .english): return "No more trains tonight"
        case (.serviceOver, .greek): return "Δεν υπάρχουν άλλα δρομολόγια απόψε"
        case (.serviceOver, .albanian): return "Nuk ka më trena sonte"
        case (.serviceOver, .italian): return "Nessun altro treno stasera"
        case (.enableLocationForNext, .english): return "Enable location to see your next train"
        case (.enableLocationForNext, .greek): return "Ενεργοποίησε την τοποθεσία για τον επόμενο συρμό σου"
        case (.enableLocationForNext, .albanian): return "Aktivizo vendndodhjen për trenin tënd të ardhshëm"
        case (.enableLocationForNext, .italian): return "Attiva la posizione per vedere il prossimo treno"
        case (.serviceAlertAffectsLine, .english): return "Active alerts may affect departures"
        case (.serviceAlertAffectsLine, .greek): return "Ενεργές ειδοποιήσεις μπορεί να επηρεάζουν τα δρομολόγια"
        case (.serviceAlertAffectsLine, .albanian): return "Njoftimet aktive mund te ndikojne nisjet"
        case (.serviceAlertAffectsLine, .italian): return "Avvisi attivi possono influire sulle partenze"
        default: return text(for: .english)
        }
    }
}
