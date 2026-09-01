package com.syrmos.core.domain.interchange

import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station

/** A line serving the same physical hub, paired with the station id to open on it. */
data class InterchangeTarget(val line: Line, val stationId: String)

/**
 * Resolves the other lines serving the same physical hub as a station, each
 * paired with the station id to open on that line, nearest hub first.
 *
 * Ported from iOS `SyrmosData.interchangeTargets` (PR #48). Computed purely by
 * PROXIMITY across every line: any line with a stop within [RADIUS_METERS] of
 * the hub is a real transfer. It never reads the hub station's stored
 * `lineIds` (doing so creates invalid (stationId, lineId) pairs and phantom
 * departures, the trap the iOS work hit over three review passes); instead it
 * resolves each candidate line's OWN nearest station id. That keeps it complete
 * for every region with no hand-maintained table, and correct even when a hub's
 * per-line ids use different suffixes (e.g. M3_AER vs A1_AIR).
 *
 * Only operational, scheduled lines are offered, so a tapped transfer always
 * opens a boardable timetable (dropping suspended lines like DK1 and
 * scheduleless shuttles like X3/2X that would open an empty timetable).
 */
object InterchangeResolver {

    const val RADIUS_METERS = 150

    fun resolve(
        hubLatitude: Double,
        hubLongitude: Double,
        currentLineId: String,
        lines: List<Line>,
        stationsByLine: Map<String, List<Station>>,
        hasSchedule: (lineId: String) -> Boolean,
    ): List<InterchangeTarget> {
        val targets = mutableListOf<Triple<Line, String, Int>>()
        for (line in lines) {
            if (line.id == currentLineId || !line.isOperational || !hasSchedule(line.id)) continue
            val nearest = stationsByLine[line.id].orEmpty().minByOrNull {
                distanceInMeters(hubLatitude, hubLongitude, it.latitude, it.longitude)
            } ?: continue
            val meters = distanceInMeters(hubLatitude, hubLongitude, nearest.latitude, nearest.longitude)
            if (meters <= RADIUS_METERS) targets += Triple(line, nearest.id, meters)
        }
        return targets.sortedBy { it.third }.map { InterchangeTarget(it.first, it.second) }
    }
}
