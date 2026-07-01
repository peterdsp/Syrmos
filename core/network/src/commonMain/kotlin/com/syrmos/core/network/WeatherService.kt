package com.syrmos.core.network

import com.syrmos.core.model.weather.CurrentWeather
import com.syrmos.core.model.weather.HourlyForecast
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Current conditions, next-hours strip, and today's high/low from Open-Meteo
 * (free, no API key, CORS-friendly so it works on Web too). All failures are
 * silent; the caller keeps the last reading. The only genuinely-online piece
 * of the weather feature.
 */
class WeatherService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Reading(
        val current: CurrentWeather,
        val hourly: List<HourlyForecast>,
        val highC: Double?,
        val lowC: Double?,
    )

    suspend fun fetch(latitude: Double, longitude: Double): Reading? {
        return runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=time,temperature_2m,apparent_temperature,is_day,precipitation," +
                "relative_humidity_2m,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=1&timezone=auto"
            val body = httpClient.get(url).bodyAsText()
            val resp = json.decodeFromString<OpenMeteoResponse>(body)
            Reading(
                current = resp.current.toDomain(),
                hourly = resp.nextHours(6),
                highC = resp.daily?.max?.firstOrNull(),
                lowC = resp.daily?.min?.firstOrNull(),
            )
        }.getOrNull()
    }

    @Serializable
    private data class OpenMeteoResponse(
        val current: Current,
        val hourly: Hourly? = null,
        val daily: Daily? = null,
    ) {
        /** The next [count] hourly points at or after the current hour. */
        fun nextHours(count: Int): List<HourlyForecast> {
            val h = hourly ?: return emptyList()
            val times = h.time
            val temps = h.temperature
            val codes = h.weatherCode
            if (times.isEmpty()) return emptyList()
            // ISO strings sort lexicographically, so >= current.time works.
            val start = times.indexOfFirst { it >= current.time }.let { if (it < 0) 0 else it }
            val end = minOf(start + count, times.size)
            return (start until end).mapNotNull { i ->
                val t = temps.getOrNull(i) ?: return@mapNotNull null
                val c = codes.getOrNull(i) ?: 0
                HourlyForecast(hourLabel = times[i].substringAfter('T').take(5), temperatureC = t, weatherCode = c)
            }
        }
    }

    @Serializable
    private data class Current(
        val time: String = "",
        @SerialName("temperature_2m") val temperature: Double = 0.0,
        @SerialName("apparent_temperature") val apparent: Double = 0.0,
        @SerialName("is_day") val isDay: Int = 1,
        @SerialName("precipitation") val precipitation: Double = 0.0,
        @SerialName("relative_humidity_2m") val humidity: Int = 0,
        @SerialName("weather_code") val weatherCode: Int = 0,
        @SerialName("wind_speed_10m") val windSpeed: Double = 0.0,
    ) {
        fun toDomain() = CurrentWeather(
            temperatureC = temperature,
            apparentC = apparent,
            weatherCode = weatherCode,
            isDay = isDay == 1,
            windKph = windSpeed,
            humidity = humidity,
            precipitationMm = precipitation,
        )
    }

    @Serializable
    private data class Hourly(
        val time: List<String> = emptyList(),
        @SerialName("temperature_2m") val temperature: List<Double> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    )

    @Serializable
    private data class Daily(
        @SerialName("temperature_2m_max") val max: List<Double> = emptyList(),
        @SerialName("temperature_2m_min") val min: List<Double> = emptyList(),
    )
}
