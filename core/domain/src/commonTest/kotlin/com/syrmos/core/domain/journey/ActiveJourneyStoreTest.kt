package com.syrmos.core.domain.journey

import com.syrmos.core.domain.go.GuidanceJourney
import com.syrmos.core.domain.go.GuidanceLeg
import com.syrmos.core.domain.go.GuidancePosition
import com.syrmos.core.domain.go.GuidanceStop
import com.syrmos.core.model.journey.ActiveJourney
import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyContract
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.JourneyPhase
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.ProgressSource
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure live-session lifecycle: start / advance / back / end, the phase
 * derivation, and (the point of the whole thing) that a session persisted as
 * id-anchored `legId` + `confirmedStopId` resumes onto the exact same engine
 * position even after stop NAMES change (e.g. a language switch), because only ids
 * are trusted. Runs on every KMP target, so web and iOS mirrors can be checked
 * against the same behaviour.
 */
class ActiveJourneyStoreTest {

    private val t0 = Instant.parse("2026-09-13T09:00:00+03:00")
    private val t1 = Instant.parse("2026-09-13T09:05:00+03:00")
    private val serviceDate = LocalDate.parse("2026-09-13")

    // Two ride legs with a transfer between them. Leg 1 (line-A) has 4 stops so an
    // interior position is a genuine RIDING state; leg 2 (line-B) has 2.
    private fun option() = JourneyOption(
        id = "opt-1",
        requestId = "req-1",
        transferCount = 1,
        feasibility = Feasibility(FeasibilityStatus.COMFORTABLE, explanationCode = "test"),
        legs = listOf(
            Leg(
                id = "leg-A", kind = LegKind.RIDE, fromId = "s1", toId = "s4", lineId = "line-A",
                orderedStopIds = listOf("s1", "s2", "s3", "s4"), serviceDate = serviceDate,
            ),
            Leg(
                id = "xfer", kind = LegKind.TRANSFER, fromId = "s4", toId = "s4", serviceDate = serviceDate,
            ),
            Leg(
                id = "leg-B", kind = LegKind.RIDE, fromId = "s4", toId = "s5", lineId = "line-B",
                orderedStopIds = listOf("s4", "s5"), serviceDate = serviceDate,
            ),
        ),
    )

    private fun guidance(nameSuffix: String = "") = GuidanceJourney(
        legs = listOf(
            GuidanceLeg(
                lineId = "line-A", towards = "S4$nameSuffix",
                stops = listOf(
                    GuidanceStop("s1", "S1$nameSuffix"), GuidanceStop("s2", "S2$nameSuffix"),
                    GuidanceStop("s3", "S3$nameSuffix"), GuidanceStop("s4", "S4$nameSuffix"),
                ),
            ),
            GuidanceLeg(
                lineId = "line-B", towards = "S5$nameSuffix",
                stops = listOf(GuidanceStop("s4", "S4$nameSuffix"), GuidanceStop("s5", "S5$nameSuffix")),
            ),
        ),
    )

    @Test
    fun startBeginsAtOriginReadyToBoard() {
        val a = ActiveJourneyStore.start("go-1", option(), guidance(), t0)
        assertEquals(JourneyPhase.READY_TO_BOARD, a.phase)
        assertEquals("leg-A", a.legId)
        assertEquals("s1", a.confirmedStopId)
        assertEquals(t0, a.startedAt)
        assertEquals(t0, a.updatedAt)
        assertEquals(GuidancePosition(0, 0), ActiveJourneyStore.positionOf(a, guidance()))
    }

