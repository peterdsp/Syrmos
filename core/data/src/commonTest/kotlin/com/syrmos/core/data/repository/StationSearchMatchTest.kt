package com.syrmos.core.data.repository

import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The station-search predicate must match English, Greek and Albanian names and
 * the station code, mirroring the iOS Browse-All filter. Before the fix the
 * Android DB query and seed fallback matched only name/name_el, so Albanian
 * names and station codes never matched.
 */
class StationSearchMatchTest {

    private val station = Station(
        id = "M1_ATT",
        name = "Attiki",
        nameEl = "Αττική",
        nameSq = "Atiki",
        latitude = 37.999,
        longitude = 23.722,
        lineIds = listOf("M1", "M2"),
    )

    @Test
    fun matchesEnglishName() {
        assertTrue(StationRepositoryImpl.matchesQuery(station, "attik"))
    }

    @Test
    fun matchesGreekName() {
        assertTrue(StationRepositoryImpl.matchesQuery(station, "αττικ"))
    }

    @Test
    fun matchesAlbanianName() {
        assertTrue(StationRepositoryImpl.matchesQuery(station, "atik"))
    }

    @Test
    fun matchesStationCode() {
        assertTrue(StationRepositoryImpl.matchesQuery(station, "m1_att"))
    }

    @Test
    fun doesNotMatchUnrelatedQuery() {
        assertFalse(StationRepositoryImpl.matchesQuery(station, "piraeus"))
    }

    @Test
    fun handlesMissingAlbanianNameWithoutCrashing() {
        val noSq = station.copy(nameSq = null)
        assertTrue(StationRepositoryImpl.matchesQuery(noSq, "attik"), "English still matches")
        assertFalse(StationRepositoryImpl.matchesQuery(noSq, "atik"), "no Albanian to match")
    }
}
