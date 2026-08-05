package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class STASYAnnouncement(
    val id: String,
    val title: String,
    val titleEn: String,
    val titleSq: String = "",
    val titleIt: String = "",
    val date: String,
    val summary: String = "",
    val summaryEn: String = "",
    val summarySq: String = "",
    val summaryIt: String = "",
    val url: String,
    val isServiceAlert: Boolean,
    val affectedLines: List<String> = emptyList(),
    val severity: String = "info",          // "info" | "warning" | "closure"
    val validFrom: String? = null,           // YYYY-MM-DD or null
    val validUntil: String? = null,
)

/** Network-wide STASY service-status badge. `status` is `normal`, `alert`,
 *  or `unknown`; `serviceUntil` is "HH:MM" when an alert sets a cutoff. */
data class STASYServiceStatus(
    val status: String,
    val rawMessage: String,
    val rawMessageEn: String,
    val rawMessageSq: String = "",
    val rawMessageIt: String = "",
    val serviceUntil: String?,
) {
    val isAlert: Boolean get() = status == "alert"
    val isNormal: Boolean get() = status == "normal"
}

data class STASYFeed(
    val status: STASYServiceStatus?,
    val announcements: List<STASYAnnouncement>,
)

/**
 * Fetches STASY service announcements from the Syrmos API proxy on the
 * Raspberry Pi (api-syrmos.peterdsp.dev/api/announcements). The Pi scrapes
 * stasy.gr every 5 minutes via cron and caches the result as JSON so every
 * client gets the same, lightweight ~5 KB payload without each device hitting
 * stasy.gr directly.
 */
class STASYAnnouncementService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchAnnouncements(): Flow<List<STASYAnnouncement>> = flow {
        emit(fetchFeedOnce().announcements)
    }

    fun fetchFeed(): Flow<STASYFeed> = flow {
        emit(fetchFeedOnce())
    }

    private suspend fun fetchFeedOnce(): STASYFeed {
        return try {
            val response = httpClient.get(ANNOUNCEMENTS_URL)
            val body = response.bodyAsText()
            val payload = json.decodeFromString<AnnouncementsPayload>(body)
            val status = payload.status?.let {
                STASYServiceStatus(
                    status = it.status,
                    rawMessage = it.rawMessage,
                    rawMessageEn = it.rawMessageEn.ifBlank { it.rawMessage },
                    rawMessageSq = it.rawMessageSq,
                    rawMessageIt = it.rawMessageIt,
                    serviceUntil = it.serviceUntil,
                )
            }
            val items = payload.announcements.map { item ->
                STASYAnnouncement(
                    id = item.id,
                    title = item.title,
                    titleEn = item.titleEn.ifBlank { item.title },
                    titleSq = item.titleSq,
                    titleIt = item.titleIt,
                    date = item.date,
                    summary = item.summary,
                    summaryEn = item.summaryEn,
                    summarySq = item.summarySq,
                    summaryIt = item.summaryIt,
                    url = item.url,
                    isServiceAlert = item.category == CATEGORY_SERVICE_ALERT,
                    affectedLines = item.affectedLines,
                    severity = item.severity.ifBlank { "info" },
                    validFrom = item.validFrom,
                    validUntil = item.validUntil,
                )
            }
            STASYFeed(status, items)
        } catch (_: Exception) {
            STASYFeed(null, emptyList())
        }
    }

    @Serializable
    private data class AnnouncementsPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val status: StatusPayload? = null,
        val announcements: List<AnnouncementItem> = emptyList(),
    )

    @Serializable
    private data class StatusPayload(
        val status: String = "unknown",
        val rawMessage: String = "",
        @SerialName("rawMessageEn") val rawMessageEn: String = "",
        @SerialName("rawMessageSq") val rawMessageSq: String = "",
        @SerialName("rawMessageIt") val rawMessageIt: String = "",
        val serviceUntil: String? = null,
    )

    @Serializable
    private data class AnnouncementItem(
        val id: String,
        val title: String,
        @SerialName("titleEn") val titleEn: String = "",
        @SerialName("titleSq") val titleSq: String = "",
        @SerialName("titleIt") val titleIt: String = "",
        val date: String = "",
        val summary: String = "",
        @SerialName("summaryEn") val summaryEn: String = "",
        @SerialName("summarySq") val summarySq: String = "",
        @SerialName("summaryIt") val summaryIt: String = "",
        val url: String = "",
        val category: String = "",
        @SerialName("affectedLines") val affectedLines: List<String> = emptyList(),
        val severity: String = "info",
        @SerialName("validFrom") val validFrom: String? = null,
        @SerialName("validUntil") val validUntil: String? = null,
    )

    private companion object {
        private const val ANNOUNCEMENTS_URL = "https://api-syrmos.peterdsp.dev/api/announcements"
        private const val CATEGORY_SERVICE_ALERT = "serviceAlert"
    }
}
