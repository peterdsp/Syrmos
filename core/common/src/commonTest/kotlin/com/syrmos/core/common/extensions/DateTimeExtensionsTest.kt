package com.syrmos.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals

/** A test clock frozen at a fixed instant, so time-dependent code is deterministic. */
private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class DateTimeExtensionsTest {

    // 09:00 UTC on 2026-07-08. Athens is UTC+3 in July (DST), so 12:00 local.
    private val summerMorning = FixedClock(Instant.parse("2026-07-08T09:00:00Z"))

    @Test
    fun athens_time_is_offset_from_utc_in_summer() {
        assertEquals(LocalTime(12, 0), currentAthensTime(summerMorning))
    }

    @Test
    fun athens_time_is_standard_offset_in_winter() {
        // 09:00 UTC on 2026-01-15. Athens is UTC+2 (EET) in January, so 11:00
        // local. Pins standard time, catching any DST rule that wrongly applies
        // the summer offset outside the March-October window.
        val winterMorning = FixedClock(Instant.parse("2026-01-15T09:00:00Z"))
        assertEquals(LocalTime(11, 0), currentAthensTime(winterMorning))
    }

    @Test
    fun athens_date_reflects_the_frozen_instant() {
        assertEquals(LocalDate(2026, 7, 8), currentAthensDate(summerMorning))
        assertEquals(Month.JULY, currentAthensDate(summerMorning).month)
    }

    @Test
    fun after_midnight_utc_can_be_next_day_in_athens() {
        // 22:30 UTC on 2026-07-08 is 01:30 on 2026-07-09 in Athens.
        val lateUtc = FixedClock(Instant.parse("2026-07-08T22:30:00Z"))
        assertEquals(LocalDate(2026, 7, 9), currentAthensDate(lateUtc))
        assertEquals(LocalTime(1, 30), currentAthensTime(lateUtc))
    }

    @Test
    fun daylight_saving_starts_on_the_last_sunday_of_march() {
        val before = FixedClock(Instant.parse("2026-03-29T00:59:00Z"))
        val after = FixedClock(Instant.parse("2026-03-29T01:00:00Z"))
        assertEquals(LocalTime(2, 59), currentAthensTime(before))
        assertEquals(LocalTime(4, 0), currentAthensTime(after))
    }

    @Test
    fun daylight_saving_ends_on_the_last_sunday_of_october() {
        val before = FixedClock(Instant.parse("2026-10-25T00:59:00Z"))
        val after = FixedClock(Instant.parse("2026-10-25T01:00:00Z"))
        assertEquals(LocalTime(3, 59), currentAthensTime(before))
        assertEquals(LocalTime(3, 0), currentAthensTime(after))
    }
}
