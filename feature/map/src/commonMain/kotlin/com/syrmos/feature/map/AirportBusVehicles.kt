package com.syrmos.feature.map

import com.syrmos.core.network.AirportBusService

/**
 * One live airport express-bus position for the map layer.
 *
 * The Pi's oasa-airport-bus-watcher serves live vehicle positions (vehicles[])
 * alongside the ETAs at /api/oasa-airport-buses. Mirrors iOS AirportBusVehicle /
 * web airportBusVehicles so all three clients plot the same fleet.
 * Now supports unified normalized data for all operators.
 */
data class AirportBusVehicle(
    val id: String,
    val lineId: String,
    val latitude: Double,
    val longitude: Double,
    val heading: Double = 0.0,
    val toAirport: Boolean? = null,
    val operator: String = "oasa",
)

object AirportBusVehicles {
    /**
     * Extract plottable vehicles: drop rows without a line id or a finite
     * non-zero coordinate (the null-island / GPS-less rows the feed can carry).
     */
    fun parse(response: AirportBusService.OasaAirportBusResponse?): List<AirportBusVehicle> {
        if (response == null) return emptyList()
        return response.vehicles.mapNotNull { v ->
            if (v.lineId.isBlank()) return@mapNotNull null
            if (!v.lat.isFinite() || !v.lng.isFinite()) return@mapNotNull null
            if (v.lat == 0.0 && v.lng == 0.0) return@mapNotNull null
            AirportBusVehicle(
                id = v.vehicleId,
                lineId = v.lineId,
                latitude = v.lat,
                longitude = v.lng,
                heading = v.heading,
                toAirport = null,
                operator = "oasa"
            )
        }
    }

    /**
     * Parse vehicles from unified response for a specific operator
     */
    fun parseUnified(response: AirportBusService.UnifiedAirportBusResponse?, operator: String? = null): List<AirportBusVehicle> {
        if (response == null) return emptyList()

        val operatorsToProcess = if (operator != null) {
            listOfNotNull(response.operators[operator])
        } else {
            response.operators.values.toList()
        }

        return operatorsToProcess.flatMap { opData ->
            opData.vehicles.mapNotNull { v ->
                if (v.line_id.isBlank()) return@mapNotNull null
                if (!v.latitude.isFinite() || !v.longitude.isFinite()) return@mapNotNull null
                if (v.latitude == 0.0 && v.longitude == 0.0) return@mapNotNull null
                AirportBusVehicle(
                    id = v.vehicle_id,
                    lineId = v.line_id,
                    latitude = v.latitude,
                    longitude = v.longitude,
                    heading = v.heading,
                    toAirport = v.to_airport,
                    operator = v.operator
                )
            }
        }
    }

    /**
     * Parse vehicles from single operator response
     */
    fun parseOperator(response: AirportBusService.OperatorResponse?): List<AirportBusVehicle> {
        if (response == null) return emptyList()

        return response.vehicles.mapNotNull { v ->
            if (v.line_id.isBlank()) return@mapNotNull null
            if (!v.latitude.isFinite() || !v.longitude.isFinite()) return@mapNotNull null
            if (v.latitude == 0.0 && v.longitude == 0.0) return@mapNotNull null
            AirportBusVehicle(
                id = v.vehicle_id,
                lineId = v.line_id,
                latitude = v.latitude,
                longitude = v.longitude,
                heading = v.heading,
                toAirport = v.to_airport,
                operator = v.operator
            )
        }
    }
}
