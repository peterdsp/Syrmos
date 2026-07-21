package com.syrmos.core.domain.usecase

import com.syrmos.core.common.extensions.currentAthensDayOfWeek
import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.common.extensions.minutesUntil
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.common.extensions.toDisplayString
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Departure
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek

data class UpcomingDeparture(
    val time: String,
    val minutesAway: Int,
    val direction: Direction,
    val lineId: String,
    val notes: String? = null,
    val serviceType: String? = null,
    /** Where this row came from, so the UI can show a source-confidence chip. */
    val sourceConfidence: SourceConfidence = SourceConfidence.UNKNOWN,
)

class GetNextDeparturesUseCase(
    private val scheduleRepository: ScheduleRepositoryImpl,
    private val bandProjector: ComputeDeparturesFromBandsUseCase? = null,
    private val serverProjector: SyrmosSchedulesService? = null,
) {
    fun invoke(
        stationId: String,
        lineId: String,
        direction: Direction,
        limit: Int = 5,
    ): Flow<List<UpcomingDeparture>> {
        val fallback = fallbackDepartures(
            stationId = stationId,
            lineId = lineId,
            direction = direction,
            limit = limit,
        )
        if (serverProjector != null) {
            return flow {
                val lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId)
                val projected = serverProjector.fetchProjectedDepartures(
                    stationId = stationId,
                    lineIds = lineIds,
                    limit = limit,
                    direction = direction.name.lowercase(),
                ).firstOrNull()?.departures.orEmpty()
                if (projected.isNotEmpty()) {
                    emit(projected.mapNotNull { it.toUpcomingDeparture(direction) })
                } else {
                    val localProjected = projectFromBands(
                        stationId = stationId,
                        lineId = lineId,
                        direction = direction,
                        limit = limit,
                    )
                    if (localProjected.isNotEmpty()) {
                        emit(localProjected)
                    } else {
                        emitAll(fallback)
                    }
                }
            }
        }

        // Source of truth: live API frequency_bands. Empty when bundles
        // haven't been fetched yet (offline cold-start), we then fall
        // through to the bundled seed for an offline-first first impression.
        val localProjected = projectFromBands(
            stationId = stationId,
            lineId = lineId,
            direction = direction,
            limit = limit,
        )
        if (localProjected.isNotEmpty()) {
            return kotlinx.coroutines.flow.flowOf(localProjected)
        }

        return fallback
    }

    private fun projectFromBands(
        stationId: String,
        lineId: String,
        direction: Direction,
        limit: Int,
    ): List<UpcomingDeparture> {
        val projector = bandProjector ?: return emptyList()
        val lineIds = if (lineId == "M3") listOf("M3", "M3_AIR") else listOf(lineId)
        // Projected from a frequency band, not an exact timetabled minute.
        return projector.invoke(
            lineIds = lineIds,
            direction = direction,
            limit = limit,
            stationId = stationId,
        ).map { it.copy(sourceConfidence = SourceConfidence.ESTIMATED) }
    }

    private fun fallbackDepartures(
        stationId: String,
        lineId: String,
        direction: Direction,
        limit: Int,
    ): Flow<List<UpcomingDeparture>> {
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
                    // Bundled seed timetable - the offline-first snapshot.
                    sourceConfidence = SourceConfidence.OFFLINE,
                )
            }
        }
    }

    private fun SyrmosSchedulesService.ProjectedDeparture.toUpcomingDeparture(
        fallbackDirection: Direction,
    ): UpcomingDeparture? {
        val resolvedLineId = lineId.ifBlank { line }
        if (resolvedLineId.isBlank() || time.isBlank()) return null
        return UpcomingDeparture(
            time = time,
            minutesAway = minutesAway,
            direction = when (directionKey.lowercase()) {
                "inbound" -> Direction.INBOUND
                "outbound" -> Direction.OUTBOUND
                else -> fallbackDirection
            },
            lineId = resolvedLineId,
            notes = direction.ifBlank { null },
            serviceType = serviceType.ifBlank { null },
            // Timetabled departure served from the live schedules API.
            sourceConfidence = SourceConfidence.SCHEDULED,
        )
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
