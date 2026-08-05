package com.syrmos.feature.map

import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.SimulatedTrain
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.SyrmosLivePositionsService
import com.syrmos.core.network.SyrmosSchedulesService
import kotlinx.datetime.Clock
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    closedStationIds: Set<String> = emptySet(),
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
        if (fromStop.stationId in closedStationIds || toStop.stationId in closedStationIds) continue
        val fromStation = stationById[fromStop.stationId] ?: continue
        val toStation = stationById[toStop.stationId] ?: continue
        val segDuration = (toStop.minutesFromOrigin - fromStop.minutesFromOrigin).toDouble()
        val linearFrac = if (segDuration > 0) {
            ((elapsedMinutes - fromStop.minutesFromOrigin) / segDuration).coerceIn(0.0, 1.0)
        } else 0.0
        val fraction = stationAwareEase(linearFrac)

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
            bearing = bearingDeg(fromStation.latitude, fromStation.longitude, toStation.latitude, toStation.longitude),
        )
    }
    return trains
}


/// Metro/tram/M3_AIR/suburban-A1-A4 come from the offsets-based [simulateTrains].
private val LIVE_POSITION_LINES =
    setOf("M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4")

/// Project moving vehicles for NATIONAL rail + rail-replacement BUS lines, which
/// have no live-position feed and no station-offsets on the Pi. For every trip
/// running right now (by the Athens wall clock) we find the segment the clock
/// lands in and interpolate the position between its two stations (a chord, like
/// [simulateTrains]), tagging a compass [SimulatedTrain.bearing] so the map can
/// point the triangle the right way. This is the "else interpolate from the
/// timetable" path; if the Pi ever serves their live positions those win per line.
///
/// [today] is the schedule day-type string ("mon_thu" / "fri" / "sat" / "sun").
/// [nowMinutes] is minutes since Athens midnight (fractional for smooth glide).
fun projectScheduledTrains(
    lines: List<Line>,
    lineStations: Map<String, List<Station>>,
    bundles: Map<String, SyrmosSchedulesService.LineSchedule>,
    today: String,
    nowMinutes: Double,
): List<SimulatedTrain> {
    if (bundles.isEmpty()) return emptyList()
    val lineById = lines.filter { it.isOperational }.associateBy { it.id }
    val stationById: Map<String, Station> = lineStations.values.flatten().associateBy { it.id }
    val out = mutableListOf<SimulatedTrain>()

    for (line in lineById.values) {
        if (line.id in LIVE_POSITION_LINES) continue
        val bundle = bundles[line.id] ?: continue
        for (trip in bundle.trips) {
            // dayType is authoritative; an empty dayType runs every day.
            val td = trip.dayType.lowercase()
            if (td.isNotEmpty() && td != today) continue
            val stops = trip.stops
            if (stops.size < 2) continue
            val times = stops.map { toMinutesOfDay(it.departureTime) }
            if (times.any { it == null }) continue
            val t = times.map { it!! }
            // Skip trips that wrap past midnight (non-monotonic) - rare on these lines.
            var monotonic = true
            for (i in 1 until t.size) if (t[i] < t[i - 1]) { monotonic = false; break }
            if (!monotonic) continue
            if (nowMinutes < t.first() || nowMinutes > t.last()) continue

            var seg = 0
            for (i in 0 until stops.size - 1) {
                if (t[i] <= nowMinutes && nowMinutes < t[i + 1]) { seg = i; break }
            }
            val from = stationById[stops[seg].stationId] ?: continue
            val to = stationById[stops[seg + 1].stationId] ?: continue
            val dur = (t[seg + 1] - t[seg]).toDouble()
            val linearFrac = if (dur > 0) ((nowMinutes - t[seg]) / dur).coerceIn(0.0, 1.0) else 0.0
            val frac = stationAwareEase(linearFrac)
            val lat = from.latitude + (to.latitude - from.latitude) * frac
            val lon = from.longitude + (to.longitude - from.longitude) * frac
            val direction = if (trip.direction.lowercase() == "outbound") Direction.OUTBOUND else Direction.INBOUND
            val dest = stationById[stops.last().stationId]?.name ?: line.terminalB

            out += SimulatedTrain(
                id = "${line.id}_${trip.trainNo}_${trip.direction}",
                lineId = line.id,
                lineName = line.name,
                lineColor = line.color,
                lineType = line.type,
                direction = direction,
                originName = stationById[stops.first().stationId]?.name ?: line.terminalA,
                destinationName = dest,
                currentStationName = from.name,
                nextStationName = to.name,
                progress = frac,
                latitude = lat,
                longitude = lon,
                isAirportService = false,
                bearing = bearingDeg(from.latitude, from.longitude, to.latitude, to.longitude),
            )
        }
    }
    return out
}

private fun toMinutesOfDay(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

/**
 * Station-aware easing: trains decelerate near stations (segment endpoints)
 * and cruise faster mid-segment, mimicking real rail kinematics.
 * Uses a cubic bezier approximation of the "train glide" signature curve
 * applied symmetrically (ease-in from origin, ease-out into destination).
 *
 * The curve is: t -> 3t^2 - 2t^3 (smoothstep), which gives zero velocity
 * at t=0 and t=1 (station dwell) and peak velocity at t=0.5 (mid-segment).
 */
fun stationAwareEase(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)
}

/// Compass bearing (0 = north) from one coordinate to another.
fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    fun rad(d: Double) = d * PI / 180.0
    val y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
    val x = cos(rad(lat1)) * sin(rad(lat2)) -
        sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
    return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
}
