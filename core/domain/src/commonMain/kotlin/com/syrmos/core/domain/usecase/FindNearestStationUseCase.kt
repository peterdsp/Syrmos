package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.location.NearestStationResult
import com.syrmos.core.model.location.UserLocation
import kotlinx.coroutines.flow.Flow

class FindNearestStationUseCase(
    private val stationRepository: StationRepositoryImpl,
) {
    fun invoke(
        location: UserLocation,
        limit: Int = 5,
    ): Flow<List<NearestStationResult>> {
        return stationRepository.findNearestStations(location, limit)
    }
}
