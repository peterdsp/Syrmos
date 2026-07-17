package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The network a line or station belongs to.
 *
 * Deliberately not called "city": the Thessaloniki suburban corridors run to
 * Larisa (~150 km) and Florina (~160 km), and [NATIONAL] intercity services span
 * the country, so a line legitimately crosses several cities. The unit is the
 * network, not the municipality.
 *
 * Region drives exactly five things: the default map camera, the no-GPS home
 * hero, track-picker grouping, announcement scoping (a Thessaloniki user must
 * never be shown Athens STASY alerts) and the weather coordinates.
 *
 * Nearest-station deliberately stays global. If you are physically in
 * Thessaloniki, the nearest station already is a Thessaloniki one, so geometry
 * settles it and a region filter would only add a way to be wrong.
 */
@Serializable
enum class Region {
    @SerialName("athens")
    ATHENS,

    @SerialName("thessaloniki")
    THESSALONIKI,

    /** Intercity / long-distance. An Athens-Thessaloniki train belongs to neither city. */
    @SerialName("national")
    NATIONAL,
    ;

    companion object {
        /** Unknown values fall back to Athens rather than throwing on a newer payload. */
        fun fromRaw(raw: String?): Region = when (raw?.lowercase()) {
            "thessaloniki" -> THESSALONIKI
            "national" -> NATIONAL
            else -> ATHENS
        }
    }
}
