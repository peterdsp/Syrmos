package com.syrmos.core.domain.station

import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationDepartureScopeTest {

    private fun st(id: String, name: String, lines: List<String>) =
        Station(id = id, name = name, nameEl = name, latitude = 37.9, longitude = 23.7, lineIds = lines)

    // Syntagma as the seed really holds it: two per-line rows, one complex.
    private val stations = listOf(
        st("M2_SYN", "Syntagma", listOf("M2")),
        st("M3_SYN", "Syntagma", listOf("M3")),
        st("M1_OMO", "Omonia", listOf("M1")),
    )
    private val syntagma = listOf("M2_SYN", "M3_SYN")

    private fun normalize(l: String) = if (l.startsWith("M3")) "M3" else l

    @Test fun a_complex_asks_every_line_at_its_own_stop() {
        // The reported bug: this used to be M2 only.
        assertEquals(
            listOf(
                StationDepartureScope.Target("M2_SYN", "M2"),
                StationDepartureScope.Target("M3_SYN", "M3"),
            ),
            StationDepartureScope.targets(syntagma, stations),
        )
    }

    @Test fun no_line_is_ever_asked_at_the_wrong_stop() {
        // Asking M2_SYN for M3 is what returned nothing.
        val targets = StationDepartureScope.targets(syntagma, stations)
        for (t in targets) {
            val station = stations.first { it.id == t.stationId }
            assertTrue(t.lineId in station.lineIds, "${t.lineId} is not served at ${t.stationId}")
        }
    }

    @Test fun a_line_filter_picks_the_stop_that_serves_it() {
        assertEquals(
            listOf(StationDepartureScope.Target("M3_SYN", "M3")),
            StationDepartureScope.targets(syntagma, stations, lineFilter = "M3", normalize = ::normalize),
        )
    }

    @Test fun the_line_filter_normalises_airport_variants() {
        val withAirport = stations + st("M3_AIR", "Airport", listOf("M3_AIR"))
        val targets = StationDepartureScope.targets(
            listOf("M3_SYN", "M3_AIR"), withAirport, lineFilter = "M3", normalize = ::normalize,
        )
        assertEquals(2, targets.size)
    }

    @Test fun a_station_outside_the_registry_still_answers_for_itself() {
        assertEquals(
            listOf(StationDepartureScope.Target("M1_OMO", "M1")),
            StationDepartureScope.targets(listOf("M1_OMO"), stations),
        )
    }

    @Test fun an_unknown_member_id_is_skipped_not_fatal() {
        val targets = StationDepartureScope.targets(listOf("M2_SYN", "GHOST"), stations)
        assertEquals(listOf(StationDepartureScope.Target("M2_SYN", "M2")), targets)
    }

    @Test fun a_stop_serving_two_lines_yields_both() {
        val hub = listOf(st("GR_ATH", "Athens", listOf("A1", "IC1")))
        assertEquals(2, StationDepartureScope.targets(listOf("GR_ATH"), hub).size)
    }

    @Test fun duplicate_member_ids_do_not_duplicate_targets() {
        assertEquals(2, StationDepartureScope.targets(syntagma + syntagma, stations).size)
    }

    @Test fun an_empty_complex_asks_nothing() {
        assertTrue(StationDepartureScope.targets(emptyList(), stations).isEmpty())
    }
}
