package com.syrmos.core.network

import com.syrmos.core.model.transit.LiveSuburbanTrain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Polls the Syrmos API at api-syrmos.peterdsp.dev/api/trains for live
 * suburban train positions. The Raspberry Pi daemon holds a single SSE
 * connection to railway.gov.gr, parses ONLY the trainPositionsUx event,
 * merges schedule info, infers line ids (A1, A2, A3, A4), filters out
 * unassigned freight locomotives, and writes the result as a tiny JSON
 * file (~1.5 KB) cached by nginx.
 *
 * Each client therefore polls a small JSON file every 10 seconds instead
 * of holding its own SSE connection and parsing 10 KB+ of unused
 * schedule cards per second. This is what killed the iOS app on devices
 * — keep this pattern.
 */
class RailwayGovLiveTrackerService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeSuburbanTrains(lineId: String): Flow<List<LiveSuburbanTrain>> {
        return observeSuburbanTrains(setOf(lineId))
    }

    fun observeSuburbanTrains(lineIds: Set<String>? = null): Flow<List<LiveSuburbanTrain>> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                val response = httpClient.get(TRAINS_URL)
                val body = response.bodyAsText()
                val payload = json.decodeFromString<TrainsPayload>(body)
                val trains = payload.trains
                    .asSequence()
                    .filter { lineIds.isNullOrEmpty() || it.lineId in lineIds }
                    .map { it.toDomain() }
                    .sortedWith(
                        compareBy<LiveSuburbanTrain> { it.delayMinutes }
                            .thenBy { it.trainNumber },
                    )
                    .toList()
                emit(trains)
            } catch (_: Exception) {
                // Keep the last known UI state and retry on next poll.
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun TrainItem.toDomain(): LiveSuburbanTrain {
        return LiveSuburbanTrain(
            id = id,
            lineId = lineId,
            trainNumber = trainNumber,
            origin = origin.takeIf { it.isNotBlank() },
            destination = destination.takeIf { it.isNotBlank() },
            nextStation = nextStation.takeIf { it.isNotBlank() },
            delayMinutes = delayMinutes,
            progress = null,
            speedKph = null,
            latitude = lat,
            longitude = lng,
            updatedAt = "",
        )
    }

    @Serializable
    private data class TrainsPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val trains: List<TrainItem> = emptyList(),
    )

    @Serializable
    private data class TrainItem(
        val id: String = "",
        val lineId: String = "",
        val trainNumber: String = "",
        val origin: String = "",
        val destination: String = "",
        val nextStation: String = "",
        val delayMinutes: Int = 0,
        val serviceType: String = "",
        val lat: Double,
        val lng: Double,
    )

    private companion object {
        private const val TRAINS_URL = "https://api-syrmos.peterdsp.dev/api/trains"
        private const val POLL_INTERVAL_MS = 10_000L
    }
}
