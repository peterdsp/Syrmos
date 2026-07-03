package com.syrmos.core.model.weather

/**
 * Current conditions used both by the weather card and by Ariadne's
 * weather-aware routing. Weather is inherently online, so a snapshot carries
 * the time it was fetched; surfaces show that timestamp and degrade honestly
 * when it is stale or missing, matching the app's offline-first contract.
 */
data class CurrentWeather(
    val temperatureC: Double,
    val apparentC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val windKph: Double,
    val humidity: Int,
    val precipitationMm: Double,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

data class WeatherSnapshot(
    val current: CurrentWeather,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val fetchedAtEpochSeconds: Long,
    val highC: Double? = null,
    val lowC: Double? = null,
    val hourly: List<HourlyForecast> = emptyList(),
)

/** One point in the next-hours strip shown on the weather card. */
data class HourlyForecast(
    /** "14:00" style label. */
    val hourLabel: String,
    val temperatureC: Double,
    val weatherCode: Int,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode)
}

/** WMO weather-code buckets, with the bits routing and the UI care about. */
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, SHOWERS, THUNDERSTORM, UNKNOWN;

    /** True when being outside is unpleasant, so routing should reduce exposure. */
    val isWet: Boolean
        get() = this == DRIZZLE || this == RAIN || this == SNOW ||
            this == SHOWERS || this == THUNDERSTORM

    /**
     * True when conditions are severe enough to warrant an on-Home warning
     * card with emergency contact numbers. Heavy showers, thunderstorms,
     * and snow qualify; light drizzle or overcast do not.
     */
    val isSevere: Boolean
        get() = this == SHOWERS || this == THUNDERSTORM || this == SNOW

    companion object {
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82, 85, 86 -> SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}
