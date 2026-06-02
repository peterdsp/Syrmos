package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow

class SearchStationsUseCase(
    private val stationRepository: StationRepositoryImpl,
) {
    fun invoke(query: String): Flow<List<Station>> {
        if (query.length < 2) return kotlinx.coroutines.flow.flowOf(emptyList())
        return stationRepository.searchStations(query)
    }
}
