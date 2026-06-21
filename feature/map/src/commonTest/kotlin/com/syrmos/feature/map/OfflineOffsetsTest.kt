package com.syrmos.feature.map

import com.syrmos.core.network.SyrmosSchedulesService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the offline-first invariant: bundled station offsets
 * (loaded by `StationOffsetsRepository` from `station-offsets.json`) must
 * convert cleanly into the (lineId, direction) keyed map that
 * `MapViewModel.pollLivePositions()` and `simulateTrains()` expect.
 *
 * The bug this guards against: if bundled offsets stay empty after boot,
 * `pollLivePositions()` falls back to the network. On iOS without a live
 * connection (or with the silent `runCatching` swallowing a 5xx), the trains
 * never render. Verifying that the conversion is a pure, deterministic
 * function over the repo state means we can rely on bundled data alone.
 */
class OfflineOffsetsTest {

    @Test
    fun emptyRepoReturnsEmptyMap() {
        assertTrue(convertBundledOffsets(emptyMap()).isEmpty())
    }

    @Test
    fun convertsPipeKeyToLineDirectionPair() {
        val repoData = mapOf(
            "M3|outbound" to listOf(
                SyrmosSchedulesService.StationOffsetStop(
                    stationId = "M3_DT",
                    stationEn = "Dimotiko Theatro",
                    stopSequence = 1,
                    minutesFromOrigin = 0,
                ),
                SyrmosSchedulesService.StationOffsetStop(
                    stationId = "M3_KO",
                    stationEn = "Korydallos",
                    stopSequence = 2,
                    minutesFromOrigin = 3,
                ),
            ),
        )
        val converted = convertBundledOffsets(repoData)
        val stops = converted[("M3" to "outbound")]
        assertEquals(2, stops?.size)
        assertEquals("M3_DT", stops?.first()?.stationId)
        assertEquals(3, stops?.last()?.minutesFromOrigin)
    }

    @Test
    fun missingDirectionDefaultsToOutbound() {
        val repoData = mapOf(
            "M2" to listOf(
                SyrmosSchedulesService.StationOffsetStop(
                    stationId = "M2_AE",
                    stopSequence = 1,
                    minutesFromOrigin = 0,
                ),
            ),
        )
        val converted = convertBundledOffsets(repoData)
        assertTrue(converted.containsKey("M2" to "outbound"))
    }

    @Test
    fun preservesAllLines() {
        val repoData = mapOf(
            "M1|outbound" to listOf(stop("M1_A", 1, 0), stop("M1_B", 2, 5)),
            "M1|inbound" to listOf(stop("M1_B", 1, 0), stop("M1_A", 2, 5)),
            "M2|outbound" to listOf(stop("M2_A", 1, 0)),
            "M3|outbound" to listOf(stop("M3_A", 1, 0)),
            "T6|outbound" to listOf(stop("T6_A", 1, 0)),
            "T7|outbound" to listOf(stop("T7_A", 1, 0)),
        )
        val converted = convertBundledOffsets(repoData)
        assertEquals(6, converted.size)
        assertTrue(converted.containsKey("M1" to "inbound"))
        assertTrue(converted.containsKey("T7" to "outbound"))
    }

    @Test
    fun preservesStopOrderingFromRepo() {
        val repoData = mapOf(
            "M2|outbound" to listOf(
                stop("M2_A", 1, 0),
                stop("M2_B", 2, 4),
                stop("M2_C", 3, 9),
            ),
        )
        val converted = convertBundledOffsets(repoData)
        val stops = converted[("M2" to "outbound")] ?: error("missing")
        assertEquals(listOf(0, 4, 9), stops.map { it.minutesFromOrigin })
    }

    private fun stop(id: String, seq: Int, minutes: Int) =
        SyrmosSchedulesService.StationOffsetStop(
            stationId = id,
            stopSequence = seq,
            minutesFromOrigin = minutes,
        )
}
