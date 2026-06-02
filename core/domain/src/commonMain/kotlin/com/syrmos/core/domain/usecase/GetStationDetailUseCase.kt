package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class StationDetail(
    val station: Station,
    val connectingLines: List<Line>,
)

class GetStationDetailUseCase(
    private val stationRepository: StationRepositoryImpl,
    private val lineRepository: LineRepositoryImpl,
) {
    fun invoke(stationId: String): Flow<StationDetail?> {
        return combine(
            stationRepository.getStationById(stationId),
            lineRepository.getAllLines(),
        ) { station, allLines ->
            station?.let {
                StationDetail(
                    station = it,
                    connectingLines = allLines.filter { line ->
                        it.lineIds.contains(line.id)
                    },
                )
            }
        }
    }
}
