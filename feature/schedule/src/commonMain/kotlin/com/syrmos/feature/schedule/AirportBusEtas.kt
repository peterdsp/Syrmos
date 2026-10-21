package com.syrmos.feature.schedule

import com.syrmos.core.network.AirportBusService
import com.syrmos.core.model.schedule.SourceConfidence

/**
 * Soonest-first live ETAs per airport express line (X93/X95/X96/X97).
 *
 * The Pi's oasa-airport-bus-watcher polls OASA Telematics getStopArrivals for the
 * airport stop (10705) and serves real per-vehicle ETAs at /api/oasa-airport-buses.
 * `minutesAway` is OASA's own btime2 estimate for a tracked bus to reach the
 * airport, so the soonest per line is genuinely the next X-bus a rider can board
 * there - a real LIVE source, never a timetable dressed up as live.
 *
 * Mirrors iOS AirportBusService.reduce and web reduceAirportBuses so the three
 * clients agree on the transform.
 */
data class LiveAirportBuses(val etasByLine: Map<String, List<Int>>) {
    /** The next tracked bus of [line] reaching the airport stop, if any. */
    fun soonest(line: String): Int? = etasByLine[line]?.firstOrNull()

    val isEmpty: Boolean get() = etasByLine.isEmpty()

    companion object {
        val EMPTY = LiveAirportBuses(emptyMap())
    }
}

/// Operational status for service alerts
enum class ServiceStatus {
    OPERATIONAL, DISRUPTED, SUSPENDED, UNKNOWN;

    companion object {
        fun fromString(value: String): ServiceStatus = when (value.lowercase()) {
            "operational" -> OPERATIONAL
            "disrupted" -> DISRUPTED
            "suspended" -> SUSPENDED
            else -> UNKNOWN
        }
    }
}

/// Direction relative to airport
enum class Direction {
    TO_AIRPORT, FROM_AIRPORT, UNKNOWN;

    companion object {
        fun fromString(value: String?): Direction = when (value?.lowercase()) {
            "to_airport" -> TO_AIRPORT
            "from_airport" -> FROM_AIRPORT
            else -> UNKNOWN
        }
    }
}

/// Confidence level mapping
enum class ConfidenceLevel {
    LIVE, SCHEDULED, ESTIMATED, OPERATOR_LINK, STALE, UNAVAILABLE, UNKNOWN;

    fun toSourceConfidence(): SourceConfidence = when (this) {
        LIVE -> SourceConfidence.LIVE
        SCHEDULED -> SourceConfidence.SCHEDULED
        ESTIMATED -> SourceConfidence.ESTIMATED
        OPERATOR_LINK -> SourceConfidence.OPERATOR_LINK
        STALE, UNAVAILABLE -> SourceConfidence.OFFLINE
        UNKNOWN -> SourceConfidence.UNKNOWN
    }

    companion object {
        fun fromString(value: String?): ConfidenceLevel = when (value?.lowercase()) {
            "live" -> LIVE
            "scheduled" -> SCHEDULED
            "estimated" -> ESTIMATED
            "operator-link", "operator_link" -> OPERATOR_LINK
            "stale" -> STALE
            "unavailable" -> UNAVAILABLE
            else -> UNKNOWN
        }
    }
}

/// Unified live airport bus data with support for multiple operators
data class UnifiedAirportBuses(
    val updatedAt: String? = null,
    val operators: Map<String, OperatorData> = emptyMap(),
    val sources: List<SourceInfo> = emptyList(),
) {
    val isEmpty: Boolean get() = operators.isEmpty()

    fun soonest(line: String, operator: String): Int? = operators[operator]?.etasByLine?.get(line)?.firstOrNull()
    fun hasOperator(operator: String): Boolean = operators.containsKey(operator)
    fun getOperatorData(operator: String): OperatorData? = operators[operator]
}

