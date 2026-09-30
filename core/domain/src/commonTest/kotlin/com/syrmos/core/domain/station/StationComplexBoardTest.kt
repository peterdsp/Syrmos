package com.syrmos.core.domain.station

import com.syrmos.core.model.schedule.SourceConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mirrors the cross-client board contract in
 * `fixtures/station-board/athens-all-directions.json` case for case, the same
 * cases the web `web-tests/station-board.test.js` and the Swift
 * `StationComplexBoardFixtureTests` assert against, so the three clients cannot
 * drift apart.
 *
 * commonTest cannot read repository files, so the cases are inlined. The web
 * suite's `station-board-parity.test.js` fails if a case id in the fixture has
 * no matching test here, which is what keeps this copy honest.
 */
class StationComplexBoardTest {

    private val complex = StationComplexBoard.Complex(
        id = "CPX_ATHENS_LARISSA",
        name = "Athens · Larissa Station",
        nameEl = "Αθήνα · Σταθμός Λαρίσης",
        nameSq = "Athinë · Stacioni Larisa",
        nameIt = "Atene · Stazione Larissa",
        areas = listOf(
            StationComplexBoard.Complex.Area(
                "metro", "Metro", "Μετρό", "Metro", "Metropolitana", listOf("M2_STA"),
            ),
            StationComplexBoard.Complex.Area(
                "rail", "Railway station", "Σιδηροδρομικός σταθμός", "Stacioni hekurudhor",
                "Stazione ferroviaria", listOf("A1_ATH", "A3_ATH", "A4_ATH", "GR_ATH"),
            ),
        ),
    )

    private fun dep(
        stop: String,
        area: String,
        line: String,
        destination: String,
        minutes: Int,
        trip: String? = null,
        source: SourceConfidence = SourceConfidence.SCHEDULED,
        pattern: String = "",
        cancelled: Boolean = false,
        boardsHere: Boolean = true,
        serviceDate: String? = null,
        time: String = "",
    ) = StationComplexBoard.Candidate(
        stopId = stop,
        areaId = area,
        lineId = line,
        destination = destination,
        patternKey = pattern,
        tripId = trip,
        serviceDate = serviceDate,
        time = time,
        absoluteMinutes = minutes,
        source = source,
        cancelled = cancelled,
        boardsHere = boardsHere,
    )

