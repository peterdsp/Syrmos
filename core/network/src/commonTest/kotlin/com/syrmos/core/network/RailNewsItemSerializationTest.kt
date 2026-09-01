package com.syrmos.core.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RailNewsItem must round-trip through JSON so the offline cache
 * (RailNewsRepository / DatabaseRailNewsCache, audit #19) can persist and
 * restore the last good news payload. Pins the @Serializable contract and the
 * multilingual fields the cache relies on.
 */
class RailNewsItemSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun listRoundTripsThroughJson() {
        val original = listOf(
            RailNewsItem(
                id = "1",
                title = "Απεργία στο μετρό",
                titleEn = "Metro strike",
                titleSq = "Grevë në metro",
                titleIt = "Sciopero metro",
                summary = "Λεπτομέρειες",
                summaryEn = "Details",
                url = "https://example.com/1",
                publishedAt = "2026-09-01T08:00:00Z",
                categories = listOf("strike", "metro"),
            ),
            RailNewsItem(
                id = "2",
                title = "Νέο δρομολόγιο",
                titleEn = "New service",
                url = "https://example.com/2",
                publishedAt = "2026-09-01T09:30:00Z",
            ),
        )
        val decoded = json.decodeFromString<List<RailNewsItem>>(json.encodeToString(original))
        assertEquals(original, decoded, "the cached list must decode back identically")
    }
}