/// Per-operator airport bus data
data class OperatorData(
    val operator: String = "",
    val source: String? = null,
    val attribution: String? = null,
    val updatedAt: String? = null,
    val etasByLine: Map<String, List<Int>> = emptyMap(),
    val vehicles: List<VehiclePosition> = emptyList(),
    val alerts: List<ServiceAlert> = emptyList(),
    val routes: List<RouteInfo> = emptyList(),
) {
    val isEmpty: Boolean get() = etasByLine.isEmpty() && vehicles.isEmpty()
    fun soonest(line: String): Int? = etasByLine[line]?.firstOrNull()
}

/// Vehicle position for map display
data class VehiclePosition(
    val id: String = "",
    val vehicleId: String = "",
    val lineId: String = "",
    val operator: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Double = 0.0,
    val toAirport: Boolean? = null,
)

/// Route information
data class RouteInfo(
    val routeId: String = "",
    val operator: String = "",
    val publicName: String = "",
    val publicNameEn: String? = null,
    val origin: String = "",
    val destination: String = "",
    val airport: String = "",
    val color: String = "",
    val direction: Direction = Direction.UNKNOWN,
    val isOperational: Boolean = true,
)

/// Service alert
data class ServiceAlert(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val operator: String = "",
    val severity: String = "info",
    val url: String? = null,
)

/// Source information
data class SourceInfo(
    val operator: String = "",
    val name: String = "",
    val sourceUrl: String? = null,
    val attribution: String = "",
    val isLive: Boolean = false,
    val lastTested: String? = null,
    val notes: String? = null,
)

object AirportBusEtas {
    /**
     * Collapse the raw feed into soonest-first ETAs per line. Negative ETAs clamp
     * to 0 (bus at the stop). Lines with no tracked vehicle are absent. A null
     * response (offline / fetch failure) yields [LiveAirportBuses.EMPTY].
     */
    fun reduce(response: AirportBusService.OasaAirportBusResponse?): LiveAirportBuses {
        if (response == null) return LiveAirportBuses.EMPTY
        val byLine = HashMap<String, MutableList<Int>>()
        for (arrival in response.airportArrivals) {
            if (arrival.lineId.isBlank()) continue
            byLine.getOrPut(arrival.lineId) { mutableListOf() }.add(maxOf(0, arrival.minutesAway))
        }
        return LiveAirportBuses(byLine.mapValues { (_, etas) -> etas.sorted() })
    }

    /**
     * Reduce unified response to LiveAirportBuses, focusing on a specific operator
     */
    fun reduceUnified(response: AirportBusService.UnifiedAirportBusResponse?, operator: String? = null): LiveAirportBuses {
        if (response == null) return LiveAirportBuses.EMPTY
        val byLine = HashMap<String, MutableList<Int>>()

        val operatorsToProcess = if (operator != null) {
            listOfNotNull(response.operators[operator])
        } else {
            response.operators.values.toList()
        }

        for (opData in operatorsToProcess) {
            for (arrival in opData.arrivals) {
                if (arrival.line_id.isBlank()) continue
                // Airport-bound arrivals only: the ETA is labelled "to the airport
                // stop", so a from_airport bus must not be folded in and shown as
                // airport-bound. An unknown/absent direction is kept.
                if (arrival.direction == "from_airport") continue
                arrival.estimated_minutes?.takeIf { it >= 0 }?.let { minutes ->
                    byLine.getOrPut(arrival.line_id) { mutableListOf() }.add(minutes)
                }
            }
        }

        return LiveAirportBuses(byLine.mapValues { (_, etas) -> etas.sorted() })
    }

