package com.syrmos.core.network

import com.syrmos.core.common.AppLanguage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RailNewsItem(
    val id: String,
    val title: String,
    val titleEn: String,
    val titleSq: String = "",
    val titleIt: String = "",
    val summary: String = "",
    val summaryEn: String = "",
    val summarySq: String = "",
    val summaryIt: String = "",
    val url: String,
    val publishedAt: String,
    val thumbnailUrl: String = "",
    val categories: List<String> = emptyList(),
) {
    fun localizedTitle(language: AppLanguage): String = when (language) {
        AppLanguage.GREEK -> title
        AppLanguage.ALBANIAN -> titleSq.takeIf { it.isUsableLocalizedContent() }
            ?: "Njoftim hekurudhor"
        AppLanguage.ITALIAN -> titleIt.takeIf { it.isUsableLocalizedContent() }
            ?: "Avviso ferroviario"
        else -> titleEn.takeIf { it.isUsableLocalizedContent() }
            ?: title.takeIf { it.isUsableLocalizedContent() }
            ?: "Rail announcement"
    }

    fun localizedSummary(language: AppLanguage): String = when (language) {
        AppLanguage.GREEK -> summary
        AppLanguage.ALBANIAN -> summarySq.takeIf { it.isUsableLocalizedContent() }
            ?: "Hap njoftimin zyrtar për hollësi të plota."
        AppLanguage.ITALIAN -> summaryIt.takeIf { it.isUsableLocalizedContent() }
            ?: "Apri l'avviso ufficiale per tutti i dettagli."
        else -> summaryEn.takeIf { it.isUsableLocalizedContent() }
            ?: summary.takeIf { it.isUsableLocalizedContent() }
            ?: "Open the official notice for full details."
    }
}

class RailNewsService(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchNews(): Flow<List<RailNewsItem>> = flow {
        emit(fetchOnce())
    }

    private suspend fun fetchOnce(): List<RailNewsItem> {
        return try {
            val response = httpClient.get(NEWS_URL)
            val body = response.bodyAsText()
            val payload = json.decodeFromString<NewsPayload>(body)
            payload.news
                // Skip a malformed row rather than failing the whole feed to one
                // bad entry (id/title default to blank below).
                .filter { it.title.isNotBlank() }
                .map { item ->
                RailNewsItem(
                    id = item.id,
                    title = item.title,
                    titleEn = item.titleEn.ifBlank { item.title },
                    titleSq = item.titleSq,
                    titleIt = item.titleIt,
                    summary = item.summary,
                    summaryEn = item.summaryEn,
                    summarySq = item.summarySq,
                    summaryIt = item.summaryIt,
                    url = item.url,
                    publishedAt = item.publishedAt,
                    thumbnailUrl = item.thumbnailUrl,
                    categories = item.categories,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class NewsPayload(
        @SerialName("updatedAt") val updatedAt: String? = null,
        val count: Int = 0,
        val news: List<NewsItemPayload> = emptyList(),
    )

    @Serializable
    private data class NewsItemPayload(
        // Defaulted so one malformed row doesn't fail the whole news payload.
        val id: String = "",
        val title: String = "",
        @SerialName("titleEn") val titleEn: String = "",
        @SerialName("titleSq") val titleSq: String = "",
        @SerialName("titleIt") val titleIt: String = "",
        val summary: String = "",
        @SerialName("summaryEn") val summaryEn: String = "",
        @SerialName("summarySq") val summarySq: String = "",
        @SerialName("summaryIt") val summaryIt: String = "",
        val url: String = "",
        @SerialName("publishedAt") val publishedAt: String = "",
        @SerialName("thumbnailUrl") val thumbnailUrl: String = "",
        val categories: List<String> = emptyList(),
    )

    private companion object {
        private const val NEWS_URL = "https://api-syrmos.peterdsp.dev/api/news"
    }
}
