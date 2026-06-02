package com.syrmos.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Frequency(
    @SerialName("line_id") val lineId: String,
    @SerialName("day_type") val dayType: DayType,
    @SerialName("time_range") val timeRange: String,
    @SerialName("frequency_minutes") val frequencyMinutes: Int,
)
