package com.syrmos.core.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeedLine(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    val type: String,
    val color: String,
    @SerialName("terminal_a") val terminalA: String,
    @SerialName("terminal_b") val terminalB: String,
    @SerialName("station_count") val stationCount: Int,
)

@Serializable
data class SeedStation(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("line_ids") val lineIds: List<String>,
    @SerialName("is_interchange") val isInterchange: Boolean = false,
    val accessibility: Boolean = true,
    val zone: Int = 1,
)

@Serializable
data class SeedTransfer(
    @SerialName("station_id") val stationId: String,
    @SerialName("from_line_id") val fromLineId: String,
    @SerialName("to_line_id") val toLineId: String,
    @SerialName("walking_minutes") val walkingMinutes: Int = 3,
)

@Serializable
data class SeedFrequency(
    @SerialName("line_id") val lineId: String,
    @SerialName("day_type") val dayType: String,
    @SerialName("time_range") val timeRange: String,
    @SerialName("frequency_minutes") val frequencyMinutes: Int,
)
