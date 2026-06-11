package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Offline-first loader for schedule rules + frequency bands.
 *
 * The bundled seed continues to ship in the app so it works offline from
 * second 0. This service fetches the manifest from the Pi
 * (api-syrmos.peterdsp.dev/api/schedules/manifest) with an If-None-Match
 * header. On 304 there's nothing to do. On 200 the caller fetches each
 * changed line's bundle and atomically swaps it into the persistent store.
 *
 * Failures are silent: the app falls through to whatever the persistent
 * store last cached, and ultimately to the bundled seed. This is the same
 * pattern as [SyrmosLinesService].
 */
class SyrmosSchedulesService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the manifest, or null if nothing changed / network failed. */
    fun fetchManifest(lastEtag: String?): Flow<ManifestResult> = flow {
        val result = runCatching {
            val response = httpClient.get(MANIFEST_URL) {
                if (!lastEtag.isNullOrBlank()) header("If-None-Match", lastEtag)
            }
            when (response.status) {
                HttpStatusCode.NotModified -> ManifestResult.NotModified
                HttpStatusCode.OK -> ManifestResult.Fresh(
                    json.decodeFromString<Manifest>(response.bodyAsText()),
                )
                else -> ManifestResult.Failure("HTTP ${response.status.value}")
            }
        }.getOrElse { ManifestResult.Failure(it.message ?: "fetch failed") }
        emit(result)
    }

    fun fetchLineBundle(lineId: String): Flow<LineSchedule?> = flow {
        val payload = runCatching {
            val response = httpClient.get("$LINE_URL_PREFIX$lineId")
            json.decodeFromString<LineSchedule>(response.bodyAsText())
        }.getOrNull()
        emit(payload)
    }

    sealed interface ManifestResult {
        data object NotModified : ManifestResult
        data class Fresh(val manifest: Manifest) : ManifestResult
        data class Failure(val reason: String) : ManifestResult
    }

    @Serializable
    data class Manifest(
        val version: Int,
        @SerialName("updatedAt") val updatedAt: String,
        @SerialName("clientMinVersion") val clientMinVersion: Int = 0,
        val etag: String,
        @SerialName("perLineHashes") val perLineHashes: Map<String, String> = emptyMap(),
        @SerialName("linesHash") val linesHash: String = "",
        @SerialName("holidaysHash") val holidaysHash: String = "",
        @SerialName("overridesHash") val overridesHash: String = "",
    )

    @Serializable
    data class LineSchedule(
        @SerialName("lineId") val lineId: String,
        val rules: List<RuleEntry> = emptyList(),
        val bands: List<BandEntry> = emptyList(),
    )

    @Serializable
    data class RuleEntry(
        @SerialName("dayType") val dayType: String,
        @SerialName("openTime") val openTime: String,
        @SerialName("closeTime") val closeTime: String,
        @SerialName("is247") val is247: Boolean = false,
        val notes: String = "",
    )

    @Serializable
    data class BandEntry(
        @SerialName("dayType") val dayType: String,
        @SerialName("timeStart") val timeStart: String,
        @SerialName("timeEnd") val timeEnd: String,
        @SerialName("headwayMinutes") val headwayMinutes: Double,
        val label: String = "",
    )

    private companion object {
        private const val BASE = "https://api-syrmos.peterdsp.dev"
        private const val MANIFEST_URL = "$BASE/api/schedules/manifest"
        private const val LINE_URL_PREFIX = "$BASE/api/schedules/"
    }
}
