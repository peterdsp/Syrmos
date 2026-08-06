package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OasaAirportBusService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAirportBuses(): OasaAirportBusResponse? = runCatching {
        val response = httpClient.get("$BASE_URL/api/oasa-airport-buses")
        val body = response.bodyAsText()
        json.decodeFromString<OasaAirportBusResponse>(body)
    }.getOrNull()

    @Serializable
    data class OasaAirportBusResponse(
        val updatedAt: String = "",
        val vehicles: List<OasaBusVehicle> = emptyList(),
        val airportArrivals: List<OasaBusArrival> = emptyList(),
    )

    @Serializable
    data class OasaBusVehicle(
        val vehicleId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val heading: Double = 0.0,
        val routeCode: Int = 0,
        val lineId: String = "",
        val timestamp: String = "",
    )

    @Serializable
    data class OasaBusArrival(
        val lineId: String = "",
        val routeCode: Int = 0,
        val vehicleId: String = "",
        val minutesAway: Int = 0,
    )

    companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}
