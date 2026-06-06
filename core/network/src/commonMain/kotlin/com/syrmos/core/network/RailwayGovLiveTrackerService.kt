package com.syrmos.core.network

import com.syrmos.core.model.transit.LiveSuburbanTrain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
                httpClient.prepareGet(TRAIN_STREAM_URL) {
                    accept(ContentType.Text.EventStream)
                    contentType(ContentType.Text.EventStream)
                    headers.append(HttpHeaders.CacheControl, "no-cache")
                    headers.append(HttpHeaders.UserAgent, USER_AGENT)
                    timeout {
                        requestTimeoutMillis = STREAM_TIMEOUT_MS
                    }
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    var currentEventName: String? = null
                    val currentData = StringBuilder()

                    while (currentCoroutineContext().isActive) {
                        val line = channel.readUTF8Line() ?: break

                        if (line.isEmpty()) {
                            if (currentEventName == TRAIN_POSITIONS_EVENT && currentData.isNotEmpty()) {
                                emit(parseSuburbanTrains(lineIds, currentData.toString()))
                            }
                            currentEventName = null
                            currentData.clear()
                            continue
                        }

                        when {
                            line.startsWith(EVENT_PREFIX) -> {
                                currentEventName = line.removePrefix(EVENT_PREFIX).trim()
                            }

                            line.startsWith(DATA_PREFIX) -> {
                                if (currentData.isNotEmpty()) {
                                    currentData.append('\n')
                                }
                                currentData.append(line.removePrefix(DATA_PREFIX).trimStart())
                            }
                        }
                    }
                }
            } catch (_: HttpRequestTimeoutException) {
                // Retry the stream when the server closes the connection.
            } catch (_: Exception) {
                // Keep the last known UI state and retry after a short backoff.
            }

            delay(RECONNECT_DELAY_MS)
        }
    }

    private fun parseSuburbanTrains(
        lineIds: Set<String>?,
        payload: String,
    ): List<LiveSuburbanTrain> {
        return runCatching {
            json.decodeFromString<TrainPositionsPayload>(payload)
                .positions
                .asSequence()
                .filter { it.matchesSuburbanLine(lineIds) }
                .mapNotNull { it.toDomain() }
                .sortedWith(
                    compareBy<LiveSuburbanTrain> { it.delayMinutes }
                        .thenBy { it.trainNumber },
                )
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun TrainPositionPayload.matchesSuburbanLine(lineIds: Set<String>?): Boolean {
        val inferredLineId = inferSuburbanLineId() ?: return false

        return lineIds.isNullOrEmpty() || inferredLineId in lineIds
    }

    private fun TrainPositionPayload.inferSuburbanLineId(): String? {
        val originText = origin.orEmpty().normalizedRouteText()
        val destinationText = destination.orEmpty().normalizedRouteText()
        val nextStationText = nextStation.orEmpty().normalizedRouteText()
        val corridorText = corridor.orEmpty().normalizedRouteText()
        val routeText = listOf(originText, destinationText, nextStationText, corridorText).joinToString(" ")

        if (routeMatches(routeText, "ανω λιοσια", "ano liosia") &&
            routeMatches(routeText, "αεροδρομ", "airport")) return "A2"

        if (routeMatches(routeText, "αθην", "athens") &&
            routeMatches(routeText, "χαλκιδ", "chalcis")) return "A3"

        if (routeMatches(routeText, "πειραι", "piraeus") &&
            routeMatches(routeText, "κιατ", "kiato")) return "A4"

        if (corridorText == "pirair" ||
            routeMatches(routeText, "πειραι", "piraeus") &&
            routeMatches(routeText, "αεροδρομ", "airport")) return "A1"

        if (corridorText == "e85") return "A3"

        return null
    }

    private fun TrainPositionPayload.toDomain(): LiveSuburbanTrain? {
        val inferredLineId = inferSuburbanLineId() ?: return null
        val trainId = id ?: trainId ?: return null
        val trainLabel = trainNumber ?: name ?: locomotiveNumber ?: return null
        val latitudeValue = lat ?: return null
        val longitudeValue = lng ?: return null

        return LiveSuburbanTrain(
            id = trainId,
            lineId = inferredLineId,
            trainNumber = trainLabel.trim(),
            origin = origin,
            destination = destination,
            nextStation = nextStation,
            delayMinutes = delay ?: 0,
            progress = progress,
            speedKph = speed?.takeIf { it >= 0.0 },
            latitude = latitudeValue,
            longitude = longitudeValue,
            updatedAt = timestamp ?: receivedAt.orEmpty(),
        )
    }

    private fun routeMatches(
        routeText: String,
        greekToken: String,
        latinToken: String,
    ): Boolean {
        return routeText.contains(greekToken) ||
            routeText.contains(latinToken)
    }

    private fun String.normalizedRouteText(): String {
        return lowercase()
            .replace('ά', 'α')
            .replace('έ', 'ε')
            .replace('ή', 'η')
            .replace('ί', 'ι')
            .replace('ϊ', 'ι')
            .replace('ΐ', 'ι')
            .replace('ό', 'ο')
            .replace('ύ', 'υ')
            .replace('ϋ', 'υ')
            .replace('ΰ', 'υ')
            .replace('ώ', 'ω')
            .replace('-', ' ')
            .trim()
    }

    private companion object {
        private const val TRAIN_STREAM_URL = "https://railway.gov.gr/api/train-stream"
        private const val TRAIN_POSITIONS_EVENT = "trainPositionsUx"
        private const val SUBURBAN_SERVICE_TYPE = "Suburban"
        private const val EVENT_PREFIX = "event:"
        private const val DATA_PREFIX = "data:"
        private const val STREAM_TIMEOUT_MS = 45_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val USER_AGENT = "Mozilla/5.0 (compatible; Syrmos/1.0)"
    }
}

@Serializable
private data class TrainPositionsPayload(
    val positions: List<TrainPositionPayload> = emptyList(),
)

@Serializable
private data class TrainPositionPayload(
    val id: String? = null,
    @SerialName("trainId") val trainId: String? = null,
    val name: String? = null,
    @SerialName("trainNumber") val trainNumber: String? = null,
    @SerialName("serviceType") val serviceType: String? = null,
    val origin: String? = null,
        val destination: String? = null,
        @SerialName("nextStation") val nextStation: String? = null,
        val corridor: String? = null,
        val delay: Int? = null,
        val progress: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val speed: Double? = null,
    val timestamp: String? = null,
    val receivedAt: String? = null,
    @SerialName("locomotiveNumber") val locomotiveNumber: String? = null,
)
