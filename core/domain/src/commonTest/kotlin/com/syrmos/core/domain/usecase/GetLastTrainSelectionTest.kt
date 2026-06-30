package com.syrmos.core.domain.usecase

import com.syrmos.core.model.transit.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the selection rule behind the "last train home" teaser
 * ([GetLastTrainUseCase.selectLastTrain]). The band projector returns every
 * remaining slot for tonight sorted ascending by minutesAway; the teaser must
 * surface the single latest one that's still within the "tonight" window.
 *
 * The invariant that matters: a look-ahead row (e.g. tomorrow's first M3_AIR
 * train, which the projector can append up to a week out) must NOT be reported
 * as tonight's last train. The 12h horizon is what keeps that honest.
 */
class GetLastTrainSelectionTest {

    private fun dep(minutesAway: Int, time: String, lineId: String = "M2") =
        UpcomingDeparture(
            time = time,
            minutesAway = minutesAway,
            direction = Direction.OUTBOUND,
            lineId = lineId,
        )

    @Test
    fun picks_the_latest_slot_within_the_window() {
        val departures = listOf(
            dep(4, "23:30"),
            dep(24, "23:50"),
            dep(44, "00:10"),
        )
        val last = GetLastTrainUseCase.selectLastTrain(departures, maxLookaheadMinutes = 12 * 60)
        assertEquals("00:10", last?.time)
        assertEquals(44, last?.minutesAway)
    }

    @Test
    fun ignores_lookahead_row_beyond_the_horizon() {
        // 00:10 tonight (44 min) is the real last train. The 05:30 first
        // airport train tomorrow shows up as a 7h+ look-ahead row and must be
        // excluded, otherwise the teaser would claim "leave by 05:30".
        val departures = listOf(
            dep(4, "23:30"),
            dep(44, "00:10"),
            dep(7 * 60 + 20, "05:30", lineId = "M3_AIR"),
        )
        val last = GetLastTrainUseCase.selectLastTrain(departures, maxLookaheadMinutes = 6 * 60)
        assertEquals("00:10", last?.time)
        assertEquals(44, last?.minutesAway)
    }

    @Test
    fun returns_null_when_service_is_over() {
        assertNull(GetLastTrainUseCase.selectLastTrain(emptyList(), maxLookaheadMinutes = 12 * 60))
    }

    @Test
    fun excludes_negative_minutes_away() {
        // Defensive: a stale slot that has already passed (negative) is never
        // "the last train".
        val departures = listOf(dep(-3, "23:00"), dep(12, "23:15"))
        val last = GetLastTrainUseCase.selectLastTrain(departures, maxLookaheadMinutes = 12 * 60)
        assertEquals("23:15", last?.time)
    }
}
