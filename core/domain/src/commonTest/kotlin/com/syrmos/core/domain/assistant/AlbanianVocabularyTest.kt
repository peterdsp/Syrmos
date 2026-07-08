package com.syrmos.core.domain.assistant

import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Albanian is first-class (large Athens community). These pin that Albanian /
 * Latin station spellings resolve through the real `AssistantVocabularyBuilder`,
 * not just the hand-written test vocabularies elsewhere — the airport is the
 * highest-traffic case ("aeroport" must reach the airport station).
 */
class AlbanianVocabularyTest {

    private fun station(id: String, name: String, nameEl: String, lineIds: List<String>) =
        Station(id = id, name = name, nameEl = nameEl, latitude = 0.0, longitude = 0.0, lineIds = lineIds)

    private val parser: AthensTransitParser = run {
        val stations = listOf(
            station("AIR", "Airport", "Αεροδρόμιο", listOf("M3")),
            station("SYN", "Syntagma", "Σύνταγμα", listOf("M2", "M3")),
            station("PIR", "Piraeus", "Πειραιάς", listOf("M1", "M3")),
        )
        val lines = listOf(
            Line("M1", "Line 1", "Γραμμή 1", LineType.METRO, LineColor.GREEN, "PIR", "KIF", 24),
            Line("M2", "Line 2", "Γραμμή 2", LineType.METRO, LineColor.RED, "ANT", "ELL", 20),
            Line("M3", "Line 3", "Γραμμή 3", LineType.METRO, LineColor.BLUE, "AIR", "DIM", 21),
        )
        AthensTransitParser(AssistantVocabularyBuilder.build(stations, lines))
    }

    @Test
    fun builder_adds_albanian_airport_alias() {
        val airport = AssistantVocabularyBuilder.build(
            listOf(station("AIR", "Airport", "Αεροδρόμιο", listOf("M3"))),
            emptyList(),
        ).stations.single()
        assertTrue("Aeroport" in airport.names, "expected Albanian 'Aeroport' alias, got ${airport.names}")
    }

    @Test
    fun albanian_aeroport_resolves_to_airport_station() {
        // "jam te syntagma dua aeroport" — Albanian "I'm at Syntagma, I want airport".
        val intent = parser.parse("jam te syntagma dua aeroport")
        val trip = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("SYN", trip.fromStationId)
        assertEquals("AIR", trip.toStationId)
    }

    @Test
    fun albanian_how_do_i_go_to_airport() {
        val intent = parser.parse("si shkoj nga Pireas te aeroport")
        val trip = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("PIR", trip.fromStationId)
        assertEquals("AIR", trip.toStationId)
    }
}
