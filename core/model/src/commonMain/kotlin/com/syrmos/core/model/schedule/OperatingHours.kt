package com.syrmos.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OperatingHours(
    @SerialName("line_id") val lineId: String,
    val direction: String,
    @SerialName("day_type") val dayType: DayType,
    @SerialName("first_departure") val firstDeparture: String,
    @SerialName("last_departure") val lastDeparture: String,
)
