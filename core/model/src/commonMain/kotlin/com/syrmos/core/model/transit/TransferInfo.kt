package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransferInfo(
    @SerialName("station_id") val stationId: String,
    @SerialName("from_line_id") val fromLineId: String,
    @SerialName("to_line_id") val toLineId: String,
    @SerialName("walking_minutes") val walkingMinutes: Int = 3,
)
