package com.syrmos.core.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The generator's `schedules-v2/lines.json`: `{version, updatedAt, lines: [...]}`.
 *
 * This is the single source of truth for lines and stations. It replaces the old
 * flat `seed/lines.json`, which was generated from hardcoded Swift by a sync
 * script that had been broken since the June 2026 iOS restructure, so the two
 * copies silently drifted apart (86 of 201 station ids diverged). See
 * docs/plans/2026-07-17-server-as-single-source-for-lines.md.
 */
@Serializable
data class SeedLinesPayload(
    val version: Int = 0,
    val updatedAt: String = "",
    val lines: List<SeedLine> = emptyList(),
)

@Serializable
data class SeedLine(
    val id: String,
    val name: String,
    val nameEl: String,
    val type: String,
    val color: String,
    val terminalA: String,
    val terminalB: String,
    val stationCount: Int,
    /** athens | thessaloniki | national. Defaulted so a stale bundle still parses. */
    val region: String = "athens",
    /**
     * operational | under_construction. A non-operational line still renders, greyed,
     * but must never produce a departure or a train. Defaults to operational so an
     * older bundle behaves exactly as it does today.
     */
    val status: String = "operational",
    val stations: List<SeedLineStation> = emptyList(),
)

/** A station as nested under a line in `schedules-v2/lines.json`. */
@Serializable
data class SeedLineStation(
    val id: String,
    val name: String,
    val nameEl: String,
    val lat: Double,
    val lng: Double,
    val region: String = "athens",
    val accessibility: Boolean = true,
    val zone: Int = 1,
)

@Serializable
data class SeedStation(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    @SerialName("name_sq") val nameSq: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("line_ids") val lineIds: List<String>,
    @SerialName("is_interchange") val isInterchange: Boolean = false,
    val accessibility: Boolean = true,
    val zone: Int = 1,
    val region: String = "athens",
    @SerialName("source_confidence") val sourceConfidence: String = "scheduled",
)

@Serializable
data class SeedTransfer(
    @SerialName("station_id") val stationId: String,
    @SerialName("from_line_id") val fromLineId: String,
    @SerialName("to_line_id") val toLineId: String,
    @SerialName("walking_minutes") val walkingMinutes: Int = 3,
)

@Serializable
data class SeedRoute(
    @SerialName("line_id") val lineId: String,
    @SerialName("station_ids") val stationIds: List<String>,
)

@Serializable
data class SeedFrequency(
    @SerialName("line_id") val lineId: String,
    @SerialName("day_type") val dayType: String,
    @SerialName("time_range") val timeRange: String,
    @SerialName("frequency_minutes") val frequencyMinutes: Int,
)

/**
 * One `schedules-v2/{lineId}.json` file, only the bits the offline departures
 * seed needs. National rail + rail-replacement buses publish a full per-train
 * `trips` timetable (and no frequency `bands`); metro/tram/suburban use bands
 * instead and carry an empty `trips`. The map's TrainSimulator already reads
 * these trips to animate vehicles; we expand them into schedule_entity so the
 * bottom-sheet departures work offline for the trip-based lines too.
 */
@Serializable
data class SeedLineScheduleFile(
    @SerialName("lineId") val lineId: String = "",
    @SerialName("trips") val trips: List<SeedTripEntry> = emptyList(),
)

@Serializable
data class SeedTripEntry(
    @SerialName("direction") val direction: String = "",
    @SerialName("dayType") val dayType: String = "",
    @SerialName("stops") val stops: List<SeedTripStop> = emptyList(),
)

@Serializable
data class SeedTripStop(
    @SerialName("stationId") val stationId: String = "",
    @SerialName("departureTime") val departureTime: String = "",
)

@Serializable
data class SeedServicePattern(
    @SerialName("line_id") val lineId: String,
    val direction: String,
    @SerialName("frequency_minutes") val frequencyMinutes: Int,
    @SerialName("service_type") val serviceType: String,
    @SerialName("station_ids") val stationIds: List<String>? = null,
    @SerialName("excluded_station_ids") val excludedStationIds: List<String>? = null,
)
