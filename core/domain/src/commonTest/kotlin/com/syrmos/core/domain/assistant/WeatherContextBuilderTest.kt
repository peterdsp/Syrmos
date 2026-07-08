package com.syrmos.core.domain.assistant

import com.syrmos.core.model.weather.CurrentWeather
import com.syrmos.core.model.weather.WeatherRisk
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.model.weather.WeatherSource
import com.syrmos.core.model.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherContextBuilderTest {

    private fun snapshot(tempC: Double, windKph: Double, code: Int): WeatherSnapshot =
        WeatherSnapshot(
            current = CurrentWeather(
                temperatureC = tempC,
                apparentC = tempC,
                weatherCode = code,
                isDay = true,
                windKph = windKph,
                humidity = 40,
                precipitationMm = 0.0,
            ),
            latitude = 37.98,
            longitude = 23.73,
            placeName = "Athens",
            fetchedAtEpochSeconds = 0L,
        )

    @Test
    fun live_hot_dry_is_HOT() {
        val ctx = WeatherContextBuilder.fromSnapshot(snapshot(36.0, 8.0, code = 0))
        assertEquals(WeatherSource.LIVE, ctx.source)
        assertEquals(WeatherState.HOT, ctx.state)
        assertEquals(WeatherRisk.MEDIUM, ctx.heatRisk)
    }

    @Test
    fun live_thunderstorm_is_RAINY_and_beats_heat() {
        // Hot AND stormy -> rain wins the dominant state.
        val ctx = WeatherContextBuilder.fromSnapshot(snapshot(33.0, 10.0, code = 95))
        assertEquals(WeatherState.RAINY, ctx.state)
        assertEquals(WeatherRisk.HIGH, ctx.rainRisk)
    }

    @Test
    fun live_windy_mild_is_WINDY() {
        val ctx = WeatherContextBuilder.fromSnapshot(snapshot(21.0, 50.0, code = 0))
        assertEquals(WeatherState.WINDY, ctx.state)
        assertEquals(WeatherRisk.HIGH, ctx.windRisk)
    }

    @Test
    fun live_mild_calm_is_NORMAL() {
        val ctx = WeatherContextBuilder.fromSnapshot(snapshot(22.0, 10.0, code = 1))
        assertEquals(WeatherState.NORMAL, ctx.state)
    }

    @Test
    fun no_snapshot_falls_back_to_seasonal_july_hot() {
        val ctx = WeatherContextBuilder.resolve(snapshot = null, month = 7)
        assertEquals(WeatherSource.SEASONAL_FALLBACK, ctx.source)
        assertEquals(WeatherState.HOT, ctx.state)
        // Seasonal has no live reading.
        assertNull(ctx.temperatureC)
        assertNull(ctx.condition)
        assertEquals(7, ctx.month)
    }

    @Test
    fun no_snapshot_winter_is_normal_but_known() {
        val ctx = WeatherContextBuilder.resolve(snapshot = null, month = 1)
        assertEquals(WeatherSource.SEASONAL_FALLBACK, ctx.source)
        assertEquals(WeatherState.NORMAL, ctx.state)
        assertTrue(ctx.isKnown)
    }

    @Test
    fun nothing_at_all_is_unknown() {
        val ctx = WeatherContextBuilder.resolve(snapshot = null, month = null)
        assertEquals(WeatherSource.UNKNOWN, ctx.source)
        assertTrue(!ctx.isKnown)
    }

    @Test
    fun athens_climate_july_is_hot_34() {
        val p = AthensClimate.profile(7)
        assertEquals(34, p.typicalHighC)
        assertEquals(WeatherState.HOT, p.typicalState)
    }

    @Test
    fun risk_thresholds() {
        assertEquals(WeatherRisk.HIGH, WeatherContextBuilder.heatRisk(39.0))
        assertEquals(WeatherRisk.MEDIUM, WeatherContextBuilder.heatRisk(33.0))
        assertEquals(WeatherRisk.LOW, WeatherContextBuilder.heatRisk(25.0))
        assertEquals(WeatherRisk.HIGH, WeatherContextBuilder.windRisk(48.0))
        assertEquals(WeatherRisk.LOW, WeatherContextBuilder.windRisk(12.0))
    }
}
