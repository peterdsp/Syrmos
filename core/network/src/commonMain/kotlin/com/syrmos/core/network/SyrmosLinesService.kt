package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline-first loader for transit lines.
 *
 * Embedded data in the app is always the source-of-truth fallback so the app
 * works offline from second 0. This service tries to fetch the latest
 * `/api/lines` snapshot from the Pi and emits it when fresher. Callers can
 * persist the result to disk (DataStore on Android, localStorage on Web)
 * and prefer it over embedded data on next launch.
 *
 * The API returns 9 lines × 195 stations in ~19 KB cached by Cloudflare so
 * the round-trip is cheap and infrequent updates (a few times a year) can
 * land server-side without app releases.
 */
class SyrmosLinesService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchLines(): Flow<LinesPayload?> = flow {
        val payload = runCatching {
            val response = httpClient.get(LINES_URL)
            json.decodeFromString<LinesPayload>(response.bodyAsText())
        }.getOrNull()
        emit(payload)
    }

    @Serializable
    data class LinesPayload(
        val version: Int = 1,
        @SerialName("updatedAt") val updatedAt: String = "",
        val lines: List<RemoteLine> = emptyList(),
    )

    @Serializable
    data class RemoteLine(
        val id: String,
        val name: String,
        val nameEl: String,
        val type: String,
        val color: String,
        val terminalA: String,
        val terminalB: String,
        val stationCount: Int,
        val stations: List<RemoteStation>,
    )

    @Serializable
    data class RemoteStation(
        val id: String,
        val name: String,
        val nameEl: String,
        val lat: Double,
        val lng: Double,
    )

    private companion object {
        private const val LINES_URL = "https://api-syrmos.peterdsp.dev/api/lines"
    }
}
