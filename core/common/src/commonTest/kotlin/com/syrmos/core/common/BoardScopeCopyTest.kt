package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The board line read "1 services" on every client because the count word was
 * always plural. Pin the singular in all four languages.
 */
class BoardScopeCopyTest {

    @Test fun one_service_is_singular_in_every_language() {
        assertEquals("service", BoardScopeCopy.services(1, AppLanguage.ENGLISH))
        assertEquals("υπηρεσία", BoardScopeCopy.services(1, AppLanguage.GREEK))
        assertEquals("shërbim", BoardScopeCopy.services(1, AppLanguage.ALBANIAN))
        assertEquals("servizio", BoardScopeCopy.services(1, AppLanguage.ITALIAN))
    }

    @Test fun more_than_one_service_is_plural_in_every_language() {
        assertEquals("services", BoardScopeCopy.services(3, AppLanguage.ENGLISH))
        assertEquals("υπηρεσίες", BoardScopeCopy.services(3, AppLanguage.GREEK))
        assertEquals("shërbime", BoardScopeCopy.services(3, AppLanguage.ALBANIAN))
        assertEquals("servizi", BoardScopeCopy.services(3, AppLanguage.ITALIAN))
    }

    @Test fun zero_services_uses_the_plural_as_english_and_greek_both_do() {
        AppLanguage.entries.forEach {
            assertEquals(BoardScopeCopy.services(2, it), BoardScopeCopy.services(0, it))
        }
    }

    @Test fun singular_and_plural_differ_in_every_language() {
        AppLanguage.entries.forEach {
            assertNotEquals(BoardScopeCopy.services(1, it), BoardScopeCopy.services(2, it))
        }
    }

    @Test fun albanian_keeps_its_diacritic_in_both_forms() {
        assertEquals("shërbim", BoardScopeCopy.services(1, AppLanguage.ALBANIAN))
        assertEquals("shërbime", BoardScopeCopy.services(9, AppLanguage.ALBANIAN))
    }
}
