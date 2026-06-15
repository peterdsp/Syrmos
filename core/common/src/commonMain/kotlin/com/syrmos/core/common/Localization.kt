package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    GREEK("el", "Ελληνικά"),
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
    ONBOARD_CONTINUE, ONBOARD_GET_STARTED, ONBOARD_SKIP;

    fun text(lang: AppLanguage): String = when (this) {
        APP_SUBTITLE -> if (lang == AppLanguage.GREEK) "Ζωντανοί χρόνοι σιδηροδρόμων Αθήνας" else "Live Athens rail times"
        METRO -> if (lang == AppLanguage.GREEK) "Μετρό" else "Metro"
        TRAM -> if (lang == AppLanguage.GREEK) "Τραμ" else "Tram"
        SUBURBAN -> if (lang == AppLanguage.GREEK) "Προαστιακός" else "Suburban"
        SERVICE_ALERTS -> if (lang == AppLanguage.GREEK) "Έκτακτες Ανακοινώσεις" else "Service Alerts"
        LATEST_FROM_STASY -> if (lang == AppLanguage.GREEK) "Ενημέρωση Μετρό & Τραμ" else "Metro & Tram updates"
        READ_MORE -> if (lang == AppLanguage.GREEK) "Διαβάστε περισσότερα" else "Read more"
        SHOW_MORE -> if (lang == AppLanguage.GREEK) "Δείτε περισσότερα" else "Show more"
        SHOW_LESS -> if (lang == AppLanguage.GREEK) "Δείτε λιγότερα" else "Show less"
        LINES -> if (lang == AppLanguage.GREEK) "Γραμμές" else "Lines"
        SETTINGS -> if (lang == AppLanguage.GREEK) "Ρυθμίσεις" else "Settings"
        HOME -> if (lang == AppLanguage.GREEK) "Αρχική" else "Home"
        MAP -> if (lang == AppLanguage.GREEK) "Χάρτης" else "Map"
        LANGUAGE -> if (lang == AppLanguage.GREEK) "Γλώσσα" else "Language"
        THEME -> if (lang == AppLanguage.GREEK) "Θέμα" else "Theme"
        SYSTEM_DEFAULT -> if (lang == AppLanguage.GREEK) "Σύστημα" else "System"
        PREFERENCES -> if (lang == AppLanguage.GREEK) "Προτιμήσεις" else "Preferences"
        DATA -> if (lang == AppLanguage.GREEK) "Δεδομένα" else "Data"
        SCHEDULE_VERSION -> if (lang == AppLanguage.GREEK) "Έκδοση δρομολογίων" else "Schedule version"
        STATIONS -> if (lang == AppLanguage.GREEK) "Σταθμοί" else "Stations"
        ABOUT -> if (lang == AppLanguage.GREEK) "Σχετικά" else "About"
        ABOUT_TEXT -> if (lang == AppLanguage.GREEK) {
            "Δεδομένα δρομολογίων από τα επίσημα προγράμματα ΣΤΑΣΥ και Hellenic Train. Η εφαρμογή δεν σχετίζεται με ΣΤΑΣΥ, Hellenic Train ή ΟΑΣΑ."
        } else {
            "Schedule data from STASY and Hellenic Train official timetables. This app is not affiliated with STASY, Hellenic Train or OASA."
        }
        COULD_NOT_REACH -> if (lang == AppLanguage.GREEK) "Δεν ήταν δυνατή η σύνδεση με stasy.gr" else "Could not reach stasy.gr"
        LIVE_TRACKER -> if (lang == AppLanguage.GREEK) "Ζωντανός εντοπισμός" else "Live tracker"
        ACTIVE_TRAINS -> if (lang == AppLanguage.GREEK) "ενεργά τρένα" else "active trains"
        NO_LIVE_TRAINS -> if (lang == AppLanguage.GREEK) {
            "Δεν υπάρχουν ζωντανές θέσεις για αυτή τη γραμμή αυτή τη στιγμή"
        } else {
            "No live train positions are available for this line right now"
        }
        NEXT_STOP -> if (lang == AppLanguage.GREEK) "Επόμενη στάση" else "Next stop"
        UPDATED -> if (lang == AppLanguage.GREEK) "Ενημερώθηκε" else "Updated"
        SPEED -> if (lang == AppLanguage.GREEK) "Ταχύτητα" else "Speed"
        ON_TIME -> if (lang == AppLanguage.GREEK) "Στην ώρα του" else "On time"
        DELAYED -> if (lang == AppLanguage.GREEK) "Καθυστέρηση" else "Delayed"
        ONBOARD_WELCOME_TITLE -> if (lang == AppLanguage.GREEK) "Καλώς ήρθες στο Syrmos" else "Welcome to Syrmos"
        ONBOARD_WELCOME_BODY -> if (lang == AppLanguage.GREEK) {
            "Ζωντανές αφίξεις για Μετρό, Τραμ και Προαστιακό της Αθήνας, στην τσέπη σου."
        } else {
            "Live arrivals for the Athens Metro, Tram and Suburban network, in your pocket."
        }
        ONBOARD_LIVE_TITLE -> if (lang == AppLanguage.GREEK) "Συρμοί σε πραγματικό χρόνο" else "Trains in real time"
        ONBOARD_LIVE_BODY -> if (lang == AppLanguage.GREEK) {
            "Δες τις επόμενες αναχωρήσεις και πού βρίσκεται κάθε συρμός στον χάρτη, με δεδομένα από ΣΤΑΣΥ και Hellenic Train."
        } else {
            "See the next departures and where every train is on the map, refreshed from STASY and Hellenic Train."
        }
        ONBOARD_LOCATION_TITLE -> if (lang == AppLanguage.GREEK) "Πιο κοντά σε σένα" else "Closest to you"
        ONBOARD_LOCATION_BODY -> if (lang == AppLanguage.GREEK) {
            "Επίτρεψε την τοποθεσία για να βλέπεις τους πιο κοντινούς σταθμούς. Χρησιμοποιείται μόνο στη συσκευή."
        } else {
            "Allow location so we can show the nearest stations and arrivals first. Used only on device."
        }
        ONBOARD_LOCATION_CTA -> if (lang == AppLanguage.GREEK) "Επίτρεψε την τοποθεσία" else "Allow location"
        ONBOARD_PRIVACY_TITLE -> if (lang == AppLanguage.GREEK) "Χωρίς λογαριασμό. Χωρίς παρακολούθηση." else "No accounts. No tracking."
        ONBOARD_PRIVACY_BODY -> if (lang == AppLanguage.GREEK) {
            "Το Syrmos δεν ζητάει σύνδεση και δεν αποθηκεύει προσωπικά δεδομένα. Μόνο συρμούς."
        } else {
            "Syrmos doesn't ask you to sign in and doesn't store personal data. Just trains."
        }
        ONBOARD_CONTINUE -> if (lang == AppLanguage.GREEK) "Συνέχεια" else "Continue"
        ONBOARD_GET_STARTED -> if (lang == AppLanguage.GREEK) "Ξεκίνα" else "Get started"
        ONBOARD_SKIP -> if (lang == AppLanguage.GREEK) "Παράλειψη" else "Skip"
    }
}
