package com.syrmos.core.domain.assistant

import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.weather.WeatherContext
import com.syrmos.core.model.weather.WeatherState

/**
 * A candidate route plus its exposure (how sheltered it is), the two facts the
 * ranker needs. Exposure is computed by the caller from the route's line types
 * via [StationComfort], keeping the ranker pure and trivially testable.
 */
data class RouteCandidate(
    val result: JourneyResult,
    val exposure: Exposure,
)

/** A scored candidate. Lower [score] is better; [weatherPenalty] is the part of
 *  the score that came from adverse weather meeting exposure, so the answer can
 *  explain "slower but drier". */
data class ScoredRoute(
    val candidate: RouteCandidate,
    val score: Double,
    val weatherPenalty: Double,
)

/**
 * Ranks route candidates by the user's [RoutePreference], with an adverse-weather
 * tilt: on a hot / rainy / windy day an exposed (tram / surface) route is
 * penalised, so a slightly slower but sheltered option can win a close call. This
 * is the "genuinely clever" bit — Ariadne doesn't just report the fastest route,
 * she picks the one that fits the moment and can say why. Pure and offline.
 */
object RouteRanker {

    fun rank(
        candidates: List<RouteCandidate>,
        preference: RoutePreference,
        weather: WeatherContext?,
    ): List<ScoredRoute> =
        candidates
            .map { c ->
                val penalty = weatherPenalty(c.exposure, weather)
                ScoredRoute(c, baseScore(c.result, preference) + penalty, penalty)
            }
            // Stable sort keeps input order for exact ties, so the planner's
            // primary (fastest) route wins when nothing separates them.
            .sortedBy { it.score }

    fun best(
        candidates: List<RouteCandidate>,
        preference: RoutePreference,
        weather: WeatherContext?,
    ): ScoredRoute? = rank(candidates, preference, weather).firstOrNull()

    /**
     * Base cost before weather. FASTEST is pure minutes; FEWEST_CHANGES makes a
     * transfer dominate everything (each change worth ~1000 "minutes"); BALANCED
     * charges a modest ~5 min per change so a small time win doesn't justify an
     * extra transfer.
     */
    private fun baseScore(result: JourneyResult, preference: RoutePreference): Double {
        val minutes = result.totalMinutes.toDouble()
        val transfers = result.transferCount.toDouble()
        return when (preference) {
            RoutePreference.FASTEST -> minutes
            RoutePreference.FEWEST_CHANGES -> transfers * 1000.0 + minutes
            RoutePreference.BALANCED -> minutes + transfers * 5.0
        }
    }

    /**
     * Extra cost when adverse weather meets an exposed route. Nothing in calm
     * weather; a bigger hit when the risk is HIGH. Sheltered routes are never
     * penalised (that's their advantage).
     */
    private fun weatherPenalty(exposure: Exposure, weather: WeatherContext?): Double {
        if (weather == null || weather.state == WeatherState.NORMAL) return 0.0
        val exposureFactor = when (exposure) {
            Exposure.EXPOSED -> 1.0
            Exposure.MIXED -> 0.5
            Exposure.SHELTERED -> 0.0
        }
        if (exposureFactor == 0.0) return 0.0
        // A HIGH-risk day hurts an exposed route more than a MEDIUM one.
        val severity = maxOf(weather.heatRisk.ordinal, weather.rainRisk.ordinal, weather.windRisk.ordinal)
        val base = when (severity) {
            2 -> 12.0 // HIGH
            1 -> 8.0  // MEDIUM
            else -> 6.0
        }
        return base * exposureFactor
    }
}
