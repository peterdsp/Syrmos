package com.syrmos.core.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeedScheduleFile(
    @SerialName("line_id") val lineId: String,
    val direction: String,
    @SerialName("day_type") val dayType: String,
    val note: String? = null,
    @SerialName("station_departures") val stationDepartures: Map<String, List<String>>,
)
