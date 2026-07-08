package com.syrmos.core.domain.assistant

import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station

/**
 * Builds the [AssistantVocabulary] Ariadne matches against from the app's own
 * station and line data, so the parser stays in sync with whatever the bundle
 * ships. Pure and offline.
 */
object AssistantVocabularyBuilder {
    fun build(stations: List<Station>, lines: List<Line>): AssistantVocabulary {
        val stationVocab = stations.map { st ->
            // Albanian is first-class: the bundled Station only carries EN + EL
            // names, so an Albanian speaker typing "aeroport" (not "Airport")
            // would fall through to fuzzy matching. Augment key stations with
            // Albanian and common Latin/Greeklish spellings so SQ input resolves
            // as reliably as EN/EL. Matched by token so it works regardless of
            // the station's exact display name.
            val folded = AthensTransitParser.fold("${st.name} ${st.nameEl}")
            val extra = SQ_AND_LATIN_ALIASES.filterKeys { folded.contains(it) }.values.flatten()
            StationVocab(
                id = st.id,
                names = (listOf(st.name, st.nameEl) + extra).filter { it.isNotBlank() }.distinct(),
                lineIds = st.lineIds,
            )
        }
        val lineVocab = lines.map { line ->
            val aliases = buildList {
                add(line.id)
                if (line.name.isNotBlank()) add(line.name)
                if (line.nameEl.isNotBlank()) add(line.nameEl)
                // "line 2", "γραμμή 2", "metro 2" / "tram 6" from the id suffix.
                val suffix = line.id.dropWhile { !it.isDigit() }
                if (suffix.isNotEmpty()) {
                    add("line $suffix")
                    add("γραμμη $suffix")
                    add("linja $suffix")
                    when (line.id.first()) {
                        'M' -> add("metro $suffix")
                        'T' -> add("tram $suffix")
                    }
                }
            }.filter { it.isNotBlank() }.distinct()
            LineVocab(id = line.id, aliases = aliases)
        }
        return AssistantVocabulary(stations = stationVocab, lines = lineVocab)
    }

    /**
     * Albanian and common Latin/Greeklish station spellings, keyed by an
     * accent-folded token that appears in the station's EN or EL name. Kept to
     * confident, real variants (Albanian speakers are a large Athens community,
     * so this is core coverage, not a nicety). The proper long-term fix is a
     * `nameSq` on the bundled Station data; this closes the highest-traffic
     * gaps now, starting with the airport.
     */
    private val SQ_AND_LATIN_ALIASES: Map<String, List<String>> = mapOf(
        "airport" to listOf("Aeroport", "Aeroporti"),        // SQ airport / definite
        "aerodromio" to listOf("Aeroport", "Aeroporti"),
        "piraeus" to listOf("Pireas", "Pireu"),      // Greeklish / SQ
        "syntagma" to listOf("Sintagma"),            // common Latin variant
    )
}
