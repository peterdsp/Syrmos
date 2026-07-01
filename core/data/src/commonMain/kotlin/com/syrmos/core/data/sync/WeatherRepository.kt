package com.syrmos.core.data.sync

import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.network.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Holds the latest weather snapshot. The card and Ariadne's weather-aware
 * routing read [snapshot]; it carries the fetch time so both can show "as of
 * HH:MM" and degrade honestly when there's no network. Defaults to central
 * Athens so Web and no-location launches still show something.
 */
class WeatherRepository(
    private val weatherService: WeatherService,
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    /** Most recent snapshot, regardless of age, for offline reads. */
    val cached: WeatherSnapshot? get() = _snapshot.value

    suspend fun refresh(
        latitude: Double = ATHENS_LAT,
        longitude: Double = ATHENS_LON,
        placeName: String = "Athens",
    ) {
        val reading = weatherService.fetch(latitude, longitude) ?: return
        _snapshot.value = WeatherSnapshot(
            current = reading.current,
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            fetchedAtEpochSeconds = Clock.System.now().epochSeconds,
            highC = reading.highC,
            lowC = reading.lowC,
            hourly = reading.hourly,
        )
    }

    companion object {
        const val ATHENS_LAT = 37.9838
        const val ATHENS_LON = 23.7275
    }
}
