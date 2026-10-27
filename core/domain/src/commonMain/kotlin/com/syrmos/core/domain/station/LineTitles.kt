package com.syrmos.core.domain.station

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One localized line title for every screen ("Γραμμή 3 (Δημοτικό Θέατρο -
 * Δουκίσσης Πλακεντίας)", "Line 3 (Dimotiko Theatro - Doukissis Plakentias)").
 *
 * The seed's line terminals are Latin, so in Greek the parenthesised terminals
 * are resolved to the line's own terminal stations (per-line ids such as
 * `M3_DOY` first) and shown with their Greek station names, keeping the localized
 * line label ("Γραμμή 3", "Τραμ T6"). Seed station names may be abbreviated
 * ("Douk. Plakentias" for "Doukissis Plakentias"), which [matchTerminal] accepts.
 * When the stations are not loaded or a terminal does not resolve, the seed
 * terminal is shown exactly as before.
 */
object LineTitles {
    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    /** Called once the station table is available (app start). */
    fun setStations(list: List<Station>) {
        if (list.isNotEmpty()) _stations.value = list
    }

    /** The localized line label: "Γραμμή 3", "Linea 3", "Tram T6". */
    fun label(line: Line, lang: AppLanguage): String {
        if (lang == AppLanguage.GREEK && line.nameEl.isNotBlank()) return line.nameEl
        if (lang == AppLanguage.ITALIAN) return line.name
            .replace("Line ", "Linea ")
            .replace("Suburban ", "Suburbano ")
        if (lang == AppLanguage.ALBANIAN) return line.name
            .replace("Line ", "Linja ")
            .replace("Suburban ", "Periferik ")
        return line.name
    }

    fun withTerminals(line: Line, lang: AppLanguage): String = withTerminals(line, lang, _stations.value)

    /**
     * "Γραμμή 3 (Δημοτικό Θέατρο - Δουκίσσης Πλακεντίας)". In Greek each terminal is
     * the Greek name of the line's terminal station; when it does not resolve (or
     * the stations are not loaded) the seed terminal is used, as before. Other
     * languages keep the seed terminals, which are already Latin.
     */
    fun withTerminals(line: Line, lang: AppLanguage, stations: List<Station>): String {
        val label = label(line, lang)
        if (label.contains(" (")) return label
        if (line.terminalA.isBlank() && line.terminalB.isBlank()) return label
        return "$label (${terminals(line, lang, stations)})"
    }

    fun terminals(line: Line, lang: AppLanguage): String = terminals(line, lang, _stations.value)

    /** "Δημοτικό Θέατρο - Δουκίσσης Πλακεντίας" in Greek, the seed terminals otherwise. */
    fun terminals(line: Line, lang: AppLanguage, stations: List<Station>): String {
        fun term(name: String): String = stationName(name, line.id, lang, stations)
        return "${term(line.terminalA)} - ${term(line.terminalB)}"
    }

    fun stationName(name: String, lineId: String?, lang: AppLanguage): String =
        stationName(name, lineId, lang, _stations.value)

    /**
     * A departure destination or terminal in the reader's language: the matched
     * station's Greek name in Greek ("Syntagma" -> "Σύνταγμα", "Airport" ->
     * "Αεροδρόμιο"), the given (seed or already localized) name otherwise or when
     * nothing matches.
     */
    fun stationName(name: String, lineId: String?, lang: AppLanguage, stations: List<Station>): String {
        if (lang != AppLanguage.GREEK || name.isBlank()) return name
        return matchTerminal(name, lineId, stations)?.nameEl?.takeIf { it.isNotBlank() } ?: name
    }

    /**
     * The station a terminal name refers to: exact name first, then an
     * abbreviation match token by token ("Douk." prefixes "Doukissis"). Stations
     * whose per-line id belongs to [lineId] win over namesakes on other lines.
     */
    fun matchTerminal(terminal: String, lineId: String?, stations: List<Station>): Station? {
        val wanted = terminal.trim().lowercase()
        if (wanted.isEmpty()) return null
        val ordered = if (lineId == null) stations
        else stations.sortedBy { if (it.id.startsWith("${lineId}_")) 0 else 1 }
        ordered.firstOrNull { it.name.trim().lowercase() == wanted }?.let { return it }
        val target = wanted.split(' ').filter { it.isNotBlank() }
        return ordered.firstOrNull { st ->
            val tokens = st.name.lowercase().split(' ').filter { it.isNotBlank() }
            tokens.size == target.size && tokens.zip(target).all { (x, y) ->
                val stem = x.trimEnd('.')
                stem.isNotEmpty() && y.startsWith(stem)
            }
        }
    }
}

/** Shared localized line label; replaces the per-screen copies. */
fun Line.localizedName(lang: AppLanguage): String = LineTitles.label(this, lang)

/** Shared "label (terminal - terminal)" title with Greek terminal names in Greek. */
fun Line.localizedNameWithTerminals(lang: AppLanguage): String = LineTitles.withTerminals(this, lang)

/** Shared "terminal - terminal" subtitle, Greek station names in Greek. */
fun Line.localizedTerminals(lang: AppLanguage): String = LineTitles.terminals(this, lang)

/** The line's terminal in [lang]: terminalB when [outbound], else terminalA. */
fun Line.localizedTerminal(outbound: Boolean, lang: AppLanguage): String =
    LineTitles.stationName(if (outbound) terminalB else terminalA, id, lang)
