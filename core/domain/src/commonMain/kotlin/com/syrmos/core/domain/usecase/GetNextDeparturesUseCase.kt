package com.syrmos.core.domain.usecase

import com.syrmos.core.common.extensions.currentAthensDayOfWeek
import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.common.extensions.minutesUntil
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.common.extensions.toDisplayString
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Departure
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek

data class UpcomingDeparture(
    val time: String,
    val minutesAway: Int,
    val direction: Direction,
    val lineId: String,
    val notes: String? = null,
)

class GetNextDeparturesUseCase(
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val bandProjector: ComputeDeparturesFromBandsUseCase? = null,
) {
    fun invoke(
        stationId: String,
        lineId: String,
        direction: Direction,
        limit: Int = 5,
    ): Flow<List<UpcomingDeparture>> {
        // Source of truth: live API frequency_bands. Empty when bundles
        // haven't been fetched yet (offline cold-start) — we then fall
        // through to the bundled seed for an offline-first first impression.
        if (bandProjector != null) {
            val lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId)
            val live = bandProjector.invoke(
                lineIds = lineIds,
                direction = direction,
                limit = limit,
                stationId = stationId,
            )
            if (live.isNotEmpty()) {
                return kotlinx.coroutines.flow.flowOf(live)
            }
        }

        val now = currentAthensTime()
        val dayType = resolveCurrentDayType()
        val currentTimeString = now.toDisplayString()

        return scheduleRepository.getNextDepartures(
            stationId = stationId,
            lineId = lineId,
            direction = direction,
            dayType = dayType,
            currentTime = currentTimeString,
            limit = limit,
        ).map { departures ->
            departures.map { departure ->
                val departureTime = parseTime(departure.time)
                val minutesAway = now.minutesUntil(departureTime)
                UpcomingDeparture(
                    time = departure.time,
                    minutesAway = minutesAway,
                    direction = direction,
                    lineId = lineId,
                    notes = departure.notes,
                )
            }
        }
    }

    private fun resolveCurrentDayType(): DayType {
        return when (currentAthensDayOfWeek()) {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY -> DayType.WEEKDAY
            DayOfWeek.FRIDAY -> DayType.FRIDAY
            DayOfWeek.SATURDAY -> DayType.SATURDAY
            DayOfWeek.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
    }
}
