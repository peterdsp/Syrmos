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
                // Anchor departures to clock-aligned slots so the countdown
                // actually ticks down each time the user looks at the screen.
                // e.g. on a 5-minute frequency at 14:31 the next slots are
                // 14:35, 14:40, 14:45, 14:50 — at 14:32 the first one is
                // "3 min" not still "5 min".
                val now = currentAthensTime()
                val nowMinutes = now.hour * 60 + now.minute
                val secondOffset = if (now.second >= 30) 1 else 0
                patterns.forEach { pattern ->
                    val freq = pattern.frequencyMinutes.coerceAtLeast(1)
                    var nextSlot = ((nowMinutes / freq) + 1) * freq
                    repeat(4) {
                        val minutesAway = (nextSlot - nowMinutes - secondOffset).coerceAtLeast(0)
                        val slotMinutes = nextSlot % (24 * 60)
                        val hour = slotMinutes / 60
                        val minute = slotMinutes % 60
                        val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                        allDepartures += UpcomingDeparture(
                            time = time,
                            minutesAway = minutesAway,
                            direction = Direction.OUTBOUND,
                            lineId = lineId,
                            notes = pattern.direction,
                        )
                        nextSlot += freq
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
