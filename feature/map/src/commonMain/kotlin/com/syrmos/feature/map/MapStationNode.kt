package com.syrmos.feature.map

import com.syrmos.core.model.transit.Station
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
                .sortedWith(compareBy<Station> { it.latitude }.thenBy { it.longitude }.thenBy { it.id })
                .groupBy { station -> station.clusterKey() }
                .flatMap { (_, groupedStations) ->
                    groupedStations
                        .clusterByProximity()
                        .mapIndexed { index, cluster ->
                            val primary = cluster.first()
                            val lineIds = cluster
                                .flatMap { it.lineIds }
                                .distinct()
                            val stationIdByLineId = cluster
                                .flatMap { station -> station.lineIds.map { lineId -> lineId to station.id } }
                                .distinctBy { it.first }
                                .toMap()

                            MapStationNode(
                                id = primary.clusterKey() + "_" + index + "_" + cluster.latitudeBucket() + "_" + cluster.longitudeBucket(),
                                stationIds = cluster.map { it.id },
                                stationIdByLineId = stationIdByLineId,
                                name = primary.name,
                                nameEl = primary.nameEl,
                                latitude = cluster.map { it.latitude }.average(),
                                longitude = cluster.map { it.longitude }.average(),
                                lineIds = lineIds,
                                isInterchange = lineIds.size > 1 || cluster.any { it.isInterchange },
                                accessibility = cluster.any { it.accessibility },
                                zone = cluster.minOf { it.zone },
                            )
                        }
                }
                .sortedWith(compareBy<MapStationNode> { it.latitude }.thenBy { it.longitude }.thenBy { it.name })
        }
    }
}

private fun Station.clusterKey(): String {
    return listOf(name.normalizeStationText(), nameEl.normalizeStationText())
        .filter { it.isNotBlank() }
        .sorted()
        .joinToString("|")
}

private fun List<Station>.clusterByProximity(radiusMeters: Double = 300.0): List<List<Station>> {
    val clusters = mutableListOf<MutableList<Station>>()
    for (station in this) {
        val cluster = clusters.firstOrNull { existing ->
            existing.any { other ->
                distanceMeters(other.latitude, other.longitude, station.latitude, station.longitude) <= radiusMeters
            }
        }
        if (cluster != null) {
            cluster.add(station)
        } else {
            clusters.add(mutableListOf(station))
        }
    }
    return clusters
}

private fun String.normalizeStationText(): String {
    return lowercase()
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

private fun List<Station>.latitudeBucket(): Int = (map { it.latitude }.average() * 10000).toInt()

private fun List<Station>.longitudeBucket(): Int = (map { it.longitude }.average() * 10000).toInt()

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0
    val dLat = (lat2 - lat1) * kotlin.math.PI / 180.0
    val dLon = (lon2 - lon1) * kotlin.math.PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * kotlin.math.PI / 180.0) * cos(lat2 * kotlin.math.PI / 180.0) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
}
