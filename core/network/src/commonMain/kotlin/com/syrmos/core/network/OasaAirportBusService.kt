package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/// Unified airport bus service supporting both legacy OASA endpoint and new normalized model
class AirportBusService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val baseUrl = "https://api-syrmos.peterdsp.dev"

    // MARK: - Legacy OASA support (backward compatible)

    @Deprecated("Use fetchUnified() or fetchOperator() for the new normalized model")
    suspend fun fetchAirportBuses(): OasaAirportBusResponse? = runCatching {
        val response = httpClient.get("$baseUrl/api/oasa-airport-buses")
        val body = response.bodyAsText()
        json.decodeFromString<OasaAirportBusResponse>(body)
    }.getOrNull()

    // MARK: - Unified Normalized Model

    /// Fetch unified airport bus data for all operators (OASA + OSETH)
    suspend fun fetchUnified(): UnifiedAirportBusResponse? = runCatching {
        val response = httpClient.get("$baseUrl/api/airport-buses")
        val body = response.bodyAsText()
        json.decodeFromString<UnifiedAirportBusResponse>(body)
    }.getOrNull()

    /// Fetch data for a specific operator
    suspend fun fetchOperator(operator: String): OperatorResponse? = runCatching {
        val response = httpClient.get("$baseUrl/api/airport-buses/$operator")
        val body = response.bodyAsText()
        json.decodeFromString<OperatorResponse>(body)
    }.getOrNull()

    /// Fetch data for a specific operator and route
    suspend fun fetchRoute(operator: String, routeId: String): RouteResponse? = runCatching {
        val response = httpClient.get("$baseUrl/api/airport-buses/$operator/$routeId")
        val body = response.bodyAsText()
        json.decodeFromString<RouteResponse>(body)
    }.getOrNull()

    /// Fetch sources metadata
    suspend fun fetchSources(): SourcesResponse? = runCatching {
        val response = httpClient.get("$baseUrl/api/airport-buses/sources")
        val body = response.bodyAsText()
        json.decodeFromString<SourcesResponse>(body)
    }.getOrNull()

    // MARK: - Legacy data classes

    @Serializable
    data class OasaAirportBusResponse(
        val updatedAt: String = "",
        val vehicles: List<OasaBusVehicle> = emptyList(),
        val airportArrivals: List<OasaBusArrival> = emptyList(),
        val routes: Map<String, RouteDirs> = emptyMap(),
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

    @Serializable
    data class RouteDirs(
        val toAirport: List<Int> = emptyList(),
        val fromAirport: List<Int> = emptyList(),
    )

    // MARK: - Unified data classes

    @Serializable
    data class UnifiedRouteStop(
        val stop_id: String = "",
        val stop_name: String = "",
        val stop_name_el: String? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val sequence: Int = 0,
        val direction: String? = null,
    )

    @Serializable
    data class UnifiedShapePoint(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val sequence: Int = 0,
        // Present for Athens OASA routes whose road geometry is published per
        // direction ("to_airport" / "from_airport"); null for single-shape
        // operators like OSETH. Clients group shape points by this.
        val direction: String? = null,
    )

    /// Normalized route metadata
    @Serializable
    data class UnifiedRoute(
        val route_id: String = "",
        val operator: String = "",
        val public_name: String = "",
        val public_name_en: String? = null,
        val public_name_el: String? = null,
        val public_name_sq: String? = null,
        val public_name_it: String? = null,
        val origin: String = "",
        val destination: String = "",
        val airport: String = "",
        val color: String = "",
        val direction: String? = null,
        val is_operational: Boolean = true,
        val source: String? = null,
        val source_url: String? = null,
        val attribution: String? = null,
        val observed_at: String? = null,
        val to_airport_route_codes: List<Int> = emptyList(),
        val from_airport_route_codes: List<Int> = emptyList(),
        val to_airport_stop_ids: List<Int> = emptyList(),
        val from_airport_stop_ids: List<Int> = emptyList(),
        // Ordered operator stop catalogue and its verified stop-derived
        // geometry. These fields are optional for backwards-compatible cached
        // snapshots and are safe to use offline.
        val stops: List<UnifiedRouteStop> = emptyList(),
        val shape: List<UnifiedShapePoint> = emptyList(),
        val geometry_source: String? = null,
    )

    /// Normalized vehicle position
    @Serializable
    data class UnifiedVehicle(
        val vehicle_id: String = "",
        val line_id: String = "",
        val route_id: String = "",
        val operator: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val heading: Double = 0.0,
        val observed_at: String = "",
        val source: String? = null,
        val source_url: String? = null,
        val confidence: String? = null,
        val attribution: String? = null,
        val to_airport: Boolean? = null,
    )

    /// Normalized arrival/ETA
    @Serializable
    data class UnifiedArrival(
        val line_id: String = "",
        val route_id: String = "",
        val operator: String = "",
        val stop_id: String = "",
        val stop_name: String = "",
        val destination: String = "",
        val direction: String? = null,
        val scheduled_time: String? = null,
        val estimated_minutes: Int? = null,
        val vehicle_id: String? = null,
        val source: String? = null,
        val source_url: String? = null,
        val observed_at: String = "",
        val freshness: Double? = null,
        val confidence: String? = null,
        val service_status: String? = null,
        val attribution: String? = null,
    )

    /// Normalized alert
    @Serializable
    data class UnifiedAlert(
        val alert_id: String = "",
        val operator: String = "",
        val title: String = "",
        val summary: String = "",
        val url: String? = null,
        val severity: String? = null,
        val source: String? = null,
        val source_url: String? = null,
        val observed_at: String? = null,
        val attribution: String? = null,
        val route_id: String? = null,
        val title_en: String? = null,
        val summary_en: String? = null,
        val valid_from: String? = null,
        val valid_until: String? = null,
    )

    /// Per-operator data in unified response
    @Serializable
    data class OperatorData(
        val operator: String = "",
        val source: String? = null,
        val attribution: String? = null,
        val updated_at: String? = null,
        val routes: List<UnifiedRoute> = emptyList(),
        val vehicles: List<UnifiedVehicle> = emptyList(),
        val arrivals: List<UnifiedArrival> = emptyList(),
        val alerts: List<UnifiedAlert> = emptyList(),
        val generated_at: String? = null,
        val generation_duration: Double? = null,
    )

    /// Unified response from /api/airport-buses
    @Serializable
    data class UnifiedAirportBusResponse(
        val updated_at: String = "",
        val operators: Map<String, OperatorData> = emptyMap(),
        val sources: List<SourceInfo> = emptyList(),
        val generated_at: String? = null,
        val generation_duration: Double? = null,
    )

    /// Source information
    @Serializable
    data class SourceInfo(
        val operator: String? = null,
        val name: String? = null,
        val source_url: String? = null,
        val attribution: String? = null,
        val is_live: Boolean? = null,
        val last_tested: String? = null,
        val notes: String? = null,
    )

    /// Single operator response
    @Serializable
    data class OperatorResponse(
        val operator: String = "",
        val source: String? = null,
        val attribution: String? = null,
        val updated_at: String? = null,
        val routes: List<UnifiedRoute> = emptyList(),
        val vehicles: List<UnifiedVehicle> = emptyList(),
        val arrivals: List<UnifiedArrival> = emptyList(),
        val alerts: List<UnifiedAlert> = emptyList(),
        val generated_at: String? = null,
        val generation_duration: Double? = null,
    )

    /// Single route response
    @Serializable
    data class RouteResponse(
        val operator: String = "",
        val route_id: String = "",
        val source: String? = null,
        val attribution: String? = null,
        val updated_at: String? = null,
        val route: UnifiedRoute? = null,
        val vehicles: List<UnifiedVehicle> = emptyList(),
        val arrivals: List<UnifiedArrival> = emptyList(),
        val alerts: List<UnifiedAlert> = emptyList(),
        val generated_at: String? = null,
        val generation_duration: Double? = null,
    )

    /// Sources response
    @Serializable
    data class SourcesResponse(
        val sources: List<SourceInfo> = emptyList(),
        val generated_at: String? = null,
    )

    companion object {
        private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    }
}

// Type alias for backward compatibility
typealias OasaAirportBusService = AirportBusService
