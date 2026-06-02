package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Line(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    val type: LineType,
    val color: LineColor,
    @SerialName("terminal_a") val terminalA: String,
    @SerialName("terminal_b") val terminalB: String,
    @SerialName("station_count") val stationCount: Int,
)
