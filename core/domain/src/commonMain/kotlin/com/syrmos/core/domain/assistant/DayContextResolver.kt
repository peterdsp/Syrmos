package com.syrmos.core.domain.assistant

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

/**
 * Maps a parsed [DayContext] to a day offset from today, so a "this weekend" or
 * "tomorrow" question projects the right service day instead of falling back to
 * today. Pure and testable: pass [today] explicitly. ISO weekday numbering,
 * Monday = 1 … Sunday = 7, Saturday = 6.
 */
object DayContextResolver {
    fun dayOffset(day: DayContext, today: LocalDate): Int {
        val dow = today.dayOfWeek.isoDayNumber
        return when (day) {
            DayContext.TODAY -> 0
            DayContext.TOMORROW -> 1
            DayContext.SATURDAY -> (6 - dow + 7) % 7
            DayContext.SUNDAY -> (7 - dow + 7) % 7
            // Already the weekend? Use today; otherwise the next Saturday.
            DayContext.WEEKEND -> if (dow >= 6) 0 else 6 - dow
        }
    }

    fun dayOffset(day: DayContext): Int {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.of("Europe/Athens")).date
        return dayOffset(day, today)
    }
}
