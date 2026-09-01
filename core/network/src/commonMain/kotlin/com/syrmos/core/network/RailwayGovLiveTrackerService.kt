package com.syrmos.core.network

import com.syrmos.core.common.LiveDataFreshness
import com.syrmos.core.model.transit.LiveSuburbanTrain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
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
                // Fast per-call timeout: this is a live poll feeding the map, so
                // a stalled request should fail quickly and let the loop retry /
                // fall back, not hang on the 30s client default.
                val response = httpClient.get(TRAINS_URL) {
                    timeout { requestTimeoutMillis = LIVE_REQUEST_TIMEOUT_MS }
                }
                val body = response.bodyAsText()
                val payload = json.decodeFromString<TrainsPayload>(body)
                val trains = payload.trains
                    .asSequence()
                    // Skip a train that has no coordinate rather than letting the
                    // whole payload fail to parse. lat/lng are now nullable, so
                    // one GPS-less train (just appeared, scrape glitch) no longer
                    // throws MissingFieldException and freezes the entire live map
                    // on the previous frame. Mirrors the web map's null guard.
                    .filter { it.lat != null && it.lng != null }
                    .filter { lineIds.isNullOrEmpty() || it.lineId in lineIds }
                    .map { it.toDomain() }
                    .sortedWith(
                        compareBy<LiveSuburbanTrain> { it.delayMinutes }
                            .thenBy { it.trainNumber },
                    )
                    .toList()
                // A poll came back from the API, so we're online and the
                // suburban positions are live. Record it so the home
                // offline-alive pill flips to "live".
                LiveDataFreshness.markLive()
                emit(trains)
            } catch (e: Exception) {
                println("[LiveTracker] poll failed: ${e.message}")
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
            originEn = originEn.takeIf { it.isNotBlank() },
            destination = destination.takeIf { it.isNotBlank() },
            destinationEn = destinationEn.takeIf { it.isNotBlank() },
            nextStation = nextStation.takeIf { it.isNotBlank() },
            nextStationEn = nextStationEn.takeIf { it.isNotBlank() },
            delayMinutes = delayMinutes,
            serviceType = serviceType,
            status = status ?: "in_service",
            inService = inService ?: true,
            progress = progress,
            speedKph = speed,
            latitude = lat ?: 0.0,
            longitude = lng ?: 0.0,
            updatedAt = "",
            course = course,
            altitude = altitude,
            locomotiveNumber = locomotiveNumber,
            distanceToDestination = distanceToDestination,
            distanceToNextStation = distanceToNextStation,
            signalStatus = signalStatus,
            corridor = corridor,
            trainType = trainType,
            scheduledDeparture = scheduledDeparture,
            scheduledArrival = scheduledArrival,
            scheduleStatus = scheduleStatus,
            trainId = trainId,
            liveStreamUrl = liveStream?.playlistUrl?.takeIf { it.isNotBlank() },
        )
    }

    @Serializable
    private data class TrainsPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val trains: List<TrainItem> = emptyList(),
    )

    @Serializable
    private data class LiveStreamInfo(
        val playlistUrl: String = "",
        val streamingStatus: String = "",
    )

    @Serializable
    private data class TrainItem(
        val id: String = "",
        val lineId: String = "",
        val trainNumber: String = "",
        val origin: String = "",
        val originEn: String = "",
        val destination: String = "",
        val destinationEn: String = "",
        val nextStation: String = "",
        val nextStationEn: String = "",
        val delayMinutes: Int = 0,
        val serviceType: String = "",
        // Boardability flags from the Pi feed. Nullable so older payloads that
        // omit them decode fine; toDomain() defaults them to an in-service train.
        val status: String? = null,
        val inService: Boolean? = null,
        // Nullable + defaulted: a train can briefly appear with no GPS fix. If
        // these stayed required, one such row would fail the whole payload decode
        // (they were the only non-defaulted fields) and blank the live map.
        val lat: Double? = null,
        val lng: Double? = null,
        val speed: Double? = null,
        val course: Double? = null,
        val altitude: Double? = null,
        val progress: Double? = null,
        val locomotiveNumber: String? = null,
        val distanceToDestination: Int? = null,
        val distanceToNextStation: Int? = null,
        val signalStatus: String? = null,
        val corridor: String? = null,
        val trainType: String? = null,
        val scheduledDeparture: String? = null,
        val scheduledArrival: String? = null,
        val scheduleStatus: String? = null,
        val trainId: String? = null,
        val liveStream: LiveStreamInfo? = null,
    )

    private companion object {
        private const val TRAINS_URL = "https://api-syrmos.peterdsp.dev/api/trains"
        private const val POLL_INTERVAL_MS = 10_000L
    }
}
