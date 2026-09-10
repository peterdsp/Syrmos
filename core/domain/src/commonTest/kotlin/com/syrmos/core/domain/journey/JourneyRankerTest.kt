package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.Ranking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mirrors fixtures/journeys/ranking.json case-for-case so the Kotlin JourneyRanker
 * and web SyrmosJourneyRanker order, dedup, cap and badge identically (prompt 7.2).
 */
class JourneyRankerTest {

    private val date = LocalDate.parse("2026-01-15")

    private fun opt(
        id: String,
        durationSeconds: Int?,
        transferCount: Int,
        walkingSeconds: Int?,
        arrival: String? = null,
        lineId: String = id,
        fromId: String = "x",
        toId: String = "y",
    ) = JourneyOption(
        id = id, requestId = "req",
        durationSeconds = durationSeconds, transferCount = transferCount, walkingSeconds = walkingSeconds,
        arrivalInstant = arrival?.let { Instant.parse("2026-01-15T$it+02:00") },
        legs = listOf(Leg(id = "$id-l", kind = LegKind.RIDE, fromId = fromId, toId = toId, lineId = lineId, serviceDate = date)),
        feasibility = Feasibility(FeasibilityStatus.COMFORTABLE, explanationCode = "seed"),
    )

    private fun assertRank(out: List<JourneyOption>, order: List<String>, firstBadge: String?) {
        assertEquals(order, out.map { it.id }, "ordered ids")
        assertEquals(firstBadge, out.first().rankingBadge, "top badge")
        for (i in 1 until out.size) assertNull(out[i].rankingBadge, "option $i badge")
        assertTrue(out.size <= JourneyRanker.MAX_OPTIONS, "at most three")
    }

    @Test fun fastestOrdersAndBadges() = assertRank(
        JourneyRanker.rank(listOf(
            opt("b", 2100, 1, 100, "09:15:00"),
            opt("a", 1800, 0, 200, "09:12:00"),
            opt("c", 2400, 2, 50, "09:20:00"),
        ), Ranking.FASTEST),
        listOf("a", "b", "c"), "fastest")

    @Test fun dedupIdenticalLegSequences() = assertRank(
        JourneyRanker.rank(listOf(
            opt("a", 1800, 0, 0, "09:12:00", lineId = "M2"),
            opt("b", 2000, 0, 0, "09:14:00", lineId = "M2"),
        ), Ranking.FASTEST),
        listOf("a"), "fastest")

    @Test fun capAtThreeNeverPadded() = assertRank(
        JourneyRanker.rank(listOf(
            opt("o5", 500, 0, 0), opt("o1", 100, 0, 0), opt("o3", 300, 0, 0),
            opt("o2", 200, 0, 0), opt("o4", 400, 0, 0),
        ), Ranking.FASTEST),
        listOf("o1", "o2", "o3"), "fastest")

    @Test fun fewestChangesOrdersAndBadges() = assertRank(
        JourneyRanker.rank(listOf(
            opt("c2", 1000, 2, 0), opt("c0", 3000, 0, 0), opt("c1", 2000, 1, 0),
        ), Ranking.FEWEST_CHANGES),
        listOf("c0", "c1", "c2"), "fewestChanges")

    @Test fun leastWalkingNullsLast() = assertRank(
        JourneyRanker.rank(listOf(
            opt("w300", 1000, 0, 300), opt("wnull", 1000, 0, null), opt("w120", 1000, 0, 120),
        ), Ranking.LEAST_WALKING),
        listOf("w120", "w300", "wnull"), "leastWalking")

    @Test fun tieBreakByArrivalThenId() = assertRank(
        JourneyRanker.rank(listOf(
            opt("late", 1800, 0, 0, "09:10:00"), opt("early", 1800, 0, 0, "09:05:00"),
        ), Ranking.FASTEST),
        listOf("early", "late"), "fastest")

    @Test fun badgeNullWhenObjectiveMetricUnknown() = assertRank(
        JourneyRanker.rank(listOf(
            opt("q", null, 0, 0, "09:20:00"), opt("p", null, 0, 0, "09:10:00"),
        ), Ranking.FASTEST),
        listOf("p", "q"), null)
}
