package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect val communityPlatformName: String

@Serializable
data class CommunityIssue(
    val scopeId: String,
    val scopeLabel: String,
    val signal: String,
    val detail: String = "",
    val count: Int,
    val latestAt: String,
)

@Serializable
data class CommunitySummary(
    val displayMode: String = "normal",
    val scopeId: String? = null,
    val activeIssueCount: Int = 0,
    val normalReportCount: Int = 0,
    val totalReportsThisWeek: Int = 0,
    val estimatedJourneysToday: Int? = null,
    val estimatedDailyJourneys: Int? = null,
    val issues: List<CommunityIssue> = emptyList(),
    val updatedAt: String = "",
) {
    val hasIssues: Boolean get() = displayMode == "issues" && issues.isNotEmpty()
}

@Serializable
data class CommunityHistoryBucket(
    val period: String,
    val totalReports: Int = 0,
    val positiveReports: Int = 0,
    val issueReports: Int = 0,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class CommunityHistory(
    val granularity: String = "day",
    val scopeId: String? = null,
    val buckets: List<CommunityHistoryBucket> = emptyList(),
    val updatedAt: String = "",
    val privacy: String = "",
)

@Serializable
private data class CommunityReportRequest(
    val reportId: String,
    val scopeId: String,
    val scopeLabel: String,
    val signal: String,
    val detail: String = "",
    val platform: String,
    val locale: String,
)

@Serializable
data class CommunityReportReceipt(
    val ok: Boolean = false,
    val reportId: String = "",
    val expiresAt: String = "",
)

class CommunityReportService(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchSummary(scopeId: String? = null): CommunitySummary? = runCatching {
        val response = httpClient.get("$BASE_URL/api/community/summary") {
            if (!scopeId.isNullOrBlank()) parameter("scopeId", scopeId)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        json.decodeFromString<CommunitySummary>(response.bodyAsText())
    }.getOrNull()

    suspend fun fetchHistory(
        period: String = "day",
        scopeId: String? = null,
        limit: Int = 31,
    ): CommunityHistory? = runCatching {
        val response = httpClient.get("$BASE_URL/api/community/history") {
            parameter("period", period)
            parameter("limit", limit)
            if (!scopeId.isNullOrBlank()) parameter("scopeId", scopeId)
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        json.decodeFromString<CommunityHistory>(response.bodyAsText())
    }.getOrNull()

    suspend fun submit(
        reportId: String,
        scopeId: String,
        scopeLabel: String,
        signal: String,
        detail: String,
        locale: String,
    ): CommunityReportReceipt? = runCatching {
        val response = httpClient.post("$BASE_URL/api/community/reports") {
            contentType(ContentType.Application.Json)
            setBody(
                CommunityReportRequest(
                    reportId = reportId,
                    scopeId = scopeId,
                    scopeLabel = scopeLabel,
                    signal = signal,
                    detail = detail,
                    platform = communityPlatformName,
                    locale = locale,
                )
            )
        }
        if (response.status != HttpStatusCode.OK) return@runCatching null
        json.decodeFromString<CommunityReportReceipt>(response.bodyAsText())
    }.getOrNull()

    suspend fun delete(reportId: String): Boolean = runCatching {
        httpClient.delete("$BASE_URL/api/community/reports/$reportId").status == HttpStatusCode.OK
    }.getOrDefault(false)

    fun newReportId(): String {
        val token = Random.nextBytes(18).joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
        return "report_$token"
    }

    private companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}
