package com.syrmos.core.common

import com.syrmos.core.common.extensions.minutesUntil
import com.syrmos.core.common.extensions.parseTime
import com.syrmos.core.common.extensions.toDisplayString
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DateTimeExtensionsTest {

    @Test
    fun parseTime_standard_format() {
        val time = parseTime("14:30")
        assertEquals(14, time.hour)
        assertEquals(30, time.minute)
    }

    @Test
    fun parseTime_wraps_past_midnight() {
        val time = parseTime("25:10")
        assertEquals(1, time.hour)
        assertEquals(10, time.minute)
    }

    @Test
    fun parseTime_midnight() {
        val time = parseTime("00:00")
        assertEquals(0, time.hour)
        assertEquals(0, time.minute)
    }

    @Test
    fun minutesUntil_same_day() {
        val now = LocalTime(10, 0)
        val departure = LocalTime(10, 15)
        assertEquals(15, now.minutesUntil(departure))
    }

    @Test
    fun minutesUntil_wraps_midnight() {
        val now = LocalTime(23, 50)
        val departure = LocalTime(0, 10)
        assertEquals(20, now.minutesUntil(departure))
    }

    @Test
    fun minutesUntil_exact_same_time() {
        val now = LocalTime(12, 0)
        assertEquals(0, now.minutesUntil(now))
    }

    @Test
    fun toDisplayString_pads_single_digits() {
        val time = LocalTime(5, 3)
        assertEquals("05:03", time.toDisplayString())
    }

    @Test
    fun toDisplayString_double_digits() {
        val time = LocalTime(14, 30)
        assertEquals("14:30", time.toDisplayString())
    }
}
