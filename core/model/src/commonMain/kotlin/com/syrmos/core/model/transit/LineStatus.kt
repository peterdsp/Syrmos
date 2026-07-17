package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whether a line actually carries trains.
 *
 * This exists so track that is built but not open can be drawn without implying a
 * service. Thessaloniki metro Line 2 (the Kalamaria extension) opens at the end of
 * July 2026; until then it renders greyed and every prediction path skips it.
 *
 * Opening a line is then a data change, not a code change: flip the status in the
 * server DB and seed its bands and offsets.
 *
 * Prefer [Line.isOperational] over comparing to this directly.
 */
@Serializable
enum class LineStatus {
    @SerialName("operational")
    OPERATIONAL,

    @SerialName("under_construction")
    UNDER_CONSTRUCTION,
    ;

    companion object {
        /**
         * Unknown values fall back to OPERATIONAL, matching how the app behaved
         * before the field existed. A stale bundle must not make live Athens lines
         * vanish.
         */
        fun fromRaw(raw: String?): LineStatus = when (raw?.lowercase()) {
            "under_construction" -> UNDER_CONSTRUCTION
            else -> OPERATIONAL
        }
    }
}
