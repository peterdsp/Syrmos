package com.syrmos.core.data.sync

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.network.RailNewsItem
import com.syrmos.core.network.RailNewsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Rail news with an offline cache. [RailNewsService] returns an empty list on
 * any network failure, which used to blank the news section on Android the
 * moment the device went offline (iOS keeps the last payload in UserDefaults).
 *
 * This repository writes every non-empty fetch to a persistent cache and, when
 * a fetch comes back empty, serves the last good payload instead of nothing.
 * The cache is behind [RailNewsCache] so the fetch/cache decision is unit-
 * testable without a database (the DB-backed implementation mirrors
 * WeatherRepository's thin, degrade-to-null persistence).
 */
class RailNewsRepository(
    private val railNewsService: RailNewsService,
    private val cache: RailNewsCache,
) {
    fun fetchNews(): Flow<List<RailNewsItem>> = flow {
        val fresh = railNewsService.fetchNews().firstOrNull().orEmpty()
        emit(resolve(fresh, cache))
    }

    companion object {
        /**
         * A non-empty fetch is authoritative: cache it and use it. An empty
         * fetch means "no network / feed down" (the service swallows errors to
         * an empty list), so fall back to the last cached payload rather than
         * showing a blank section. Pure over [cache] so it is testable with a
         * fake.
         */
        internal fun resolve(
            fresh: List<RailNewsItem>,
            cache: RailNewsCache,
        ): List<RailNewsItem> {
            if (fresh.isNotEmpty()) {
                cache.save(fresh)
                return fresh
            }
            return cache.load()
        }
    }
}

/**
 * Persistence seam for the rail-news offline cache. Synchronous because the
 * backing store is SQLDelight (non-suspending, like WeatherRepository's cache),
 * which keeps the fetch/cache decision testable without a coroutine builder.
 */
interface RailNewsCache {
    fun save(items: List<RailNewsItem>)
    fun load(): List<RailNewsItem>
}

/**
 * Stores the news payload as a JSON blob in metadata_entity. Degrades to a
 * no-op / empty read when there is no database (parity with WeatherRepository's
 * nullable database), and never throws: a decode failure just yields no cache.
 */
class DatabaseRailNewsCache(
    private val database: SyrmosDatabase? = null,
) : RailNewsCache {
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(items: List<RailNewsItem>) {
        val db = database ?: return
        runCatching {
            db.syrmosDatabaseQueries.setMetadata(CACHE_KEY, json.encodeToString(items))
        }
    }

    override fun load(): List<RailNewsItem> {
        val db = database ?: return emptyList()
        return runCatching {
            val raw = db.syrmosDatabaseQueries.getMetadata(CACHE_KEY).executeAsOneOrNull()
                ?: return emptyList()
            json.decodeFromString<List<RailNewsItem>>(raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        // Versioned so a future RailNewsItem shape change starts from a clean
        // cache instead of failing to decode an old blob on every launch.
        const val CACHE_KEY = "rail_news_cache_v1"
    }
}
