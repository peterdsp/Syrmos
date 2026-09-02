package com.syrmos.core.domain.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Mirrors the cross-client GO contract in `fixtures/go-guidance/cases.json` (the
 * same cases the web `web-go.js`, server `go_guidance.py` and iOS
 * `JourneyGuidance.swift` engines are tested against) so GO guidance stays
 * identical on Android. commonTest cannot read repo files, so the fixtures are
 * inlined here; keep them in step with cases.json.
 */
class GoGuidanceTest {

    private fun stop(id: String, name: String) = GuidanceStop(id, name)

    private val m2Direct3 = GuidanceJourney(
        listOf(
            GuidanceLeg(
                "M2", "Anthoupoli",
                listOf(stop("M2_SYN", "Syntagma"), stop("M2_PAN", "Panepistimio"), stop("M2_OMO", "Omonia")),
            ),
        ),
    )

    private val m1Hop2 = GuidanceJourney(
        listOf(
            GuidanceLeg("M1", "Kifisia", listOf(stop("M1_VIC", "Victoria"), stop("M1_ATT", "Attiki"))),
        ),
    )

    private val m2m3Transfer = GuidanceJourney(
        listOf(
            GuidanceLeg(
                "M2", "Elliniko",
                listOf(
                    stop("M2_LAR", "Larissa Station"), stop("M2_MET", "Metaxourgeio"),
                    stop("M2_OMO", "Omonia"), stop("M2_SYN", "Syntagma"),
                ),
            ),
            GuidanceLeg(
                "M3", "Airport",
                listOf(
                    stop("M3_SYN", "Syntagma"), stop("M3_EVA", "Evangelismos"),
                    stop("M3_MEG", "Megaro Moussikis"),
                ),
            ),
        ),
    )

    private fun pos(leg: Int, stop: Int) = GuidancePosition(leg, stop)

    @Test
    fun direct_boardThenGetOffThenArrived() {
        assertEquals(
            JourneyGuidance.Board("M2", "Anthoupoli", 2, "Panepistimio"),
            GoGuidance.guidance(m2Direct3, pos(0, 0)),
        )
        assertEquals(false, GoGuidance.shouldAlertGetOff(m2Direct3, pos(0, 0)))

        assertEquals(
            JourneyGuidance.GetOffNext("Omonia", isDestination = true, transferTo = null),
            GoGuidance.guidance(m2Direct3, pos(0, 1)),
        )
        assertTrue(GoGuidance.shouldAlertGetOff(m2Direct3, pos(0, 1)))

        assertEquals(JourneyGuidance.Arrived("Omonia"), GoGuidance.guidance(m2Direct3, pos(0, 2)))
        assertEquals(false, GoGuidance.shouldAlertGetOff(m2Direct3, pos(0, 2)))
    }

    @Test
    fun twoStopHop_boardCoincidesWithAlert() {
        assertEquals(
            JourneyGuidance.Board("M1", "Kifisia", 1, "Attiki"),
            GoGuidance.guidance(m1Hop2, pos(0, 0)),
        )
        assertTrue(GoGuidance.shouldAlertGetOff(m1Hop2, pos(0, 0)))
        assertEquals(JourneyGuidance.Arrived("Attiki"), GoGuidance.guidance(m1Hop2, pos(0, 1)))
    }

    @Test
    fun transfer_stepsThroughBoardRideGetOffTransferArrived() {
        assertEquals(
            JourneyGuidance.Board("M2", "Elliniko", 3, "Metaxourgeio"),
            GoGuidance.guidance(m2m3Transfer, pos(0, 0)),
        )
        assertEquals(
            JourneyGuidance.Ride("M2", "Elliniko", 2, "Omonia"),
            GoGuidance.guidance(m2m3Transfer, pos(0, 1)),
        )
        assertEquals(
            JourneyGuidance.GetOffNext("Syntagma", isDestination = false, transferTo = "M3"),
            GoGuidance.guidance(m2m3Transfer, pos(0, 2)),
        )
        assertTrue(GoGuidance.shouldAlertGetOff(m2m3Transfer, pos(0, 2)))
        assertEquals(
            JourneyGuidance.Transfer("Syntagma", "M3", "Airport"),
            GoGuidance.guidance(m2m3Transfer, pos(0, 3)),
        )
        assertEquals(
            JourneyGuidance.Board("M3", "Airport", 2, "Evangelismos"),
            GoGuidance.guidance(m2m3Transfer, pos(1, 0)),
        )
        assertEquals(
            JourneyGuidance.GetOffNext("Megaro Moussikis", isDestination = true, transferTo = null),
            GoGuidance.guidance(m2m3Transfer, pos(1, 1)),
        )
        assertEquals(
            JourneyGuidance.Arrived("Megaro Moussikis"),
            GoGuidance.guidance(m2m3Transfer, pos(1, 2)),
        )
    }

    @Test
    fun advance_walksToArrivedAlertingOncePerLeg() {
        for (journey in listOf(m2Direct3, m1Hop2, m2m3Transfer)) {
            var p = GuidancePosition(0, 0)
            val alertsPerLeg = HashMap<Int, Int>()
            val totalStops = journey.legs.sumOf { it.stops.size }
            var steps = 0
            while (!GoGuidance.isArrived(journey, p)) {
                if (GoGuidance.shouldAlertGetOff(journey, p)) {
                    alertsPerLeg[p.legIndex] = (alertsPerLeg[p.legIndex] ?: 0) + 1
                }
                p = GoGuidance.advance(journey, p)
                steps++
                assertTrue(steps <= totalStops + 5, "advance did not converge")
            }
            journey.legs.indices.forEach { i ->
                assertEquals(1, alertsPerLeg[i] ?: 0, "leg $i should alert exactly once")
            }
        }
    }

    @Test
    fun guidance_rejectsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { GoGuidance.guidance(m2Direct3, pos(9, 0)) }
        assertFailsWith<IllegalArgumentException> { GoGuidance.guidance(m2Direct3, pos(0, 9)) }
    }
}
