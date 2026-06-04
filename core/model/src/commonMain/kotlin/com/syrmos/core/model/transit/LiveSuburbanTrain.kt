package com.syrmos.core.model.transit

data class LiveSuburbanTrain(
    val id: String,
    val lineId: String,
    val trainNumber: String,
    val origin: String?,
    val destination: String?,
    val nextStation: String?,
    val delayMinutes: Int,
    val progress: Double?,
    val speedKph: Double?,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: String,
)
