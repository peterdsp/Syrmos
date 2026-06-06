package com.syrmos.feature.map

import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class StationTiming(
    val station: Station,
    val arrivalMinutes: Double,
    val departureMinutes: Double,
)

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a))
}

private const val DWELL_MINUTES_METRO = 0.5
private const val DWELL_MINUTES_TRAM = 0.4
private const val DWELL_MINUTES_TERMINAL = 1.0

private fun smoothEase(t: Double): Double {
    return if (t < 0.15) {
        val x = t / 0.15
        x * x * 0.15
    } else if (t > 0.85) {
        val x = (t - 0.85) / 0.15
        0.85 + (1.0 - (1.0 - x) * (1.0 - x)) * 0.15
    } else {
        t
    }
}

fun simulateTrains(
    lines: List<Line>,
    lineStations: Map<String, List<Station>>,
): List<SimulatedTrain> {
    val instant = Clock.System.now()
    val athensTime = instant.toLocalDateTime(TimeZone.of("Europe/Athens"))
    val nowMinutes = athensTime.hour * 60.0 +
        athensTime.minute +
        athensTime.second / 60.0 +
        (athensTime.nanosecond / 1_000_000_000.0) / 60.0

    val trains = mutableListOf<SimulatedTrain>()

    for (line in lines) {
        if (line.type == LineType.SUBURBAN) continue
        val stations = lineStations[line.id] ?: continue
        if (stations.size < 2) continue

        val travelMinutes = when (line.type) {
            LineType.METRO -> 1.8
            LineType.TRAM -> 2.2
            else -> 2.0
        }

        val dwellMinutes = when (line.type) {
            LineType.METRO -> DWELL_MINUTES_METRO
            LineType.TRAM -> DWELL_MINUTES_TRAM
            else -> DWELL_MINUTES_METRO
        }

        val frequency = when (line.id) {
            "M1" -> 5.0
            "M2" -> 4.0
            "M3" -> 5.0
            "T6" -> 9.0
            "T7" -> 12.0
            else -> 7.0
        }

        Direction.entries.forEach { direction ->
            val orderedStations = when (direction) {
                Direction.OUTBOUND -> stations
                Direction.INBOUND -> stations.reversed()
            }

            val segDistances = (0 until orderedStations.size - 1).map { i ->
                val a = orderedStations[i]
                val b = orderedStations[i + 1]
                haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            }
            val totalDist = segDistances.sum().coerceAtLeast(1.0)
            val totalTravelMins = travelMinutes * (orderedStations.size - 1)

            val timings = mutableListOf<StationTiming>()
            var cumulativeTime = 0.0
            orderedStations.forEachIndexed { index, station ->
                val arrival = cumulativeTime
                val dwell = when {
                    index == 0 || index == orderedStations.size - 1 -> DWELL_MINUTES_TERMINAL
                    else -> dwellMinutes
                }
                timings += StationTiming(station, arrival, arrival + dwell)
                if (index < orderedStations.size - 1) {
                    val segTravel = totalTravelMins * (segDistances[index] / totalDist)
                    cumulativeTime = arrival + dwell + segTravel
                }
            }

            val tripDuration = timings.last().arrivalMinutes

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
                    val to = timings[segmentIndex + 1]

                    val lat: Double
                    val lon: Double

                    if (elapsed < from.departureMinutes) {
                        lat = from.station.latitude
                        lon = from.station.longitude
                    } else {
                        val travelStart = from.departureMinutes
                        val travelEnd = to.arrivalMinutes
                        val travelDuration = travelEnd - travelStart
                        val rawFraction = if (travelDuration > 0) {
                            ((elapsed - travelStart) / travelDuration).coerceIn(0.0, 1.0)
                        } else 0.0

                        val fraction = smoothEase(rawFraction)
                        lat = from.station.latitude + (to.station.latitude - from.station.latitude) * fraction
                        lon = from.station.longitude + (to.station.longitude - from.station.longitude) * fraction
                    }

                    val isAirportService = line.id == "M3" && direction == Direction.OUTBOUND && segmentIndex >= stations.size - 6

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
