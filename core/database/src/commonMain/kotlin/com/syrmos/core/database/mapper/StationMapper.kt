package com.syrmos.core.database.mapper

import com.syrmos.core.database.Station_entity
import com.syrmos.core.model.transit.Station

fun Station_entity.toDomain(lineIds: List<String> = emptyList()): Station = Station(
    id = id,
    name = name,
    nameEl = name_el,
    latitude = latitude,
    longitude = longitude,
    lineIds = lineIds,
    isInterchange = is_interchange != 0L,
    accessibility = accessibility != 0L,
    zone = zone.toInt(),
)
