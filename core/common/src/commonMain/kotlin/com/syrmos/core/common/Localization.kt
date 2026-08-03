package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    GREEK("el", "Ελληνικά"),
    ALBANIAN("sq", "Shqip"),
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
    SERVICE_ALERTS, LATEST_FROM_STASY,
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
            else -> "Live Athens rail times"
        }
        METRO -> when (lang) {
            AppLanguage.GREEK -> "Μετρό"
            AppLanguage.ALBANIAN -> "Metro"
            else -> "Metro"
        }
        TRAM -> when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            else -> "Tram"
        }
        SUBURBAN -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακός"
            AppLanguage.ALBANIAN -> "Treni periferik"
            else -> "Suburban"
        }
        SERVICE_ALERTS -> when (lang) {
            AppLanguage.GREEK -> "Έκτακτες Ανακοινώσεις"
            AppLanguage.ALBANIAN -> "Njoftime urgjente"
            else -> "Service Alerts"
        }
        LATEST_FROM_STASY -> when (lang) {
            AppLanguage.GREEK -> "Ενημέρωση Μετρό & Τραμ"
            AppLanguage.ALBANIAN -> "Përditësime Metro & Tramvaj"
            else -> "Metro & Tram updates"
        }
        READ_MORE -> when (lang) {
            AppLanguage.GREEK -> "Διαβάστε περισσότερα"
            AppLanguage.ALBANIAN -> "Lexo më shumë"
            else -> "Read more"
        }
        SHOW_MORE -> when (lang) {
            AppLanguage.GREEK -> "Δείτε περισσότερα"
            AppLanguage.ALBANIAN -> "Trego më shumë"
            else -> "Show more"
        }
        SHOW_LESS -> when (lang) {
            AppLanguage.GREEK -> "Δείτε λιγότερα"
            AppLanguage.ALBANIAN -> "Trego më pak"
            else -> "Show less"
        }
        LINES -> when (lang) {
            AppLanguage.GREEK -> "Γραμμές"
            AppLanguage.ALBANIAN -> "Linjat"
            else -> "Lines"
        }
        SETTINGS -> when (lang) {
            AppLanguage.GREEK -> "Ρυθμίσεις"
            AppLanguage.ALBANIAN -> "Cilësimet"
            else -> "Settings"
        }
        HOME -> when (lang) {
            AppLanguage.GREEK -> "Αρχική"
            AppLanguage.ALBANIAN -> "Kryesore"
            else -> "Home"
        }
        MAP -> when (lang) {
            AppLanguage.GREEK -> "Χάρτης"
            AppLanguage.ALBANIAN -> "Harta"
            else -> "Map"
        }
        EXPLORE -> when (lang) {
            AppLanguage.GREEK -> "Εξερεύνηση"
            AppLanguage.ALBANIAN -> "Eksploro"
            else -> "Explore"
        }
        DEPARTURES -> when (lang) {
            AppLanguage.GREEK -> "Αναχωρήσεις"
            AppLanguage.ALBANIAN -> "Nisjet"
            else -> "Departures"
        }
        MORE_TAB -> when (lang) {
            AppLanguage.GREEK -> "Περισσότερα"
            AppLanguage.ALBANIAN -> "Më shumë"
            else -> "More"
        }
        LANGUAGE -> when (lang) {
            AppLanguage.GREEK -> "Γλώσσα"
            AppLanguage.ALBANIAN -> "Gjuha"
            else -> "Language"
        }
        THEME -> when (lang) {
            AppLanguage.GREEK -> "Θέμα"
            AppLanguage.ALBANIAN -> "Tema"
            else -> "Theme"
        }
        SYSTEM_DEFAULT -> when (lang) {
            AppLanguage.GREEK -> "Σύστημα"
            AppLanguage.ALBANIAN -> "Sistemi"
            else -> "System"
        }
        PREFERENCES -> when (lang) {
            AppLanguage.GREEK -> "Προτιμήσεις"
            AppLanguage.ALBANIAN -> "Preferencat"
            else -> "Preferences"
        }
        DATA -> when (lang) {
            AppLanguage.GREEK -> "Δεδομένα"
            AppLanguage.ALBANIAN -> "Të dhënat"
            else -> "Data"
        }
        SCHEDULE_VERSION -> when (lang) {
            AppLanguage.GREEK -> "Έκδοση δρομολογίων"
            AppLanguage.ALBANIAN -> "Versioni i orarit"
            else -> "Schedule version"
        }
        STATIONS -> when (lang) {
            AppLanguage.GREEK -> "Σταθμοί"
            AppLanguage.ALBANIAN -> "Stacionet"
            else -> "Stations"
        }
        ABOUT -> when (lang) {
            AppLanguage.GREEK -> "Σχετικά"
            AppLanguage.ALBANIAN -> "Rreth"
            else -> "About"
        }
        ABOUT_TEXT -> when (lang) {
            AppLanguage.GREEK -> "Δεδομένα δρομολογίων από τα επίσημα προγράμματα ΣΤΑΣΥ και Hellenic Train. Η εφαρμογή δεν σχετίζεται με ΣΤΑΣΥ, Hellenic Train ή ΟΑΣΑ."
            AppLanguage.ALBANIAN -> "Të dhënat e orareve nga oraret zyrtare STASY dhe Hellenic Train. Ky aplikacion nuk është i lidhur me STASY, Hellenic Train ose OASA."
            else -> "Schedule data from STASY and Hellenic Train official timetables. This app is not affiliated with STASY, Hellenic Train or OASA."
        }
        COULD_NOT_REACH -> when (lang) {
            AppLanguage.GREEK -> "Δεν ήταν δυνατή η σύνδεση με stasy.gr"
            AppLanguage.ALBANIAN -> "Nuk arritëm të lidhemi me stasy.gr"
            else -> "Could not reach stasy.gr"
        }
        LIVE_TRACKER -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανός εντοπισμός"
            AppLanguage.ALBANIAN -> "Gjurmim i drejtpërdrejtë"
            else -> "Live tracker"
        }
        ACTIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "ενεργά τρένα"
            AppLanguage.ALBANIAN -> "trena aktiv"
            else -> "active trains"
        }
        NO_LIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν ζωντανές θέσεις για αυτή τη γραμμή αυτή τη στιγμή"
            AppLanguage.ALBANIAN -> "Nuk ka pozicione të drejtpërdrejta për këtë linjë në këtë moment"
            else -> "No live train positions are available for this line right now"
        }
        NEXT_STOP -> when (lang) {
            AppLanguage.GREEK -> "Επόμενη στάση"
            AppLanguage.ALBANIAN -> "Stacioni tjetër"
            else -> "Next stop"
        }
        UPDATED -> when (lang) {
            AppLanguage.GREEK -> "Ενημερώθηκε"
            AppLanguage.ALBANIAN -> "Përditësuar"
            else -> "Updated"
        }
        SPEED -> when (lang) {
            AppLanguage.GREEK -> "Ταχύτητα"
            AppLanguage.ALBANIAN -> "Shpejtësia"
            else -> "Speed"
        }
        ON_TIME -> when (lang) {
            AppLanguage.GREEK -> "Στην ώρα του"
            AppLanguage.ALBANIAN -> "Në kohë"
            else -> "On time"
        }
        DELAYED -> when (lang) {
            AppLanguage.GREEK -> "Καθυστέρηση"
            AppLanguage.ALBANIAN -> "Me vonesë"
            else -> "Delayed"
        }
        ONBOARD_WELCOME_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Καλώς ήρθες στο Syrmos"
            AppLanguage.ALBANIAN -> "Mirëserdhe në Syrmos"
            else -> "Welcome to Syrmos"
        }
        ONBOARD_WELCOME_BODY -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανές αφίξεις για Μετρό, Τραμ και Προαστιακό της Αθήνας, στην τσέπη σου."
            AppLanguage.ALBANIAN -> "Mbërritjet e drejtpërdrejta për Metron, Tramvajin dhe Trenin periferik të Athinës, në xhepin tënd."
            else -> "Live arrivals for the Athens Metro, Tram and Suburban network, in your pocket."
        }
        ONBOARD_LIVE_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Συρμοί σε πραγματικό χρόνο"
            AppLanguage.ALBANIAN -> "Trena në kohë reale"
            else -> "Trains in real time"
        }
        ONBOARD_LIVE_BODY -> when (lang) {
            AppLanguage.GREEK -> "Δες τις επόμενες αναχωρήσεις και πού βρίσκεται κάθε συρμός στον χάρτη, με δεδομένα από ΣΤΑΣΥ και Hellenic Train."
            AppLanguage.ALBANIAN -> "Shih nisjet e ardhshme dhe ku ndodhet çdo tren në hartë, përditësuar nga STASY dhe Hellenic Train."
            else -> "See the next departures and where every train is on the map, refreshed from STASY and Hellenic Train."
        }
        ONBOARD_LOCATION_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Πιο κοντά σε σένα"
            AppLanguage.ALBANIAN -> "Më pranë teje"
            else -> "Closest to you"
        }
        ONBOARD_LOCATION_BODY -> when (lang) {
            AppLanguage.GREEK -> "Επίτρεψε την τοποθεσία για να βλέπεις τους πιο κοντινούς σταθμούς. Χρησιμοποιείται μόνο στη συσκευή."
            AppLanguage.ALBANIAN -> "Lejo vendndodhjen që të shohësh stacionet më të afërta dhe mbërritjet të parat. Përdoret vetëm në pajisje."
            else -> "Allow location so we can show the nearest stations and arrivals first. Used only on device."
        }
        ONBOARD_LOCATION_CTA -> when (lang) {
            AppLanguage.GREEK -> "Επίτρεψε την τοποθεσία"
            AppLanguage.ALBANIAN -> "Lejo vendndodhjen"
            else -> "Allow location"
        }
        ONBOARD_NOTIF_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Μεινε ενημερος"
            AppLanguage.ALBANIAN -> "Qendro i informuar"
            else -> "Stay informed"
        }
        ONBOARD_NOTIF_BODY -> when (lang) {
            AppLanguage.GREEK -> "Λαβε ειδοποιησεις για διακοπες υπηρεσιων κοντα σου, καιρικες προειδοποιησεις που μπορει να επηρεασουν τη μετακινηση σου, και πρωινη ενημερωση με τα τελευταια νεα."
            AppLanguage.ALBANIAN -> "Merr njoftime per nderprerje sherbimesh prane teje, paralajmerime moti qe mund te ndikojne udhetimin tend, dhe informim mengjesit me perditesimet me te fundit."
            else -> "Get alerts for service disruptions near you, weather warnings that may affect your commute, and a morning briefing with the latest updates."
        }
        ONBOARD_NOTIF_CTA -> when (lang) {
            AppLanguage.GREEK -> "Επιτρεψε τις ειδοποιησεις"
            AppLanguage.ALBANIAN -> "Lejo njoftimet"
            else -> "Allow notifications"
        }
        ONBOARD_PRIVACY_TITLE -> when (lang) {
            AppLanguage.GREEK -> "Χωρίς λογαριασμό. Χωρίς παρακολούθηση."
            AppLanguage.ALBANIAN -> "Pa llogari. Pa gjurmim."
            else -> "No accounts. No tracking."
        }
        ONBOARD_PRIVACY_BODY -> when (lang) {
            AppLanguage.GREEK -> "Το Syrmos δεν ζητάει σύνδεση και δεν αποθηκεύει προσωπικά δεδομένα. Μόνο συρμούς."
            AppLanguage.ALBANIAN -> "Syrmos nuk kërkon hyrje dhe nuk ruan të dhëna personale. Vetëm trena."
            else -> "Syrmos doesn't ask you to sign in and doesn't store personal data. Just trains."
        }
        ONBOARD_CONTINUE -> when (lang) {
            AppLanguage.GREEK -> "Συνέχεια"
            AppLanguage.ALBANIAN -> "Vazhdo"
            else -> "Continue"
        }
        ONBOARD_GET_STARTED -> when (lang) {
            AppLanguage.GREEK -> "Ξεκίνα"
            AppLanguage.ALBANIAN -> "Fillo"
            else -> "Get started"
        }
        ONBOARD_SKIP -> when (lang) {
            AppLanguage.GREEK -> "Παράλειψη"
            AppLanguage.ALBANIAN -> "Anashkalo"
            else -> "Skip"
        }
        NEXT_TRAIN -> when (lang) {
            AppLanguage.GREEK -> "Επόμενος συρμός"
            AppLanguage.ALBANIAN -> "Treni i ardhshëm"
            else -> "Next train"
        }
        TO -> when (lang) {
            AppLanguage.GREEK -> "προς"
            AppLanguage.ALBANIAN -> "drejt"
            else -> "to"
        }
        LIVE -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανά"
            AppLanguage.ALBANIAN -> "Drejtpërdrejt"
            else -> "Live"
        }
        RUNNING_OFFLINE -> when (lang) {
            AppLanguage.GREEK -> "Εκτός σύνδεσης"
            AppLanguage.ALBANIAN -> "Pa internet"
            else -> "Running offline"
        }
        PREDICTED_FROM_SCHEDULE -> when (lang) {
            AppLanguage.GREEK -> "Πρόβλεψη από το πρόγραμμα"
            AppLanguage.ALBANIAN -> "Parashikuar nga orari"
            else -> "Predicted from schedule"
        }
        SOURCE_SCHEDULED -> when (lang) {
            AppLanguage.GREEK -> "Πρόγραμμα"
            AppLanguage.ALBANIAN -> "Orar"
            else -> "Scheduled"
        }
        SOURCE_ESTIMATED -> when (lang) {
            AppLanguage.GREEK -> "Εκτίμηση"
            AppLanguage.ALBANIAN -> "Vlerësim"
            else -> "Estimated"
        }
        SOURCE_OFFLINE -> when (lang) {
            AppLanguage.GREEK -> "Εκτός σύνδεσης"
            AppLanguage.ALBANIAN -> "Pa internet"
            else -> "Offline snapshot"
        }
        SOURCE_OPERATOR -> when (lang) {
            AppLanguage.GREEK -> "Δείτε πάροχο"
            AppLanguage.ALBANIAN -> "Kontrolloni operatorin"
            else -> "Check operator"
        }
        LAST_TRAIN -> when (lang) {
            AppLanguage.GREEK -> "Τελευταίος συρμός"
            AppLanguage.ALBANIAN -> "Treni i fundit"
            else -> "Last train"
        }
        LEAVE_BY -> when (lang) {
            AppLanguage.GREEK -> "φύγε έως"
            AppLanguage.ALBANIAN -> "nisu deri në"
            else -> "leave by"
        }
        SERVICE_OVER -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν άλλα δρομολόγια απόψε"
            AppLanguage.ALBANIAN -> "Nuk ka më trena sonte"
            else -> "No more trains tonight"
        }
        ENABLE_LOCATION_FOR_NEXT -> when (lang) {
            AppLanguage.GREEK -> "Ενεργοποίησε την τοποθεσία για τον επόμενο συρμό σου"
            AppLanguage.ALBANIAN -> "Aktivizo vendndodhjen për trenin tënd të ardhshëm"
            else -> "Enable location to see your next train"
        }
        DESKTOP_PLANNER -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιασμός"
            AppLanguage.ALBANIAN -> "Planifikim"
            else -> "Planner"
        }
        DESKTOP_SCHEDULES -> when (lang) {
            AppLanguage.GREEK -> "Δρομολόγια"
            AppLanguage.ALBANIAN -> "Oraret"
            else -> "Schedules"
        }
        DESKTOP_PASSES -> when (lang) {
            AppLanguage.GREEK -> "Κάρτες"
            AppLanguage.ALBANIAN -> "Kartat"
            else -> "Passes"
        }
        DESKTOP_ACCOUNT -> when (lang) {
            AppLanguage.GREEK -> "Λογαριασμός"
            AppLanguage.ALBANIAN -> "Llogaria"
            else -> "Account"
        }
        DESKTOP_SUBTITLE -> when (lang) {
            AppLanguage.GREEK -> "Κέντρο ελέγχου σιδηροδρόμων Αθήνας"
            AppLanguage.ALBANIAN -> "Qendra e kontrollit të hekurudhave të Athinës"
            else -> "Athens rail command center"
        }
        NETWORK_STATUS -> when (lang) {
            AppLanguage.GREEK -> "Κατάσταση δικτύου"
            AppLanguage.ALBANIAN -> "Statusi i rrjetit"
            else -> "Network status"
        }
        NETWORK_STATUS_BODY -> when (lang) {
            AppLanguage.GREEK -> "Δεδομένα μετρό, τραμ και προαστιακού φορτωμένα για σχεδιασμό."
            AppLanguage.ALBANIAN -> "Të dhënat e metrosë, tramvajit dhe trenit periferik të ngarkuara për planifikim."
            else -> "Metro, tram and suburban data loaded for planning."
        }
        DESKTOP_HEADER -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιαστής συγκοινωνιών Αθήνας"
            AppLanguage.ALBANIAN -> "Planifikuesi i transportit të Athinës"
            else -> "Athens transit planner"
        }
        TRIP_PLANNING -> when (lang) {
            AppLanguage.GREEK -> "Σχεδιασμός διαδρομής"
            AppLanguage.ALBANIAN -> "Planifikimi i udhëtimit"
            else -> "Trip planning"
        }
        SEARCH_STATION -> when (lang) {
            AppLanguage.GREEK -> "Αναζήτηση σταθμού ή προορισμού"
            AppLanguage.ALBANIAN -> "Kërko stacion ose destinacion"
            else -> "Search station or destination"
        }
        STATIONS_LOWER -> when (lang) {
            AppLanguage.GREEK -> "σταθμοί"
            AppLanguage.ALBANIAN -> "stacione"
            else -> "stations"
        }
        LINES_HERE -> when (lang) {
            AppLanguage.GREEK -> "γραμμές εδώ"
            AppLanguage.ALBANIAN -> "linja këtu"
            else -> "lines here"
        }
        ACCESSIBLE_STOPS -> when (lang) {
            AppLanguage.GREEK -> "προσβάσιμες στάσεις"
            AppLanguage.ALBANIAN -> "ndalesa të aksesueshme"
            else -> "accessible stops"
        }
        SELECT_STATION_HINT -> when (lang) {
            AppLanguage.GREEK -> "Επίλεξε έναν σταθμό στον χάρτη για να δεις γραμμές, προσβασιμότητα και επόμενα βήματα."
            AppLanguage.ALBANIAN -> "Zgjidh një stacion në hartë për të parë linjat, aksesueshmërinë dhe hapat e ardhshëm."
            else -> "Select a station on the map to inspect lines, accessibility and next steps."
        }
        TRANSFER_STATION -> when (lang) {
            AppLanguage.GREEK -> "Σταθμός μετεπιβίβασης"
            AppLanguage.ALBANIAN -> "Stacion transferimi"
            else -> "Transfer station"
        }
        DIRECT_STATION -> when (lang) {
            AppLanguage.GREEK -> "Απλός σταθμός"
            AppLanguage.ALBANIAN -> "Stacion i drejtpërdrejtë"
            else -> "Direct station"
        }
        MERGED_RECORDS -> when (lang) {
            AppLanguage.GREEK -> "συγχωνευμένες εγγραφές"
            AppLanguage.ALBANIAN -> "regjistrime të bashkuara"
            else -> "merged records"
        }
        LIVE_TRAINS -> when (lang) {
            AppLanguage.GREEK -> "Ζωντανά τρένα"
            AppLanguage.ALBANIAN -> "Trena të drejtpërdrejtë"
            else -> "Live trains"
        }
        SUBURBAN_RAILWAY -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακός σιδηρόδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha periferike"
            else -> "Suburban railway"
        }
        NEAR -> when (lang) {
            AppLanguage.GREEK -> "Κοντά σε"
            AppLanguage.ALBANIAN -> "Afër"
            else -> "Near"
        }
        NEXT_SHORT -> when (lang) {
            AppLanguage.GREEK -> "Επόμενος"
            AppLanguage.ALBANIAN -> "Tjetri"
            else -> "Next"
        }
        NO_LIVE_TRAINS_NOW -> when (lang) {
            AppLanguage.GREEK -> "Δεν υπάρχουν ζωντανά τρένα αυτή τη στιγμή."
            AppLanguage.ALBANIAN -> "Nuk ka trena të drejtpërdrejtë për momentin."
            else -> "No live trains available right now."
        }
        NEARBY_POPULAR -> when (lang) {
            AppLanguage.GREEK -> "Κοντινοί ή δημοφιλείς σταθμοί"
            AppLanguage.ALBANIAN -> "Stacione afër ose popullore"
            else -> "Nearby or popular stations"
        }
        POPULAR_INTERCHANGE -> when (lang) {
            AppLanguage.GREEK -> "Δημοφιλής κόμβος"
            AppLanguage.ALBANIAN -> "Nyje popullore"
            else -> "Popular interchange"
        }
        POPULAR_STOP -> when (lang) {
            AppLanguage.GREEK -> "Δημοφιλής στάση"
            AppLanguage.ALBANIAN -> "Ndalesë popullore"
            else -> "Popular stop"
        }
        LINES_LOWER -> when (lang) {
            AppLanguage.GREEK -> "γραμμές"
            AppLanguage.ALBANIAN -> "linja"
            else -> "lines"
        }
        ROUTE_COMPARISON -> when (lang) {
            AppLanguage.GREEK -> "Σύγκριση διαδρομών"
            AppLanguage.ALBANIAN -> "Krahasim rrugësh"
            else -> "Route comparison"
        }
        FASTEST -> when (lang) {
            AppLanguage.GREEK -> "Ταχύτερη"
            AppLanguage.ALBANIAN -> "Më e shpejta"
            else -> "Fastest"
        }
        FEWEST_TRANSFERS -> when (lang) {
            AppLanguage.GREEK -> "Λιγότερες μετεπιβιβάσεις"
            AppLanguage.ALBANIAN -> "Më pak transferime"
            else -> "Fewest transfers"
        }
        BEST_COVERAGE -> when (lang) {
            AppLanguage.GREEK -> "Καλύτερη κάλυψη"
            AppLanguage.ALBANIAN -> "Mbulimi më i mirë"
            else -> "Best coverage"
        }
        ONE_TRANSFER -> when (lang) {
            AppLanguage.GREEK -> "1 μετεπιβίβαση"
            AppLanguage.ALBANIAN -> "1 transferim"
            else -> "1 transfer"
        }
        SCHEDULE_BOARD -> when (lang) {
            AppLanguage.GREEK -> "Πίνακας δρομολογίων"
            AppLanguage.ALBANIAN -> "Tabela e orareve"
            else -> "Schedule board"
        }
        EXPORT -> when (lang) {
            AppLanguage.GREEK -> "Εξαγωγή"
            AppLanguage.ALBANIAN -> "Eksporto"
            else -> "Export"
        }
        PRINT_SCHEDULE -> when (lang) {
            AppLanguage.GREEK -> "Εκτύπωση δρομολογίου"
            AppLanguage.ALBANIAN -> "Printo orarin"
            else -> "Print schedule"
        }
        DOWNLOAD_PDF -> when (lang) {
            AppLanguage.GREEK -> "Λήψη PDF"
            AppLanguage.ALBANIAN -> "Shkarko PDF"
            else -> "Download PDF"
        }
        NOW -> when (lang) {
            AppLanguage.GREEK -> "Τώρα"
            AppLanguage.ALBANIAN -> "Tani"
            else -> "Now"
        }
        THEN -> when (lang) {
            AppLanguage.GREEK -> "μετά"
            AppLanguage.ALBANIAN -> "pastaj"
            else -> "then"
        }
        DESTINATIONS -> when (lang) {
            AppLanguage.GREEK -> "Προορισμοί"
            AppLanguage.ALBANIAN -> "Destinacione"
            else -> "Destinations"
        }
        YOUR_NETWORK -> when (lang) {
            AppLanguage.GREEK -> "Το δίκτυό σου"
            AppLanguage.ALBANIAN -> "Rrjeti yt"
            else -> "Your Network"
        }
        BROWSE_ALL_STATIONS -> when (lang) {
            AppLanguage.GREEK -> "Περιήγηση σε όλους τους 389 σταθμούς"
            AppLanguage.ALBANIAN -> "Shfleto te gjitha 389 stacionet"
            else -> "Browse all 389 stations"
        }
        DEST_AIRPORT -> when (lang) {
            AppLanguage.GREEK -> "Αεροδρόμιο Αθηνών"
            AppLanguage.ALBANIAN -> "Aeroporti i Athinës"
            else -> "Athens Airport"
        }
        DEST_AIRPORT_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η πιο γρήγορη διαδρομή στο τερματικό"
            AppLanguage.ALBANIAN -> "Rruga jote më e shpejtë drejt terminalit"
            else -> "Your fastest route to the terminal"
        }
        DEST_PIRAEUS -> when (lang) {
            AppLanguage.GREEK -> "Πειραιάς"
            AppLanguage.ALBANIAN -> "Pireu"
            else -> "Piraeus Port"
        }
        DEST_PIRAEUS_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Πλοία, κρουαζιέρες, παραλιακές συνδέσεις"
            AppLanguage.ALBANIAN -> "Tragete, kroaziera, lidhje bregdetare"
            else -> "Ferries, cruises, coastal connections"
        }
        DEST_MONASTIRAKI -> when (lang) {
            AppLanguage.GREEK -> "Μοναστηράκι"
            AppLanguage.ALBANIAN -> "Monastiraki"
            else -> "Monastiraki"
        }
        DEST_MONASTIRAKI_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Ιστορική καρδιά, δύο γραμμές μετρό"
            AppLanguage.ALBANIAN -> "Zemra historike, dy linja metroje"
            else -> "Historic heart, two metro lines"
        }
        DEST_KIFISIA -> when (lang) {
            AppLanguage.GREEK -> "Κηφισιά"
            AppLanguage.ALBANIAN -> "Kifisia"
            else -> "Kifisia"
        }
        DEST_KIFISIA_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Βόρεια προάστια, τέρμα πράσινης γραμμής"
            AppLanguage.ALBANIAN -> "Periferia veriore, terminali i linjës së gjelbër"
            else -> "Northern suburbs, green line terminus"
        }
        DEST_THESSALONIKI -> when (lang) {
            AppLanguage.GREEK -> "Θεσσαλονίκη"
            AppLanguage.ALBANIAN -> "Selanik"
            else -> "Thessaloniki Central"
        }
        DEST_THESSALONIKI_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η δεύτερη πόλη της Ελλάδας με τρένο"
            AppLanguage.ALBANIAN -> "Qyteti i dytë i Greqisë me tren"
            else -> "Greece's second city by rail"
        }
        DEST_METEORA -> when (lang) {
            AppLanguage.GREEK -> "Μετέωρα / Καλαμπάκα"
            AppLanguage.ALBANIAN -> "Meteora / Kalambaka"
            else -> "Meteora / Kalampaka"
        }
        DEST_METEORA_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Μοναστήρια στον ουρανό"
            AppLanguage.ALBANIAN -> "Manastire në qiell"
            else -> "Monasteries in the sky"
        }
        DEST_PATRAS -> when (lang) {
            AppLanguage.GREEK -> "Πάτρα"
            AppLanguage.ALBANIAN -> "Patra"
            else -> "Patras"
        }
        DEST_PATRAS_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Η πύλη της Πελοποννήσου"
            AppLanguage.ALBANIAN -> "Porta e Peloponezit"
            else -> "Gateway to the Peloponnese"
        }
        DEST_DIAKOPTO -> when (lang) {
            AppLanguage.GREEK -> "Οδοντωτός Διακοπτού"
            AppLanguage.ALBANIAN -> "Hekurudha e dhëmbëzuar Diakopto"
            else -> "Diakopto Rack Railway"
        }
        DEST_DIAKOPTO_HOOK -> when (lang) {
            AppLanguage.GREEK -> "Μία από τις πιο γραφικές διαδρομές της Ευρώπης"
            AppLanguage.ALBANIAN -> "Një nga udhetimet me piktoreske te Europes"
            else -> "One of Europe's most scenic rides"
        }
    }
}
