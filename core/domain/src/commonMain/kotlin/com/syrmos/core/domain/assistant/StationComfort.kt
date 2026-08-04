package com.syrmos.core.domain.assistant

import com.syrmos.core.model.transit.LineType

/**
 * Weather-exposure model for a route, used by Ariadne when the user asks for a
 * rainy-day route. Derived from line type rather than a per-station dataset
 * (which doesn't exist yet): the Athens metro runs underground and sheltered,
 * tram stops are open-air at street level, and suburban stations are mostly
 * surface-level with partial cover.
 */
enum class Exposure { SHELTERED, MIXED, EXPOSED }

object StationComfort {
    fun forLineType(type: LineType): Exposure = when (type) {
        LineType.METRO -> Exposure.SHELTERED
        LineType.TRAM -> Exposure.EXPOSED
        LineType.SUBURBAN -> Exposure.MIXED
        LineType.BUS -> Exposure.EXPOSED
        LineType.SCENIC -> Exposure.EXPOSED
    }

    /** Worst exposure across a route's legs, so the advice is honest. */
    fun forRoute(types: List<LineType>): Exposure {
        if (types.any { forLineType(it) == Exposure.EXPOSED }) return Exposure.EXPOSED
        if (types.any { forLineType(it) == Exposure.MIXED }) return Exposure.MIXED
        return Exposure.SHELTERED
    }
}
