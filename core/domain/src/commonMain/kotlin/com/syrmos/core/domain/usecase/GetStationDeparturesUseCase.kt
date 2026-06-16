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
            // Source of truth: the server projector when online, the bundled
            // frequency_bands + station_offsets when offline. Either way we
            // ask per direction so both terminals get a fair shot at the
            // visible slots; calling once with a single direction would
            // starve the other side (the bug behind one-direction-only
            // offline screens at Kerameikos etc.).
            val bandsResults = mutableListOf<UpcomingDeparture>()
            Direction.entries.forEach { direction ->
                val departures = getNextDepartures.invoke(
                    stationId = stationId,
                    lineId = lineId,
                    direction = direction,
                    limit = 8,
                ).first()
                bandsResults.addAll(departures)
            }
            if (bandsResults.isNotEmpty()) {
                allDepartures.addAll(bandsResults)
                return@forEach
            }

            // Last resort: cruder synthetic patterns from service_patterns.json.
            // Hit only when neither server nor bundled bands have anything for
            // this line — keeps the screen non-blank on a totally cold install.
            val patterns = transitPatternRepository.getPatternsFor(lineId, stationId)
            if (patterns.isEmpty()) return@forEach
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
        }

        return allDepartures.sortedBy { it.minutesAway }.take(8)
    }
}
