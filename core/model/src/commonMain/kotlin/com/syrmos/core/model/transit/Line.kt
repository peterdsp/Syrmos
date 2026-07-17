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
    val region: Region = Region.ATHENS,
    val status: LineStatus = LineStatus.OPERATIONAL,
) {
    /**
     * A line that does not run must never produce a departure, a train, a
     * last-train answer or a track-picker entry. It still renders on the map,
     * greyed, because the track exists and hiding it would be its own kind of
     * lie. Check this, not the id.
     */
    val isOperational: Boolean get() = status == LineStatus.OPERATIONAL
}
