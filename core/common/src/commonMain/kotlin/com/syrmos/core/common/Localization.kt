package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    GREEK("el", "Ελληνικά"),
    ALBANIAN("sq", "Shqip"),
    ITALIAN("it", "Italiano"),
}

expect fun detectSystemLanguage(): AppLanguage
expect fun persistLanguage(lang: AppLanguage)
expect fun loadPersistedLanguage(): AppLanguage?

object LocalizationManager {
    private val _language = MutableStateFlow(loadPersistedLanguage() ?: detectSystemLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        persistLanguage(lang)
    }

    operator fun get(key: L): String = key.text(language.value)
}

enum class L {
    APP_SUBTITLE,
    METRO, TRAM, SUBURBAN,
    SERVICE_ALERTS, SERVICE_ALERT_AFFECTS_LINE, LATEST_FROM_STASY,
    READ_MORE, SHOW_MORE, SHOW_LESS,
    LINES, SETTINGS, HOME, MAP, EXPLORE, DEPARTURES, MORE_TAB,
    LANGUAGE, THEME, SYSTEM_DEFAULT,
    PREFERENCES, DATA,
    SCHEDULE_VERSION, STATIONS,
    ABOUT, ABOUT_TEXT,
    COULD_NOT_REACH,
    LIVE_TRACKER, ACTIVE_TRAINS, NO_LIVE_TRAINS,
    NEXT_STOP, UPDATED, SPEED, ON_TIME, DELAYED,
    ONBOARD_WELCOME_TITLE, ONBOARD_WELCOME_BODY,
    ONBOARD_LIVE_TITLE, ONBOARD_LIVE_BODY,
    ONBOARD_LOCATION_TITLE, ONBOARD_LOCATION_BODY, ONBOARD_LOCATION_CTA,
    ONBOARD_MAP_TOOLS_TITLE, ONBOARD_MAP_TOOLS_BODY,
    ONBOARD_NOTIF_TITLE, ONBOARD_NOTIF_BODY, ONBOARD_NOTIF_CTA,
    ONBOARD_PRIVACY_TITLE, ONBOARD_PRIVACY_BODY,
    ONBOARD_CONTINUE, ONBOARD_GET_STARTED, ONBOARD_SKIP,
    NEXT_TRAIN, TO, LIVE, RUNNING_OFFLINE, PREDICTED_FROM_SCHEDULE,
    SOURCE_SCHEDULED, SOURCE_ESTIMATED, SOURCE_OFFLINE, SOURCE_OPERATOR,
    LAST_TRAIN, LEAVE_BY, SERVICE_OVER, ENABLE_LOCATION_FOR_NEXT,
    DESKTOP_PLANNER, DESKTOP_SCHEDULES, DESKTOP_PASSES, DESKTOP_ACCOUNT,
    DESKTOP_SUBTITLE, NETWORK_STATUS, NETWORK_STATUS_BODY, DESKTOP_HEADER,
    TRIP_PLANNING, SEARCH_STATION, STATIONS_LOWER, LINES_HERE, ACCESSIBLE_STOPS,
    SELECT_STATION_HINT, TRANSFER_STATION, DIRECT_STATION, MERGED_RECORDS,
    LIVE_TRAINS, SUBURBAN_RAILWAY, NEAR, NEXT_SHORT, NO_LIVE_TRAINS_NOW,
    NEARBY_POPULAR, POPULAR_INTERCHANGE, POPULAR_STOP, LINES_LOWER,
    ROUTE_COMPARISON, FASTEST, FEWEST_TRANSFERS, BEST_COVERAGE, ONE_TRANSFER,
    SCHEDULE_BOARD, EXPORT, PRINT_SCHEDULE, DOWNLOAD_PDF,
    NOW, THEN,
    DESTINATIONS, YOUR_NETWORK, BROWSE_ALL_STATIONS,
    DEST_AIRPORT, DEST_AIRPORT_HOOK,
    DEST_PIRAEUS, DEST_PIRAEUS_HOOK,
    DEST_MONASTIRAKI, DEST_MONASTIRAKI_HOOK,
    DEST_KIFISIA, DEST_KIFISIA_HOOK,
    DEST_THESSALONIKI, DEST_THESSALONIKI_HOOK,
    DEST_METEORA, DEST_METEORA_HOOK,
    DEST_PATRAS, DEST_PATRAS_HOOK,
    DEST_DIAKOPTO, DEST_DIAKOPTO_HOOK;

