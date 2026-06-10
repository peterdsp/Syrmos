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
    val date: String,
    val url: String,
    val isServiceAlert: Boolean,
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
        try {
            val response = httpClient.get(ANNOUNCEMENTS_URL)
            val body = response.bodyAsText()
            val payload = json.decodeFromString<AnnouncementsPayload>(body)
            emit(
                payload.announcements.map { item ->
                    STASYAnnouncement(
                        id = item.id,
                        title = item.title,
                        date = item.date,
                        url = item.url,
                        isServiceAlert = item.category == CATEGORY_SERVICE_ALERT,
                    )
                },
            )
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    @Serializable
    private data class AnnouncementsPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val announcements: List<AnnouncementItem> = emptyList(),
    )

    @Serializable
    private data class AnnouncementItem(
        val id: String,
        val title: String,
        val date: String = "",
        val summary: String = "",
        val url: String = "",
        val category: String = "",
    )

    private companion object {
        private const val ANNOUNCEMENTS_URL = "https://api-syrmos.peterdsp.dev/api/announcements"
        private const val CATEGORY_SERVICE_ALERT = "serviceAlert"
    }
}