    @Test
    fun advanceWalksEveryPhaseThenArrives() {
        val g = guidance()
        var a = ActiveJourneyStore.start("go-1", option(), g, t0)
        assertEquals(JourneyPhase.READY_TO_BOARD, a.phase) // (0,0) board

        a = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(JourneyPhase.RIDING, a.phase) // (0,1) two stops remain
        assertEquals("s2", a.confirmedStopId)

        a = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(JourneyPhase.ALIGHT_SOON, a.phase) // (0,2) one stop remains

        a = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(JourneyPhase.TRANSFER, a.phase) // (0,3) at interchange
        assertEquals("leg-A", a.legId)
        assertEquals("s4", a.confirmedStopId)

        a = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(JourneyPhase.READY_TO_BOARD, a.phase) // (1,0) board line-B
        assertEquals("leg-B", a.legId)
        assertEquals("s4", a.confirmedStopId)

        a = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(JourneyPhase.ARRIVED, a.phase) // (1,1) destination
        assertEquals("s5", a.confirmedStopId)

        // Advancing past the destination is a no-op.
        val end = ActiveJourneyStore.advance(a, g, t1)
        assertEquals(ActiveJourneyStore.positionOf(a, g), ActiveJourneyStore.positionOf(end, g))
    }

    @Test
    fun backStepsAcrossTheLegBoundary() {
        val g = guidance()
        var a = ActiveJourneyStore.start("go-1", option(), g, t0)
        repeat(4) { a = ActiveJourneyStore.advance(a, g, t1) } // now (1,0)
        assertEquals(GuidancePosition(1, 0), ActiveJourneyStore.positionOf(a, g))

        a = ActiveJourneyStore.back(a, g, t1) // back onto leg-A alight stop
        assertEquals(GuidancePosition(0, 3), ActiveJourneyStore.positionOf(a, g))
        assertEquals("leg-A", a.legId)
        assertEquals("s4", a.confirmedStopId)
    }

    @Test
    fun resumesToSameStopEvenWhenNamesChange() {
        // Persist mid-trip, then resume with a differently-NAMED guidance (same ids),
        // as happens after a language switch. Ids must win.
        val g = guidance("")
        var a = ActiveJourneyStore.start("go-1", option(), g, t0)
        a = ActiveJourneyStore.advance(a, g, t1) // (0,1) s2
        a = ActiveJourneyStore.advance(a, g, t1) // (0,2) s3

        val raw = JourneyContract.encodeActiveJourney(a)
        val decoded = JourneyContract.decodeActiveJourney(raw)
        assertTrue(decoded is JourneyContract.DecodeResult.Ok)
        val resumed = decoded.value

        val relabelled = guidance(" (EL)")
        assertEquals(GuidancePosition(0, 2), ActiveJourneyStore.positionOf(resumed, relabelled))
        assertEquals(JourneyPhase.ALIGHT_SOON, ActiveJourneyStore.phaseFor(relabelled, GuidancePosition(0, 2)))
    }

    @Test
    fun unknownStopFallsBackToLegOriginNeverThrows() {
        val g = guidance()
        val a = ActiveJourneyStore.start("go-1", option(), g, t0)
            .copy(legId = "leg-B", confirmedStopId = "ghost")
        // leg-B exists (index 1) but the stop id does not: land on the leg origin.
        assertEquals(GuidancePosition(1, 0), ActiveJourneyStore.positionOf(a, g))
    }

    @Test
    fun endMarksEndedAndIsNotResumable() {
        val g = guidance()
        val a = ActiveJourneyStore.start("go-1", option(), g, t0)
        assertTrue(ActiveJourneyStore.isResumable(a))
        val ended = ActiveJourneyStore.end(a, t1)
        assertEquals(JourneyPhase.ENDED, ended.phase)
        assertEquals(t1, ended.updatedAt)
        assertTrue(ActiveJourneyStore.isEnded(ended))
        assertFalse(ActiveJourneyStore.isResumable(ended))
        // The frozen snapshot survives ending, so a summary can still be shown.
        assertEquals(a.itinerarySnapshot, ended.itinerarySnapshot)
    }

    @Test
    fun progressSourceIsRecordedOnAdvance() {
        val g = guidance()
        val a = ActiveJourneyStore.start("go-1", option(), g, t0)
        val stepped = ActiveJourneyStore.advance(a, g, t1, source = ProgressSource.GPS)
        assertEquals(ProgressSource.GPS, stepped.progressSource)
        assertEquals(t1, stepped.progressObservedAt)
    }
}
