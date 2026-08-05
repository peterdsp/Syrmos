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
            val folded = AthensTransitParser.fold("${st.name} ${st.nameEl}")
            val extra = SQ_AND_LATIN_ALIASES.filterKeys { folded.contains(it) }.values.flatten()
            StationVocab(
                id = st.id,
                names = (listOfNotNull(st.name, st.nameEl, st.nameSq) + extra)
                    .filter { it.isNotBlank() }.distinct(),
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
                    add("linea $suffix")
                    when (line.id.first()) {
                        'M' -> add("metro $suffix")
                        'T' -> add("tram $suffix")
                    }
                    if (line.id.startsWith("DK")) {
                        addAll(listOf("odontotos", "rack railway", "scenic"))
                    }
                }
            }.filter { it.isNotBlank() }.distinct()
            LineVocab(id = line.id, aliases = aliases)
        }
        return AssistantVocabulary(stations = stationVocab, lines = lineVocab)
    }

    /**
     * Greeklish and extra SQ variants keyed by an accent-folded token from
     * the station's EN or EL name. The bundled nameSq covers most Albanian,
     * but alternate spellings and Greeklish still need these overrides.
     */
    private val SQ_AND_LATIN_ALIASES: Map<String, List<String>> = mapOf(
        "airport" to listOf("Aeroport", "Aeroporti", "Aeroporto"),
        "aerodromio" to listOf("Aeroport", "Aeroporti", "Aeroporto"),
        "piraeus" to listOf("Pireas", "Pireu", "Pireo"),
        "syntagma" to listOf("Sintagma"),
        "thessaloniki" to listOf("Selanik", "Selaniku", "Thesaloniki"),
        "athens" to listOf("Athina", "Athine", "Athinë"),
        "acropolis" to listOf("Akropoli", "Akropolis"),
        "omonia" to listOf("Omonoia"),
        "monastiraki" to listOf("Monastiraqi"),
        "nikaia" to listOf("Nikea", "Nikaja"),
        "victoria" to listOf("Viktoria"),
        "attiki" to listOf("Atiki"),
        "kifisia" to listOf("Kifissia"),
        "elliniko" to listOf("Helliniko"),
        "peristeri" to listOf("Peristeri"),
        "aigaleo" to listOf("Egaleo", "Aigaleo"),
        "larisa" to listOf("Larisis"),
        "patra" to listOf("Patra", "Patras"),
        "aghios" to listOf("Agios"),
        "agios" to listOf("Aghios"),
        "corinth" to listOf("Korinthi", "Korinthos", "Korinh"),
        "korinthos" to listOf("Corinth", "Korinthi"),
        "megara" to listOf("Megara"),
        "kiato" to listOf("Kiato", "Qiato"),
        "chalkida" to listOf("Halkidhe", "Halkida", "Chalkis"),
    )
}
