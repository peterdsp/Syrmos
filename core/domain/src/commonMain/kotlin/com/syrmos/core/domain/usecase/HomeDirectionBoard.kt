package com.syrmos.core.domain.usecase

import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line

/**
 * The Home direction board: the nearest station's next train in EVERY
 * direction, not just the soonest one. One row per (line, direction) with the
 * next two times, soonest direction first, capped to `maxRows`. Mirrors the iOS
 * `DepartureGrouping.directionBoard`.
 */
object HomeDirectionBoard {
    data class Row(
        val lineId: String,
        val direction: Direction,
        /** Ascending minutes away, at most two. */
        val times: List<Int>,
        /** Departures in this direction beyond the two shown. */
        val moreCount: Int,
        /** Resolved by the caller for colour and terminal names; null when unknown. */
        val line: Line? = null,
    )

    fun rows(upcoming: List<UpcomingDeparture>, maxRows: Int = 4): List<Row> {
        if (maxRows <= 0) return emptyList()
        val sorted = upcoming.sortedBy { it.minutesAway }
        val order = ArrayList<Pair<String, Direction>>()
        val members = HashMap<Pair<String, Direction>, MutableList<UpcomingDeparture>>()
        for (d in sorted) {
            val key = d.lineId to d.direction
            if (members[key] == null) {
                order += key
                members[key] = mutableListOf()
            }
            members.getValue(key) += d
        }
        return order.take(maxRows).map { key ->
            val all = members.getValue(key)
            Row(
                lineId = key.first,
                direction = key.second,
                times = all.take(2).map { it.minutesAway },
                moreCount = maxOf(0, all.size - 2),
            )
        }
    }
}
