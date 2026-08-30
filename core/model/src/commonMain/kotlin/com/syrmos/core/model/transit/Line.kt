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
     * A line that carries scheduled service and may therefore produce a
     * departure, a train, a last-train answer or a track-picker entry.
     *
     * [LineStatus.SEASONAL] counts as operational: a seasonal line (the Pelion
     * railway) is a real boardable service whose own dated trips already gate it
     * to the days it runs, so it draws in colour and lists like any other line.
     * Only the built-but-closed states ([isBuiltButClosed]) are excluded. Check
     * this, not the id.
     */
    val isOperational: Boolean
        get() = status == LineStatus.OPERATIONAL || status == LineStatus.SEASONAL

    /**
     * Track that exists but carries no service right now: never opened
     * ([LineStatus.UNDER_CONSTRUCTION]) or a real line temporarily halted
     * ([LineStatus.SUSPENDED]). It still renders on the map, greyed, because
     * hiding real track would be its own kind of lie, but it is labelled so it
     * can never be mistaken for a line in service.
     */
    val isBuiltButClosed: Boolean
        get() = status == LineStatus.UNDER_CONSTRUCTION || status == LineStatus.SUSPENDED

    /** A real line temporarily not running (rockfalls, works). */
    val isSuspended: Boolean get() = status == LineStatus.SUSPENDED

    /** Runs only part of the year / on some day-types (Pelion railway). */
    val isSeasonal: Boolean get() = status == LineStatus.SEASONAL
}
