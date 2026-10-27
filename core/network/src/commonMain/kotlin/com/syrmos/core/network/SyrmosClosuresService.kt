package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * 3.0.2: raw reads of `GET /api/closures` and `GET /api/broadcasts?since=` on the
 * Pi (see docs/3.0.2-CONTRACTS.md). Returns the body, or null when the call
 * failed, so the repository can tell "network down" from "nothing active" the same
 * way [STASYAnnouncementService.fetchFeed] does. Parsing lives in core/data so
 * the bundled `closures.json` and the live payload share one decoder.
 */
class SyrmosClosuresService(
    private val httpClient: HttpClient,
) {
    suspend fun fetchClosuresBody(): String? = fetch("$BASE_URL/api/closures")

    suspend fun fetchBroadcastsBody(sinceEpochSeconds: Long): String? =
        fetch("$BASE_URL/api/broadcasts?since=$sinceEpochSeconds")

    private suspend fun fetch(url: String): String? = try {
        val response = httpClient.get(url)
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (_: Exception) {
        null
    }

    companion object {
        const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}
