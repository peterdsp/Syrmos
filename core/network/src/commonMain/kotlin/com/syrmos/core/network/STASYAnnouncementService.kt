package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class STASYAnnouncement(
    val id: String,
    val title: String,
    val date: String,
    val url: String,
    val isServiceAlert: Boolean,
)

class STASYAnnouncementService(
    private val httpClient: HttpClient,
) {
    fun fetchAnnouncements(): Flow<List<STASYAnnouncement>> = flow {
        try {
            val response = httpClient.get("https://www.stasy.gr") {
                header("User-Agent", "Mozilla/5.0 (compatible; Syrmos/1.0)")
            }
            val html = response.bodyAsText()
            emit(parseAnnouncements(html))
        } catch (_: Exception) {
            emit(emptyList())
        }
    }

    private fun parseAnnouncements(html: String): List<STASYAnnouncement> {
        val results = mutableListOf<STASYAnnouncement>()

        // Parse WordPress post links from stasy.gr
        val linkPattern = Regex(
            """href="(https://www\.stasy\.gr/[^"]+)"[^>]*>([^<]{15,})</a>""",
        )

        for (match in linkPattern.findAll(html)) {
            val url = match.groupValues[1]
            val title = match.groupValues[2]
                .trim()
                .replace("&#8211;", "–")
                .replace("&#8230;", "…")
                .replace("&amp;", "&")

            if (url.contains("#") || title.contains("menu", ignoreCase = true)) continue

            // Check if nearby text contains "Έκτακτες" (service alert marker)
            val matchStart = match.range.first
            val contextStart = (matchStart - 300).coerceAtLeast(0)
            val context = html.substring(contextStart, matchStart)
            val isAlert = context.contains("Έκτακτες") || context.contains("ektaktes", ignoreCase = true)

            val datePattern = Regex(
                """(\d{1,2}\s+(?:Ιανουαρίου|Φεβρουαρίου|Μαρτίου|Απριλίου|Μαΐου|Ιουνίου|Ιουλίου|Αυγούστου|Σεπτεμβρίου|Οκτωβρίου|Νοεμβρίου|Δεκεμβρίου),?\s*\d{4})""",
            )
            val matchEnd = match.range.last
            val dateContext = html.substring(
                (matchStart - 300).coerceAtLeast(0),
                (matchEnd + 300).coerceAtMost(html.length),
            )
            val dateMatch = datePattern.find(dateContext)

            val announcement = STASYAnnouncement(
                id = url,
                title = title,
                date = dateMatch?.groupValues?.get(1).orEmpty(),
                url = url,
                isServiceAlert = isAlert,
            )

            if (results.none { it.id == announcement.id }) {
                results.add(announcement)
            }
        }

        return results
    }
}
