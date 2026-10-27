package com.syrmos.core.domain.station

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals

class LineTitlesTest {
    private fun line(id: String, name: String, nameEl: String, type: LineType, a: String, b: String) =
        Line(id, name, nameEl, type, LineColor.BLUE, a, b, 0)

    private fun st(id: String, name: String, el: String) = Station(id, name, el, latitude = 0.0, longitude = 0.0, lineIds = listOf(id.substringBefore('_')))

    // Seed shapes: terminals in Latin on the line, abbreviated station names.
    private val m3 = line("M3", "Line 3", "Γραμμή 3", LineType.METRO, "Dimotiko Theatro", "Doukissis Plakentias")
    private val t6 = line("T6", "Tram T6", "Τραμ T6", LineType.TRAM, "Syntagma", "Pikrodafni")
    private val m1 = line("M1", "Line 1", "Γραμμή 1", LineType.METRO, "Piraeus", "Kifissia")
    private val stations = listOf(
        st("M3_DIM", "Dimotiko Theatro", "Δημοτικό Θέατρο"),
        st("M3_DOY", "Douk. Plakentias", "Δουκίσσης Πλακεντίας"),
        st("A1_PIR", "Piraeus", "Πειραιάς (Προαστιακός)"),
        st("M1_PIR", "Piraeus", "Πειραιάς"),
        st("M1_KIF", "Kifissia", "Κηφισιά"),
        st("T6_SYN", "Syntagma", "Σύνταγμα"),
        st("T6_PIK", "Pikrodafni", "Πικροδάφνη"),
    )

    @Test
    fun fourLanguages_m3_t6_m1() {
        val expected = mapOf(
            AppLanguage.GREEK to listOf(
                "Γραμμή 3 (Δημοτικό Θέατρο - Δουκίσσης Πλακεντίας)",
                "Τραμ T6 (Σύνταγμα - Πικροδάφνη)",
                "Γραμμή 1 (Πειραιάς - Κηφισιά)",
            ),
            AppLanguage.ENGLISH to listOf(
                "Line 3 (Dimotiko Theatro - Doukissis Plakentias)",
                "Tram T6 (Syntagma - Pikrodafni)",
                "Line 1 (Piraeus - Kifissia)",
            ),
            AppLanguage.ALBANIAN to listOf(
                "Linja 3 (Dimotiko Theatro - Doukissis Plakentias)",
                "Tram T6 (Syntagma - Pikrodafni)",
                "Linja 1 (Piraeus - Kifissia)",
            ),
            AppLanguage.ITALIAN to listOf(
                "Linea 3 (Dimotiko Theatro - Doukissis Plakentias)",
                "Tram T6 (Syntagma - Pikrodafni)",
                "Linea 1 (Piraeus - Kifissia)",
            ),
        )
        for ((lang, titles) in expected) {
            assertEquals(titles, listOf(m3, t6, m1).map { LineTitles.withTerminals(it, lang, stations) }, lang.name)
        }
        assertEquals("Γραμμή 3", LineTitles.label(m3, AppLanguage.GREEK))
        assertEquals("Δημοτικό Θέατρο - Δουκίσσης Πλακεντίας", LineTitles.terminals(m3, AppLanguage.GREEK, stations))
    }

    @Test
    fun withoutStations_fallsBackToSeedTerminals() {
        assertEquals("Γραμμή 3 (Dimotiko Theatro - Doukissis Plakentias)", LineTitles.withTerminals(m3, AppLanguage.GREEK, emptyList()))
    }

    @Test
    fun abbreviationMatch_prefersTheLinesOwnStation() {
        assertEquals("M3_DOY", LineTitles.matchTerminal("Doukissis Plakentias", "M3", stations)?.id)
        assertEquals("M1_PIR", LineTitles.matchTerminal("Piraeus", "M1", stations)?.id)
        assertEquals(null, LineTitles.matchTerminal("Nowhere", "M1", stations))
    }
}
