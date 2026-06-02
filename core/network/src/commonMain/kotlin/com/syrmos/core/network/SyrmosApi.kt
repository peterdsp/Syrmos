package com.syrmos.core.network

/**
 * Placeholder for future OASA/STASY live data API.
 *
 * Athens transit does not currently expose a public API for
 * real-time arrivals. When one becomes available, this interface
 * will provide live departure data to supplement the offline
 * schedule database.
 */
interface SyrmosApi {
    suspend fun getLiveArrivals(stationId: String): List<LiveArrival>
}

data class LiveArrival(
    val lineId: String,
    val direction: String,
    val estimatedMinutes: Int,
)
