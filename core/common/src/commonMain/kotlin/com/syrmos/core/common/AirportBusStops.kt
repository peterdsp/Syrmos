package com.syrmos.core.common

/**
 * Ordering and naming for the stop list on an airport bus.
 *
 * The unified payload gives one `stops` array per LINE, not per run, and an
 * OASA line has several route codes: X97 publishes 5373 and 5375 towards the
 * airport and 5374 and 5376 back, 78 stops in total. Every stop carries its own
 * `routeCode`, `direction` and `sequence`, and the sequences restart at 1 for
 * each code.
 *
 * Sorting that array by `sequence` alone, which is what the bus sheet did,
 * interleaves four separate itineraries: sequence 1 appears four times, so the
 * rider saw "AIR TERMINAL (ALL DEPARTURES)" twice in a row and an order that
 * ran Elliniko, then the airport, then back into town. A bus runs exactly one
 * route code, so that code is the whole answer.
 */
object AirportBusStops {

    /** A stop as the clients need it here. Clients map their own model onto this. */
    data class Stop(
        val stopId: String,
        val name: String,
        val nameEl: String? = null,
        val routeCode: Int? = null,
        val direction: String? = null,
        val sequence: Int? = null,
    )

    /**
     * The itinerary of the run a vehicle is on: only that vehicle's route code,
     * in sequence order, with consecutive repeats collapsed.
     *
     * [routeCode] null (an older payload, or a vehicle with no code) falls back
     * to [direction], and failing that to every stop, so the sheet degrades to
     * the old behaviour instead of going blank.
     */
    fun itinerary(stops: List<Stop>, routeCode: Int?, direction: String? = null): List<Stop> {
        val scoped = when {
            routeCode != null && stops.any { it.routeCode == routeCode } ->
                stops.filter { it.routeCode == routeCode }
            !direction.isNullOrBlank() && stops.any { it.direction == direction } ->
                stops.filter { it.direction == direction }
            else -> stops
        }
        val ordered = scoped.sortedBy { it.sequence ?: Int.MAX_VALUE }
        return collapseConsecutive(ordered)
    }

    /**
     * Drop a stop that repeats the one before it. A circular run legitimately
     * starts and ends at the same terminal, so only ADJACENT repeats collapse;
     * the terminal still appears at both ends.
     */
    fun collapseConsecutive(stops: List<Stop>): List<Stop> {
        val out = mutableListOf<Stop>()
        for (s in stops) {
            val prev = out.lastOrNull()
            if (prev != null && prev.stopId == s.stopId) continue
            out.add(s)
        }
        return out
    }

    /**
     * The stop name to show. OASA publishes Greeklish ("EMP/KO PARKO
     * AERODROMIOY") in `stop_name` and the real Greek in `stop_name_el`
     * ("ΕΜΠ/ΚΟ ΠΑΡΚΟ ΑΕΡΟΔΡΟΜΙΟΥ"). Greek readers get the Greek; everyone else
     * gets the Latin form, which is at least readable to them.
     */
    fun displayName(stop: Stop, lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> stop.nameEl?.takeIf { it.isNotBlank() } ?: stop.name
        else -> stop.name.takeIf { it.isNotBlank() } ?: stop.nameEl.orEmpty()
    }
}
