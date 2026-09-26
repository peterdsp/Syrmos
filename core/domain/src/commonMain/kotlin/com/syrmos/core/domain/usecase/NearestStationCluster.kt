package com.syrmos.core.domain.usecase

import com.syrmos.core.model.location.NearestStationResult

/**
 * The stations that make up the rider's nearest stop. Interchanges are separate
 * per-line station ids (Omonia has an M1 and an M2 id 40 m apart), so the
 * nearest single result carries one line only. The Home hero and its direction
 * board need every line at the physical stop, so this groups the nearest result
 * with the other results within the interchange radius. Line ids are never
 * enriched onto a station: each member keeps its own id and its own lines, and
 * departures are loaded per member (see the interchange rule in the memory
 * notes: 150 m to the REAL per-line station id).
 */
object NearestStationCluster {
    const val RADIUS_METERS = 150

    /**
     * The nearest result plus every other result whose distance from the rider
     * is within [radiusMeters] of the nearest one's, in distance order. Empty
     * when there are no results.
     */
    fun members(
        results: List<NearestStationResult>,
        radiusMeters: Int = RADIUS_METERS,
    ): List<NearestStationResult> {
        val sorted = results.sortedBy { it.distanceMeters }
        val nearest = sorted.firstOrNull() ?: return emptyList()
        return sorted.filter { it.distanceMeters - nearest.distanceMeters <= radiusMeters }
    }

    /** Every (stationId, lineId) pair to load departures for, without duplicates. */
    fun stops(results: List<NearestStationResult>, radiusMeters: Int = RADIUS_METERS): List<Pair<String, String>> =
        members(results, radiusMeters)
            .flatMap { m -> m.lineIds.map { m.stationId to it } }
            .distinct()
}
