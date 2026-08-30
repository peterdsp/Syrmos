package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whether a line actually carries trains, and if not, why.
 *
 * This exists so track that carries no service right now can still be drawn
 * without implying one. Three distinct "not a normal live service" states, each
 * honest about a different reality:
 *
 *  - [UNDER_CONSTRUCTION]: built or being built, never yet opened. Thessaloniki
 *    metro Line 2 was this before it opened. Renders greyed, no predictions.
 *  - [SUSPENDED]: a real line that DID run and is temporarily not running (e.g.
 *    the Diakopto-Kalavryta rack railway, halted 13 March 2026 after rockfalls).
 *    Renders greyed but is labelled "suspended", not "under construction", because
 *    calling a 130-year-old railway "under construction" is a lie.
 *  - [SEASONAL]: runs only part of the year and only on some day-types (the Pelion
 *    railway runs weekends and holidays, April to October). It is a real, boardable
 *    line in season, so it draws in its own colour, but its sheet says so and it is
 *    never given a confident live countdown out of season.
 *
 * Opening, suspending, or seasonally scoping a line is a data change, not a code
 * change: set the status in the server DB and the bundled seed.
 *
 * Prefer [Line.isOperational] / [Line.isBuiltButClosed] / [Line.isSeasonal] over
 * comparing to this directly.
 */
@Serializable
enum class LineStatus {
    @SerialName("operational")
    OPERATIONAL,

    @SerialName("under_construction")
    UNDER_CONSTRUCTION,

    @SerialName("suspended")
    SUSPENDED,

    @SerialName("seasonal")
    SEASONAL,
    ;

    companion object {
        /**
         * Unknown values fall back to OPERATIONAL, matching how the app behaved
         * before the field existed. A stale bundle must not make live Athens lines
         * vanish.
         */
        fun fromRaw(raw: String?): LineStatus = when (raw?.lowercase()) {
            "under_construction" -> UNDER_CONSTRUCTION
            "suspended" -> SUSPENDED
            "seasonal" -> SEASONAL
            else -> OPERATIONAL
        }
    }
}
