package com.syrmos.core.domain.assistant

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins DayContext -> day-offset math. 2026-06-29 is a Monday. */
class DayContextResolverTest {
    private val monday = LocalDate(2026, 6, 29)   // Monday
    private val saturday = LocalDate(2026, 7, 4)  // Saturday
    private val sunday = LocalDate(2026, 7, 5)    // Sunday

    @Test fun today_is_zero() {
        assertEquals(0, DayContextResolver.dayOffset(DayContext.TODAY, monday))
    }

    @Test fun tomorrow_is_one() {
        assertEquals(1, DayContextResolver.dayOffset(DayContext.TOMORROW, monday))
    }

    @Test fun saturday_from_monday_is_five() {
        assertEquals(5, DayContextResolver.dayOffset(DayContext.SATURDAY, monday))
    }

    @Test fun sunday_from_monday_is_six() {
        assertEquals(6, DayContextResolver.dayOffset(DayContext.SUNDAY, monday))
    }

    @Test fun weekend_from_monday_is_next_saturday() {
        assertEquals(5, DayContextResolver.dayOffset(DayContext.WEEKEND, monday))
    }

    @Test fun weekend_on_saturday_is_today() {
        assertEquals(0, DayContextResolver.dayOffset(DayContext.WEEKEND, saturday))
    }

    @Test fun weekend_on_sunday_is_today() {
        assertEquals(0, DayContextResolver.dayOffset(DayContext.WEEKEND, sunday))
    }

    @Test fun saturday_on_saturday_is_today() {
        assertEquals(0, DayContextResolver.dayOffset(DayContext.SATURDAY, saturday))
    }
}
