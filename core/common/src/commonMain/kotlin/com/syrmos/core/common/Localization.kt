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
    LINES, SETTINGS, HOME, MAP,
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
    ONBOARD_PRIVACY_TITLE, ONBOARD_PRIVACY_BODY,
    ONBOARD_CONTINUE, ONBOARD_GET_STARTED, ONBOARD_SKIP,
    NEXT_TRAIN, TO, LIVE, RUNNING_OFFLINE, PREDICTED_FROM_SCHEDULE,
    LAST_TRAIN, LEAVE_BY, SERVICE_OVER, ENABLE_LOCATION_FOR_NEXT;

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
    }
}
