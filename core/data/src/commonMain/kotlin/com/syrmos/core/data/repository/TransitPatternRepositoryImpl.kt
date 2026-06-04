package com.syrmos.core.data.repository

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.data.seed.SeedServicePattern
import kotlinx.serialization.json.Json

class TransitPatternRepositoryImpl(
    private val resourceReader: ResourceReader,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedPatterns: List<SeedServicePattern>? = null

    suspend fun getPatternsFor(
        lineId: String,
        stationId: String,
    ): List<SeedServicePattern> {
        return loadPatterns().filter { pattern ->
            pattern.lineId == lineId &&
                (pattern.stationIds == null || stationId in pattern.stationIds) &&
                (pattern.excludedStationIds == null || stationId !in pattern.excludedStationIds)
        }
    }

    private suspend fun loadPatterns(): List<SeedServicePattern> {
        cachedPatterns?.let { return it }
        val patterns = json.decodeFromString<List<SeedServicePattern>>(
            resourceReader.readText("files/seed/service_patterns.json")
        )
        cachedPatterns = patterns
        return patterns
    }
}
