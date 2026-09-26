package com.syrmos.core.domain.usecase

import com.syrmos.core.model.location.NearestStationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearestStationClusterTest {

    private fun r(id: String, d: Int, vararg lines: String) =
        NearestStationResult(stationId = id, stationName = id, distanceMeters = d, lineIds = lines.toList())

    @Test
    fun groupsTheInterchangePlatformsWithinTheRadius() {
        val results = listOf(r("OMONIA_M2", 60, "M2"), r("OMONIA_M1", 100, "M1"), r("PANEPISTIMIO", 420, "M2"))
        assertEquals(listOf("OMONIA_M2", "OMONIA_M1"), NearestStationCluster.members(results).map { it.stationId })
        assertEquals(listOf("OMONIA_M2" to "M2", "OMONIA_M1" to "M1"), NearestStationCluster.stops(results))
    }

    @Test
    fun sortsByDistanceBeforeGrouping() {
        val results = listOf(r("B", 120, "M1"), r("A", 30, "M2"), r("C", 700, "T6"))
        assertEquals(listOf("A", "B"), NearestStationCluster.members(results).map { it.stationId })
    }

    @Test
    fun aLoneStationIsItsOwnCluster() {
        val results = listOf(r("SYGGROU_FIX", 80, "M2"), r("NEOS_KOSMOS", 900, "M2"))
        assertEquals(listOf("SYGGROU_FIX"), NearestStationCluster.members(results).map { it.stationId })
        assertTrue(NearestStationCluster.members(emptyList()).isEmpty())
    }

    @Test
    fun stopsAreDistinctAcrossMembers() {
        val results = listOf(r("X", 10, "M1", "M2"), r("X", 10, "M2"))
        assertEquals(listOf("X" to "M1", "X" to "M2"), NearestStationCluster.stops(results))
    }
}
