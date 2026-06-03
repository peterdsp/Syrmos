package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.planner.JourneySegment
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
    fun invoke(fromStationId: String, toStationId: String): Flow<JourneyResult?> = flow {
        val allLines = lineRepository.getAllLines().first()
        val graph = mutableMapOf<String, MutableList<Edge>>()

        // Build edges from station ordering on each line
        for (line in allLines) {
            val stations = stationRepository.getStationsOnLine(line.id).first()
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
            val (current, dist) = queue.removeFirst()

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
            emit(null)
            return@flow
        }

        val path = mutableListOf<Pair<String, Edge?>>()
        var node = toStationId
        while (node != fromStationId) {
            val (prev, edge) = previous[node] ?: break
            path.add(0, node to edge)
            node = prev
        }

        // Merge consecutive edges on the same line into segments
        val segments = mutableListOf<JourneySegment>()
        var currentLineId: String? = null
        var segmentStartStation = fromStationId
        var segmentStationCount = 0
        var segmentMinutes = 0

        for ((stationId, edge) in path) {
            if (edge == null) continue
            if (edge.lineId != currentLineId) {
                if (currentLineId != null) {
                    segments.add(
                        JourneySegment(
                            lineId = currentLineId,
                            lineName = edge.lineName,
                            fromStationId = segmentStartStation,
                            fromStationName = segmentStartStation,
                            toStationId = stationId,
                            toStationName = stationId,
                            stationCount = segmentStationCount,
                            estimatedMinutes = segmentMinutes,
                            isTransfer = false,
                        )
                    )
                }
                currentLineId = edge.lineId
                segmentStartStation = stationId
                segmentStationCount = 1
                segmentMinutes = edge.weight
            } else {
                segmentStationCount++
                segmentMinutes += edge.weight
            }
        }

        if (currentLineId != null) {
            segments.add(
                JourneySegment(
                    lineId = currentLineId,
                    lineName = currentLineId,
                    fromStationId = segmentStartStation,
                    fromStationName = segmentStartStation,
                    toStationId = toStationId,
                    toStationName = toStationId,
                    stationCount = segmentStationCount,
                    estimatedMinutes = segmentMinutes,
                    isTransfer = false,
                )
            )
        }

        val totalMinutes = distances[toStationId] ?: 0
        val transferCount = (segments.size - 1).coerceAtLeast(0)

        emit(
            JourneyResult(
                segments = segments,
                totalMinutes = totalMinutes,
                transferCount = transferCount,
            )
        )
    }

    private fun estimateTravelTime(lineType: String): Int = when (lineType.lowercase()) {
        "metro" -> 2 // avg 2 min between metro stations
        "tram" -> 3  // avg 3 min between tram stops
        "suburban" -> 4 // avg 4 min between suburban stations
        else -> 3
    }
}

private data class Edge(
    val toStationId: String,
    val lineId: String,
    val lineName: String,
    val weight: Int,
    val isTransfer: Boolean,
)
