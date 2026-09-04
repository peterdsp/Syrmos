package com.syrmos.core.domain.departures

import com.syrmos.core.model.schedule.SourceConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the departure-board grouping transform: consecutive departures sharing a
 * (line, destination) collapse into one group carrying the next few times, so
 * the station board stops repeating "Line 3 · towards X · Scheduled" on every
 * row. Mirrors web `web-tests/departures-grouping.test.js` and iOS
 * `DepartureGroupingTests` so the three clients group identically.
 */
class DepartureGroupingTest {

    private fun dep(
        line: String,
        min: Int,
        time: String = "",
        dir: String = "Doukissis Plakentias",
        service: String? = null,
        source: SourceConfidence = SourceConfidence.SCHEDULED,
    ) = ResolvedDeparture(
        lineId = line,
        destination = dir,
        minutesAway = min,
        time = time,
        serviceType = service,
        sourceConfidence = source,
    )

    @Test
    fun collapsesSameLineAndDestinationWithOrderedTimes() {
        val g = groupDepartures(
            listOf(
                dep("M3", 4, "12:31"),
                dep("M3", 12, "12:39"),
                dep("M3", 22, "12:49"),
            ),
        )
        assertEquals(1, g.size, "three same-destination rows become one group")
        assertEquals("Doukissis Plakentias", g[0].destination)
        assertEquals("M3", g[0].lineId)
        assertEquals(listOf(4, 12, 22), g[0].times.map { it.minutesAway })
        assertEquals(listOf("12:31", "12:39", "12:49"), g[0].times.map { it.time })
        assertEquals(3, g[0].total)
        assertEquals(0, g[0].moreCount)
    }

    @Test
    fun distinctDestinationsStaySeparateSoonestFirst() {
        val g = groupDepartures(
            listOf(
                dep("M3", 4, dir = "Airport"),
                dep("M3", 6, dir = "Dimotiko Theatro"),
                dep("M3", 22, dir = "Airport"),
            ),
        )
        assertEquals(2, g.size, "two destinations => two groups")
        assertEquals("Airport", g[0].destination, "group ordered by its soonest member")
        assertEquals(listOf(4, 22), g[0].times.map { it.minutesAway }, "non-adjacent members still merge")
        assertEquals("Dimotiko Theatro", g[1].destination)
    }

    @Test
    fun destinationKeyFoldsCaseAndWhitespace() {
        val g = groupDepartures(
            listOf(
                dep("M3", 4, dir = "Airport"),
                dep("M3", 9, dir = "airport "),
            ),
        )
        assertEquals(1, g.size)
        assertEquals("Airport", g[0].destination, "keeps the first original spelling for display")
        assertEquals(listOf(4, 9), g[0].times.map { it.minutesAway })
    }

    @Test
    fun maxTimesCapsAndReportsRemainder() {
        val g = groupDepartures(
            listOf(
                dep("M3", 2, dir = "Kifissia"),
                dep("M3", 9, dir = "Kifissia"),
                dep("M3", 16, dir = "Kifissia"),
                dep("M3", 24, dir = "Kifissia"),
            ),
            maxTimes = 3,
        )
        assertEquals(listOf(2, 9, 16), g[0].times.map { it.minutesAway })
        assertEquals(1, g[0].moreCount)
        assertEquals(4, g[0].total)
    }

    @Test
    fun maxTimesZeroKeepsEveryTime() {
        val g = groupDepartures(
            listOf(
                dep("M3", 2, dir = "Kifissia"),
                dep("M3", 9, dir = "Kifissia"),
            ),
            maxTimes = 0,
        )
        assertEquals(2, g[0].times.size)
        assertEquals(0, g[0].moreCount)
    }

    @Test
    fun confidenceReflectsSoonestNotStrongest() {
        // Soonest is live -> live chip (times come out ascending even though the
        // live one was passed second).
        val live = groupDepartures(
            listOf(
                dep("M3", 8, dir = "Airport", source = SourceConfidence.SCHEDULED),
                dep("M3", 3, dir = "Airport", source = SourceConfidence.LIVE),
            ),
        )
        assertEquals(3, live[0].times.first().minutesAway, "times sorted ascending")
        assertEquals(SourceConfidence.LIVE, live[0].sourceConfidence)

        // Soonest is scheduled and a LATER time is live -> chip stays scheduled,
        // so a tracked vehicle is never advertised over a scheduled lead time.
        val sched = groupDepartures(
            listOf(
                dep("M3", 3, dir = "Airport", source = SourceConfidence.SCHEDULED),
                dep("M3", 20, dir = "Airport", source = SourceConfidence.LIVE),
            ),
        )
        assertEquals(SourceConfidence.SCHEDULED, sched[0].sourceConfidence)
    }

    @Test
    fun differentLinesNeverMergeEvenWithSameDestination() {
        val g = groupDepartures(
            listOf(
                dep("M3", 5, dir = "Piraeus"),
                dep("A2", 6, dir = "Piraeus"),
            ),
        )
        assertEquals(2, g.size)
        assertEquals(listOf("M3", "A2"), g.map { it.lineId })
    }

    @Test
    fun carriesServiceTypeForAirportPill() {
        val g = groupDepartures(
            listOf(
                dep("M3", 4, dir = "Airport", service = "airport"),
                dep("M3", 14, dir = "Airport", service = "airport"),
            ),
        )
        assertEquals("airport", g[0].serviceType)
    }

    @Test
    fun emptyInputYieldsNoGroups() {
        assertTrue(groupDepartures(emptyList()).isEmpty())
    }
}
