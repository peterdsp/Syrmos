package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.planner.JourneySegment
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Plans a journey between two stations across the Athens rail network.
 *
 * Builds an in-memory graph where nodes are stations and edges are:
 * 1. Consecutive stations on the same line (weight = estimated travel time)
 * 2. Transfer edges at interchange stations (weight = walking time, typically 3-5 min)
 *
 * Uses Dijkstra for minimum total time. BFS variant available for minimum transfers.
 */
class PlanJourneyUseCase(
    private val stationRepository: StationRepositoryImpl,
    private val lineRepository: LineRepositoryImpl,
) {
    /**
     * Fastest route across the whole network.
     *
     * Only operational lines are routable. Track that is built but not open (e.g.
     * Thessaloniki Line 2 until the Kalamaria extension opens) renders on the map
     * because it is real, but routing a journey through it would hand the user a
     * plan they cannot travel, which is worse than no plan at all.
     */
    fun invoke(fromStationId: String, toStationId: String): Flow<JourneyResult?> = flow {
        emit(compute(fromStationId, toStationId, operationalLines()))
    }

    private suspend fun operationalLines(): List<Line> =
        lineRepository.getAllLines().first().filter { it.isOperational }

    /**
     * Metro-only alternative (M1/M2/M3), the sheltered option Ariadne can offer
     * when the fastest route uses the tram or a surface line and the weather
     * makes staying underground worth a few extra minutes. Null when there's no
     * all-metro path between the two stations.
     */
    fun metroOnly(fromStationId: String, toStationId: String): Flow<JourneyResult?> = flow {
        val metro = operationalLines().filter { it.type == LineType.METRO }
        emit(compute(fromStationId, toStationId, metro))
    }

    private suspend fun compute(
        fromStationId: String,
        toStationId: String,
        allLines: List<Line>,
    ): JourneyResult? {
        val graph = mutableMapOf<String, MutableList<Edge>>()
        // id -> display name, so emitted segments carry real station names
        // instead of raw IDs.
        val stationNames = mutableMapOf<String, String>()

        // Build edges from station ordering on each line
        for (line in allLines) {
            val stations = stationRepository.getStationsOnLine(line.id).first()
            stations.forEach { stationNames[it.id] = it.name }
            for (i in 0 until stations.size - 1) {
                val a = stations[i]
                val b = stations[i + 1]
                val travelTime = estimateTravelTime(line.type.name)
                val edge = Edge(
                    toStationId = b.id,
                    lineId = line.id,
                    lineName = line.name,
                    weight = travelTime,
                    isTransfer = false,
                )
                val reverseEdge = Edge(
                    toStationId = a.id,
                    lineId = line.id,
                    lineName = line.name,
                    weight = travelTime,
                    isTransfer = false,
                )
                graph.getOrPut(a.id) { mutableListOf() }.add(edge)
                graph.getOrPut(b.id) { mutableListOf() }.add(reverseEdge)
            }
        }

        // Add transfer edges at interchange stations
        val interchanges = stationRepository.getInterchangeStations().first()
        for (station in interchanges) {
            if (station.lineIds.size < 2) continue
            for (i in station.lineIds.indices) {
                for (j in i + 1 until station.lineIds.size) {
                    val transferTime = 3 // default walking time
                    // Transfer from line i to line j at this station
                    // We model transfers as edges within the same physical station
                    // but between different line contexts
                    graph.getOrPut(station.id) { mutableListOf() }
                }
            }
        }

        // Dijkstra
        val distances = mutableMapOf<String, Int>()
        val previous = mutableMapOf<String, Pair<String, Edge>>()
        val visited = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, Int>>() // stationId to distance

        distances[fromStationId] = 0
        queue.add(fromStationId to 0)

        while (queue.isNotEmpty()) {
            queue.sortBy { it.second }
            val (current, dist) = queue.removeAt(0)

            if (current in visited) continue
            visited.add(current)

            if (current == toStationId) break

            val edges = graph[current] ?: continue
            for (edge in edges) {
                val newDist = dist + edge.weight
                if (newDist < (distances[edge.toStationId] ?: Int.MAX_VALUE)) {
                    distances[edge.toStationId] = newDist
                    previous[edge.toStationId] = current to edge
                    queue.add(edge.toStationId to newDist)
                }
            }
        }

        // Reconstruct path
        if (toStationId !in previous && fromStationId != toStationId) {
            return null
        }

        val path = mutableListOf<Pair<String, Edge?>>()
        var node = toStationId
        while (node != fromStationId) {
            val (prev, edge) = previous[node] ?: break
            path.add(0, node to edge)
            node = prev
        }

        val segments = reconstructSegments(path, fromStationId, toStationId, stationNames)

        val totalMinutes = distances[toStationId] ?: 0
        val transferCount = (segments.size - 1).coerceAtLeast(0)

        return JourneyResult(
            segments = segments,
            totalMinutes = totalMinutes,
            transferCount = transferCount,
        )
    }

    private fun estimateTravelTime(lineType: String): Int = when (lineType.lowercase()) {
        "metro" -> 2 // avg 2 min between metro stations
        "tram" -> 3  // avg 3 min between tram stops
        "suburban" -> 4 // avg 4 min between suburban stations
        else -> 3
    }
}

