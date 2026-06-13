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

    /**
     * Pull the per-station minutes-from-origin grid scraped from STASY's
     * timetable pages. Apps multiply this by the band-projected origin
     * departures to get exact HH:MM at every metro and tram stop. Returns
     * null if the endpoint hasn't shipped yet or the network failed.
     */
    fun fetchStationOffsets(): Flow<StationOffsetsPayload?> = flow {
        val payload = runCatching {
            val response = httpClient.get(STATION_OFFSETS_URL)
            json.decodeFromString<StationOffsetsPayload>(response.bodyAsText())
        }.getOrNull()
        emit(payload)
    }

    /**
     * OASA fare catalogue scraped from oasa.gr/en/tickets/prices-of-products/.
     * Drives the native Tickets screen on iOS / Android / Web in place of the
     * old "open OASA website in a SafariSheet" flow.
     */
    fun fetchFares(): Flow<FaresPayload?> = flow {
        val payload = runCatching {
            val response = httpClient.get(FARES_URL)
            json.decodeFromString<FaresPayload>(response.bodyAsText())
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

    @Serializable
    data class StationOffsetsPayload(
        @SerialName("updatedAt") val updatedAt: String = "",
        val source: String = "",
        val lines: List<StationOffsetGroup> = emptyList(),
    )

    @Serializable
    data class StationOffsetGroup(
        @SerialName("lineId") val lineId: String,
        val direction: String,
        val origin: String = "",
        val destination: String = "",
        val stops: List<StationOffsetStop> = emptyList(),
    )

    @Serializable
    data class StationOffsetStop(
        @SerialName("stationId") val stationId: String = "",
        @SerialName("stationEn") val stationEn: String = "",
        @SerialName("stopSequence") val stopSequence: Int = 0,
        @SerialName("minutesFromOrigin") val minutesFromOrigin: Int = 0,
    )

    @Serializable
    data class FaresPayload(
        @SerialName("updatedAt") val updatedAt: String = "",
        val products: List<FareProduct> = emptyList(),
        @SerialName("infoLinks") val infoLinks: List<InfoLink> = emptyList(),
    )

    @Serializable
    data class FareProduct(
        val section: String,
        @SerialName("titleEn") val titleEn: String,
        @SerialName("titleEl") val titleEl: String = "",
        @SerialName("fullPriceEur") val fullPriceEur: Double? = null,
        @SerialName("discountedPriceEur") val discountedPriceEur: Double? = null,
        val validity: String = "",
        val notes: String = "",
        val tags: List<String> = emptyList(),
        @SerialName("sourceUrl") val sourceUrl: String = "",
    )

    @Serializable
    data class InfoLink(
        val id: String,
        @SerialName("operator") val operatorId: String,
        val icon: String,
        @SerialName("titleEn") val titleEn: String,
        @SerialName("titleEl") val titleEl: String,
        @SerialName("urlEn") val urlEn: String,
        @SerialName("urlEl") val urlEl: String,
    )

    private companion object {
        private const val BASE = "https://api-syrmos.peterdsp.dev"
        private const val MANIFEST_URL = "$BASE/api/schedules/manifest"
        private const val LINE_URL_PREFIX = "$BASE/api/schedules/"
        private const val STATION_OFFSETS_URL = "$BASE/api/station-offsets"
        private const val FARES_URL = "$BASE/api/fares"
    }
}
