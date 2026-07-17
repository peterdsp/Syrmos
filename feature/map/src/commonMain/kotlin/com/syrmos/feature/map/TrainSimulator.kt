package com.syrmos.feature.map

import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.SyrmosLivePositionsService
import kotlinx.datetime.Clock

/// Snapshot of `/api/live-positions` + `/api/station-offsets` cached by the
/// MapViewModel. Passing it in (rather than fetching inside the simulator)
/// keeps the position math pure and lets the model decide refresh cadence.
data class LivePositionsSnapshot(
    val trains: List<SyrmosLivePositionsService.LiveTrain>,
    /// Keyed by (lineId, directionKey) -> ordered stops by stopSequence.
    val offsets: Map<Pair<String, String>, List<SyrmosLivePositionsService.OffsetStop>>,
    /// The Athens-local instant the API reported as "now"; we anchor each
    /// train's originDepartureMinute against this to recover its absolute
    /// epoch second, so wall-clock progression inside the simulator stays
    /// synced with the projector even between fetches.
    val generatedAtEpochSeconds: Long,
)

fun simulateTrains(
    lines: List<Line>,
    lineStations: Map<String, List<Station>>,
    snapshot: LivePositionsSnapshot?,
): List<SimulatedTrain> {
    if (snapshot == null || snapshot.trains.isEmpty()) return emptyList()
    // Only operational lines get trains. A line that is built but not open (e.g.
    // Thessaloniki Line 2 until the Kalamaria extension opens) still renders on
    // the map, greyed, because the track is real -- but it must never carry a
    // moving dot, because the service does not exist. Filtering here rather than
    // at each caller means no future data or feed change can put a train on track
    // that carries none.
    val lineById = lines.filter { it.isOperational }.associateBy { it.id }
    val stationById: Map<String, Station> = lineStations.values.flatten().associateBy { it.id }

    val nowEpochSeconds = Clock.System.now().epochSeconds
    val trains = mutableListOf<SimulatedTrain>()

    for (raw in snapshot.trains) {
        // station_offsets keys M3_AIR under M3 because the airport service
        // shares the M3 polyline up to Doukissis Plakentias; mirror that.
        val offsetKey = (if (raw.lineId == "M3_AIR") "M3" else raw.lineId) to raw.directionKey
        val stops = snapshot.offsets[offsetKey] ?: continue
        if (stops.size < 2) continue

        val displayLineId = if (raw.lineId == "M3_AIR") "M3" else raw.lineId
        val line = lineById[displayLineId] ?: continue

        // Recover this train's absolute origin-departure epoch second from
        // the API-reported "elapsedMinutes" at the snapshot's generatedAt.
        // Subsequent re-runs of the simulator (every second between API
        // polls) advance elapsedMinutes purely by wall-clock difference,
        // which is what makes the dot glide smoothly.
        val originEpochSec = snapshot.generatedAtEpochSeconds - (raw.elapsedMinutes * 60.0).toLong()
        val elapsedMinutes = (nowEpochSeconds - originEpochSec) / 60.0
        if (elapsedMinutes < 0 || elapsedMinutes > raw.totalTravelMinutes + 0.5) continue

        // Find which segment the train is currently on.
        var segIdx = 0
        for (i in 0 until stops.size - 1) {
            if (stops[i].minutesFromOrigin <= elapsedMinutes &&
                elapsedMinutes < stops[i + 1].minutesFromOrigin) {
                segIdx = i
                break
            }
            if (i == stops.size - 2) segIdx = i
        }
        val fromStop = stops[segIdx]
        val toStop = stops[segIdx + 1]
        val fromStation = stationById[fromStop.stationId] ?: continue
        val toStation = stationById[toStop.stationId] ?: continue
        val segDuration = (toStop.minutesFromOrigin - fromStop.minutesFromOrigin).toDouble()
        val fraction = if (segDuration > 0) {
            ((elapsedMinutes - fromStop.minutesFromOrigin) / segDuration).coerceIn(0.0, 1.0)
        } else 0.0

        val lat = fromStation.latitude + (toStation.latitude - fromStation.latitude) * fraction
        val lon = fromStation.longitude + (toStation.longitude - fromStation.longitude) * fraction
        val direction = if (raw.directionKey == "outbound") Direction.OUTBOUND else Direction.INBOUND

        trains += SimulatedTrain(
            id = "${raw.lineId}_${raw.directionKey}_${raw.originDepartureMinute.toInt()}",
            lineId = displayLineId,
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
            currentStationName = fromStation.name,
            nextStationName = toStation.name,
            progress = (elapsedMinutes / raw.totalTravelMinutes.toDouble()).coerceIn(0.0, 1.0),
            latitude = lat,
            longitude = lon,
            isAirportService = raw.lineId == "M3_AIR",
        )
    }
    return trains
}

