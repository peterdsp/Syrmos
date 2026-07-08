package com.syrmos.core.model.weather

/**
 * Where a [WeatherContext] came from, so Ariadne can be honest about certainty.
 * LIVE / FORECAST are real observations; SEASONAL_FALLBACK is climatology for
 * the month ("usually hot this time of year"), never presented as "now";
 * UNKNOWN means we have nothing and must say so.
 */
enum class WeatherSource { LIVE, FORECAST, SEASONAL_FALLBACK, UNKNOWN }

/** The dominant advisory Ariadne acts on. Kept deliberately small (the four
 *  states that actually change transit advice) rather than a full forecast. */
enum class WeatherState { NORMAL, HOT, RAINY, WINDY }

/** Coarse risk band for a single factor (heat / rain / wind). */
enum class WeatherRisk { LOW, MEDIUM, HIGH }

/**
 * A weather signal reduced to what changes transit advice: the source (so
 * phrasing stays honest), the dominant [state], and per-factor risk bands. It
 * never replaces the transit engine; the route ranker and answer composer read
 * it to tilt advice (prefer underground in heat, avoid exposed tram in rain).
 *
 * [condition], [temperatureC], and [windKph] are null for a SEASONAL_FALLBACK
 * context, because climatology gives a typical picture, not a live reading.
 */
data class WeatherContext(
    val source: WeatherSource,
    val state: WeatherState,
    val condition: WeatherCondition? = null,
    val temperatureC: Double? = null,
    val windKph: Double? = null,
    val heatRisk: WeatherRisk = WeatherRisk.LOW,
    val rainRisk: WeatherRisk = WeatherRisk.LOW,
    val windRisk: WeatherRisk = WeatherRisk.LOW,
    val placeName: String? = null,
    /** Month (1..12) this context describes; set for seasonal, else null. */
    val month: Int? = null,
) {
    val isLive: Boolean get() = source == WeatherSource.LIVE || source == WeatherSource.FORECAST
    val isKnown: Boolean get() = source != WeatherSource.UNKNOWN

    companion object {
        val UNKNOWN = WeatherContext(source = WeatherSource.UNKNOWN, state = WeatherState.NORMAL)
    }
}

/**
 * Typical Athens weather for a calendar month, the honest fallback when there's
 * no live reading. [typicalHighC] and [typicalCondition] describe the norm; the
 * caller phrases it as "usually / this time of year", never "now".
 */
data class SeasonalWeatherProfile(
    val month: Int,               // 1..12
    val city: String,
    val typicalCondition: WeatherCondition,
    val typicalHighC: Int,
    val typicalState: WeatherState,
)