    /**
     * Reduce unified response to unified structure with all features
     */
    fun reduceToUnified(response: AirportBusService.UnifiedAirportBusResponse?): UnifiedAirportBuses {
        if (response == null) return UnifiedAirportBuses()

        val operatorsData = HashMap<String, OperatorData>()
        var allSources = emptyList<SourceInfo>()

        // Process each operator
        for ((opKey, opData) in response.operators) {
            val etasByLine = HashMap<String, MutableList<Int>>()
            val vehiclesForOp = mutableListOf<VehiclePosition>()
            val alertsForOp = mutableListOf<ServiceAlert>()
            val routesForOp = mutableListOf<RouteInfo>()

            // Process arrivals
            for (arrival in opData.arrivals) {
                if (arrival.line_id.isNotBlank()) {
                    arrival.estimated_minutes?.takeIf { it >= 0 }?.let { minutes ->
                        etasByLine.getOrPut(arrival.line_id) { mutableListOf() }.add(minutes)
                    }
                }
            }

            // Sort ETAs for each line
            val sortedEtas = etasByLine.mapValues { (_, etas) -> etas.sorted() }

            // Process vehicles
            for (vehicle in opData.vehicles) {
                if (vehicle.line_id.isNotBlank() &&
                    vehicle.latitude != 0.0 && vehicle.longitude != 0.0 &&
                    vehicle.latitude.isFinite() && vehicle.longitude.isFinite()) {
                    vehiclesForOp.add(VehiclePosition(
                        id = vehicle.vehicle_id,
                        vehicleId = vehicle.vehicle_id,
                        lineId = vehicle.line_id,
                        operator = vehicle.operator,
                        latitude = vehicle.latitude,
                        longitude = vehicle.longitude,
                        heading = vehicle.heading,
                        toAirport = vehicle.to_airport
                    ))
                }
            }

            // Process alerts
            for (alert in opData.alerts) {
                alertsForOp.add(ServiceAlert(
                    id = alert.alert_id,
                    title = alert.title,
                    summary = alert.summary,
                    operator = alert.operator,
                    severity = alert.severity ?: "info",
                    url = alert.url
                ))
            }

            // Process routes
            for (route in opData.routes) {
                routesForOp.add(RouteInfo(
                    routeId = route.route_id,
                    operator = route.operator,
                    publicName = route.public_name,
                    publicNameEn = route.public_name_en,
                    origin = route.origin,
                    destination = route.destination,
                    airport = route.airport,
                    color = route.color,
                    direction = Direction.fromString(route.direction),
                    isOperational = route.is_operational
                ))
            }

            operatorsData[opKey] = OperatorData(
                operator = opKey,
                source = opData.source,
                attribution = opData.attribution,
                updatedAt = opData.updated_at,
                etasByLine = sortedEtas,
                vehicles = vehiclesForOp,
                alerts = alertsForOp,
                routes = routesForOp
            )
        }

        // Process sources
        for (source in response.sources) {
            allSources = allSources + SourceInfo(
                operator = source.operator ?: "",
                name = source.name ?: "",
                sourceUrl = source.source_url,
                attribution = source.attribution ?: "",
                isLive = source.is_live ?: false,
                lastTested = source.last_tested,
                notes = source.notes
            )
        }

        return UnifiedAirportBuses(
            updatedAt = response.updated_at,
            operators = operatorsData,
            sources = allSources
        )
    }

    /**
     * Get route information for a specific route and operator
     */
    fun getRouteInfo(unified: UnifiedAirportBuses, operator: String, routeId: String): RouteInfo? =
        unified.operators[operator]?.routes?.firstOrNull { it.routeId == routeId }

    /**
     * Get vehicle positions for a specific route and operator
     */
    fun getVehiclePositions(unified: UnifiedAirportBuses, operator: String, routeId: String): List<VehiclePosition> =
        unified.operators[operator]?.vehicles?.filter { it.lineId == routeId } ?: emptyList()

    /**
     * Get alerts for a specific route and operator
     */
    fun getAlerts(unified: UnifiedAirportBuses, operator: String, routeId: String): List<ServiceAlert> =
        unified.operators[operator]?.alerts ?: emptyList()

    /**
     * Get confidence level for a route
     */
    fun getConfidenceLevel(unified: UnifiedAirportBuses, operator: String, routeId: String): ConfidenceLevel {
        val route = getRouteInfo(unified, operator, routeId)
        val operatorData = unified.operators[operator]

        if (operatorData?.etasByLine?.containsKey(routeId) == true &&
            !operatorData.etasByLine[routeId].isNullOrEmpty()) {
            return ConfidenceLevel.LIVE
        }

        if (route != null) {
            return if (route.isOperational) ConfidenceLevel.OPERATOR_LINK else ConfidenceLevel.UNAVAILABLE
        }

        return ConfidenceLevel.UNKNOWN
    }
}
