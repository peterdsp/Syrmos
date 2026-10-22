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
    // Greek variants (null when no localized name is known, e.g. a line terminal
    // that is not resolved from a station). The map UI resolves the active
    // language from these so a tapped simulated train reads in the rider's
    // language instead of the Latin `*Name` fields.
    val lineNameEl: String? = null,
    val originNameEl: String? = null,
    val destinationNameEl: String? = null,
    val currentStationNameEl: String? = null,
    val nextStationNameEl: String? = null,
    val progress: Double,
    val latitude: Double,
    val longitude: Double,
    val isAirportService: Boolean = false,
    /// Compass heading (0 = north) of travel along the current segment. Used to
    /// rotate the directional triangle for national/bus vehicles. 0.0 for the
    /// metro/tram/suburban vehicles that render as directional sprites instead.
    val bearing: Double = 0.0,
)
