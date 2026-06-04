package com.syrmos.core.model.transit

data class SimulatedTrain(
    val id: String,
    val lineId: String,
    val lineName: String,
    val lineColor: LineColor,
    val lineType: LineType,
    val direction: Direction,
    val originName: String,
    val destinationName: String,
    val currentStationName: String,
    val nextStationName: String,
    val progress: Double,
    val latitude: Double,
    val longitude: Double,
    val isAirportService: Boolean = false,
)
