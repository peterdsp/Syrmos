package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Route(
    @SerialName("line_id") val lineId: String,
    val direction: Direction,
    @SerialName("station_ids") val stationIds: List<String>,
    @SerialName("travel_time_minutes") val travelTimeMinutes: Int,
)

@Serializable
enum class Direction {
    @SerialName("outbound") OUTBOUND,
    @SerialName("inbound") INBOUND,
}
