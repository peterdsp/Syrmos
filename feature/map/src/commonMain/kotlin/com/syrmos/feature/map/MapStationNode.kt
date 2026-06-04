package com.syrmos.feature.map

import com.syrmos.core.model.transit.Station
import kotlin.math.roundToLong

data class MapStationNode(
    val id: String,
    val stationIds: List<String>,
    val stationIdByLineId: Map<String, String>,
    val name: String,
    val nameEl: String,
    val latitude: Double,
    val longitude: Double,
    val lineIds: List<String>,
    val isInterchange: Boolean,
    val accessibility: Boolean,
    val zone: Int,
) {
    fun displayName(): String = name.ifBlank { nameEl }

    companion object {
        fun fromStations(stations: List<Station>): List<MapStationNode> {
            return stations
                .groupBy { station ->
                    "${station.latitude.toMapKey()}|${station.longitude.toMapKey()}"
                }
                .map { (_, groupedStations) ->
                    val primary = groupedStations.first()
                    val lineIds = groupedStations
                        .flatMap { it.lineIds }
                        .distinct()
                    val stationIdByLineId = groupedStations
                        .flatMap { station -> station.lineIds.map { lineId -> lineId to station.id } }
                        .distinctBy { it.first }
                        .toMap()

                    MapStationNode(
                        id = "${primary.latitude.toMapKey()}_${primary.longitude.toMapKey()}",
                        stationIds = groupedStations.map { it.id },
                        stationIdByLineId = stationIdByLineId,
                        name = primary.name,
                        nameEl = primary.nameEl,
                        latitude = primary.latitude,
                        longitude = primary.longitude,
                        lineIds = lineIds,
                        isInterchange = lineIds.size > 1 || groupedStations.any { it.isInterchange },
                        accessibility = groupedStations.any { it.accessibility },
                        zone = groupedStations.minOf { it.zone },
                    )
                }
                .sortedWith(compareBy<MapStationNode> { it.latitude }.thenBy { it.longitude }.thenBy { it.name })
        }
    }
}

private fun Double.toMapKey(): String = (this * 1_000_000).roundToLong().toString()
