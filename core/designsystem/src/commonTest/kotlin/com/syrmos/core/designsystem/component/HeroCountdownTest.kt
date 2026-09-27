package com.syrmos.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeroCountdownTest {
    @Test
    fun minutes_and_hours_use_the_readers_abbreviations() {
        assertEquals("8 λεπ", heroCountdown(8 * 60, "Τώρα", minuteLabel = "λεπ", hourLabel = "ω").text)
        assertEquals("1ω 5λεπ", heroCountdown(65 * 60, "Τώρα", minuteLabel = "λεπ", hourLabel = "ω").text)
        assertEquals("2h", heroCountdown(2 * 3600, "Now").text)
        assertEquals("8 min", heroCountdown(8 * 60, "Now").text)
    }

    @Test
    fun the_live_window_and_now_do_not_depend_on_language() {
        assertEquals("Τώρα", heroCountdown(0, "Τώρα", minuteLabel = "λεπ").text)
        val state = heroCountdown(95, "Now", minuteLabel = "λεπ")
        assertEquals("1:35", state.text)
        assertTrue(!state.isImminent)
        assertTrue(heroCountdown(45, "Now").isImminent)
    }
}
