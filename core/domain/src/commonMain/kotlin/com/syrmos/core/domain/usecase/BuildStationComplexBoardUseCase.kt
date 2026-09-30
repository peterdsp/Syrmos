package com.syrmos.core.domain.usecase

import com.syrmos.core.domain.station.LineStopOrder
import com.syrmos.core.domain.station.StationComplexBoard
import com.syrmos.core.domain.station.StationComplexRepository
import com.syrmos.core.model.transit.Direction
import kotlinx.coroutines.flow.first

/**
 * Builds the all-directions board for the station complex a rider is standing in.
 *
 * Replaces the path through [GetStationDeparturesUseCase] (which ended with
 * `.take(8)`) and [HomeDirectionBoard] (four groups keyed by `(lineId,
 * Direction)`). Both truncated before destination coverage was established, and
 * a two-value direction enum cannot represent a branch, a short turn or a
 * trip-specific destination. The station was not the station either: Athens is
 * five boarding stop ids that no client joined.
 *
 * Every (boarding stop, service, destination) group is enumerated first;
 * presentation limits are applied last, inside [StationComplexBoard].
 */
class BuildStationComplexBoardUseCase(
    private val stationComplexRepository: StationComplexRepository,
    private val lineStopOrder: LineStopOrder,
    private val getNextDepartures: GetNextDeparturesUseCase,
) {

    /**
     * @param stopIds the rider's boarding stops. Any one of them resolves the
     *   whole complex; a stop the registry does not claim becomes a
     *   single-station complex of its own boarding stops.
     * @param displayName fallback complex name for a station outside the
     *   registry.
     */
    suspend operator fun invoke(
        stopIds: List<String>,
        displayName: String = "",
        displayNameEl: String = "",
        windowMinutes: Int = 12 * 60,
        maxTimesPerGroup: Int = 3,
        perDirectionLimit: Int = PER_DIRECTION_LIMIT,
    ): StationComplexBoard.Board {
        val complex = resolveComplex(stopIds, displayName, displayNameEl)
        val candidates = mutableListOf<StationComplexBoard.Candidate>()
        val coverage = mutableListOf<StationComplexBoard.CoverageEntry>()

        for (area in complex.areas) {
            for (stopId in area.stopIds) {
                for (line in lineStopOrder.linesBoardingAt(stopId)) {
                    val directions = lineStopOrder.directionsAt(line.lineId, stopId)
                    if (directions.isEmpty()) continue
                    var produced = 0
                    for (direction in directions) {
                        // Ask per direction so a frequent direction cannot
                        // starve the other one; the two are separate groups and
                        // separate queries.
                        val departures = getNextDepartures.invoke(
                            stationId = stopId,
                            lineId = line.lineId,
                            direction = if (direction.key == "inbound") {
                                Direction.INBOUND
                            } else {
                                Direction.OUTBOUND
                            },
                            limit = perDirectionLimit,
                        ).first()
                        for (departure in departures) {
                            if (departure.minutesAway > windowMinutes) continue
                            // The server's own headsign wins when it supplies
                            // one; otherwise the direction's terminal, which is
                            // the only destination a frequency band can honestly
                            // claim.
                            val destination = departure.notes
                                ?.takeIf { it.isNotBlank() }
                                ?: direction.destination
                            candidates += StationComplexBoard.Candidate(
                                stopId = stopId,
                                areaId = area.id,
                                lineId = departure.lineId.ifBlank { line.lineId },
                                destination = destination,
                                // Provider-qualified so two lines cannot collide
                                // on a train number.
                                tripId = departure.trainNo?.let { "${line.lineId}:$it" },
                                time = departure.time,
                                absoluteMinutes = departure.minutesAway,
                                source = departure.sourceConfidence,
                                trainNo = departure.trainNo,
                                serviceType = departure.serviceType.orEmpty(),
                            )
                            produced++
                        }
                    }
                    // A service with nothing in the window still names the
                    // destinations it serves, so the row reads "Leianokladi, no
                    // departure in the next 12 hours" and not a bare line id.
                    if (produced == 0) {
                        for (direction in directions) {
                            coverage += StationComplexBoard.CoverageEntry(
                                areaId = area.id,
                                stopId = stopId,
                                lineId = line.lineId,
                                destination = direction.destination,
                                state = StationComplexBoard.Coverage.NO_DEPARTURE_IN_WINDOW,
                            )
                        }
                    } else {
                        coverage += StationComplexBoard.CoverageEntry(
                            areaId = area.id,
                            stopId = stopId,
                            lineId = line.lineId,
                            state = StationComplexBoard.Coverage.LOADED,
                        )
                    }
                }
            }
        }

        return StationComplexBoard.build(
            complex = complex,
            candidates = candidates,
            coverage = coverage,
            windowMinutes = windowMinutes,
            maxTimesPerGroup = maxTimesPerGroup,
        )
    }

    private suspend fun resolveComplex(
        stopIds: List<String>,
        displayName: String,
        displayNameEl: String,
    ): StationComplexBoard.Complex {
        for (stopId in stopIds) {
            stationComplexRepository.complexForStop(stopId)?.let { return it }
        }
        // Not in the reviewed registry: a single-station complex of its own
        // boarding stops, grouped by the modes actually boardable there.
        val areas = LinkedHashMap<String, MutableList<String>>()
        for (stopId in stopIds) {
            for (line in lineStopOrder.linesBoardingAt(stopId)) {
                val areaId = lineStopOrder.areaIdFor(line.type)
                val stops = areas.getOrPut(areaId) { mutableListOf() }
                if (stopId !in stops) stops += stopId
            }
        }
        val name = displayName.ifBlank { stopIds.firstOrNull().orEmpty() }
        return StationComplexBoard.Complex(
            id = "STOP:${stopIds.firstOrNull().orEmpty()}",
            name = name,
            nameEl = displayNameEl.ifBlank { name },
            nameSq = name,
            nameIt = name,
            areas = areas.map { (areaId, stops) ->
                val labels = AREA_LABELS[areaId] ?: listOf(areaId, areaId, areaId, areaId)
                StationComplexBoard.Complex.Area(
                    id = areaId,
                    name = labels[0],
                    nameEl = labels[1],
                    nameSq = labels[2],
                    nameIt = labels[3],
                    stopIds = stops,
                )
            },
            synthetic = true,
        )
    }

    private companion object {
        /**
         * Per (stop, line, direction) depth. Deliberately generous: the cap
         * exists to bound work, never to decide which destinations exist.
         */
        const val PER_DIRECTION_LIMIT = 8

        val AREA_LABELS: Map<String, List<String>> = mapOf(
            "metro" to listOf("Metro", "Μετρό", "Metro", "Metropolitana"),
            "tram" to listOf("Tram", "Τραμ", "Tramvaj", "Tram"),
            "rail" to listOf(
                "Railway station", "Σιδηροδρομικός σταθμός",
                "Stacioni hekurudhor", "Stazione ferroviaria",
            ),
            "bus" to listOf(
                "Rail-replacement bus", "Λεωφορείο αντικατάστασης",
                "Autobus zëvendësues", "Bus sostitutivo",
            ),
        )
    }
}
