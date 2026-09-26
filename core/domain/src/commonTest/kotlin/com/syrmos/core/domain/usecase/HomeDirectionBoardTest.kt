package com.syrmos.core.domain.usecase

import com.syrmos.core.model.transit.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Twin of the iOS `DepartureGroupingTests` direction-board cases. */
class HomeDirectionBoardTest {

    private fun dep(line: String, min: Int, dir: Direction) =
        UpcomingDeparture(time = "", minutesAway = min, direction = dir, lineId = line)

    @Test
    fun everyDirectionSoonestFirstWithTwoTimes() {
        val rows = HomeDirectionBoard.rows(listOf(
            dep("M1", 9, Direction.OUTBOUND),
            dep("M1", 4, Direction.INBOUND),
            dep("M3", 6, Direction.OUTBOUND),
            dep("M1", 14, Direction.INBOUND),
            dep("M1", 19, Direction.OUTBOUND),
            dep("M1", 24, Direction.INBOUND),
            dep("M3", 16, Direction.INBOUND),
        ))
        assertEquals(listOf("M1" to Direction.INBOUND, "M3" to Direction.OUTBOUND, "M1" to Direction.OUTBOUND, "M3" to Direction.INBOUND),
            rows.map { it.lineId to it.direction })
        assertEquals(listOf(4, 14), rows[0].times)
        assertEquals(1, rows[0].moreCount)
        assertEquals(listOf(9, 19), rows[2].times)
    }

    @Test
    fun cappedAndUnsortedInput() {
        val rows = HomeDirectionBoard.rows(listOf(
            dep("T6", 30, Direction.OUTBOUND),
            dep("M2", 3, Direction.INBOUND),
            dep("M2", 7, Direction.OUTBOUND),
        ), maxRows = 2)
        assertEquals(listOf("M2" to Direction.INBOUND, "M2" to Direction.OUTBOUND), rows.map { it.lineId to it.direction })
        assertTrue(HomeDirectionBoard.rows(emptyList()).isEmpty())
        assertTrue(HomeDirectionBoard.rows(listOf(dep("M2", 3, Direction.INBOUND)), maxRows = 0).isEmpty())
    }
}
