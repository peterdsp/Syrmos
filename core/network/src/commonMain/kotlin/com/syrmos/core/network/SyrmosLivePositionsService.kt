package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Talks to the API's `/api/live-positions` and `/api/station-offsets`
 * endpoints. The map uses the active-train list + the offsets table to
 * interpolate each metro/tram dot's position along its line. Both
 * outputs come from the same projector that powers `/api/departures/next`,
 * so the moving icon and the bottom-sheet "X min" stay in lockstep.
 *
 * The previous TrainSimulator drew positions by guessing travel time and
 * walking haversine distances between stations. The two numbers were
 * unrelated to the projector, so the dot would creep into a station
 * while the sheet still said "9 min away" for that station.
 */
class SyrmosLivePositionsService(
    private val httpClient: HttpClient,
) {

    suspend fun fetchActiveTrains(lineIds: List<String>): LivePositionsResponse? = runCatching {
        val ids = lineIds.joinToString(",")
        // Fast per-call timeout: a stalled live poll should drop to the
        // simulated layer in seconds, not hang the map for the 30s default.
        val response = httpClient.get("$BASE_URL/api/live-positions?lineIds=$ids") {
            timeout { requestTimeoutMillis = LIVE_REQUEST_TIMEOUT_MS }
        }
        parseLivePositions(response.bodyAsText())
    }.getOrNull()

    suspend fun fetchStationOffsets(): StationOffsetsResponse? = runCatching {
        val response = httpClient.get("$BASE_URL/api/station-offsets") {
            timeout { requestTimeoutMillis = LIVE_REQUEST_TIMEOUT_MS }
        }
        parseStationOffsets(response.bodyAsText())
    }.getOrNull()

    @Serializable
    data class LivePositionsResponse(
        val generatedAt: String = "",
        val lineIds: List<String> = emptyList(),
        val trains: List<LiveTrain> = emptyList(),
    )

    // Every field carries a safe default so a row missing one still decodes
    // (see parseLivePositions); a row with no line/direction is dropped there
    // because it can't be placed on the map.
    @Serializable
    data class LiveTrain(
        val lineId: String = "",
        val directionKey: String = "",
        val originDepartureMinute: Double = 0.0,
        val elapsedMinutes: Double = 0.0,
        val totalTravelMinutes: Int = 0,
        val serviceType: String = "regular",
    )

    @Serializable
    data class StationOffsetsResponse(
        val updatedAt: String = "",
        val source: String = "",
        val lines: List<OffsetLine> = emptyList(),
    )

    @Serializable
    data class OffsetLine(
        val lineId: String = "",
        val direction: String = "",
        val origin: String = "",
        val destination: String = "",
        val stops: List<OffsetStop> = emptyList(),
    )

    @Serializable
    data class OffsetStop(
        val stationId: String = "",
        val stationEn: String = "",
        val stopSequence: Int = 0,
        val minutesFromOrigin: Int = 0,
    )

    companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Decode the live-positions payload row by row so ONE malformed vehicle
         * (a missing or wrong-typed field) is skipped instead of throwing and
         * nulling the whole response, which would silently drop the entire live
         * map back to the schedule simulator. Mirrors the iOS resilience fixes
         * for the trains feed and announcements: tolerate partial rows, drop the
         * useless ones (no line or direction), keep the rest. A body that is not
         * a JSON object still throws, so the caller's runCatching falls back to
         * the bundled/simulated layer exactly as before.
         */
        internal fun parseLivePositions(body: String): LivePositionsResponse {
            val root = json.parseToJsonElement(body).jsonObject
            val trains = (root["trains"] as? JsonArray).orEmpty()
                .mapNotNull { el -> runCatching { json.decodeFromJsonElement(LiveTrain.serializer(), el) }.getOrNull() }
                .filter { it.lineId.isNotBlank() && it.directionKey.isNotBlank() }
            return LivePositionsResponse(
                generatedAt = (root["generatedAt"] as? JsonPrimitive)?.contentOrNull ?: "",
                lineIds = (root["lineIds"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                trains = trains,
            )
        }

        /**
         * Same row-by-row tolerance for the station-offsets table: a single
         * malformed line entry is skipped instead of voiding every line's
         * offsets (which would leave the live dots with nowhere to interpolate).
         */
        internal fun parseStationOffsets(body: String): StationOffsetsResponse {
            val root = json.parseToJsonElement(body).jsonObject
            val lines = (root["lines"] as? JsonArray).orEmpty()
                .mapNotNull { el -> runCatching { json.decodeFromJsonElement(OffsetLine.serializer(), el) }.getOrNull() }
                .filter { it.lineId.isNotBlank() && it.direction.isNotBlank() }
            return StationOffsetsResponse(
                updatedAt = (root["updatedAt"] as? JsonPrimitive)?.contentOrNull ?: "",
                source = (root["source"] as? JsonPrimitive)?.contentOrNull ?: "",
                lines = lines,
            )
        }
    }
}
