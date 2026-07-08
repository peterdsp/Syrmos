package com.syrmos.core.domain.assistant

import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.weather.WeatherContext
import com.syrmos.core.model.weather.WeatherRisk
import com.syrmos.core.model.weather.WeatherSource
import com.syrmos.core.model.weather.WeatherState
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteRankerTest {

    private fun route(minutes: Int, transfers: Int): JourneyResult =
        JourneyResult(segments = emptyList(), totalMinutes = minutes, transferCount = transfers)

    private fun candidate(minutes: Int, transfers: Int, exposure: Exposure) =
        RouteCandidate(route(minutes, transfers), exposure)

    private val hotDay = WeatherContext(
        source = WeatherSource.LIVE,
        state = WeatherState.HOT,
        heatRisk = WeatherRisk.HIGH,
    )
    private val calmDay = WeatherContext(source = WeatherSource.LIVE, state = WeatherState.NORMAL)

    @Test
    fun fastest_prefers_fewer_minutes() {
        val fast = candidate(20, transfers = 2, exposure = Exposure.SHELTERED)
        val slow = candidate(30, transfers = 0, exposure = Exposure.SHELTERED)
        val best = RouteRanker.best(listOf(slow, fast), RoutePreference.FASTEST, calmDay)
        assertEquals(20, best?.candidate?.result?.totalMinutes)
    }

    @Test
    fun fewest_changes_prefers_direct_even_if_slower() {
        val fastWithChange = candidate(20, transfers = 1, exposure = Exposure.SHELTERED)
        val directSlower = candidate(26, transfers = 0, exposure = Exposure.SHELTERED)
        val best = RouteRanker.best(listOf(fastWithChange, directSlower), RoutePreference.FEWEST_CHANGES, calmDay)
        assertEquals(0, best?.candidate?.result?.transferCount)
    }

    @Test
    fun balanced_takes_faster_when_the_transfer_saving_is_small() {
        // 22 min direct vs 20 min with one change: 2 min isn't worth the change.
        val direct = candidate(22, transfers = 0, exposure = Exposure.SHELTERED)
        val oneChange = candidate(20, transfers = 1, exposure = Exposure.SHELTERED)
        val best = RouteRanker.best(listOf(oneChange, direct), RoutePreference.BALANCED, calmDay)
        assertEquals(0, best?.candidate?.result?.transferCount)
    }

    @Test
    fun hot_day_prefers_sheltered_over_a_slightly_faster_exposed_route() {
        // 28 min exposed (tram) vs 31 min sheltered (metro): on a hot day the
        // sheltered one should win despite being 3 min slower.
        val fastExposed = candidate(28, transfers = 0, exposure = Exposure.EXPOSED)
        val slowSheltered = candidate(31, transfers = 0, exposure = Exposure.SHELTERED)
        val best = RouteRanker.best(listOf(fastExposed, slowSheltered), RoutePreference.FASTEST, hotDay)
        assertEquals(Exposure.SHELTERED, best?.candidate?.exposure)
    }

    @Test
    fun calm_day_keeps_the_faster_exposed_route() {
        val fastExposed = candidate(28, transfers = 0, exposure = Exposure.EXPOSED)
        val slowSheltered = candidate(31, transfers = 0, exposure = Exposure.SHELTERED)
        val best = RouteRanker.best(listOf(fastExposed, slowSheltered), RoutePreference.FASTEST, calmDay)
        assertEquals(Exposure.EXPOSED, best?.candidate?.exposure)
    }

    @Test
    fun weather_penalty_is_recorded_only_for_exposed_routes_in_adverse_weather() {
        val exposed = RouteRanker.rank(
            listOf(candidate(20, 0, Exposure.EXPOSED)), RoutePreference.FASTEST, hotDay,
        ).first()
        val sheltered = RouteRanker.rank(
            listOf(candidate(20, 0, Exposure.SHELTERED)), RoutePreference.FASTEST, hotDay,
        ).first()
        assertEquals(0.0, sheltered.weatherPenalty)
        assert(exposed.weatherPenalty > 0.0)
    }

    @Test
    fun no_weather_context_means_no_penalty() {
        val scored = RouteRanker.rank(
            listOf(candidate(20, 0, Exposure.EXPOSED)), RoutePreference.FASTEST, weather = null,
        ).first()
        assertEquals(0.0, scored.weatherPenalty)
    }
}
