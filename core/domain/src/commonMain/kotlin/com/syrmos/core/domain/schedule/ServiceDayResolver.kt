package com.syrmos.core.domain.schedule

import com.syrmos.core.model.schedule.DayType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

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
        return when (mmdd) {
            "01-01", "05-01", "10-28", "12-25", "12-26" -> "sun"
            "08-15" -> "aug_15"
            "12-24", "12-31" -> "dec_24_31"
            "01-02", "01-06", "11-17" -> "sat"
            else -> null
        }
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
