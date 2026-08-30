package com.syrmos.core.database.mapper

import com.syrmos.core.database.Line_entity
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineStatus
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region

// region and status MUST round-trip here. getAllLines() reads the database first
// and only falls back to the seed, so dropping them on the way out of SQLDelight
// would quietly hand every line back as athens/operational: an under-construction
// line would look live the moment the database was populated, no matter how
// carefully the payload marked it. That is the whole guarantee, lost in a mapper.
fun Line_entity.toDomain(): Line = Line(
    id = id,
    name = name,
    nameEl = name_el,
    type = LineType.valueOf(type.uppercase()),
    color = LineColor.fromHexOrType(color, type),
    terminalA = terminal_a,
    terminalB = terminal_b,
    stationCount = station_count.toInt(),
    region = Region.fromRaw(region),
    status = LineStatus.fromRaw(status),
)

fun Line.toEntity(): Line_entity = Line_entity(
    id = id,
    name = name,
    name_el = nameEl,
    type = type.name.lowercase(),
    color = color.hex,
    terminal_a = terminalA,
    terminal_b = terminalB,
    station_count = stationCount.toLong(),
    region = region.name.lowercase(),
    status = when (status) {
        LineStatus.OPERATIONAL -> "operational"
        LineStatus.UNDER_CONSTRUCTION -> "under_construction"
        LineStatus.SUSPENDED -> "suspended"
        LineStatus.SEASONAL -> "seasonal"
    },
)
