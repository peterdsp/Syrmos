package com.syrmos.feature.map

import com.syrmos.core.network.OasaAirportBusService

/**
 * One live airport express-bus position for the map layer.
 *
 * The Pi's oasa-airport-bus-watcher serves live vehicle positions (vehicles[])
 * alongside the ETAs at /api/oasa-airport-buses. Mirrors iOS AirportBusVehicle /
 * web airportBusVehicles so all three clients plot the same fleet.
 */
data class AirportBusVehicle(
    val id: String,
    val lineId: String,
    val latitude: Double,
    val longitude: Double,
)

object AirportBusVehicles {
    /**
     * Extract plottable vehicles: drop rows without a line id or a finite
     * non-zero coordinate (the null-island / GPS-less rows the feed can carry).
     */
    fun parse(response: OasaAirportBusService.OasaAirportBusResponse?): List<AirportBusVehicle> {
        if (response == null) return emptyList()
        return response.vehicles.mapNotNull { v ->
            if (v.lineId.isBlank()) return@mapNotNull null
            if (!v.lat.isFinite() || !v.lng.isFinite()) return@mapNotNull null
            if (v.lat == 0.0 && v.lng == 0.0) return@mapNotNull null
            AirportBusVehicle(id = v.vehicleId, lineId = v.lineId, latitude = v.lat, longitude = v.lng)
        }
    }
}
