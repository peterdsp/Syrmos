package com.syrmos.core.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for [reconstructSegments], the pure path-to-segments step of
 * PlanJourneyUseCase. Three bugs were shipping before this was extracted:
 *
 *  BUG1: the first segment overwrote its start with the second station, so every
 *        plan reported the wrong origin (one stop into the trip).
 *  BUG2: a line change closed the running segment at the station one stop PAST
 *        the interchange, and opened the next one there too, so multi-line plans
 *        named the wrong interchange.
 *  BUG4: segments carried raw station IDs instead of display names.
 *
 * `path` mirrors what compute() builds from Dijkstra's `previous` map: element
 * (stationId, edge) where `edge` leads to stationId and starts at the previous
 * element's station (the origin for the first element).
 */
class PlanJourneyReconstructionTest {

    private fun edge(to: String, line: String, weight: Int = 2) =
        Edge(toStationId = to, lineId = line, lineName = "Line $line", weight = weight, isTransfer = false)

    private val names = mapOf(
        "A" to "Alpha", "B" to "Beta", "C" to "Gamma", "D" to "Delta", "E" to "Epsilon",
    )

    @Test
    fun singleLineKeepsOriginAndCountsEveryHop() {
        // A -> B -> C -> D, all on line 1.
        val path = listOf(
            "B" to edge("B", "1"),
            "C" to edge("C", "1"),
            "D" to edge("D", "1"),
        )

        val segments = reconstructSegments(path, fromStationId = "A", toStationId = "D", stationNames = names)

        assertEquals(1, segments.size)
        val only = segments.single()
        assertEquals("A", only.fromStationId, "origin must be A, not the second station (BUG1)")
        assertEquals("Alpha", only.fromStationName, "segment must carry the display name (BUG4)")
        assertEquals("D", only.toStationId)
        assertEquals("Delta", only.toStationName)
        assertEquals("Line 1", only.lineName)
        assertEquals(3, only.stationCount)
        assertEquals(6, only.estimatedMinutes)
    }

    @Test
    fun lineChangeClosesAtTheInterchangeNotOneStopPast() {
        // A -> B -> C on line 1, then C -> D -> E on line 2. C is the interchange.
        val path = listOf(
            "B" to edge("B", "1"),
            "C" to edge("C", "1"),
            "D" to edge("D", "2"),
            "E" to edge("E", "2"),
        )

        val segments = reconstructSegments(path, fromStationId = "A", toStationId = "E", stationNames = names)

        assertEquals(2, segments.size)

        val first = segments[0]
        assertEquals("A", first.fromStationId)
        assertEquals("C", first.toStationId, "first leg must end at the interchange C, not D (BUG2)")
        assertEquals("Gamma", first.toStationName)
        assertEquals("Line 1", first.lineName)
        assertEquals(2, first.stationCount)

        val second = segments[1]
        assertEquals("C", second.fromStationId, "second leg must start at the interchange C (BUG2)")
        assertEquals("Gamma", second.fromStationName)
        assertEquals("E", second.toStationId)
        assertEquals("Epsilon", second.toStationName)
        assertEquals("Line 2", second.lineName)
        assertEquals(2, second.stationCount)
    }

    @Test
    fun samePlaceJourneyProducesNoSegments() {
        val segments = reconstructSegments(emptyList(), fromStationId = "A", toStationId = "A", stationNames = names)
        assertEquals(0, segments.size)
    }

    @Test
    fun missingNameFallsBackToTheId() {
        val path = listOf("Z" to edge("Z", "1"))
        val segments = reconstructSegments(path, fromStationId = "A", toStationId = "Z", stationNames = names)
        assertEquals("Alpha", segments.single().fromStationName)
        assertEquals("Z", segments.single().toStationName, "unknown id falls back to the raw id, never crashes")
    }
}
