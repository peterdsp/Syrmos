package com.syrmos.core.domain.station

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedLinesPayload
import kotlinx.serialization.json.Json

/**
 * The ordered boarding stops of every line, read from `lines.json`'s nested
 * `stations[]`.
 *
 * This is the only authoritative statement that a line serves a stop.
 * `stations.json:line_ids` carries INTERCHANGE unions, so reading boarding
 * membership from it routed an `A1` lookup to the metro stop `M2_STA` at Athens.
 *
 * Mirrors the Swift `SyrmosLineStops` and the web
 * `SyrmosStationBoard.resolveComplexServices`.
 */
class LineStopOrder(
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: Map<String, LineStops>? = null

    data class LineStops(
        val lineId: String,
        val terminalA: String,
        val terminalB: String,
        val type: String,
        val stopIds: List<String>,
    )

    suspend fun byLine(): Map<String, LineStops> {
        cached?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<SeedLinesPayload>(resourceReader.readText(LINES_PATH))
                .lines
                .filter { it.stations.isNotEmpty() }
                .associate { line ->
                    line.id to LineStops(
                        lineId = line.id,
                        terminalA = line.terminalA,
                        terminalB = line.terminalB,
                        type = line.type,
                        stopIds = line.stations.map { it.id },
                    )
                }
        }.getOrElse { emptyMap() }
        cached = loaded
        return loaded
    }

    /** Every line that actually boards at [stopId], sorted for determinism. */
    suspend fun linesBoardingAt(stopId: String): List<LineStops> =
        byLine().values.filter { stopId in it.stopIds }.sortedBy { it.lineId }

    /**
     * The directions a rider can actually leave in from [stopId].
     *
     * A terminal offers ONE direction, not two: projecting both is how a board
     * invents a train to the station the rider is already standing in.
     */
    suspend fun directionsAt(lineId: String, stopId: String): List<Direction> {
        val line = byLine()[lineId] ?: return emptyList()
        val index = line.stopIds.indexOf(stopId)
        if (index < 0) return emptyList()
        val out = mutableListOf<Direction>()
        if (index < line.stopIds.lastIndex && line.terminalB.isNotBlank()) {
            out += Direction(line.terminalB, "outbound")
        }
        if (index > 0 && line.terminalA.isNotBlank()) {
            out += Direction(line.terminalA, "inbound")
        }
        return out
    }

    data class Direction(val destination: String, val key: String)

    /** The boarding area a line's mode belongs to inside a complex. */
    fun areaIdFor(type: String): String = when (type.lowercase()) {
        "metro" -> "metro"
        "tram" -> "tram"
        "bus" -> "bus"
        // Suburban, scenic and intercity services all board on rail platforms.
        else -> "rail"
    }

    private companion object {
        const val LINES_PATH = "files/seed/schedules-v2/lines.json"
    }
}
