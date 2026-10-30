package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Why a network-wide closure (a strike) must be entered STATION-scoped, with an
 * empty `lineIds`.
 *
 * [StationClosures.lineIdOfStation] reads the line off the station id prefix,
 * which holds in Athens (`M3_SYN` -> `M3`) and NOT on the national network,
 * where ids are geographic: `GR_LAR` -> `GR`, `EV_ALX` -> `EV`. A closure that
 * names its lines is then narrowed by [StationClosures.applies] to a line the
 * station never resolves to, and the station silently stays open.
 *
 * The web mirror avoids this by only accepting a prefix that is a known line id
 * (`lineOfStationId(stationId, knownLineIds)`), so the two implementations
 * disagree here. Recorded in docs/PARITY.md rather than changed mid-incident:
 * the production 30/10 closure is station-scoped, which both agree on.
 */
class ClosureLineScopeTrapTest {

    private val now = 1_793_319_000L

    private fun closure(lines: List<String>) = StationClosure(
        id = "strike",
        stationIds = listOf("A1_ATH", "GR_LAR", "EV_ALX"),
        lineIds = lines,
        mode = ClosureMode.CLOSED,
        startsAtEpochSeconds = 1_793_311_200L,
        endsAtEpochSeconds = 1_793_397_600L,
        status = "active",
    )

    @Test fun a_geographic_station_id_does_not_resolve_to_a_line() {
        assertEquals("GR", StationClosures.lineIdOfStation("GR_LAR"))
        assertEquals("EV", StationClosures.lineIdOfStation("EV_ALX"))
        // Neither is a real line id, which is the whole problem.
        assertEquals("M3", StationClosures.lineIdOfStation("M3_SYN"))
        assertNull(StationClosures.lineIdOfStation("NOPREFIX"))
    }

    @Test fun station_scoped_is_the_shape_that_closes_the_whole_network() {
        val c = listOf(closure(lines = emptyList()))
        assertEquals(
            setOf("A1_ATH", "GR_LAR", "EV_ALX"),
            StationClosures.closedStationIds(c, now),
        )
    }

    @Test fun naming_the_lines_silently_loses_every_national_station() {
        // Documents the trap. If this ever starts closing all three, the
        // implementations have converged and the comment above can go.
        val c = listOf(closure(lines = listOf("A1", "IC1", "AL1")))
        val closed = StationClosures.closedStationIds(c, now)
        assertTrue("A1_ATH" in closed, "Athens ids match their line prefix, so they close")
        assertFalse("GR_LAR" in closed, "geographic id resolves to GR, which is not in lineIds")
        assertFalse("EV_ALX" in closed, "geographic id resolves to EV, which is not in lineIds")
    }

    @Test fun a_line_scoped_closure_still_suspends_its_lines_for_the_vehicle_layer() {
        // suspendedLineIds reads lineIds directly, so it is unaffected by the
        // prefix heuristic. Both paths exist for this reason.
        val c = listOf(closure(lines = listOf("A1", "IC1")))
        assertEquals(setOf("A1", "IC1"), StationClosures.suspendedLineIds(c, now))
    }
}
