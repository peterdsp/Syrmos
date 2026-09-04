package com.syrmos.feature.map

import com.syrmos.core.network.OasaAirportBusService.OasaAirportBusResponse
import com.syrmos.core.network.OasaAirportBusService.OasaBusVehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mirrors iOS LiveAirportBusService.parse and web airportBusVehicles so all three
 * clients plot the same fleet from /api/oasa-airport-buses.
 */
class AirportBusVehiclesTest {

    private fun response(vararg vehicles: OasaBusVehicle) =
        OasaAirportBusResponse(updatedAt = "", vehicles = vehicles.toList(), airportArrivals = emptyList())

    @Test
    fun parseKeepsValidVehiclesAndDropsBadRows() {
        val vehicles = AirportBusVehicles.parse(response(
            OasaBusVehicle(vehicleId = "61233", lat = 37.90, lng = 23.90, lineId = "X95"),
            OasaBusVehicle(vehicleId = "0", lat = 0.0, lng = 0.0, lineId = "X95"),   // null island
            OasaBusVehicle(vehicleId = "y", lat = 37.9, lng = 23.9, lineId = ""),    // no line
        ))
        assertEquals(1, vehicles.size)
        assertEquals("61233", vehicles[0].id)
        assertEquals("X95", vehicles[0].lineId)
        assertEquals(37.90, vehicles[0].latitude)
    }

    @Test
    fun parseHandlesNullResponse() {
        assertTrue(AirportBusVehicles.parse(null).isEmpty())
    }
}
