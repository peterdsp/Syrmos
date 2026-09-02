package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The weather request is the only piece of the app that can send the user's
 * location off-device (to Open-Meteo, and only when they share it). It must
 * leave only an APPROXIMATE position, matching the store-privacy label, so the
 * coordinates are coarsened to ~1 km before the request is built. These tests
 * pin that a precise device coordinate can never reach the URL verbatim.
 */
class WeatherServiceCoordinatePrivacyTest {

    @Test
    fun coarsenRoundsToTwoDecimals() {
        // A precise GPS fix (5+ decimals) collapses to 2 decimals (~1 km).
        assertEquals(37.98, WeatherService.coarsen(37.983421))
        assertEquals(23.73, WeatherService.coarsen(23.728120))
        // Negative coordinates round symmetrically.
        assertEquals(-0.13, WeatherService.coarsen(-0.126900))
    }

    @Test
    fun buildForecastUrlEmitsOnlyCoarseCoordinates() {
        val url = WeatherService.buildForecastUrl(37.983421, 23.728120)
        // The coarse values are present...
        assertTrue(url.contains("latitude=37.98"), url)
        assertTrue(url.contains("longitude=23.73"), url)
        // ...and the precise device fix never appears verbatim.
        assertTrue(!url.contains("37.983421"), "precise latitude leaked: $url")
        assertTrue(!url.contains("23.728120"), "precise longitude leaked: $url")
        assertTrue(!url.contains("37.9834"), "sub-100m latitude leaked: $url")
    }
}
