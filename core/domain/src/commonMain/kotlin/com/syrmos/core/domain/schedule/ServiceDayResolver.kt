package com.syrmos.core.domain.schedule

import com.syrmos.core.model.schedule.DayType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The single source of truth for Greek public-transport service days.
 *
 * The fixed-date holiday calendar used to be copied across the band projector,
 * the active-train projector, the seed-DB departures fallback and the map
 * fallback. The last two omitted holidays entirely, so on a public holiday that
 * falls on a weekday (Aug 15 on a Tuesday) the offline seed path queried weekday
 * service instead of the Sunday service that actually runs. Consolidated here so
 * the calendar is defined once and every path applies it.
 */
object ServiceDayResolver {

    /**
     * The fixed-date holiday band key for [date], or null on an ordinary day.
     * `sun`/`sat` group the holidays that run Sunday/Saturday service; `aug_15`
     * and `dec_24_31` are their own bundled band keys.
     */
    fun holidayDayType(date: LocalDate): String? {
        val mmdd = "${pad(date.monthNumber)}-${pad(date.dayOfMonth)}"
        val fixed = when (mmdd) {
            // 03-25 (Independence Day) was missing, so it ran weekday service.
            "01-01", "03-25", "05-01", "10-28", "12-25", "12-26" -> "sun"
            "08-15" -> "aug_15"
            "12-24", "12-31" -> "dec_24_31"
            "01-02", "01-06", "11-17" -> "sat"
            else -> null
        }
        if (fixed != null) return fixed
        // Movable Orthodox holidays, all Sunday service. These were absent, so on
        // Clean Monday / Good Friday / Easter Monday (always a weekday) the app
        // ran weekday service instead of the Sunday service that actually runs.
        val easter = orthodoxEaster(date.year)
        val movable = setOf(
            easter.minus(48, DateTimeUnit.DAY), // Clean Monday
            easter.minus(2, DateTimeUnit.DAY),  // Good Friday
            easter,                             // Easter Sunday
            easter.plus(1, DateTimeUnit.DAY),   // Easter Monday
            easter.plus(50, DateTimeUnit.DAY),  // Whit (Pentecost) Monday
        )
        return if (date in movable) "sun" else null
    }

    /**
     * Orthodox Easter (Gregorian calendar) via the Meeus Julian algorithm plus
     * the 13-day Julian-to-Gregorian offset valid for 1900-2099.
     */
    fun orthodoxEaster(year: Int): LocalDate {
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = ((d + e + 114) % 31) + 1
        return LocalDate(year, month, day).plus(13, DateTimeUnit.DAY)
    }

    /**
     * The base [DayType] the seed schedule tables are keyed by. The seed carries
     * only weekday/friday/saturday/sunday rows, so a holiday resolves to the
     * service it actually runs: Sunday for the major holidays and Aug 15,
     * Saturday for the half-holidays and the Dec 24/31 reduced-service days.
     */
    fun baseDayType(date: LocalDate): DayType =
        when (holidayDayType(date)) {
            "sun", "aug_15" -> DayType.SUNDAY
            "sat", "dec_24_31" -> DayType.SATURDAY
            else -> when (date.dayOfWeek) {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> DayType.WEEKDAY
                DayOfWeek.FRIDAY -> DayType.FRIDAY
                DayOfWeek.SATURDAY -> DayType.SATURDAY
                DayOfWeek.SUNDAY -> DayType.SUNDAY
                else -> DayType.WEEKDAY
            }
        }

    private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"
}