internal data class Edge(
    val toStationId: String,
    val lineId: String,
    val lineName: String,
    val weight: Int,
    val isTransfer: Boolean,
)

/**
 * Merges a Dijkstra path into line segments. Extracted as a pure function so the
 * reconstruction rules (keep the origin, close a line change at the interchange,
 * carry real station names) can be unit tested without a database.
 *
 * Each `path` element is (stationId, edge) where `edge` LEADS TO stationId and
 * starts at the previous element's station (the origin for the very first). So a
 * line change at element k must close the running segment at the PREVIOUS station
 * (the interchange), not at the station one stop past it.
 */
internal fun reconstructSegments(
    path: List<Pair<String, Edge?>>,
    fromStationId: String,
    toStationId: String,
    stationNames: Map<String, String>,
): List<JourneySegment> {
    val segments = mutableListOf<JourneySegment>()
    fun nameOf(id: String): String = stationNames[id] ?: id
    var currentLineId: String? = null
    var currentLineName = ""
    var segmentStartStation = fromStationId
    var segmentStationCount = 0
    var segmentMinutes = 0
    var prevStationId = fromStationId

    for ((stationId, edge) in path) {
        if (edge == null) { prevStationId = stationId; continue }
        when {
            currentLineId == null -> {
                // First segment keeps the origin as its start (the old code
                // overwrote it with the second station, dropping the origin).
                currentLineId = edge.lineId
                currentLineName = edge.lineName
                segmentStationCount = 1
                segmentMinutes = edge.weight
            }
            edge.lineId != currentLineId -> {
                segments.add(
                    JourneySegment(
                        lineId = currentLineId,
                        lineName = currentLineName,
                        fromStationId = segmentStartStation,
                        fromStationName = nameOf(segmentStartStation),
                        toStationId = prevStationId,
                        toStationName = nameOf(prevStationId),
                        stationCount = segmentStationCount,
                        estimatedMinutes = segmentMinutes,
                        isTransfer = false,
                    )
                )
                currentLineId = edge.lineId
                currentLineName = edge.lineName
                segmentStartStation = prevStationId
                segmentStationCount = 1
                segmentMinutes = edge.weight
            }
            else -> {
                segmentStationCount++
                segmentMinutes += edge.weight
            }
        }
        prevStationId = stationId
    }

    if (currentLineId != null) {
        segments.add(
            JourneySegment(
                lineId = currentLineId,
                lineName = currentLineName,
                fromStationId = segmentStartStation,
                fromStationName = nameOf(segmentStartStation),
                toStationId = toStationId,
                toStationName = nameOf(toStationId),
                stationCount = segmentStationCount,
                estimatedMinutes = segmentMinutes,
                isTransfer = false,
            )
        )
    }

    return segments
}
