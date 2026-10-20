package com.syrmos.core.domain.usecase

import com.syrmos.core.common.extensions.distanceInMeters
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.planner.JourneySegment
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region
import com.syrmos.core.model.transit.Station
import kotlin.math.roundToInt
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

    /**
     * Fastest route with a set of lines banned, used to generate materially
     * distinct alternatives (k-shortest via line-banning): re-plan with each line
     * the base route used removed, yielding a different route where one exists.
     * Null when banning leaves no path.
     */
    fun invoke(fromStationId: String, toStationId: String, bannedLineIds: Set<String>): Flow<JourneyResult?> = flow {
        emit(compute(fromStationId, toStationId, operationalLines().filter { it.id !in bannedLineIds }))
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
        // id -> (lat, lon), so transfer edges can require real physical proximity.
        val stationCoords = mutableMapOf<String, Pair<Double, Double>>()

        // Build edges from station ordering on each line
        for (line in allLines) {
            val stations = stationRepository.getStationsOnLine(line.id).first()
            stations.forEach {
                stationNames[it.id] = it.name
                stationCoords[it.id] = it.latitude to it.longitude
            }
            for (i in 0 until stations.size - 1) {
                val a = stations[i]
                val b = stations[i + 1]
                val travelTime = segmentMinutes(line, a, b)
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

        // Transfer edges between co-located stations that share a folded display
        // name (mirrors web-planner.js buildGraph). Each line keeps its OWN
        // per-line station id even at an interchange, so without these edges the
        // graph is disconnected across lines and every cross-line journey returns
        // null.
        //
        // A shared name is necessary but NOT sufficient: "Panepistimio",
        // "Syntagma", "Omonia" etc. exist in both Athens and Thessaloniki, so
        // grouping by name alone fuses the two cities' metros into one graph and
        // the planner "changes" from an Athens line to a Thessaloniki line at a
        // station 500 km away (e.g. Piraeus to Nea Elvetia via M2 -> TM1 instead
        // of the intercity train). Require the two stops to be physically within
        // MAX_TRANSFER_METERS, which a real interchange is and a cross-city
        // namesake never is.
        val idsByFoldedName = mutableMapOf<String, MutableList<String>>()
        for ((id, name) in stationNames) {
            idsByFoldedName.getOrPut(foldStationName(name)) { mutableListOf() }.add(id)
        }
        for (ids in idsByFoldedName.values) {
            if (ids.size < 2) continue
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val a = ids[i]
                    val b = ids[j]
                    val ca = stationCoords[a]
                    val cb = stationCoords[b]
                    if (ca != null && cb != null &&
                        distanceInMeters(ca.first, ca.second, cb.first, cb.second) > MAX_TRANSFER_METERS
                    ) {
                        continue
                    }
                    graph.getOrPut(a) { mutableListOf() }
                        .add(Edge(b, TRANSFER_LINE_ID, "", TRANSFER_MINUTES, isTransfer = true))
                    graph.getOrPut(b) { mutableListOf() }
                        .add(Edge(a, TRANSFER_LINE_ID, "", TRANSFER_MINUTES, isTransfer = true))
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

    /// Ride time for one hop, from the stop-to-stop distance and a cruising
    /// speed (km/h, including dwell) per line type. National-region suburban is
    /// intercity rail (fast, long gaps between stops); national-region bus is a
    /// long-distance coach. A flat per-stop value made the Athens-Thessaloniki
    /// intercity (~500 km) cost like ten metro hops instead of its real ~5 hours.
    private fun segmentMinutes(line: Line, a: Station, b: Station): Int {
        val km = distanceInMeters(a.latitude, a.longitude, b.latitude, b.longitude) / 1000.0
        val national = line.region == Region.NATIONAL
        val speedKmh = when (line.type.name.lowercase()) {
            "metro" -> 32.0
            "tram" -> 18.0
            "scenic" -> 25.0
            "suburban" -> if (national) 95.0 else 42.0
            "bus" -> if (national) 70.0 else 20.0
            else -> 40.0
        }
        return maxOf(1, (km / speedKmh * 60.0).roundToInt())
    }

    companion object {
        /** Walking time estimate at an interchange (matches web TRANSFER_MINUTES). */
        const val TRANSFER_MINUTES = 3

        /**
         * Max metres between two same-named stops for them to count as one
         * physical interchange. A real transfer is a few metres; a cross-city
         * namesake (Athens vs Thessaloniki "Panepistimio") is hundreds of km, so
         * this stops the planner stitching separate cities' networks together.
         */
        const val MAX_TRANSFER_METERS = 500
        /** Sentinel line id on a transfer edge; reconstructSegments skips it. */
        const val TRANSFER_LINE_ID = "__transfer__"
    }
}

/**
 * Accent-folded, alphanumerics-only key for grouping co-located interchange
 * stations by display name. Mirrors web-planner.js `nameKey` and iOS
 * AthensTransitParser.fold so the three clients group interchanges identically.
 */
internal fun foldStationName(name: String): String =
    name.lowercase()
        .replace(Regex("[\\u0300-\\u036f]"), "") // strip combining diacritics
        .let {
            // NFD-fold common Greek/Latin accented vowels that survive lowercase.
            buildString {
                for (ch in it) append(
                    when (ch) {
                        'ά' -> 'α'; 'έ' -> 'ε'; 'ή' -> 'η'; 'ί', 'ϊ', 'ΐ' -> 'ι'
                        'ό' -> 'ο'; 'ύ', 'ϋ', 'ΰ' -> 'υ'; 'ώ' -> 'ω'
                        'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'
                        'í', 'ì', 'ï', 'î' -> 'i'; 'ó', 'ò', 'ö', 'ô' -> 'o'
                        'ú', 'ù', 'ü', 'û' -> 'u'
                        else -> ch
                    },
                )
            }
        }
        .replace(Regex("[^a-z0-9α-ω]"), "")

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
    // Every station the running leg calls at, start inclusive. Kept alongside
    // segmentStationCount so a ride carries its real stop sequence, not just ends.
    val segmentStops = mutableListOf<String>()

    for ((stationId, edge) in path) {
        if (edge == null) { prevStationId = stationId; continue }
        if (edge.isTransfer) {
            // Close the running leg at the interchange on the OLD line, then let
            // the next real edge start a fresh leg from the co-located station on
            // the new line. The transfer itself is never its own segment.
            if (currentLineId != null) {
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
                        orderedStopIds = segmentStops.toList(),
                    )
                )
                currentLineId = null
            }
            segmentStartStation = stationId
            segmentStationCount = 0
            segmentMinutes = 0
            segmentStops.clear()
            prevStationId = stationId
            continue
        }
        when {
            currentLineId == null -> {
                // First segment keeps the origin as its start (the old code
                // overwrote it with the second station, dropping the origin).
                currentLineId = edge.lineId
                currentLineName = edge.lineName
                segmentStationCount = 1
                segmentMinutes = edge.weight
                segmentStops.clear()
                segmentStops += segmentStartStation
                segmentStops += stationId
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
                        orderedStopIds = segmentStops.toList(),
                    )
                )
                currentLineId = edge.lineId
                currentLineName = edge.lineName
                segmentStartStation = prevStationId
                segmentStationCount = 1
                segmentMinutes = edge.weight
                segmentStops.clear()
                segmentStops += prevStationId
                segmentStops += stationId
            }
            else -> {
                segmentStationCount++
                segmentMinutes += edge.weight
                segmentStops += stationId
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
                orderedStopIds = segmentStops.toList().let {
                    if (it.lastOrNull() == toStationId) it else it + toStationId
                },
            )
        )
    }

    return segments
}
