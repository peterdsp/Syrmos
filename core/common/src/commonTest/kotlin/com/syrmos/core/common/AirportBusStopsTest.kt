package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Built from the real X97 payload: four route codes (5373 and 5375 towards the
 * airport, 5374 and 5376 back), 78 stops, sequences restarting at 1 per code.
 */
class AirportBusStopsTest {

    private fun s(id: String, name: String, code: Int, dir: String, seq: Int, el: String? = null) =
        AirportBusStops.Stop(id, name, el, code, dir, seq)

    // Two of the four real X97 itineraries, interleaved exactly as the payload
    // delivers them.
    private val x97 = listOf(
        s("OASA_1", "AIR TERMINAL (ALL DEPARTURES)", 5373, "to_airport", 1, "ΚΤΙΡΙΟ ΑΝΑΧΩΡΗΣΕΩΝ"),
        s("OASA_9", "ST. ELLHNIKO", 5374, "from_airport", 1, "ΣΤ. ΕΛΛΗΝΙΚΟ"),
        s("OASA_2", "EMP/KO PARKO AERODROMIOY", 5373, "to_airport", 2, "ΕΜΠ/ΚΟ ΠΑΡΚΟ ΑΕΡΟΔΡΟΜΙΟΥ"),
        s("OASA_8", "TROXAIA", 5374, "from_airport", 2, "ΤΡΟΧΑΙΑ"),
        s("OASA_3", "KTIRIO DIOIKHSHS", 5373, "to_airport", 3),
        s("OASA_7", "ANTHEON", 5374, "from_airport", 3),
    )

    @Test fun a_vehicle_sees_only_its_own_route_code_in_order() {
        val run = AirportBusStops.itinerary(x97, routeCode = 5374)
        assertEquals(listOf("ST. ELLHNIKO", "TROXAIA", "ANTHEON"), run.map { it.name })
    }

    @Test fun the_other_direction_is_a_different_itinerary() {
        val run = AirportBusStops.itinerary(x97, routeCode = 5373)
        assertEquals(
            listOf("AIR TERMINAL (ALL DEPARTURES)", "EMP/KO PARKO AERODROMIOY", "KTIRIO DIOIKHSHS"),
            run.map { it.name },
        )
    }

    @Test fun no_stop_is_ever_listed_twice_in_a_row() {
        // This is the reported symptom: "AIR TERMINAL (ALL DEPARTURES)" twice.
        for (code in listOf(5373, 5374)) {
            val names = AirportBusStops.itinerary(x97, code).map { it.stopId }
            assertEquals(names.size, names.toSet().size, "code $code repeated a stop")
        }
    }

    @Test fun a_circular_run_keeps_its_terminal_at_both_ends() {
        // X97 code 5373 really does start and end at the airport terminal, so
        // only adjacent repeats collapse.
        val circular = listOf(
            s("T", "AIR TERMINAL", 5373, "to_airport", 1),
            s("A", "ANTHEON", 5373, "to_airport", 2),
            s("T", "AIR TERMINAL", 5373, "to_airport", 3),
        )
        assertEquals(listOf("T", "A", "T"), AirportBusStops.itinerary(circular, 5373).map { it.stopId })
    }

    @Test fun an_adjacent_repeat_collapses() {
        val dupes = listOf(
            s("T", "AIR TERMINAL", 5373, "to_airport", 1),
            s("T", "AIR TERMINAL", 5373, "to_airport", 1),
            s("A", "ANTHEON", 5373, "to_airport", 2),
        )
        assertEquals(listOf("T", "A"), AirportBusStops.itinerary(dupes, 5373).map { it.stopId })
    }

    @Test fun an_unknown_route_code_falls_back_to_the_direction() {
        val run = AirportBusStops.itinerary(x97, routeCode = 9999, direction = "from_airport")
        assertEquals(listOf("ST. ELLHNIKO", "TROXAIA", "ANTHEON"), run.map { it.name })
    }

    @Test fun no_code_and_no_direction_still_lists_something() {
        // Degrade to the old behaviour rather than render an empty sheet.
        assertEquals(x97.size, AirportBusStops.itinerary(x97, null, null).size)
    }

    @Test fun greek_readers_get_the_greek_stop_name() {
        val stop = x97.first { it.name == "TROXAIA" }
        assertEquals("ΤΡΟΧΑΙΑ", AirportBusStops.displayName(stop, AppLanguage.GREEK))
        assertEquals("TROXAIA", AirportBusStops.displayName(stop, AppLanguage.ENGLISH))
    }

    @Test fun a_missing_greek_name_falls_back_to_the_latin_one() {
        val stop = x97.first { it.name == "ANTHEON" }
        assertEquals("ANTHEON", AirportBusStops.displayName(stop, AppLanguage.GREEK))
    }

    @Test fun every_language_gets_a_non_blank_name() {
        for (lang in AppLanguage.entries) {
            for (stop in x97) {
                assertTrue(AirportBusStops.displayName(stop, lang).isNotBlank(), "$lang ${stop.stopId}")
            }
        }
    }
}
