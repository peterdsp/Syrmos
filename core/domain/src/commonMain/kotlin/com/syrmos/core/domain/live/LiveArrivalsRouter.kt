package com.syrmos.core.domain.live

/**
 * Routes a (stationId, lineId) query to the right [LiveArrivalsProvider] based
 * on the line's operator. Returns the first non-null result, or null if no
 * provider can answer (the most common case today).
 *
 * Adding a new operator is just: implement [LiveArrivalsProvider], list it in
 * the DI module, no other code changes.
 */
class LiveArrivalsRouter(
    private val providers: List<LiveArrivalsProvider>,
) {
    suspend fun arrivals(
        stationId: String,
        lineId: String,
        limit: Int = 8,
    ): List<LiveArrivalsProvider.LiveArrival>? {
        for (provider in providers) {
            if (lineId !in provider.lineIds()) continue
            val result = runCatching { provider.arrivals(stationId, lineId, limit) }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        return null
    }
}
