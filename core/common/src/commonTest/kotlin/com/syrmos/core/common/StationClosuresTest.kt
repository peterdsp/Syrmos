package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors `fixtures/closures/effect.json` case for case (the same cases the server
 * projector, Swift `StationClosures` and web are tested against). commonTest cannot
 * read repo files, so the fixture is inlined; regenerate it from the JSON whenever
 * `scripts/generate-302-fixtures.py` changes.
 */
class StationClosuresTest {

    private val closures = listOf(
        StationClosure(id = "syn-parade", stationIds = listOf("M2_SYN", "M3_SYN"), lineIds = listOf(), mode = ClosureMode.PASS_THROUGH, startsAtEpochSeconds = 1793167200L, endsAtEpochSeconds = null, status = "scheduled"),
        StationClosure(id = "eva-works", stationIds = listOf("M3_EVA"), lineIds = listOf("M3"), mode = ClosureMode.CLOSED, startsAtEpochSeconds = 1793167200L, endsAtEpochSeconds = 1793196000L, status = "active"),
        StationClosure(id = "nik-exit", stationIds = listOf("M3_NIK"), lineIds = listOf(), mode = ClosureMode.EXIT_ONLY, startsAtEpochSeconds = 1793167200L, endsAtEpochSeconds = 1793170800L, status = "active"),
        StationClosure(id = "nik-entry", stationIds = listOf("M3_NIK"), lineIds = listOf(), mode = ClosureMode.ENTRY_ONLY, startsAtEpochSeconds = 1793169000L, endsAtEpochSeconds = 1793174400L, status = "active"),
        StationClosure(id = "draft-x", stationIds = listOf("M3_EVA"), lineIds = listOf(), mode = ClosureMode.CLOSED, startsAtEpochSeconds = 1793163600L, endsAtEpochSeconds = null, status = "draft"),
    )

    private fun check(id: String, stationId: String, lineId: String?, now: Long, expected: ClosureEffect) {
        assertEquals(expected, StationClosures.effect(closures, stationId, lineId, now), id)
    }

    @Test
    fun effectCases_matchFixture() {
        check("before_start_upcoming", "M2_SYN", "M2", 1793163600L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = "syn-parade", upcomingStartsAtEpochSeconds = 1793167200L))
        check("before_window_far", "M2_SYN", "M2", 1793077200L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("at_start_active", "M2_SYN", "M2", 1793167200L, ClosureEffect(active = true, mode = ClosureMode.PASS_THROUGH, canBoard = false, canAlight = false, closureId = "syn-parade", endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("open_ended_still_active", "M3_SYN", "M3", 1795759200L, ClosureEffect(active = true, mode = ClosureMode.PASS_THROUGH, canBoard = false, canAlight = false, closureId = "syn-parade", endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("line_scoped_other_line_unaffected", "M3_EVA", "M1", 1793167260L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("line_scoped_matching_line", "M3_EVA", "M3", 1793167260L, ClosureEffect(active = true, mode = ClosureMode.CLOSED, canBoard = false, canAlight = false, closureId = "eva-works", endsAtEpochSeconds = 1793196000L, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("line_null_means_any_line", "M3_EVA", null, 1793167260L, ClosureEffect(active = true, mode = ClosureMode.CLOSED, canBoard = false, canAlight = false, closureId = "eva-works", endsAtEpochSeconds = 1793196000L, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("end_is_exclusive", "M3_EVA", "M3", 1793196000L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("draft_ignored", "M3_EVA", "M3", 1793167140L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = "eva-works", upcomingStartsAtEpochSeconds = 1793167200L))
        check("exit_only_alone", "M3_NIK", "M3", 1793167260L, ClosureEffect(active = true, mode = ClosureMode.EXIT_ONLY, canBoard = false, canAlight = true, closureId = "nik-exit", endsAtEpochSeconds = 1793170800L, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("exit_plus_entry_is_closed", "M3_NIK", "M3", 1793169000L, ClosureEffect(active = true, mode = ClosureMode.CLOSED, canBoard = false, canAlight = false, closureId = "nik-exit", endsAtEpochSeconds = 1793174400L, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("entry_only_alone", "M3_NIK", "M3", 1793170800L, ClosureEffect(active = true, mode = ClosureMode.ENTRY_ONLY, canBoard = true, canAlight = false, closureId = "nik-entry", endsAtEpochSeconds = 1793174400L, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
        check("unrelated_station", "M1_KIF", "M1", 1793167260L, ClosureEffect(active = false, mode = null, canBoard = true, canAlight = true, closureId = null, endsAtEpochSeconds = null, upcomingClosureId = null, upcomingStartsAtEpochSeconds = null))
    }

    @Test
    fun closedStationIds_followTheWindow() {
        val t0 = 1793167200L
        assertEquals(emptySet(), StationClosures.closedStationIds(closures, t0 - 60))
        val at = StationClosures.closedStationIds(closures, t0 + 60)
        assertTrue("M2_SYN" in at && "M3_SYN" in at && "M3_EVA" in at && "M3_NIK" in at)
        // entry_only alone keeps boarding open.
        assertFalse("M3_NIK" in StationClosures.closedStationIds(closures, t0 + 3600))
    }

    @Test
    fun localizedText_fallsBackToEnglishThenGreek() {
        val t = LocalizedText(el = "Κλειστός", en = "Closed")
        assertEquals("Closed", t.text(AppLanguage.ITALIAN))
        assertEquals("Κλειστός", t.text(AppLanguage.GREEK))
        assertEquals("Κλειστός", LocalizedText(el = "Κλειστός").text(AppLanguage.ALBANIAN))
    }

    @Test
    fun unknownMode_isIgnored() {
        assertEquals(null, ClosureMode.fromRaw("partial"))
        assertEquals(ClosureMode.PASS_THROUGH, ClosureMode.fromRaw("pass_through"))
    }
}
