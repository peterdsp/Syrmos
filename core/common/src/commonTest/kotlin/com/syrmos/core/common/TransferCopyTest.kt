package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferCopyTest {

    @Test fun one_change_is_singular_in_every_language() {
        assertEquals("1 change", TransferCopy.changes(1, AppLanguage.ENGLISH))
        assertEquals("1 αλλαγή", TransferCopy.changes(1, AppLanguage.GREEK))
        assertEquals("1 ndërrim", TransferCopy.changes(1, AppLanguage.ALBANIAN))
        assertEquals("1 cambio", TransferCopy.changes(1, AppLanguage.ITALIAN))
    }

    @Test fun more_than_one_change_is_plural_in_every_language() {
        assertEquals("2 changes", TransferCopy.changes(2, AppLanguage.ENGLISH))
        assertEquals("2 αλλαγές", TransferCopy.changes(2, AppLanguage.GREEK))
        assertEquals("2 ndërrime", TransferCopy.changes(2, AppLanguage.ALBANIAN))
        assertEquals("2 cambi", TransferCopy.changes(2, AppLanguage.ITALIAN))
    }

    @Test fun no_language_ever_shows_the_slash_placeholder_form() {
        for (lang in AppLanguage.entries) {
            for (n in 0..4) {
                val text = TransferCopy.changes(n, lang)
                assertFalse(text.contains("/"), "$lang $n -> $text")
                assertFalse(text.contains("("), "$lang $n -> $text")
            }
        }
    }

    @Test fun the_count_is_always_present() {
        for (lang in AppLanguage.entries) {
            for (n in 0..3) assertTrue(TransferCopy.changes(n, lang).startsWith("$n "), "$lang $n")
        }
    }

    @Test fun albanian_keeps_its_diacritic() {
        assertTrue(TransferCopy.changes(1, AppLanguage.ALBANIAN).contains("ë"))
        assertTrue(TransferCopy.changes(3, AppLanguage.ALBANIAN).contains("ë"))
    }
}
