package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A strike has to take the trains off the map, not just grey the stations.
 * The vehicle layer projects from the bundled timetable and knew nothing about
 * closures, so on the Hellenic Train strike day a rider would have seen the
 * suburban network greyed out with trains still running along it.
 */
class LineSuspensionTest {

    private val now = 1_793_319_000L
    private val window = 1_793_311_200L to 1_793_397_600L

    private fun closure(
        id: String = "c1",
        stations: List<String> = emptyList(),
        lines: List<String> = emptyList(),
        mode: ClosureMode = ClosureMode.CLOSED,
        status: String = "active",
    ) = StationClosure(
        id = id,
        stationIds = stations,
        lineIds = lines,
        mode = mode,
        startsAtEpochSeconds = window.first,
        endsAtEpochSeconds = window.second,
        status = status,
    )

    @Test fun a_closure_naming_lines_suspends_exactly_those_lines() {
        val c = listOf(closure(lines = listOf("A1", "IC1")))
        assertEquals(setOf("A1", "IC1"), StationClosures.suspendedLineIds(c, now))
        assertTrue(StationClosures.isLineSuspended(c, "A1", now))
        assertFalse(StationClosures.isLineSuspended(c, "M3", now))
    }

    @Test fun only_a_full_closure_suspends_a_line() {
        // Trains still run through a pass-through stop, and entry/exit-only are
        // station-access rules, not a service suspension.
        for (mode in listOf(ClosureMode.PASS_THROUGH, ClosureMode.EXIT_ONLY, ClosureMode.ENTRY_ONLY)) {
            val c = listOf(closure(lines = listOf("A1"), mode = mode))
            assertTrue(StationClosures.suspendedLineIds(c, now).isEmpty(), "$mode must not suspend")
        }
    }

    @Test fun a_draft_or_cancelled_closure_suspends_nothing() {
        for (status in listOf("draft", "cancelled")) {
            val c = listOf(closure(lines = listOf("A1"), status = status))
            assertTrue(StationClosures.suspendedLineIds(c, now).isEmpty(), status)
        }
    }

    @Test fun a_closure_outside_its_window_suspends_nothing() {
        val c = listOf(closure(lines = listOf("A1")))
        assertTrue(StationClosures.suspendedLineIds(c, window.first - 60).isEmpty())
        assertTrue(StationClosures.suspendedLineIds(c, window.second + 60).isEmpty())
    }

    @Test fun a_line_whose_every_station_is_closed_counts_as_suspended() {
        val lineStations = mapOf(
            "A1" to listOf("A1_ATH", "A1_AIR"),
            "M3" to listOf("M3_SYN", "M3_MON"),
        )
        val closed = setOf("A1_ATH", "A1_AIR")
        assertEquals(setOf("A1"), StationClosures.linesFullyClosed(lineStations, closed))
    }

    @Test fun a_line_with_one_stop_still_open_keeps_running() {
        // The replacement-bus case: it shares a struck interchange but not all
        // of its stops, so it must not be swept up in the strike.
        val lineStations = mapOf("VL1" to listOf("GR_LAR", "GR_VOL"))
        assertTrue(StationClosures.linesFullyClosed(lineStations, setOf("GR_LAR")).isEmpty())
    }

    @Test fun a_line_with_no_stations_is_never_suspended() {
        assertTrue(StationClosures.linesFullyClosed(mapOf("X" to emptyList()), setOf("A1_ATH")).isEmpty())
    }

    @Test fun nothing_closed_means_nothing_suspended() {
        val lineStations = mapOf("A1" to listOf("A1_ATH"))
        assertTrue(StationClosures.linesFullyClosed(lineStations, emptySet()).isEmpty())
    }

    @Test fun the_real_strike_shape_greys_the_stations_and_suspends_the_lines() {
        // One station-scoped closure, exactly what the 30/10 record looks like.
        val stations = listOf("A1_ATH", "A1_AIR", "GR_LAR", "GR_THE")
        val c = listOf(closure(stations = stations))
        assertEquals(stations.toSet(), StationClosures.closedStationIds(c, now))
        val lineStations = mapOf(
            "A1" to listOf("A1_ATH", "A1_AIR"),
            "IC1" to listOf("GR_LAR", "GR_THE"),
            "M3" to listOf("M3_SYN"),
        )
        val suspended = StationClosures.linesFullyClosed(lineStations, StationClosures.closedStationIds(c, now))
        assertEquals(setOf("A1", "IC1"), suspended)
        assertFalse("M3" in suspended, "the metro is not on strike")
    }
}
