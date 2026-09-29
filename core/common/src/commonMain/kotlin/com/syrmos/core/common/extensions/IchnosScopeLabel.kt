package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage

/** The Ichnos station prefix ("Ichnos at ", "Ichnos στο ", ...) in the reader's language. */
private fun ichnosStationPrefix(lang: AppLanguage): String = when (lang) {
    AppLanguage.GREEK -> "Ichnos στο "
    AppLanguage.ALBANIAN -> "Ichnos në "
    AppLanguage.ITALIAN -> "Ichnos a "
    else -> "Ichnos at "
}

/**
 * An Ichnos report carries the label of the place it was made at, built in
 * the reporter's language when it was submitted ("Ichnos at Florina") and
 * served back verbatim. Until the reader can resolve the station id, this puts
 * the station-form prefix back into the reader's language; the place name stays
 * as the reporter wrote it. Any other form (a line or train context) is
 * returned unchanged. Twin of `localizedScopeLabel` in iOS Localization.swift.
 *
 * Prefer [resolveIchnosScopeLabel] where a station lookup is available: it
 * rebuilds the whole label, place name included, in the reader's language.
 */
fun localizedScopeLabel(label: String, lang: AppLanguage): String {
    val prefixes = listOf("Ichnos at ", "Ichnos στο ", "Ichnos në ", "Ichnos a ")
    val prefix = prefixes.firstOrNull { label.startsWith(it) } ?: return label
    return ichnosStationPrefix(lang) + label.removePrefix(prefix)
}

/**
 * The permanent, id-based station label. When the reader's client can resolve
 * the report's station id to a name in its own seed, the whole label (prefix and
 * place name) is built in the reader's language, independent of the reporter's
 * language. Twin of `ichnosStationLabel` in iOS Localization.swift.
 */
fun ichnosStationLabel(localizedStationName: String, lang: AppLanguage): String =
    ichnosStationPrefix(lang) + localizedStationName

/**
 * Resolves an Ichnos report label for the reader. If [localizedStationName]
 * returns a name for the report's scope id (a known station in the reader's
 * seed), the label is rebuilt fully in the reader's language; otherwise it
 * falls back to [localizedScopeLabel], which only re-localizes the prefix.
 * Non-station scopes (a line or train context) resolve to null and pass through
 * unchanged. Twin of `resolvedScopeLabel` in iOS Localization.swift.
 */
fun resolveIchnosScopeLabel(
    scopeId: String,
    scopeLabel: String,
    lang: AppLanguage,
    localizedStationName: (String) -> String?,
): String {
    val name = localizedStationName(scopeId)
    return if (name != null) ichnosStationLabel(name, lang) else localizedScopeLabel(scopeLabel, lang)
}
