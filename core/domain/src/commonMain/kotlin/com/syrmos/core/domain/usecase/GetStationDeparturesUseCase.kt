package com.syrmos.core.domain.usecase

import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.data.repository.TransitPatternRepositoryImpl
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.first

class GetStationDeparturesUseCase(
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val transitPatternRepository: TransitPatternRepositoryImpl,
) {
    suspend fun invoke(stationId: String, lineIds: List<String>): List<UpcomingDeparture> {
        val allDepartures = mutableListOf<UpcomingDeparture>()

        lineIds.forEach { lineId ->
            val patterns = transitPatternRepository.getPatternsFor(lineId, stationId)
            if (patterns.isNotEmpty()) {
                val now = currentAthensTime()
                patterns.forEach { pattern ->
                    (1..4).forEach { multiplier ->
                        val minutesAway = pattern.frequencyMinutes * multiplier
                        val totalMinutes = (now.hour * 60 + now.minute + minutesAway) % (24 * 60)
                        val hour = totalMinutes / 60
                        val minute = totalMinutes % 60
                        val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                        allDepartures += UpcomingDeparture(
                            time = time,
                            minutesAway = minutesAway,
                            direction = Direction.OUTBOUND,
                            lineId = lineId,
                            notes = pattern.direction,
                        )
                    }
                }
            } else {
                Direction.entries.forEach { direction ->
                    val departures = getNextDepartures.invoke(
                        stationId = stationId,
                        lineId = lineId,
                        direction = direction,
                        limit = 2,
                    ).first()
                    allDepartures.addAll(departures)
                }
            }
        }

        return allDepartures.sortedBy { it.minutesAway }.take(8)
    }
}
