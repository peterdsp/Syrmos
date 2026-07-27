package com.syrmos.core.model.fares

/**
 * A grounded fare answer for a from -> to journey, produced by the fare planner
 * and by Ariadne. Prices are transcribed from official operator sources (never
 * invented); see docs/data/2026-07-27-fares-collection.md.
 *
 * When [dynamic] is true the operator sells the trip with booking-time pricing
 * (Hellenic Train intercity), so [fullPriceEur]/[reducedPriceEur] are null and
 * the UI must show [note] + the official [sourceUrl] instead of a made-up price.
 */
data class FareQuote(
    val fullPriceEur: Double?,
    val reducedPriceEur: Double?,
    /** Human product name, e.g. "90-minute integrated ticket". */
    val product: String,
    /** Operator that sells + prices it: OASA, OSETH, Hellenic Train. */
    val operator: String,
    /** Official prices page / booking link. */
    val sourceUrl: String,
    /** Booking-time pricing (intercity): no fixed number, defer to [sourceUrl]. */
    val dynamic: Boolean = false,
    val note: String? = null,
)
