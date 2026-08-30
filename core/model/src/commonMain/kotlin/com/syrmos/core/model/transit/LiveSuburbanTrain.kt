package com.syrmos.core.model.transit

data class LiveSuburbanTrain(
    val id: String,
    val lineId: String,
    val trainNumber: String,
    val origin: String?,
    val originEn: String?,
    val destination: String?,
    val destinationEn: String?,
    val nextStation: String?,
    val nextStationEn: String?,
    val delayMinutes: Int,
    val serviceType: String,
    val progress: Double?,
    val speedKph: Double?,
    val latitude: Double,
    val longitude: Double,
    val updatedAt: String,
    val course: Double? = null,
    val altitude: Double? = null,
    val locomotiveNumber: String? = null,
    val distanceToDestination: Int? = null,
    val distanceToNextStation: Int? = null,
    val signalStatus: String? = null,
    val corridor: String? = null,
    val trainType: String? = null,
    val scheduledDeparture: String? = null,
    val scheduledArrival: String? = null,
    val scheduleStatus: String? = null,
    val trainId: String? = null,
    val liveStreamUrl: String? = null,
    // Honest boardability signal from the feed. A vehicle the upstream feed
    // could not assign to a passenger route is a real GPS dot but NOT a
    // boardable service, so the UI must not label it "On time" / delayed.
    // Defaults keep older feed rows (and any existing callers) backward-safe.
    val status: String = "in_service",
    val inService: Boolean = true,
)
