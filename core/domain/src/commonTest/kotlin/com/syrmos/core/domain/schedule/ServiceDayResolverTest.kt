package com.syrmos.core.domain.schedule

import com.syrmos.core.model.schedule.DayType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The shared Greek service-day calendar. Guards the holiday table (previously
 * copied across the band projector, the active-train projector and the seed
 * fallbacks) and, critically, the base-DayType mapping the seed-DB fallback
 * needs: a public holiday must resolve to the Sunday/Saturday service it runs,
 * not the plain weekday service (the P1 the parity audit found offline).
 */
class ServiceDayResolverTest {

    @Test
    fun holidayTableMatchesTheBundleKeys() {
        assertEquals("sun", ServiceDayResolver.holidayDayType(LocalDate(2026, 1, 1)))
        assertEquals("sun", ServiceDayResolver.holidayDayType(LocalDate(2026, 12, 25)))
        assertEquals("aug_15", ServiceDayResolver.holidayDayType(LocalDate(2026, 8, 15)))
        assertEquals("dec_24_31", ServiceDayResolver.holidayDayType(LocalDate(2026, 12, 24)))
        assertEquals("dec_24_31", ServiceDayResolver.holidayDayType(LocalDate(2026, 12, 31)))
        assertEquals("sat", ServiceDayResolver.holidayDayType(LocalDate(2026, 1, 6)))
        assertEquals("sat", ServiceDayResolver.holidayDayType(LocalDate(2026, 11, 17)))
    }

    @Test
    fun ordinaryDaysHaveNoHoliday() {
        assertNull(ServiceDayResolver.holidayDayType(LocalDate(2026, 3, 10)))
        assertNull(ServiceDayResolver.holidayDayType(LocalDate(2026, 7, 1)))
    }

    @Test
    fun holidayOverridesTheWeekdayForBaseDayType() {
        // 2026-08-15 is a Saturday and 2026-01-06 a Tuesday; both must resolve to
        // their holiday service, not their calendar weekday.
        assertEquals(DayType.SUNDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 8, 15)))
        assertEquals(DayType.SUNDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 12, 25)))
        assertEquals(DayType.SATURDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 1, 6)))
        assertEquals(DayType.SATURDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 12, 31)))
    }

    @Test
    fun ordinaryWeekdaysMapToTheirService() {
        // 2026-01-01 is a Thursday, so: 05=Mon, 09=Fri, 10=Sat, 11=Sun (none holidays).
        assertEquals(DayType.WEEKDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 1, 5)))
        assertEquals(DayType.FRIDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 1, 9)))
        assertEquals(DayType.SATURDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 1, 10)))
        assertEquals(DayType.SUNDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 1, 11)))
    }

    @Test
    fun independenceDayRunsSundayService() {
        // 2026-03-25 is a Wednesday; it was missing from the table and ran weekday.
        assertEquals("sun", ServiceDayResolver.holidayDayType(LocalDate(2026, 3, 25)))
        assertEquals(DayType.SUNDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 3, 25)))
    }

    @Test
    fun orthodoxEaster2026IsApril12() {
        assertEquals(LocalDate(2026, 4, 12), ServiceDayResolver.orthodoxEaster(2026))
    }

    @Test
    fun movableOrthodoxHolidaysRunSundayService() {
        // 2026 Orthodox Easter = Apr 12. Clean Monday Feb 23, Good Friday Apr 10,
        // Easter Monday Apr 13, Whit Monday Jun 1 are all weekdays that must run
        // Sunday service, not weekday.
        for (d in listOf(
            LocalDate(2026, 2, 23), LocalDate(2026, 4, 10),
            LocalDate(2026, 4, 13), LocalDate(2026, 6, 1),
        )) {
            assertEquals("sun", ServiceDayResolver.holidayDayType(d), "holiday $d")
            assertEquals(DayType.SUNDAY, ServiceDayResolver.baseDayType(d), "service $d")
        }
        // A nearby ordinary Tuesday stays weekday.
        assertNull(ServiceDayResolver.holidayDayType(LocalDate(2026, 4, 7)))
        assertEquals(DayType.WEEKDAY, ServiceDayResolver.baseDayType(LocalDate(2026, 4, 7)))
    }
}
