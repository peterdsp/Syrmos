package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage

/**
 * An Ichnos report carries the label of the place it was made at, built in
 * the reporter's language when it was submitted ("Ichnos at Florina") and
 * served back verbatim. Until the server stores the station id instead, a
 * reader in another language would see the reporter's language. This puts
 * the station-form label back into the reader's language; any other form
 * (a line or train context) is returned unchanged. Twin of
 * `localizedScopeLabel` in iOS Localization.swift.
 */
fun localizedScopeLabel(label: String, lang: AppLanguage): String {
    val prefixes = listOf("Ichnos at ", "Ichnos στο ", "Ichnos në ", "Ichnos a ")
    val prefix = prefixes.firstOrNull { label.startsWith(it) } ?: return label
    val name = label.removePrefix(prefix)
    val own = when (lang) {
        AppLanguage.GREEK -> "Ichnos στο "
        AppLanguage.ALBANIAN -> "Ichnos në "
        AppLanguage.ITALIAN -> "Ichnos a "
        else -> "Ichnos at "
    }
    return own + name
}
