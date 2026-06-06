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
    NEXT_STOP, UPDATED, SPEED, ON_TIME, DELAYED;

    fun text(lang: AppLanguage): String = when (this) {
        APP_SUBTITLE -> if (lang == AppLanguage.GREEK) "Ζωντανοί χρόνοι σιδηροδρόμων Αθήνας" else "Live Athens rail times"
        METRO -> if (lang == AppLanguage.GREEK) "Μετρό" else "Metro"
        TRAM -> if (lang == AppLanguage.GREEK) "Τραμ" else "Tram"
        SUBURBAN -> if (lang == AppLanguage.GREEK) "Προαστιακός" else "Suburban"
        SERVICE_ALERTS -> if (lang == AppLanguage.GREEK) "Έκτακτες Ανακοινώσεις" else "Service Alerts"
        LATEST_FROM_STASY -> if (lang == AppLanguage.GREEK) "Τελευταία από ΣΤΑΣΥ" else "Latest from STASY"
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
    }
}
