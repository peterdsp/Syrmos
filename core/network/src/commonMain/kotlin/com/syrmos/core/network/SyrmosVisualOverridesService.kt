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
 * Fetches admin-set visual overrides for icons + line drawing.
 *
 * Mirrors the iOS `SyrmosVisualOverridesStore`. The repository wraps this
 * with a StateFlow so the map composable can re-render when the admin
 * changes a line color.
 *
 * Same offline-first contract: failures return null; the caller falls back
 * to bundled assets / colors.
 */
class SyrmosVisualOverridesService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchIcons(): Flow<IconsPayload?> = flow {
        val payload = runCatching {
            val resp = httpClient.get(ICONS_URL).bodyAsText()
            json.decodeFromString<IconsPayload>(resp)
        }.getOrNull()
        emit(payload)
    }

    fun fetchLineDisplay(): Flow<LineDisplayPayload?> = flow {
        val payload = runCatching {
            val resp = httpClient.get(LINE_DISPLAY_URL).bodyAsText()
            json.decodeFromString<LineDisplayPayload>(resp)
        }.getOrNull()
        emit(payload)
    }

    @Serializable
    data class IconsPayload(
        @SerialName("updatedAt") val updatedAt: String = "",
        val stations: Map<String, String> = emptyMap(),
        val interchanges: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class LineDisplayPayload(
        @SerialName("updatedAt") val updatedAt: String = "",
        val lines: List<LineDisplay> = emptyList(),
    )

    @Serializable
    data class LineDisplay(
        @SerialName("lineId") val lineId: String,
        @SerialName("strokeColor") val strokeColor: String,
        @SerialName("strokeWeight") val strokeWeight: Int = 4,
        @SerialName("strokeDash") val strokeDash: String? = null,
        @SerialName("labelColor") val labelColor: String? = null,
        val glow: Boolean = false,
        val notes: String = "",
    )

    private companion object {
        private const val BASE = "https://api-syrmos.peterdsp.dev"
        private const val ICONS_URL = "$BASE/api/icons"
        private const val LINE_DISPLAY_URL = "$BASE/api/line-display"
    }
}
