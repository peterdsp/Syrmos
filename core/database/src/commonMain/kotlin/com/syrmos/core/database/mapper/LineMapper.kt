package com.syrmos.core.database.mapper

import com.syrmos.core.database.Line_entity
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType

fun Line_entity.toDomain(): Line = Line(
    id = id,
    name = name,
    nameEl = name_el,
    type = LineType.valueOf(type.uppercase()),
    color = LineColor.entries.first { it.hex == color },
    terminalA = terminal_a,
    terminalB = terminal_b,
    stationCount = station_count.toInt(),
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
)
