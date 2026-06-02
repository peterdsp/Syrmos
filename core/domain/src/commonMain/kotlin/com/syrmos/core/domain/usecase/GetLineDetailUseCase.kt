package com.syrmos.core.domain.usecase

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Frequency
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LineDetail(
    val line: Line,
    val stations: List<Station>,
    val frequencies: List<Frequency>,
)

class GetLineDetailUseCase(
    private val lineRepository: LineRepositoryImpl,
    private val stationRepository: StationRepositoryImpl,
    private val scheduleRepository: ScheduleRepositoryImpl,
) {
    fun invoke(lineId: String, dayType: DayType = DayType.WEEKDAY): Flow<LineDetail?> {
        return combine(
            lineRepository.getLineById(lineId),
            stationRepository.getStationsOnLine(lineId),
            scheduleRepository.getFrequencies(lineId, dayType),
        ) { line, stations, frequencies ->
            line?.let {
                LineDetail(
                    line = it,
                    stations = stations,
                    frequencies = frequencies,
                )
            }
        }
    }
}
