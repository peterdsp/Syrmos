package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class StationNameTranslatorTest {
    @Test
    fun italian_live_train_route_uses_italian_exonyms() {
        assertEquals("Atene", StationNameTranslator.resolve("Αθήνα", "Athens", AppLanguage.ITALIAN))
        assertEquals("Salonicco", StationNameTranslator.resolve("Θεσσαλονίκη", "Thessaloniki", AppLanguage.ITALIAN))
        assertEquals("Pireo", StationNameTranslator.resolve("Πειραιάς", "Piraeus", AppLanguage.ITALIAN))
    }

    @Test
    fun italian_keeps_names_without_an_exonym() {
        assertEquals("Syntagma", StationNameTranslator.resolve("Σύνταγμα", "Syntagma", AppLanguage.ITALIAN))
    }

    @Test
    fun greek_keeps_the_operator_name() {
        assertEquals("Αθήνα", StationNameTranslator.resolve("Αθήνα", "Athens", AppLanguage.GREEK))
    }
}
