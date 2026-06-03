package com.syrmos.core.data.repository

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.model.schedule.DayType
import com.syrmos.core.model.schedule.Departure
import com.syrmos.core.model.schedule.Frequency
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ScheduleRepositoryImpl(
    private val database: SyrmosDatabase,
) {
    fun getNextDepartures(
        stationId: String,
        lineId: String,
        direction: Direction,
        dayType: DayType,
        currentTime: String,
        limit: Int = 5,
    ): Flow<List<Departure>> = flow {
        val departures = database.syrmosDatabaseQueries.getNextDepartures(
            station_id = stationId,
            line_id = lineId,
            direction = direction.name.lowercase(),
            day_type = dayType.name.lowercase(),
            departure_time = currentTime,
            value_ = limit.toLong(),
        ).executeAsList().map { row ->
            Departure(time = row.departure_time, notes = row.notes)
        }
        emit(departures)
    }

    fun getAllDepartures(
        stationId: String,
        lineId: String,
        direction: Direction,
        dayType: DayType,
    ): Flow<List<Departure>> = flow {
        val departures = database.syrmosDatabaseQueries.getAllDepartures(
            station_id = stationId,
            line_id = lineId,
            direction = direction.name.lowercase(),
            day_type = dayType.name.lowercase(),
        ).executeAsList().map { row ->
            Departure(time = row.departure_time, notes = row.notes)
        }
        emit(departures)
    }

    fun getFrequencies(
        lineId: String,
        dayType: DayType,
    ): Flow<List<Frequency>> = flow {
        val frequencies = database.syrmosDatabaseQueries.getFrequencies(
            line_id = lineId,
            day_type = dayType.name.lowercase(),
        ).executeAsList().map { entity ->
            Frequency(
                lineId = entity.line_id,
                dayType = DayType.valueOf(entity.day_type.uppercase()),
                timeRange = entity.time_range,
                frequencyMinutes = entity.frequency_minutes.toInt(),
            )
        }
        emit(frequencies)
    }
}
