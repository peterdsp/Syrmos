package com.syrmos.core.model.planner

data class JourneySegment(
    val lineId: String,
    val lineName: String,
    val fromStationId: String,
    val fromStationName: String,
    val toStationId: String,
    val toStationName: String,
    val stationCount: Int,
    val estimatedMinutes: Int,
    val isTransfer: Boolean = false,
)
