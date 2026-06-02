package com.syrmos.core.model.location

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
)

data class NearestStationResult(
    val stationId: String,
    val stationName: String,
    val distanceMeters: Int,
    val lineIds: List<String>,
)
