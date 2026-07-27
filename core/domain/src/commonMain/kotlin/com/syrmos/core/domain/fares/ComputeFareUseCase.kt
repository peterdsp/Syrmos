package com.syrmos.core.domain.fares

import com.syrmos.core.model.fares.FareQuote
import com.syrmos.core.model.transit.Region

/**
 * Fare-relevant facts about a station, resolved by the caller from the station's
 * lines (region, mode). Kept minimal + pure so the fare engine is offline and
 * unit-testable with no repository.
 */
data class FareStation(
    val id: String,
    /**
     * Every network this station belongs to (via its lines). A station can be on
     * more than one: a Thessaloniki interchange like GR_THE is on the national
     * IC1 AND the local TP suburban lines. Using the full set lets the engine
     * charge a LOCAL trip locally (both stations share Thessaloniki) rather than
     * as intercity just because one endpoint also sits on a national line.
     */
    val regions: Set<Region>,
    /** Athens airport (ATH) — triggers the airport metro fare, not the flat urban one. */
    val isAirport: Boolean = false,
    /** Thessaloniki suburban (TP*) vs urban metro/bus — different zone price. */
    val isSuburban: Boolean = false,
)

/**
 * Grounded fare tables. Every number is transcribed from an official operator
 * source (see docs/data/2026-07-27-fares-collection.md). Update here + that doc
 * together; never invent a price.
 */
internal object FareTables {
    const val OASA = "OASA"
    const val OSETH = "OSETH"
    const val HELLENIC_TRAIN = "Hellenic Train"

    const val OASA_URL = "https://www.oasa.gr/en/tickets/prices-of-products/"
    const val OSETH_URL = "https://oseth.com.gr/en/tickets"
    const val HT_URL = "https://www.hellenictrain.gr/en/patras-suburban-railway"
    const val HT_BOOK_URL = "https://newtickets.hellenictrain.gr/"

    // Athens OASA integrated (metro/tram/bus/suburban within the urban zone).
    val athensUrban = FareQuote(1.20, 0.50, "90-minute integrated ticket", OASA, OASA_URL)
    val athensAirport = FareQuote(9.00, 4.50, "Airport Metro ticket (Line 3)", OASA, OASA_URL)

    // Thessaloniki OSETH (metro & bus not yet interoperable — single-mode fare).
    val thessUrban = FareQuote(0.60, 0.30, "Urban single ticket", OSETH, OSETH_URL)
    val thessSuburban = FareQuote(0.80, 0.40, "Suburban (peri-urban) single ticket", OSETH, OSETH_URL)

    // Patras suburban zone grid. Single/adjacent-zone base; the widest span
    // (A+B+C, e.g. into Kato Achaia) is €3.00. Exact multi-zone resolution is a
    // later refinement — this base is correct for the common core-network trip.
    val patrasBase = FareQuote(
        1.40, 1.00, "Suburban ticket (zone A/B/C)", HELLENIC_TRAIN, HT_URL,
        note = "Trips spanning several zones (up to A+B+C, e.g. to Kato Achaia) cost up to €3.00.",
    )

    // Intercity / regional + rail-replacement buses on IC segments: booking-time price.
    val intercity = FareQuote(
        null, null, "Intercity / regional ticket", HELLENIC_TRAIN, HT_BOOK_URL,
        dynamic = true,
        note = "Price is set at booking (route, class, date). Discounts: early-booking up to " +
            "15%, return 20%, students 25-50%, youth under 24 25%, children 4-12 50%, reduced " +
            "mobility 50% (total reduction capped at 40%). Book for the exact fare.",
    )
}

/**
 * Journey fare planner core: from -> to -> grounded price. Pure + offline.
 *
 * Zone networks (Athens, Thessaloniki, Patras) resolve to an exact price; any
 * cross-region trip or a NATIONAL leg is intercity, which is booking-priced, so
 * the quote is returned [FareQuote.dynamic] with the discount structure instead
 * of a fabricated number.
 */
class ComputeFareUseCase {
    fun invoke(from: FareStation, to: FareStation): FareQuote {
        // Charge the trip on the LOCAL network the two stations share. If they
        // share no local (non-national) network - a different city each, or the
        // only link is a national line - it is intercity, booking-priced.
        val local = (from.regions intersect to.regions).firstOrNull { it != Region.NATIONAL }
        return when (local) {
            Region.ATHENS ->
                if (from.isAirport || to.isAirport) FareTables.athensAirport
                else FareTables.athensUrban
            Region.THESSALONIKI ->
                if (from.isSuburban || to.isSuburban) FareTables.thessSuburban
                else FareTables.thessUrban
            Region.PATRAS -> FareTables.patrasBase
            else -> FareTables.intercity
        }
    }
}
