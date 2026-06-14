package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchActiveTrains(lineIds: List<String>): LivePositionsResponse? = runCatching {
        val ids = lineIds.joinToString(",")
        val response = httpClient.get("$BASE_URL/api/live-positions?lineIds=$ids")
        val body = response.bodyAsText()
        json.decodeFromString<LivePositionsResponse>(body)
    }.getOrNull()

    suspend fun fetchStationOffsets(): StationOffsetsResponse? = runCatching {
        val response = httpClient.get("$BASE_URL/api/station-offsets")
        val body = response.bodyAsText()
        json.decodeFromString<StationOffsetsResponse>(body)
    }.getOrNull()

    @Serializable
    data class LivePositionsResponse(
        val generatedAt: String,
        val lineIds: List<String> = emptyList(),
        val trains: List<LiveTrain> = emptyList(),
    )

    @Serializable
    data class LiveTrain(
        val lineId: String,
        val directionKey: String,
        val originDepartureMinute: Double,
        val elapsedMinutes: Double,
        val totalTravelMinutes: Int,
        val serviceType: String,
    )

    @Serializable
    data class StationOffsetsResponse(
        val updatedAt: String = "",
        val source: String = "",
        val lines: List<OffsetLine> = emptyList(),
    )

    @Serializable
    data class OffsetLine(
        val lineId: String,
        val direction: String,
        val origin: String = "",
        val destination: String = "",
        val stops: List<OffsetStop> = emptyList(),
    )

    @Serializable
    data class OffsetStop(
        val stationId: String,
        val stationEn: String = "",
        val stopSequence: Int,
        val minutesFromOrigin: Int,
    )

    companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}
