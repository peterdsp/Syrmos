package com.syrmos.core.domain.station

import com.syrmos.core.model.transit.Station

/**
 * Which (stop, line) pairs a "departures from X" question has to ask about.
 *
 * Boarding stops in this repo are per line: Syntagma is `M2_SYN` AND `M3_SYN`,
 * Athens is five ids. Resolving a rider's "Syntagma" lands on exactly one of
 * those rows, so asking that one id for every line the complex serves returns
 * nothing for the others, and Ariadne answered "next train from Syntagma" with
 * four M2 departures and no M3 at all. Each line has to be asked at its OWN
 * stop id.
 *
 * Membership comes from the reviewed `StationComplexRepository`, never from
 * name similarity or proximity: see that class for why.
 */
object StationDepartureScope {

    data class Target(val stationId: String, val lineId: String)

    /**
     * [memberStopIds] are the complex's boarding stops (or just the resolved
     * station when the registry does not claim it). [lineFilter] keeps only
     * that line, for "next M3 from Syntagma"; it is compared through
     * [normalize] so `M3_AIR` matches `M3`.
     *
     * Returns one target per real (stop, line) pair, in member order, with no
     * duplicates.
     */
    fun targets(
        memberStopIds: List<String>,
        stations: List<Station>,
        lineFilter: String? = null,
        normalize: (String) -> String = { it },
    ): List<Target> {
        val wanted = lineFilter?.let(normalize)
        val byId = stations.associateBy { it.id }
        val out = LinkedHashSet<Target>()
        for (stopId in memberStopIds) {
            val station = byId[stopId] ?: continue
            for (lineId in station.lineIds) {
                if (wanted != null && normalize(lineId) != wanted) continue
                out.add(Target(stopId, lineId))
            }
        }
        return out.toList()
    }
}
