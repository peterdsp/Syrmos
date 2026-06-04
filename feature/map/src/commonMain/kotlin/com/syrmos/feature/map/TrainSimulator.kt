package com.syrmos.feature.map

import com.syrmos.core.common.extensions.currentAthensTime
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station

private data class StationTiming(
    val station: Station,
    val departureMinutes: Double,
)

fun simulateTrains(
    lines: List<Line>,
    lineStations: Map<String, List<Station>>,
): List<SimulatedTrain> {
    val now = currentAthensTime()
    var nowMinutes = now.hour * 60.0 + now.minute + now.second / 60.0

    val trains = mutableListOf<SimulatedTrain>()

    for (line in lines) {
        if (line.type == LineType.SUBURBAN) continue
        val stations = lineStations[line.id] ?: continue
        if (stations.size < 2) continue

        val minutesPerStation = when (line.type) {
            LineType.METRO -> 1.8
            LineType.TRAM -> 2.2
            else -> 2.0
        }

        val frequency = when (line.id) {
            "M1" -> 5.0
            "M2" -> 4.0
            "M3" -> 5.0
            "T6" -> 9.0
            "T7" -> 12.0
            else -> 7.0
        }

        val tripDuration = (stations.size - 1) * minutesPerStation

        Direction.entries.forEach { direction ->
            val orderedStations = when (direction) {
                Direction.OUTBOUND -> stations
                Direction.INBOUND -> stations.reversed()
            }

            val timings = orderedStations.mapIndexed { index, station ->
                StationTiming(station, index * minutesPerStation)
            }

            val serviceStart = 5.0 * 60
            val serviceEnd = 25.0 * 60
            val adjustedNow = if (nowMinutes < serviceStart) nowMinutes + 24 * 60 else nowMinutes
            if (adjustedNow < serviceStart || adjustedNow > serviceEnd) return@forEach

            val firstDeparture = serviceStart + when (direction) {
                Direction.OUTBOUND -> 0.0
                Direction.INBOUND -> frequency / 2.0
            }

            var departureTime = firstDeparture
            var trainIndex = 0
            while (departureTime <= serviceEnd) {
                val elapsed = adjustedNow - departureTime
                if (elapsed >= 0 && elapsed <= tripDuration) {
                    val segmentIndex = timings.indexOfLast { it.departureMinutes <= elapsed }
                        .coerceIn(0, timings.size - 2)
                    val from = timings[segmentIndex]
                    val to = timings[(segmentIndex + 1).coerceAtMost(timings.size - 1)]
                    val segmentDuration = to.departureMinutes - from.departureMinutes
                    val fraction = if (segmentDuration > 0) {
                        ((elapsed - from.departureMinutes) / segmentDuration).coerceIn(0.0, 1.0)
                    } else 0.0

                    val lat = from.station.latitude + (to.station.latitude - from.station.latitude) * fraction
                    val lon = from.station.longitude + (to.station.longitude - from.station.longitude) * fraction

                    val isAirportService = line.id == "M3" && segmentIndex >= orderedStations.size - 6

                    trains += SimulatedTrain(
                        id = "${line.id}_${direction.name}_$trainIndex",
                        lineId = line.id,
                        lineName = line.name,
                        lineColor = line.color,
                        lineType = line.type,
                        direction = direction,
                        originName = when (direction) {
                            Direction.OUTBOUND -> line.terminalA
                            Direction.INBOUND -> line.terminalB
                        },
                        destinationName = when (direction) {
                            Direction.OUTBOUND -> line.terminalB
                            Direction.INBOUND -> line.terminalA
                        },
                        currentStationName = from.station.name,
                        nextStationName = to.station.name,
                        progress = elapsed / tripDuration,
                        latitude = lat,
                        longitude = lon,
                        isAirportService = isAirportService,
                    )
                }
                departureTime += frequency
                trainIndex++
            }
        }
    }

    return trains
}
