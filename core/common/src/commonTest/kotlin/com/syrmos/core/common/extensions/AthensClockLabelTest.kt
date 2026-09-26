package com.syrmos.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AthensClockLabelTest {
    @Test
    fun isoInstantBecomesAnAthensClock() {
        // 10:14Z on 26 September is 13:14 in Athens (EEST, UTC+3).
        assertEquals("13:14", athensClockLabel("2026-09-26T10:14:00.000Z"))
        assertEquals("13:14", athensClockLabel("2026-09-26T13:14:00+03:00"))
        // In winter (EET, UTC+2) the same wall clock is one hour later than UTC+... no: 10:14Z is 12:14.
        assertEquals("12:14", athensClockLabel("2026-01-15T10:14:00Z"))
    }

    @Test
    fun bareClocksAreNormalisedAndOtherTextPassesThrough() {
        assertEquals("09:05", athensClockLabel("9:05"))
        assertEquals("09:05", athensClockLabel("09:05:30"))
        assertEquals("n/a", athensClockLabel(" n/a "))
    }

    @Test
    fun blankIsNull() {
        assertNull(athensClockLabel(null))
        assertNull(athensClockLabel("  "))
    }
}