    fun text(lang: AppLanguage): String = when (this) {
        APP_SUBTITLE -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανοί χρόνοι σιδηροδρόμων Αθήνας"
            AppLanguage.ALBANIAN -> "Oraret e drejtpërdrejta të hekurudhave të Athinës"
            AppLanguage.ITALIAN -> "Orari ferroviari di Atene in tempo reale"
            else -> "Live Athens rail times"
        }
        METRO -> when (lang) {
            AppLanguage.GREEK -> "Μετρό"
            AppLanguage.ALBANIAN -> "Metro"
            AppLanguage.ITALIAN -> "Metro"
            else -> "Metro"
        }
        TRAM -> when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            AppLanguage.ITALIAN -> "Tram"
            else -> "Tram"
        }
        SUBURBAN -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακός"
            AppLanguage.ALBANIAN -> "Treni periferik"
            AppLanguage.ITALIAN -> "Suburbano"
            else -> "Suburban"
        }
        SERVICE_ALERTS -> when (lang) {
            AppLanguage.GREEK -> "Έκτακτες Ανακοινώσεις"
            AppLanguage.ALBANIAN -> "Njoftime urgjente"
            AppLanguage.ITALIAN -> "Avvisi di servizio"
            else -> "Service Alerts"
        }
        SERVICE_ALERT_AFFECTS_LINE -> when (lang) {
            AppLanguage.GREEK -> "Ειδοποίηση που επηρεάζει αυτή τη γραμμή"
            AppLanguage.ALBANIAN -> "Njoftim që prek këtë linjë"
            AppLanguage.ITALIAN -> "Avviso che interessa questa linea"
            else -> "Alert affecting this line"
        }
        LATEST_FROM_STASY -> when (lang) {
            AppLanguage.GREEK -> "Ενημέρωση Μετρό & Τραμ"
            AppLanguage.ALBANIAN -> "Përditësime Metro & Tramvaj"
            AppLanguage.ITALIAN -> "Aggiornamenti Metro e Tram"
            else -> "Metro & Tram updates"
        }
        READ_MORE -> when (lang) {
            AppLanguage.GREEK -> "Διαβάστε περισσότερα"
            AppLanguage.ALBANIAN -> "Lexo më shumë"
            AppLanguage.ITALIAN -> "Leggi di più"
            else -> "Read more"
        }
        SHOW_MORE -> when (lang) {
            AppLanguage.GREEK -> "Δείτε περισσότερα"
            AppLanguage.ALBANIAN -> "Trego më shumë"
            AppLanguage.ITALIAN -> "Mostra di più"
            else -> "Show more"
        }
        SHOW_LESS -> when (lang) {
            AppLanguage.GREEK -> "Δείτε λιγότερα"
            AppLanguage.ALBANIAN -> "Trego më pak"
            AppLanguage.ITALIAN -> "Mostra di meno"
            else -> "Show less"
        }
        LINES -> when (lang) {
            AppLanguage.GREEK -> "Γραμμές"
            AppLanguage.ALBANIAN -> "Linjat"
            AppLanguage.ITALIAN -> "Linee"
            else -> "Lines"
        }
        SETTINGS -> when (lang) {
            AppLanguage.GREEK -> "Ρυθμίσεις"
            AppLanguage.ALBANIAN -> "Cilësimet"
            AppLanguage.ITALIAN -> "Impostazioni"
            else -> "Settings"
        }
        HOME -> when (lang) {
            AppLanguage.GREEK -> "Αρχική"
            AppLanguage.ALBANIAN -> "Kryesore"
            AppLanguage.ITALIAN -> "Home"
            else -> "Home"
        }
        MAP -> when (lang) {
            AppLanguage.GREEK -> "Χάρτης"
            AppLanguage.ALBANIAN -> "Harta"
            AppLanguage.ITALIAN -> "Mappa"
            else -> "Map"
        }
        EXPLORE -> when (lang) {
            AppLanguage.GREEK -> "Εξερεύνηση"
            AppLanguage.ALBANIAN -> "Eksploro"
            AppLanguage.ITALIAN -> "Esplora"
            else -> "Explore"
        }
        DEPARTURES -> when (lang) {
            AppLanguage.GREEK -> "Αναχωρήσεις"
            AppLanguage.ALBANIAN -> "Nisjet"
            AppLanguage.ITALIAN -> "Partenze"
            else -> "Departures"
        }
        MORE_TAB -> when (lang) {
            AppLanguage.GREEK -> "Περισσότερα"
            AppLanguage.ALBANIAN -> "Më shumë"
            AppLanguage.ITALIAN -> "Altro"
            else -> "More"
        }
        LANGUAGE -> when (lang) {
            AppLanguage.GREEK -> "Γλώσσα"
            AppLanguage.ALBANIAN -> "Gjuha"
            AppLanguage.ITALIAN -> "Lingua"
            else -> "Language"
        }
        THEME -> when (lang) {
            AppLanguage.GREEK -> "Θέμα"
            AppLanguage.ALBANIAN -> "Tema"
            AppLanguage.ITALIAN -> "Tema"
            else -> "Theme"
        }
        SYSTEM_DEFAULT -> when (lang) {
            AppLanguage.GREEK -> "Σύστημα"
            AppLanguage.ALBANIAN -> "Sistemi"
            AppLanguage.ITALIAN -> "Sistema"
            else -> "System"
        }
        PREFERENCES -> when (lang) {
            AppLanguage.GREEK -> "Προτιμήσεις"
            AppLanguage.ALBANIAN -> "Preferencat"
            AppLanguage.ITALIAN -> "Preferenze"
            else -> "Preferences"
        }
        DATA -> when (lang) {
            AppLanguage.GREEK -> "Δεδομένα"
            AppLanguage.ALBANIAN -> "Të dhënat"
            AppLanguage.ITALIAN -> "Dati"
            else -> "Data"
        }
        SCHEDULE_VERSION -> when (lang) {
            AppLanguage.GREEK -> "Έκδοση δρομολογίων"
            AppLanguage.ALBANIAN -> "Versioni i orarit"
            AppLanguage.ITALIAN -> "Versione orario"
            else -> "Schedule version"
        }
        STATIONS -> when (lang) {
            AppLanguage.GREEK -> "Σταθμοί"
            AppLanguage.ALBANIAN -> "Stacionet"
            AppLanguage.ITALIAN -> "Stazioni"
            else -> "Stations"
        }
        ABOUT -> when (lang) {
            AppLanguage.GREEK -> "Σχετικά"
            AppLanguage.ALBANIAN -> "Rreth"
            AppLanguage.ITALIAN -> "Informazioni"
            else -> "About"
        }
        ABOUT_TEXT -> when (lang) {
            AppLanguage.GREEK -> "Οι πληροφορίες μεταφορών μπορεί να περιλαμβάνουν δημόσια ή επίσημα δεδομένα από ΣΤΑΣΥ, ΟΑΣΑ, ΟΣΕΘ, ΟΑΣΘ, Μετρό Θεσσαλονίκης, ΟΣΕ και Hellenic Train, για μετρό, τραμ, InterCity, περιφερειακές και προαστιακές υπηρεσίες, συμπεριλαμβανομένων των Προαστιακών Αθήνας, Θεσσαλονίκης και Πάτρας. Το Syrmos είναι ανεξάρτητο και δεν συνδέεται, δεν υποστηρίζεται και δεν λειτουργεί από κανέναν από αυτούς τους φορείς ή παρόχους."
            AppLanguage.ALBANIAN -> "Informacioni i transportit mund të përfshijë të dhëna publike ose zyrtare nga STASY, OASA, OSETH, OASTH, Thessaloniki Metro, OSE dhe Hellenic Train, për shërbimet e metrosë, tramvajit, InterCity, rajonale dhe periferike, përfshirë hekurudhat periferike të Athinës, Selanikut dhe Patrës. Syrmos është i pavarur dhe nuk është i lidhur, miratuar apo operuar nga asnjë prej këtyre autoriteteve ose operatorëve."
            AppLanguage.ITALIAN -> "Le informazioni sui trasporti possono includere dati pubblici o ufficiali di STASY, OASA, OSETH, OASTH, Metropolitana di Salonicco, OSE e Hellenic Train, relativi a metro, tram, InterCity, servizi regionali e suburbani, comprese le ferrovie suburbane di Atene, Salonicco e Patrasso. Syrmos è indipendente e non è affiliata, approvata o gestita da nessuna di queste autorità o operatori."
            else -> "Transport information may include public or official data from STASY, OASA, OSETH, OASTH, Thessaloniki Metro, OSE and Hellenic Train, covering metro, tram, InterCity, regional and suburban services, including Athens, Thessaloniki and Patras Suburban Railway. Syrmos is independent and is not affiliated with, endorsed by or operated by any of these authorities or operators."
        }
        COULD_NOT_REACH -> when (lang) {
            AppLanguage.GREEK -> "Δεν ήταν δυνατή η σύνδεση με stasy.gr"
            AppLanguage.ALBANIAN -> "Nuk arritëm të lidhemi me stasy.gr"
            AppLanguage.ITALIAN -> "Impossibile raggiungere stasy.gr"
            else -> "Could not reach stasy.gr"
        }
        LIVE_TRACKER -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανός εντοπισμός"
            AppLanguage.ALBANIAN -> "Gjurmim i drejtpërdrejtë"
            AppLanguage.ITALIAN -> "Tracciamento in tempo reale"
            else -> "Live tracker"
        }
        ACTIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "ενεργά τρένα"
            AppLanguage.ALBANIAN -> "trena aktiv"
            AppLanguage.ITALIAN -> "treni attivi"
            else -> "active trains"
        }
        NO_LIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν ζωντανές θέσεις για αυτή τη γραμμή αυτή τη στιγμή"
            AppLanguage.ALBANIAN -> "Nuk ka pozicione të drejtpërdrejta për këtë linjë në këtë moment"
            AppLanguage.ITALIAN -> "Non ci sono posizioni in tempo reale per questa linea al momento"
            else -> "No live train positions are available for this line right now"
        }
        NEXT_STOP -> when (lang) {
            AppLanguage.GREEK -> "Επόμενη στάση"
            AppLanguage.ALBANIAN -> "Stacioni tjetër"
            AppLanguage.ITALIAN -> "Prossima fermata"
            else -> "Next stop"
        }
        UPDATED -> when (lang) {
            AppLanguage.GREEK -> "Ενημερώθηκε"
            AppLanguage.ALBANIAN -> "Përditësuar"
            AppLanguage.ITALIAN -> "Aggiornato"
            else -> "Updated"
        }
        SPEED -> when (lang) {
            AppLanguage.GREEK -> "Ταχύτητα"
            AppLanguage.ALBANIAN -> "Shpejtësia"
            AppLanguage.ITALIAN -> "Velocità"
            else -> "Speed"
        }
        ON_TIME -> when (lang) {
            AppLanguage.GREEK -> "Στην ώρα του"
            AppLanguage.ALBANIAN -> "Në kohë"
            AppLanguage.ITALIAN -> "In orario"
            else -> "On time"
        }
        DELAYED -> when (lang) {
            AppLanguage.GREEK -> "Καθυστέρηση"
            AppLanguage.ALBANIAN -> "Me vonesë"
            AppLanguage.ITALIAN -> "In ritardo"
            else -> "Delayed"
        }
        ONBOARD_WELCOME_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Καλώς ήρθες στο Syrmos"
            AppLanguage.ALBANIAN -> "Mirëserdhe në Syrmos"
            AppLanguage.ITALIAN -> "Benvenuto su Syrmos"
            else -> "Welcome to Syrmos"
        }
        ONBOARD_WELCOME_BODY -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανές αφίξεις για Μετρό, Τραμ και Προαστιακό της Αθήνας, στην τσέπη σου."
            AppLanguage.ALBANIAN -> "Mbërritjet e drejtpërdrejta për Metron, Tramvajin dhe Trenin periferik të Athinës, në xhepin tënd."
            AppLanguage.ITALIAN -> "Arrivi in tempo reale per Metro, Tram e Suburbano di Atene, nella tua tasca."
            else -> "Live arrivals for the Athens Metro, Tram and Suburban network, in your pocket."
        }
        ONBOARD_LIVE_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Συρμοί σε πραγματικό χρόνο"
            AppLanguage.ALBANIAN -> "Trena në kohë reale"
            AppLanguage.ITALIAN -> "Treni in tempo reale"
            else -> "Trains in real time"
        }
        ONBOARD_LIVE_BODY -> when (lang) {
            AppLanguage.GREEK -> "Δες τις επόμενες αναχωρήσεις και πού βρίσκεται κάθε συρμός στον χάρτη, με δεδομένα από ΣΤΑΣΥ και Hellenic Train."
            AppLanguage.ALBANIAN -> "Shih nisjet e ardhshme dhe ku ndodhet çdo tren në hartë, përditësuar nga STASY dhe Hellenic Train."
            AppLanguage.ITALIAN -> "Vedi le prossime partenze e dove si trova ogni treno sulla mappa, aggiornato da STASY e Hellenic Train."
            else -> "See the next departures and where every train is on the map, refreshed from STASY and Hellenic Train."
        }
        ONBOARD_LOCATION_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Πιο κοντά σε σένα"
            AppLanguage.ALBANIAN -> "Më pranë teje"
            AppLanguage.ITALIAN -> "Più vicino a te"
            else -> "Closest to you"
        }
        ONBOARD_LOCATION_BODY -> when (lang) {
            AppLanguage.GREEK -> "Επίτρεψε την τοποθεσία για να βλέπεις τους πιο κοντινούς σταθμούς. Χρησιμοποιείται μόνο στη συσκευή."
            AppLanguage.ALBANIAN -> "Lejo vendndodhjen që të shohësh stacionet më të afërta dhe mbërritjet të parat. Përdoret vetëm në pajisje."
            AppLanguage.ITALIAN -> "Consenti la posizione per vedere le stazioni e gli arrivi più vicini. Usata solo sul dispositivo."
            else -> "Allow location so we can show the nearest stations and arrivals first. Used only on device."
        }
        ONBOARD_LOCATION_CTA -> when (lang) {
            AppLanguage.GREEK -> "Επίτρεψε την τοποθεσία"
            AppLanguage.ALBANIAN -> "Lejo vendndodhjen"
            AppLanguage.ITALIAN -> "Consenti posizione"
            else -> "Allow location"
        }
        ONBOARD_MAP_TOOLS_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Ο χάρτης στα χέρια σου"
            AppLanguage.ALBANIAN -> "Harta në duart e tua"
            AppLanguage.ITALIAN -> "La mappa a portata di mano"
            else -> "The map at your fingertips"
        }
        ONBOARD_MAP_TOOLS_BODY -> when (lang) {
            AppLanguage.GREEK -> "Πάτησε οποιοδήποτε όχημα για την τρέχουσα θέση, τον επόμενο σταθμό και την πρόοδο της διαδρομής."
            AppLanguage.ALBANIAN -> "Prek çdo mjet për pozicionin aktual, stacionin e ardhshëm dhe përparimin e udhëtimit."
            AppLanguage.ITALIAN -> "Tocca qualsiasi mezzo per vedere posizione attuale, prossima stazione e avanzamento del viaggio."
            else -> "Tap any vehicle for its current position, next station, and trip progress."
        }
        ONBOARD_NOTIF_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Μεινε ενημερος"
            AppLanguage.ALBANIAN -> "Qendro i informuar"
            AppLanguage.ITALIAN -> "Resta informato"
            else -> "Stay informed"
        }
        ONBOARD_NOTIF_BODY -> when (lang) {
            AppLanguage.GREEK -> "Λαβε ειδοποιησεις για διακοπες υπηρεσιων κοντα σου, καιρικες προειδοποιησεις που μπορει να επηρεασουν τη μετακινηση σου, και πρωινη ενημερωση με τα τελευταια νεα."
            AppLanguage.ALBANIAN -> "Merr njoftime per nderprerje sherbimesh prane teje, paralajmerime moti qe mund te ndikojne udhetimin tend, dhe informim mengjesit me perditesimet me te fundit."
            AppLanguage.ITALIAN -> "Ricevi avvisi per interruzioni di servizio vicino a te, avvisi meteo che possono influire sul tuo tragitto e un briefing mattutino con gli ultimi aggiornamenti."
            else -> "Get alerts for service disruptions near you, weather warnings that may affect your commute, and a morning briefing with the latest updates."
        }
        ONBOARD_NOTIF_CTA -> when (lang) {
            AppLanguage.GREEK -> "Επιτρεψε τις ειδοποιησεις"
            AppLanguage.ALBANIAN -> "Lejo njoftimet"
            AppLanguage.ITALIAN -> "Consenti notifiche"
            else -> "Allow notifications"
        }
        ONBOARD_PRIVACY_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Χωρίς λογαριασμό. Χωρίς παρακολούθηση."
            AppLanguage.ALBANIAN -> "Pa llogari. Pa gjurmim."
            AppLanguage.ITALIAN -> "Nessun account. Nessun tracciamento."
            else -> "No accounts. No tracking."
        }
        ONBOARD_PRIVACY_BODY -> when (lang) {
            AppLanguage.GREEK -> "Το Syrmos δεν ζητάει σύνδεση και δεν αποθηκεύει προσωπικά δεδομένα. Μόνο συρμούς."
            AppLanguage.ALBANIAN -> "Syrmos nuk kërkon hyrje dhe nuk ruan të dhëna personale. Vetëm trena."
            AppLanguage.ITALIAN -> "Syrmos non richiede l'accesso e non memorizza dati personali. Solo treni."
            else -> "Syrmos doesn't ask you to sign in and doesn't store personal data. Just trains."
        }
        ONBOARD_CONTINUE -> when (lang) {
            AppLanguage.GREEK -> "Συνέχεια"
            AppLanguage.ALBANIAN -> "Vazhdo"
            AppLanguage.ITALIAN -> "Continua"
            else -> "Continue"
        }
        ONBOARD_GET_STARTED -> when (lang) {
            AppLanguage.GREEK -> "Ξεκίνα"
            AppLanguage.ALBANIAN -> "Fillo"
            AppLanguage.ITALIAN -> "Inizia"
            else -> "Get started"
        }
        ONBOARD_SKIP -> when (lang) {
            AppLanguage.GREEK -> "Παράλειψη"
            AppLanguage.ALBANIAN -> "Anashkalo"
            AppLanguage.ITALIAN -> "Salta"
            else -> "Skip"
        }
        NEXT_TRAIN -> when (lang) {
            AppLanguage.GREEK -> "Επόμενος συρμός"
            AppLanguage.ALBANIAN -> "Treni i ardhshëm"
            AppLanguage.ITALIAN -> "Prossimo treno"
            else -> "Next train"
        }
        TO -> when (lang) {
            AppLanguage.GREEK -> "προς"
            AppLanguage.ALBANIAN -> "drejt"
            AppLanguage.ITALIAN -> "verso"
            else -> "to"
        }
        LIVE -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανά"
            AppLanguage.ALBANIAN -> "Drejtpërdrejt"
            AppLanguage.ITALIAN -> "Live"
            else -> "Live"
        }
        RUNNING_OFFLINE -> when (lang) {
            AppLanguage.GREEK -> "Εκτός σύνδεσης"
            AppLanguage.ALBANIAN -> "Pa internet"
            AppLanguage.ITALIAN -> "Offline"
            else -> "Running offline"
        }
        PREDICTED_FROM_SCHEDULE -> when (lang) {
            AppLanguage.GREEK -> "Πρόβλεψη από το πρόγραμμα"
            AppLanguage.ALBANIAN -> "Parashikuar nga orari"
            AppLanguage.ITALIAN -> "Previsto dall'orario"
            else -> "Predicted from schedule"
        }
        SOURCE_SCHEDULED -> when (lang) {
            AppLanguage.GREEK -> "Πρόγραμμα"
            AppLanguage.ALBANIAN -> "Orar"
            AppLanguage.ITALIAN -> "Programmato"
            else -> "Scheduled"
        }
        SOURCE_ESTIMATED -> when (lang) {
            AppLanguage.GREEK -> "Εκτίμηση"
            AppLanguage.ALBANIAN -> "Vlerësim"
            AppLanguage.ITALIAN -> "Stimato"
            else -> "Estimated"
        }
        SOURCE_OFFLINE -> when (lang) {
            AppLanguage.GREEK -> "Εκτός σύνδεσης"
            AppLanguage.ALBANIAN -> "Pa internet"
            AppLanguage.ITALIAN -> "Snapshot offline"
            else -> "Offline snapshot"
        }
        SOURCE_OPERATOR -> when (lang) {
            AppLanguage.GREEK -> "Δείτε πάροχο"
            AppLanguage.ALBANIAN -> "Kontrolloni operatorin"
            AppLanguage.ITALIAN -> "Controlla operatore"
            else -> "Check operator"
        }
        LAST_TRAIN -> when (lang) {
            AppLanguage.GREEK -> "Τελευταίος συρμός"
            AppLanguage.ALBANIAN -> "Treni i fundit"
            AppLanguage.ITALIAN -> "Ultimo treno"
            else -> "Last train"
        }
        LEAVE_BY -> when (lang) {
            AppLanguage.GREEK -> "φύγε έως"
            AppLanguage.ALBANIAN -> "nisu deri në"
            AppLanguage.ITALIAN -> "parti entro"
            else -> "leave by"
        }
        SERVICE_OVER -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν άλλα δρομολόγια απόψε"
            AppLanguage.ALBANIAN -> "Nuk ka më trena sonte"
            AppLanguage.ITALIAN -> "Nessun altro treno stasera"
            else -> "No more trains tonight"
        }
        ENABLE_LOCATION_FOR_NEXT -> when (lang) {
            AppLanguage.GREEK -> "Ενεργοποίησε την τοποθεσία για τον επόμενο συρμό σου"
            AppLanguage.ALBANIAN -> "Aktivizo vendndodhjen për trenin tënd të ardhshëm"
            AppLanguage.ITALIAN -> "Attiva la posizione per vedere il prossimo treno"
            else -> "Enable location to see your next train"
        }
        DESKTOP_PLANNER -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιασμός"
            AppLanguage.ALBANIAN -> "Planifikim"
            AppLanguage.ITALIAN -> "Pianificatore"
            else -> "Planner"
        }
        DESKTOP_SCHEDULES -> when (lang) {
            AppLanguage.GREEK -> "Δρομολόγια"
            AppLanguage.ALBANIAN -> "Oraret"
            AppLanguage.ITALIAN -> "Orari"
            else -> "Schedules"
        }
        DESKTOP_PASSES -> when (lang) {
            AppLanguage.GREEK -> "Κάρτες"
            AppLanguage.ALBANIAN -> "Kartat"
            AppLanguage.ITALIAN -> "Abbonamenti"
            else -> "Passes"
        }
        DESKTOP_ACCOUNT -> when (lang) {
            AppLanguage.GREEK -> "Λογαριασμός"
            AppLanguage.ALBANIAN -> "Llogaria"
            AppLanguage.ITALIAN -> "Account"
            else -> "Account"
        }
        DESKTOP_SUBTITLE -> when (lang) {
            AppLanguage.GREEK -> "Κέντρο ελέγχου σιδηροδρόμων Αθήνας"
            AppLanguage.ALBANIAN -> "Qendra e kontrollit të hekurudhave të Athinës"
            AppLanguage.ITALIAN -> "Centro di controllo ferroviario di Atene"
            else -> "Athens rail command center"
        }
        NETWORK_STATUS -> when (lang) {
            AppLanguage.GREEK -> "Κατάσταση δικτύου"
            AppLanguage.ALBANIAN -> "Statusi i rrjetit"
            AppLanguage.ITALIAN -> "Stato della rete"
            else -> "Network status"
        }
        NETWORK_STATUS_BODY -> when (lang) {
            AppLanguage.GREEK -> "Δεδομένα μετρό, τραμ και προαστιακού φορτωμένα για σχεδιασμό."
            AppLanguage.ALBANIAN -> "Të dhënat e metrosë, tramvajit dhe trenit periferik të ngarkuara për planifikim."
            AppLanguage.ITALIAN -> "Dati metro, tram e suburbano caricati per la pianificazione."
            else -> "Metro, tram and suburban data loaded for planning."
        }
        DESKTOP_HEADER -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιαστής συγκοινωνιών Αθήνας"
            AppLanguage.ALBANIAN -> "Planifikuesi i transportit të Athinës"
            AppLanguage.ITALIAN -> "Pianificatore trasporti di Atene"
            else -> "Athens transit planner"
        }
        TRIP_PLANNING -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιασμός διαδρομής"
            AppLanguage.ALBANIAN -> "Planifikimi i udhëtimit"
            AppLanguage.ITALIAN -> "Pianificazione viaggio"
            else -> "Trip planning"
        }
        SEARCH_STATION -> when (lang) {
            AppLanguage.GREEK -> "Αναζήτηση σταθμού ή προορισμού"
            AppLanguage.ALBANIAN -> "Kërko stacion ose destinacion"
            AppLanguage.ITALIAN -> "Cerca stazione o destinazione"
            else -> "Search station or destination"
        }
        STATIONS_LOWER -> when (lang) {
            AppLanguage.GREEK -> "σταθμοί"
            AppLanguage.ALBANIAN -> "stacione"
            AppLanguage.ITALIAN -> "stazioni"
            else -> "stations"
        }
        LINES_HERE -> when (lang) {
            AppLanguage.GREEK -> "γραμμές εδώ"
            AppLanguage.ALBANIAN -> "linja këtu"
            AppLanguage.ITALIAN -> "linee qui"
            else -> "lines here"
        }
        ACCESSIBLE_STOPS -> when (lang) {
            AppLanguage.GREEK -> "προσβάσιμες στάσεις"
            AppLanguage.ALBANIAN -> "ndalesa të aksesueshme"
            AppLanguage.ITALIAN -> "fermate accessibili"
            else -> "accessible stops"
        }
        SELECT_STATION_HINT -> when (lang) {
            AppLanguage.GREEK -> "Επίλεξε έναν σταθμό στον χάρτη για να δεις γραμμές, προσβασιμότητα και επόμενα βήματα."
            AppLanguage.ALBANIAN -> "Zgjidh një stacion në hartë për të parë linjat, aksesueshmërinë dhe hapat e ardhshëm."
            AppLanguage.ITALIAN -> "Seleziona una stazione sulla mappa per vedere linee, accessibilità e prossimi passi."
            else -> "Select a station on the map to inspect lines, accessibility and next steps."
        }
        TRANSFER_STATION -> when (lang) {
            AppLanguage.GREEK -> "Σταθμός μετεπιβίβασης"
            AppLanguage.ALBANIAN -> "Stacion transferimi"
            AppLanguage.ITALIAN -> "Stazione di interscambio"
            else -> "Transfer station"
        }
        DIRECT_STATION -> when (lang) {
            AppLanguage.GREEK -> "Απλός σταθμός"
            AppLanguage.ALBANIAN -> "Stacion i drejtpërdrejtë"
            AppLanguage.ITALIAN -> "Stazione diretta"
            else -> "Direct station"
        }
        MERGED_RECORDS -> when (lang) {
            AppLanguage.GREEK -> "συγχωνευμένες εγγραφές"
            AppLanguage.ALBANIAN -> "regjistrime të bashkuara"
            AppLanguage.ITALIAN -> "record unificati"
            else -> "merged records"
        }
        LIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανά τρένα"
            AppLanguage.ALBANIAN -> "Trena të drejtpërdrejtë"
            AppLanguage.ITALIAN -> "Treni in tempo reale"
            else -> "Live trains"
        }
        SUBURBAN_RAILWAY -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακός σιδηρόδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha periferike"
            AppLanguage.ITALIAN -> "Ferrovia suburbana"
            else -> "Suburban railway"
        }
        NEAR -> when (lang) {
            AppLanguage.GREEK -> "Κοντά σε"
            AppLanguage.ALBANIAN -> "Afër"
            AppLanguage.ITALIAN -> "Vicino"
            else -> "Near"
        }
        NEXT_SHORT -> when (lang) {
            AppLanguage.GREEK -> "Επόμενος"
            AppLanguage.ALBANIAN -> "Tjetri"
            AppLanguage.ITALIAN -> "Prossimo"
            else -> "Next"
        }
        NO_LIVE_TRAINS_NOW -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν ζωντανά τρένα αυτή τη στιγμή."
            AppLanguage.ALBANIAN -> "Nuk ka trena të drejtpërdrejtë për momentin."
            AppLanguage.ITALIAN -> "Nessun treno in tempo reale disponibile al momento."
            else -> "No live trains available right now."
        }
        NEARBY_POPULAR -> when (lang) {
            AppLanguage.GREEK -> "Κοντινοί ή δημοφιλείς σταθμοί"
            AppLanguage.ALBANIAN -> "Stacione afër ose popullore"
            AppLanguage.ITALIAN -> "Stazioni vicine o popolari"
            else -> "Nearby or popular stations"
        }
        POPULAR_INTERCHANGE -> when (lang) {
            AppLanguage.GREEK -> "Δημοφιλής κόμβος"
            AppLanguage.ALBANIAN -> "Nyje popullore"
            AppLanguage.ITALIAN -> "Interscambio popolare"
            else -> "Popular interchange"
        }
        POPULAR_STOP -> when (lang) {
            AppLanguage.GREEK -> "Δημοφιλής στάση"
            AppLanguage.ALBANIAN -> "Ndalesë popullore"
            AppLanguage.ITALIAN -> "Fermata popolare"
            else -> "Popular stop"
        }
        LINES_LOWER -> when (lang) {
            AppLanguage.GREEK -> "γραμμές"
            AppLanguage.ALBANIAN -> "linja"
            AppLanguage.ITALIAN -> "linee"
            else -> "lines"
        }
        ROUTE_COMPARISON -> when (lang) {
            AppLanguage.GREEK -> "Σύγκριση διαδρομών"
            AppLanguage.ALBANIAN -> "Krahasim rrugësh"
            AppLanguage.ITALIAN -> "Confronto percorsi"
            else -> "Route comparison"
        }
        FASTEST -> when (lang) {
            AppLanguage.GREEK -> "Ταχύτερη"
            AppLanguage.ALBANIAN -> "Më e shpejta"
            AppLanguage.ITALIAN -> "Più veloce"
            else -> "Fastest"
        }
        FEWEST_TRANSFERS -> when (lang) {
            AppLanguage.GREEK -> "Λιγότερες μετεπιβιβάσεις"
            AppLanguage.ALBANIAN -> "Më pak transferime"
            AppLanguage.ITALIAN -> "Meno cambi"
            else -> "Fewest transfers"
        }
        BEST_COVERAGE -> when (lang) {
            AppLanguage.GREEK -> "Καλύτερη κάλυψη"
            AppLanguage.ALBANIAN -> "Mbulimi më i mirë"
            AppLanguage.ITALIAN -> "Migliore copertura"
            else -> "Best coverage"
        }
        ONE_TRANSFER -> when (lang) {
            AppLanguage.GREEK -> "1 μετεπιβίβαση"
            AppLanguage.ALBANIAN -> "1 transferim"
            AppLanguage.ITALIAN -> "1 cambio"
            else -> "1 transfer"
        }
        SCHEDULE_BOARD -> when (lang) {
            AppLanguage.GREEK -> "Πίνακας δρομολογίων"
            AppLanguage.ALBANIAN -> "Tabela e orareve"
            AppLanguage.ITALIAN -> "Tabellone orari"
            else -> "Schedule board"
        }
        EXPORT -> when (lang) {
            AppLanguage.GREEK -> "Εξαγωγή"
            AppLanguage.ALBANIAN -> "Eksporto"
            AppLanguage.ITALIAN -> "Esporta"
            else -> "Export"
        }
        PRINT_SCHEDULE -> when (lang) {
            AppLanguage.GREEK -> "Εκτύπωση δρομολογίου"
            AppLanguage.ALBANIAN -> "Printo orarin"
            AppLanguage.ITALIAN -> "Stampa orario"
            else -> "Print schedule"
        }
        DOWNLOAD_PDF -> when (lang) {
            AppLanguage.GREEK -> "Λήψη PDF"
            AppLanguage.ALBANIAN -> "Shkarko PDF"
            AppLanguage.ITALIAN -> "Scarica PDF"
            else -> "Download PDF"
        }
        NOW -> when (lang) {
            AppLanguage.GREEK -> "Τώρα"
            AppLanguage.ALBANIAN -> "Tani"
            AppLanguage.ITALIAN -> "Ora"
            else -> "Now"
        }
        THEN -> when (lang) {
            AppLanguage.GREEK -> "μετά"
            AppLanguage.ALBANIAN -> "pastaj"
            AppLanguage.ITALIAN -> "poi"
            else -> "then"
        }
        DESTINATIONS -> when (lang) {
            AppLanguage.GREEK -> "Προορισμοί"
            AppLanguage.ALBANIAN -> "Destinacione"
            AppLanguage.ITALIAN -> "Destinazioni"
            else -> "Destinations"
        }
        YOUR_NETWORK -> when (lang) {
            AppLanguage.GREEK -> "Το δίκτυό σου"
            AppLanguage.ALBANIAN -> "Rrjeti yt"
            AppLanguage.ITALIAN -> "La tua rete"
            else -> "Your Network"
        }
        BROWSE_ALL_STATIONS -> when (lang) {
            AppLanguage.GREEK -> "Περιήγηση σε όλους τους 389 σταθμούς"
            AppLanguage.ALBANIAN -> "Shfleto te gjitha 389 stacionet"
            AppLanguage.ITALIAN -> "Esplora tutte le 389 stazioni"
            else -> "Browse all 389 stations"
        }
        DEST_AIRPORT -> when (lang) {
            AppLanguage.GREEK -> "Αεροδρόμιο Αθηνών"
            AppLanguage.ALBANIAN -> "Aeroporti i Athinës"
            AppLanguage.ITALIAN -> "Aeroporto di Atene"
            else -> "Athens Airport"
        }
        DEST_AIRPORT_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η πιο γρήγορη διαδρομή στο τερματικό"
            AppLanguage.ALBANIAN -> "Rruga jote më e shpejtë drejt terminalit"
            AppLanguage.ITALIAN -> "Il percorso più veloce per il terminal"
            else -> "Your fastest route to the terminal"
        }
        DEST_PIRAEUS -> when (lang) {
            AppLanguage.GREEK -> "Πειραιάς"
            AppLanguage.ALBANIAN -> "Pireu"
            AppLanguage.ITALIAN -> "Porto del Pireo"
            else -> "Piraeus Port"
        }
        DEST_PIRAEUS_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Πλοία, κρουαζιέρες, παραλιακές συνδέσεις"
            AppLanguage.ALBANIAN -> "Tragete, kroaziera, lidhje bregdetare"
            AppLanguage.ITALIAN -> "Traghetti, crociere, collegamenti costieri"
            else -> "Ferries, cruises, coastal connections"
        }
        DEST_MONASTIRAKI -> when (lang) {
            AppLanguage.GREEK -> "Μοναστηράκι"
            AppLanguage.ALBANIAN -> "Monastiraki"
            AppLanguage.ITALIAN -> "Monastiraki"
            else -> "Monastiraki"
        }
        DEST_MONASTIRAKI_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Ιστορική καρδιά, δύο γραμμές μετρό"
            AppLanguage.ALBANIAN -> "Zemra historike, dy linja metroje"
            AppLanguage.ITALIAN -> "Cuore storico, due linee metro"
            else -> "Historic heart, two metro lines"
        }
        DEST_KIFISIA -> when (lang) {
            AppLanguage.GREEK -> "Κηφισιά"
            AppLanguage.ALBANIAN -> "Kifisia"
            AppLanguage.ITALIAN -> "Kifisia"
            else -> "Kifisia"
        }
        DEST_KIFISIA_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Βόρεια προάστια, τέρμα πράσινης γραμμής"
            AppLanguage.ALBANIAN -> "Periferia veriore, terminali i linjës së gjelbër"
            AppLanguage.ITALIAN -> "Sobborghi nord, capolinea linea verde"
            else -> "Northern suburbs, green line terminus"
        }
        DEST_THESSALONIKI -> when (lang) {
            AppLanguage.GREEK -> "Θεσσαλονίκη"
            AppLanguage.ALBANIAN -> "Selanik"
            AppLanguage.ITALIAN -> "Salonicco Centrale"
            else -> "Thessaloniki Central"
        }
        DEST_THESSALONIKI_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η δεύτερη πόλη της Ελλάδας με τρένο"
            AppLanguage.ALBANIAN -> "Qyteti i dytë i Greqisë me tren"
            AppLanguage.ITALIAN -> "La seconda città della Grecia in treno"
            else -> "Greece's second city by rail"
        }
        DEST_METEORA -> when (lang) {
            AppLanguage.GREEK -> "Μετέωρα / Καλαμπάκα"
            AppLanguage.ALBANIAN -> "Meteora / Kalambaka"
            AppLanguage.ITALIAN -> "Meteora / Kalambaka"
            else -> "Meteora / Kalampaka"
        }
        DEST_METEORA_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Μοναστήρια στον ουρανό"
            AppLanguage.ALBANIAN -> "Manastire në qiell"
            AppLanguage.ITALIAN -> "Monasteri nel cielo"
            else -> "Monasteries in the sky"
        }
        DEST_PATRAS -> when (lang) {
            AppLanguage.GREEK -> "Πάτρα"
            AppLanguage.ALBANIAN -> "Patra"
            AppLanguage.ITALIAN -> "Patrasso"
            else -> "Patras"
        }
        DEST_PATRAS_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η πύλη της Πελοποννήσου"
            AppLanguage.ALBANIAN -> "Porta e Peloponezit"
            AppLanguage.ITALIAN -> "Porta del Peloponneso"
            else -> "Gateway to the Peloponnese"
        }
        DEST_DIAKOPTO -> when (lang) {
            AppLanguage.GREEK -> "Οδοντωτός Διακοπτού"
            AppLanguage.ALBANIAN -> "Hekurudha e dhëmbëzuar Diakopto"
            AppLanguage.ITALIAN -> "Ferrovia a cremagliera di Diakopto"
            else -> "Diakopto Rack Railway"
        }
        DEST_DIAKOPTO_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Μία από τις πιο γραφικές διαδρομές της Ευρώπης"
            AppLanguage.ALBANIAN -> "Një nga udhetimet me piktoreske te Europes"
            AppLanguage.ITALIAN -> "Uno dei viaggi più panoramici d'Europa"
            else -> "One of Europe's most scenic rides"
        }
    }
}
