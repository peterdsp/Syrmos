package com.syrmos.core.domain.live

/**
 * Pluggable source of real-time arrival predictions for a given (station, line).
 *
 * Today, Athens metro and tram operators (STASY) do **not** expose a public,
 * machine-readable real-time arrivals feed. Hellenic Train exposes only train
 * positions via railway.gov.gr — not stop-level predictions. So every concrete
 * implementation of this interface currently returns `null` from `arrivals(...)`.
 *
 * The interface and the use-case wiring exist now so that, the day any of those
 * operators publishes a feed, only the body of the appropriate provider needs
 * to be filled in. The rest of the app — the projector fallback, the UI, the
 * countdown, the offline-first bundle — keeps working unchanged.
 *
 * **Source-of-truth rule**: when `arrivals(...)` returns a non-null list, the
 * use case prefers it over the rule-based projector for that line. When it
 * returns `null`, the projector remains the answer.
 *
 * Real-time data only ever supplements rule-based projection. It must never
 * silently degrade the offline-first guarantee — failures here just fall
 * through to the projector.
 */
interface LiveArrivalsProvider {
    /**
     * Identifies which operator/feed this provider speaks for.
     * Used in logs and the admin scrape_log so a maintainer can tell at a
     * glance which feed regressed or came online.
     */
    val sourceId: String

    /**
     * Which line ids this provider can answer for. The use-case routes
     * lookups to the right provider based on this set.
     */
    fun lineIds(): Set<String>

    /**
     * Returns the next [limit] real-time arrival predictions at [stationId]
     * on [lineId], or `null` if the upstream feed has nothing for this query
     * (no live feed exists yet, feed down, station not modelled, etc.).
     *
     * Implementations must:
     * - Return `null` on network failure rather than throwing
     * - Respect [limit] strictly (no oversized payloads)
     * - Filter out arrivals more than 60 minutes away (out-of-band data)
     */
    suspend fun arrivals(stationId: String, lineId: String, limit: Int = 8): List<LiveArrival>?

    data class LiveArrival(
        /** Minutes until the train arrives at the station, rounded to nearest integer >= 0. */
        val minutesAway: Int,
        /** Direction label as the operator publishes it (e.g. "Airport", "Piraeus"). */
        val direction: String,
        /** Optional vehicle/train identifier for fleet tracking and debugging. */
        val vehicleId: String? = null,
        /** Whether this prediction is from live tracking (true) or a scheduled time (false). */
        val isLive: Boolean = true,
    )
}

/**
 * STASY (Metro + Tram) placeholder. STASY does not currently publish a
 * machine-readable real-time arrivals feed. When they do, fill in the body
 * of `arrivals` — the rest of the app is wired and waiting.
 */
class StasyLiveArrivalsProvider : LiveArrivalsProvider {
    override val sourceId: String = "stasy.gr"
    override fun lineIds(): Set<String> = setOf("M1", "M2", "M3", "M3_AIR", "T6", "T7")
    override suspend fun arrivals(stationId: String, lineId: String, limit: Int): List<LiveArrivalsProvider.LiveArrival>? {
        // No upstream feed yet. Returning null lets the projector be the answer.
        return null
    }
}

/**
 * OASA Telematics (bus/trolley historically; metro/tram unconfirmed) placeholder.
 * OASA's public telematics page is HTML-only today. If they expose a JSON feed,
 * implement the parser here.
 */
class OasaLiveArrivalsProvider : LiveArrivalsProvider {
    override val sourceId: String = "telematics.oasa.gr"
    override fun lineIds(): Set<String> = setOf("M1", "M2", "M3", "M3_AIR", "T6", "T7")
    override suspend fun arrivals(stationId: String, lineId: String, limit: Int): List<LiveArrivalsProvider.LiveArrival>? = null
}

/**
 * Hellenic Train suburban. railway.gov.gr exposes train *positions* via SSE
 * (already consumed by `RailwayGovLiveTrackerService`) but not per-stop
 * arrival predictions. A future feed of stop ETAs would slot in here.
 */
class HellenicTrainLiveArrivalsProvider : LiveArrivalsProvider {
    override val sourceId: String = "railway.gov.gr"
    override fun lineIds(): Set<String> = setOf("A1", "A2", "A3", "A4")
    override suspend fun arrivals(stationId: String, lineId: String, limit: Int): List<LiveArrivalsProvider.LiveArrival>? = null
}
