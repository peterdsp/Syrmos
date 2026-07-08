package com.syrmos.core.domain.assistant

import com.syrmos.core.model.weather.SeasonalWeatherProfile
import com.syrmos.core.model.weather.WeatherCondition
import com.syrmos.core.model.weather.WeatherContext
import com.syrmos.core.model.weather.WeatherRisk
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.model.weather.WeatherSource
import com.syrmos.core.model.weather.WeatherState

/**
 * Athens climatology: the typical weather per month, used as an honest fallback
 * when there's no live reading. Values are round monthly-high norms for the
 * Athens basin; precision isn't the point, the honest "this time of year"
 * framing is. Summer (Jun–Sep) is hot and dry; winter (Nov–Mar) is cooler with
 * rain possible; spring/autumn are mild.
 */
object AthensClimate {
    fun profile(month: Int): SeasonalWeatherProfile {
        val m = ((month - 1).mod(12)) + 1
        val highC = TYPICAL_HIGH_C[m - 1]
        val (condition, state) = when (m) {
            6, 7, 8, 9 -> WeatherCondition.CLEAR to WeatherState.HOT
            12, 1, 2 -> WeatherCondition.RAIN to WeatherState.NORMAL
            3, 11 -> WeatherCondition.PARTLY_CLOUDY to WeatherState.NORMAL
            else -> WeatherCondition.CLEAR to WeatherState.NORMAL // 4, 5, 10: mild
        }
        return SeasonalWeatherProfile(
            month = m,
            city = "Athens",
            typicalCondition = condition,
            typicalHighC = highC,
            typicalState = state,
        )
    }

    // Jan..Dec typical daily high (°C), Athens.
    private val TYPICAL_HIGH_C = intArrayOf(13, 14, 16, 20, 26, 31, 34, 34, 29, 23, 18, 14)
}

/**
 * Builds a [WeatherContext] from what we actually have: a live [WeatherSnapshot]
 * when one is cached, otherwise the Athens seasonal profile for the month, and
 * UNKNOWN only when even the month is unavailable. The source is stamped so the
 * answer composer can phrase live vs typical honestly.
 */
object WeatherContextBuilder {

    fun fromSnapshot(snap: WeatherSnapshot): WeatherContext {
        val temp = snap.current.temperatureC
        val wind = snap.current.windKph
        val condition = snap.current.condition
        val heat = heatRisk(temp)
        val rain = rainRisk(condition)
        val windR = windRisk(wind)
        return WeatherContext(
            source = WeatherSource.LIVE,
            state = dominantState(rain, heat, windR),
            condition = condition,
            temperatureC = temp,
            windKph = wind,
            heatRisk = heat,
            rainRisk = rain,
            windRisk = windR,
            placeName = snap.placeName,
        )
    }

    fun fromSeasonal(profile: SeasonalWeatherProfile): WeatherContext {
        val heat = heatRisk(profile.typicalHighC.toDouble())
        val rain = rainRisk(profile.typicalCondition)
        return WeatherContext(
            source = WeatherSource.SEASONAL_FALLBACK,
            // Trust the curated seasonal state, but never below what the typical
            // temperature implies (a 34° "HOT" month stays HOT).
            state = if (heat != WeatherRisk.LOW) WeatherState.HOT else profile.typicalState,
            condition = null,
            temperatureC = null,
            windKph = null,
            heatRisk = heat,
            rainRisk = rain,
            windRisk = WeatherRisk.LOW,
            placeName = profile.city,
            month = profile.month,
        )
    }

    /** Live snapshot wins; else seasonal for [month]; else UNKNOWN. */
    fun resolve(snapshot: WeatherSnapshot?, month: Int?): WeatherContext = when {
        snapshot != null -> fromSnapshot(snapshot)
        month != null -> fromSeasonal(AthensClimate.profile(month))
        else -> WeatherContext.UNKNOWN
    }

    // Athens-tuned thresholds. Heat matters for exposed waits and long walks;
    // wind matters for coastal/elevated tram sections; rain risk tracks the
    // condition's wetness/severity.
    fun heatRisk(tempC: Double): WeatherRisk = when {
        tempC >= 38 -> WeatherRisk.HIGH
        tempC >= 32 -> WeatherRisk.MEDIUM
        else -> WeatherRisk.LOW
    }

    fun windRisk(windKph: Double): WeatherRisk = when {
        windKph >= 45 -> WeatherRisk.HIGH
        windKph >= 30 -> WeatherRisk.MEDIUM
        else -> WeatherRisk.LOW
    }

    fun rainRisk(condition: WeatherCondition): WeatherRisk = when {
        condition.isSevere -> WeatherRisk.HIGH
        condition == WeatherCondition.RAIN -> WeatherRisk.MEDIUM
        condition == WeatherCondition.DRIZZLE -> WeatherRisk.MEDIUM
        else -> WeatherRisk.LOW
    }

    /** Rain beats heat beats wind when picking the single dominant advisory. */
    fun dominantState(rain: WeatherRisk, heat: WeatherRisk, wind: WeatherRisk): WeatherState = when {
        rain != WeatherRisk.LOW -> WeatherState.RAINY
        heat != WeatherRisk.LOW -> WeatherState.HOT
        wind != WeatherRisk.LOW -> WeatherState.WINDY
        else -> WeatherState.NORMAL
    }
}
