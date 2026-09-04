package com.syrmos.feature.schedule

import com.syrmos.core.network.OasaAirportBusService

/**
 * Soonest-first live ETAs per airport express line (X93/X95/X96/X97).
 *
 * The Pi's oasa-airport-bus-watcher polls OASA Telematics getStopArrivals for the
 * airport stop (10705) and serves real per-vehicle ETAs at /api/oasa-airport-buses.
 * `minutesAway` is OASA's own btime2 estimate for a tracked bus to reach the
 * airport, so the soonest per line is genuinely the next X-bus a rider can board
 * there - a real LIVE source, never a timetable dressed up as live.
 *
 * Mirrors iOS AirportBusService.reduce and web reduceAirportBuses so the three
 * clients agree on the transform.
 */
data class LiveAirportBuses(val etasByLine: Map<String, List<Int>>) {
    /** The next tracked bus of [line] reaching the airport stop, if any. */
    fun soonest(line: String): Int? = etasByLine[line]?.firstOrNull()

    val isEmpty: Boolean get() = etasByLine.isEmpty()

    companion object {
        val EMPTY = LiveAirportBuses(emptyMap())
    }
}

object AirportBusEtas {
    /**
     * Collapse the raw feed into soonest-first ETAs per line. Negative ETAs clamp
     * to 0 (bus at the stop). Lines with no tracked vehicle are absent. A null
     * response (offline / fetch failure) yields [LiveAirportBuses.EMPTY].
     */
    fun reduce(response: OasaAirportBusService.OasaAirportBusResponse?): LiveAirportBuses {
        if (response == null) return LiveAirportBuses.EMPTY
        val byLine = HashMap<String, MutableList<Int>>()
        for (arrival in response.airportArrivals) {
            if (arrival.lineId.isBlank()) continue
            byLine.getOrPut(arrival.lineId) { mutableListOf() }.add(maxOf(0, arrival.minutesAway))
        }
        return LiveAirportBuses(byLine.mapValues { (_, etas) -> etas.sorted() })
    }
}
