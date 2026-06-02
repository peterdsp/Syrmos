package com.syrmos.core.model.schedule

import com.syrmos.core.model.transit.Direction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    @SerialName("line_id") val lineId: String,
    @SerialName("station_id") val stationId: String,
    val direction: Direction,
    @SerialName("day_type") val dayType: DayType,
    val departures: List<Departure>,
)
