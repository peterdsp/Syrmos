package com.syrmos.feature.map

import com.syrmos.core.model.transit.Station

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
                    station.displayKey()
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
                        id = primary.displayKey(),
                        stationIds = groupedStations.map { it.id },
                        stationIdByLineId = stationIdByLineId,
                        name = primary.name,
                        nameEl = primary.nameEl,
                        latitude = groupedStations.map { it.latitude }.average(),
                        longitude = groupedStations.map { it.longitude }.average(),
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

private fun Station.displayKey(): String {
    val source = name.ifBlank { nameEl }
    return source
        .lowercase()
        .replace("ά", "α")
        .replace("έ", "ε")
        .replace("ή", "η")
        .replace("ί", "ι")
        .replace("ϊ", "ι")
        .replace("ΐ", "ι")
        .replace("ό", "ο")
        .replace("ύ", "υ")
        .replace("ϋ", "υ")
        .replace("ΰ", "υ")
        .replace("ώ", "ω")
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), "")
}
