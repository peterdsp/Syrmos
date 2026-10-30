package com.syrmos.core.common

/**
 * Copy for the station board's scope line ("ALL DIRECTIONS · 3 services").
 *
 * The count word lived separately in the Android Compose board and the iOS
 * SwiftUI board, both hard-coded to the plural, so a station with one timed
 * group read "1 services" on every client. Keep the rule here, once, and have
 * each client call it (iOS mirrors it in `HomeView.boardScopeLabel`).
 */
object BoardScopeCopy {

    /** The noun for [count] services, singular at exactly one. */
    fun services(count: Int, lang: AppLanguage): String = if (count == 1) {
        when (lang) {
            AppLanguage.GREEK -> "υπηρεσία"
            AppLanguage.ALBANIAN -> "shërbim"
            AppLanguage.ITALIAN -> "servizio"
            else -> "service"
        }
    } else {
        when (lang) {
            AppLanguage.GREEK -> "υπηρεσίες"
            AppLanguage.ALBANIAN -> "shërbime"
            AppLanguage.ITALIAN -> "servizi"
            else -> "services"
        }
    }
}
