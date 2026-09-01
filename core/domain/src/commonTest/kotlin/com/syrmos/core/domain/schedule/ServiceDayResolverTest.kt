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
}
