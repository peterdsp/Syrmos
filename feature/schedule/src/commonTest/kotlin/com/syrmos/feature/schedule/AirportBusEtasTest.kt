package com.syrmos.feature.schedule

import com.syrmos.core.network.OasaAirportBusService.OasaAirportBusResponse
import com.syrmos.core.network.OasaAirportBusService.OasaBusArrival
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mirrors iOS AirportServiceTests and web airport-buses.test.js so all three
 * clients reduce the /api/oasa-airport-buses feed identically.
 */
class AirportBusEtasTest {

    private fun response(vararg arrivals: OasaBusArrival) =
        OasaAirportBusResponse(updatedAt = "2026-09-03T21:42:32Z", vehicles = emptyList(), airportArrivals = arrivals.toList())

    @Test
    fun reduceGroupsSortsAndClampsEtas() {
        val live = AirportBusEtas.reduce(response(
            OasaBusArrival(lineId = "X95", minutesAway = 19),
            OasaBusArrival(lineId = "X95", minutesAway = 5),
            OasaBusArrival(lineId = "X93", minutesAway = -3), // clamps to 0
            OasaBusArrival(lineId = "", minutesAway = 4),      // dropped: no line
        ))
        assertEquals(listOf(5, 19), live.etasByLine["X95"], "ETAs sorted ascending")
        assertEquals(5, live.soonest("X95"), "soonest wins")
        assertEquals(0, live.soonest("X93"), "negative ETA clamps to 0")
        assertNull(live.soonest("X97"), "untracked line absent")
    }

    @Test
    fun reduceHandlesNullAndEmptyFeed() {
        assertTrue(AirportBusEtas.reduce(null).isEmpty)
        assertTrue(AirportBusEtas.reduce(response()).isEmpty)
    }
}
