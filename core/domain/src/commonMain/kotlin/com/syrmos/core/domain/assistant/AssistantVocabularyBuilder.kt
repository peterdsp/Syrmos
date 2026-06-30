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
            StationVocab(
                id = st.id,
                names = listOf(st.name, st.nameEl).filter { it.isNotBlank() }.distinct(),
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
}
