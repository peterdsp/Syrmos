package com.syrmos.core.data.sync

import com.syrmos.core.network.RailNewsItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rail-news offline cache decision (audit #19). A non-empty fetch is
 * authoritative and is cached; an empty fetch (the service returns an empty
 * list on any network failure) must fall back to the last cached payload
 * instead of blanking the news section.
 */
class RailNewsRepositoryTest {

    private class FakeNewsCache(private var stored: List<RailNewsItem> = emptyList()) : RailNewsCache {
        var saveCount = 0
        override fun save(items: List<RailNewsItem>) {
            stored = items
            saveCount++
        }
        override fun load(): List<RailNewsItem> = stored
    }

    private fun item(id: String) = RailNewsItem(
        id = id,
        title = "Title $id",
        titleEn = "Title $id",
        url = "https://example.com/$id",
        publishedAt = "2026-09-01T10:00:00Z",
    )

    @Test
    fun freshNonEmptyIsCachedAndReturned() {
        val cache = FakeNewsCache()
        val fresh = listOf(item("a"), item("b"))
        val result = RailNewsRepository.resolve(fresh, cache)
        assertEquals(fresh, result, "a non-empty fetch is returned as-is")
        assertEquals(1, cache.saveCount, "a non-empty fetch is written to the cache")
        assertEquals(fresh, cache.load(), "the cache now holds the fresh payload")
    }

    @Test
    fun emptyFetchFallsBackToCache() {
        val cached = listOf(item("old"))
        val cache = FakeNewsCache(cached)
        val result = RailNewsRepository.resolve(emptyList(), cache)
        assertEquals(cached, result, "an empty fetch serves the last cached payload")
        assertEquals(0, cache.saveCount, "an empty fetch never overwrites the cache")
    }

    @Test
    fun emptyFetchWithNoCacheReturnsEmpty() {
        val cache = FakeNewsCache()
        val result = RailNewsRepository.resolve(emptyList(), cache)
        assertTrue(result.isEmpty(), "no fetch and no cache yields an empty list, never a crash")
    }
}
