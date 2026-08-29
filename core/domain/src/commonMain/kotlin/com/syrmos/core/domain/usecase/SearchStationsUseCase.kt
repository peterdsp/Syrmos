package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow

class SearchStationsUseCase(
    private val stationRepository: StationRepositoryImpl,
) {
    fun invoke(query: String): Flow<List<Station>> {
        // Trim BEFORE the length guard: a whitespace-only query ("  ") is length
        // 2, so it used to slip through, and the repo's seed fallback then
        // matched every station on contains("") — returning the entire dataset.
        // Trimming also keeps the DB and fallback paths consistent.
        val trimmed = query.trim()
        if (trimmed.length < 2) return kotlinx.coroutines.flow.flowOf(emptyList())
        return stationRepository.searchStations(trimmed)
    }
}