    // fixture case: every_direction_is_represented
    @Test
    fun everyDirectionIsRepresented() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("M2_STA", "metro", "M2", "Elliniko", 0, "M2-E-1", SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Elliniko", 10, "M2-E-2", SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Elliniko", 20, "M2-E-3", SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Anthoupoli", 1, "M2-A-1", SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Anthoupoli", 11, "M2-A-2", SourceConfidence.ESTIMATED),
                dep("A3_ATH", "rail", "A3", "Chalkida", 8, "A3-1360"),
                dep("A1_ATH", "rail", "A1", "Airport", 12, "A1-2050"),
                dep("GR_ATH", "rail", "IC1", "Thessaloniki", 25, "IC-54"),
                dep("A4_ATH", "rail", "A4", "Kiato", 40, "A4-1512"),
                dep("GR_ATH", "rail", "RG1", "Leianokladi", 50, "RG-884"),
            ),
        )
        assertEquals(false, board.partial)
        assertEquals(7, board.timedGroupCount)
        assertEquals(
            listOf("Elliniko", "Anthoupoli", "Chalkida", "Airport", "Thessaloniki", "Kiato", "Leianokladi"),
            board.groups.map { it.destination },
        )
        assertEquals(listOf(0, 10, 20), board.groups[0].times.map { it.absoluteMinutes })
        assertEquals(listOf(1, 11), board.groups[1].times.map { it.absoluteMinutes })
        assertEquals("metro||M2||elliniko", board.groups[0].id)
        assertEquals("rail||RG1||leianokladi", board.groups[6].id)
        assertEquals("GR_ATH", board.groups[6].stopId)
    }

    // fixture case: frequent_line_cannot_crowd_out_a_rare_one
    @Test
    fun frequentLineCannotCrowdOutARareOne() {
        val metro = (0 until 12).map {
            dep("M2_STA", "metro", "M2", "Elliniko", it * 3, "F$it", SourceConfidence.ESTIMATED)
        }
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = metro + dep("GR_ATH", "rail", "RG1", "Leianokladi", 55, "RG-884"),
        )
        assertEquals(2, board.timedGroupCount)
        assertEquals(listOf("Elliniko", "Leianokladi"), board.groups.map { it.destination })
        assertEquals(listOf(0, 3, 6), board.groups[0].times.map { it.absoluteMinutes })
        assertEquals(12, board.groups[0].total)
        assertEquals(9, board.groups[0].moreCount)
        assertEquals(1, board.groups[1].total)
    }

    // fixture case: one_source_cannot_suppress_another_mode
    @Test
    fun oneSourceCannotSuppressAnotherMode() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("A3_ATH", "rail", "A3", "Chalkida", 8, "A3-1360"),
                dep("M2_STA", "metro", "M2", "Elliniko", 2, "M2-E-1", SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Anthoupoli", 4, "M2-A-1", SourceConfidence.ESTIMATED),
            ),
        )
        assertEquals(3, board.timedGroupCount)
        assertEquals(listOf("Elliniko", "Anthoupoli", "Chalkida"), board.groups.map { it.destination })
        assertEquals(SourceConfidence.ESTIMATED, board.groups[0].source)
        assertEquals(SourceConfidence.SCHEDULED, board.groups[2].source)
    }

    // fixture case: branch_destinations_stay_distinct
    @Test
    fun branchDestinationsStayDistinct() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("M3_SYN", "metro", "M3", "Doukissis Plakentias", 3, "M3-1", SourceConfidence.ESTIMATED, pattern = "city"),
                dep("M3_SYN", "metro", "M3", "Airport", 9, "M3-2", SourceConfidence.ESTIMATED, pattern = "airport"),
                dep("M3_SYN", "metro", "M3", "Dimotiko Theatro", 5, "M3-3", SourceConfidence.ESTIMATED, pattern = "city"),
            ),
        )
        assertEquals(3, board.timedGroupCount)
        assertEquals(
            listOf("Doukissis Plakentias", "Dimotiko Theatro", "Airport"),
            board.groups.map { it.destination },
        )
        assertEquals("metro||M3|city|doukissis plakentias", board.groups[0].id)
        assertEquals("metro||M3|airport|airport", board.groups[2].id)
    }

    // fixture case: duplicates_collapse_same_minute_trains_survive
    @Test
    fun duplicatesCollapseSameMinuteTrainsSurvive() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("A1_ATH", "rail", "A1", "Airport", 12, "A1-2050", serviceDate = "2026-09-30"),
                dep("A1_ATH", "rail", "A1", "Airport", 15, "A1-2050", SourceConfidence.LIVE, serviceDate = "2026-09-30"),
                dep("A3_ATH", "rail", "A3", "Chalkida", 20, "A3-1360", serviceDate = "2026-09-30"),
                dep("A4_ATH", "rail", "A4", "Kiato", 20, "A4-1512", serviceDate = "2026-09-30"),
            ),
        )
        assertEquals(3, board.timedGroupCount)
        assertEquals(listOf("Airport", "Chalkida", "Kiato"), board.groups.map { it.destination })
        assertEquals(listOf(15), board.groups[0].times.map { it.absoluteMinutes })
        assertEquals(SourceConfidence.LIVE, board.groups[0].source)
        // Two different trains leaving in the same minute both survive.
        assertEquals(1, board.groups[1].total)
        assertEquals(1, board.groups[2].total)
    }

    // fixture case: arrivals_and_non_pickup_stops_are_not_departures
    @Test
    fun arrivalsAndNonPickupStopsAreNotDepartures() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("GR_ATH", "rail", "IC1", "Athens", 5, "IC-55", boardsHere = false),
                dep("GR_ATH", "rail", "IC1", "Thessaloniki", 25, "IC-54"),
            ),
        )
        assertEquals(1, board.timedGroupCount)
        assertEquals("Thessaloniki", board.groups[0].destination)
    }

    // fixture case: a_cancellation_is_status_never_the_recommendation
    @Test
    fun aCancellationIsStatusNeverTheRecommendation() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("A3_ATH", "rail", "A3", "Chalkida", 8, "A3-1360", cancelled = true),
                dep("A3_ATH", "rail", "A3", "Chalkida", 68, "A3-1362"),
            ),
        )
        assertEquals(1, board.timedGroupCount)
        val group = board.groups[0]
        assertEquals(listOf(8, 68), group.times.map { it.absoluteMinutes })
        assertEquals(listOf(true, false), group.times.map { it.cancelled })
        assertEquals(68, group.next?.absoluteMinutes)
    }

    // fixture case: partial_coverage_cannot_look_complete
    @Test
    fun partialCoverageCannotLookComplete() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("M2_STA", "metro", "M2", "Elliniko", 2, "M2-E-1", SourceConfidence.ESTIMATED),
            ),
            coverage = listOf(
                StationComplexBoard.CoverageEntry(
                    areaId = "metro", stopId = "M2_STA", lineId = "M2",
                    destination = "Elliniko", state = StationComplexBoard.Coverage.LOADED,
                ),
                StationComplexBoard.CoverageEntry(
                    areaId = "rail", stopId = "GR_ATH", lineId = "IC1",
                    destination = "Thessaloniki",
                    state = StationComplexBoard.Coverage.UNAVAILABLE, reason = "source_unreachable",
                ),
            ),
        )
        assertTrue(board.partial)
        assertEquals(1, board.timedGroupCount)
        assertEquals(2, board.groups.size)
        assertEquals("Thessaloniki", board.groups[1].destination)
        assertEquals(StationComplexBoard.Coverage.UNAVAILABLE, board.groups[1].coverage)
        assertEquals(0, board.groups[1].total)
    }

    // fixture case: no_departure_in_window_shows_the_next_verified_one
    @Test
    fun noDepartureInWindowShowsTheNextVerifiedOne() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(dep("GR_ATH", "rail", "RG1", "Leianokladi", 400, "RG-884")),
            windowMinutes = 120,
        )
        assertEquals(false, board.partial)
        assertEquals(0, board.timedGroupCount)
        val group = board.groups[0]
        assertEquals(listOf(400), group.times.map { it.absoluteMinutes })
        assertEquals(listOf(true), group.times.map { it.beyondWindow })
        assertEquals(0, group.total)
        assertEquals(StationComplexBoard.Coverage.NO_DEPARTURE_IN_WINDOW, group.coverage)
        assertNull(group.next)
    }

    // fixture case: a_live_update_moves_its_own_trip_only
    @Test
    fun aLiveUpdateMovesItsOwnTripOnly() {
        val board = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("M2_STA", "metro", "M2", "Elliniko", 2, "M2-E-1", SourceConfidence.ESTIMATED, serviceDate = "2026-09-30"),
                dep("M2_STA", "metro", "M2", "Elliniko", 9, "M2-E-1", SourceConfidence.LIVE, serviceDate = "2026-09-30"),
                dep("M2_STA", "metro", "M2", "Elliniko", 12, "M2-E-2", SourceConfidence.ESTIMATED, serviceDate = "2026-09-30"),
                dep("M2_STA", "metro", "M2", "Anthoupoli", 4, "M2-A-1", SourceConfidence.ESTIMATED, serviceDate = "2026-09-30"),
            ),
        )
        assertEquals(2, board.timedGroupCount)
        assertEquals(listOf("Anthoupoli", "Elliniko"), board.groups.map { it.destination })
        val elliniko = board.groups[1]
        assertEquals(listOf(9, 12), elliniko.times.map { it.absoluteMinutes })
        // The Live label belongs to the delayed trip only.
        assertEquals(
            listOf(SourceConfidence.LIVE, SourceConfidence.ESTIMATED),
            elliniko.times.map { it.source },
        )
    }

    // ------------------------------------------------------------- unit cases

    @Test
    fun oneTripCollapsesAndTheStrongerSourceWins() {
        val out = StationComplexBoard.dedupe(
            listOf(
                dep("A1_ATH", "rail", "A1", "Airport", 5, "T1", serviceDate = "D"),
                dep("A1_ATH", "rail", "A1", "Airport", 7, "T1", SourceConfidence.LIVE, serviceDate = "D"),
                dep("A1_ATH", "rail", "A1", "Airport", 7, "T2", serviceDate = "D"),
            ),
        )
        assertEquals(2, out.size)
        assertEquals("T1", out[0].tripId)
        assertEquals(SourceConfidence.LIVE, out[0].source)
        assertEquals(7, out[0].absoluteMinutes)
        assertEquals("T2", out[1].tripId)
    }

    @Test
    fun withoutTripIdsTheExactMinuteKeepsTwoTrainsApart() {
        val out = StationComplexBoard.dedupe(
            listOf(
                dep("M2_STA", "metro", "M2", "Elliniko", 4, source = SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Elliniko", 5, source = SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Elliniko", 5, source = SourceConfidence.OFFLINE),
            ),
        )
        assertEquals(listOf(4, 5), out.map { it.absoluteMinutes })
        assertEquals(SourceConfidence.ESTIMATED, out[1].source)
    }

    @Test
    fun destinationAloneNeverMergesTwoDepartures() {
        val out = StationComplexBoard.dedupe(
            listOf(
                dep("A1_ATH", "rail", "A1", "Airport", 5),
                dep("A2_ATH", "rail", "A2", "Airport", 5),
            ),
        )
        assertEquals(2, out.size)
    }

    @Test
    fun groupIdsSurviveAReorder() {
        fun board(elliniko: Int, anthoupoli: Int) = StationComplexBoard.build(
            complex = complex,
            candidates = listOf(
                dep("M2_STA", "metro", "M2", "Elliniko", elliniko, source = SourceConfidence.ESTIMATED),
                dep("M2_STA", "metro", "M2", "Anthoupoli", anthoupoli, source = SourceConfidence.ESTIMATED),
            ),
        )
        val before = board(1, 4)
        val after = board(9, 2)
        assertEquals(listOf("Elliniko", "Anthoupoli"), before.groups.map { it.destination })
        assertEquals(listOf("Anthoupoli", "Elliniko"), after.groups.map { it.destination })
        fun id(b: StationComplexBoard.Board, dest: String) =
            b.groups.first { it.destination == dest }.id
        assertEquals(id(before, "Elliniko"), id(after, "Elliniko"))
        assertEquals(id(before, "Anthoupoli"), id(after, "Anthoupoli"))
    }

    @Test
    fun foldingMatchesAccentsButNotDifferentSpellings() {
        assertEquals(StationComplexBoard.fold("Ελληνικό"), StationComplexBoard.fold("Ελληνικο"))
        assertEquals("airport", StationComplexBoard.fold("  Airport "))
        assertNotEquals(StationComplexBoard.fold("Kifissia"), StationComplexBoard.fold("Kifisias"))
    }

    @Test
    fun localizedNamesCoverTheFourSupportedLanguages() {
        assertEquals("Athens · Larissa Station", complex.localizedName("en"))
        assertEquals("Αθήνα · Σταθμός Λαρίσης", complex.localizedName("el"))
        assertEquals("Athinë · Stacioni Larisa", complex.localizedName("sq"))
        assertEquals("Atene · Stazione Larissa", complex.localizedName("it"))
        assertEquals("Σιδηροδρομικός σταθμός", complex.localizedAreaName("rail", "el"))
        assertEquals("Metropolitana", complex.localizedAreaName("metro", "it"))
    }

    @Test
    fun anEmptyInputIsAnEmptyBoard() {
        val board = StationComplexBoard.build(complex = complex, candidates = emptyList())
        assertEquals(0, board.groups.size)
        assertEquals(0, board.timedGroupCount)
        assertEquals(false, board.partial)
    }
}
