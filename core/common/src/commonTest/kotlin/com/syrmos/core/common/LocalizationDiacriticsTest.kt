package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reader-facing text has to carry its diacritics. Greek loses meaning and
 * looks broken without the tonos, and Albanian written without ë and ç reads
 * as a transliteration rather than the language. Every string in the shared
 * table is checked in one place so a stripped entry fails the build instead
 * of shipping, on Android and on the web, which both read this table.
 */
class LocalizationDiacriticsTest {
    private val tonos = Regex("[άέήίόύώΆΈΉΊΌΎΏϊϋΐΰ]")

    // Greek words of five letters or more always carry a tonos in lower or
    // mixed case. All-caps text legitimately drops it, so it is skipped.
    private val longGreekWord = Regex("[Α-Ωα-ω]{5,}")

    // Bare forms that only exist in Albanian when the diacritics were dropped.
    private val strippedAlbanian = Regex(
        "\\b(eshte|nje|kete|ketu|gjithe|gjithcka|prane|sherbim\\w*|perdit\\w*|udhet\\w*|perdor\\w*|kerko\\w*|cmim\\w*|" +
            "vleresim\\w*|nderr\\w*|shpejtesi|nevoje|radhes|afert|disponueshem|plotesisht|jashte|te gjitha|per te|ne stacion|ne harte)\\b",
        RegexOption.IGNORE_CASE,
    )

    @Test
    fun greek_reader_text_carries_its_tonos() {
        val offenders = L.values()
            .map { it to it.text(AppLanguage.GREEK) }
            .filter { (_, text) -> text != text.uppercase() && longGreekWord.containsMatchIn(text) && !tonos.containsMatchIn(text) }
            .map { (key, text) -> "$key: $text" }
        assertTrue(offenders.isEmpty(), "Greek strings without a tonos: $offenders")
    }

    @Test
    fun albanian_reader_text_keeps_its_diacritics() {
        val offenders = L.values()
            .map { it to it.text(AppLanguage.ALBANIAN) }
            .filter { (_, text) -> strippedAlbanian.containsMatchIn(text) }
            .map { (key, text) -> "$key: $text" }
        assertTrue(offenders.isEmpty(), "Albanian strings with stripped diacritics: $offenders")
    }

    @Test
    fun the_checks_catch_a_stripped_string() {
        assertTrue(longGreekWord.containsMatchIn("Μεινε ενημερος") && !tonos.containsMatchIn("Μεινε ενημερος"))
        assertTrue(strippedAlbanian.containsMatchIn("Qendro i informuar. Merr njoftime per nderprerje sherbimesh prane teje."))
        assertTrue(!strippedAlbanian.containsMatchIn("Qëndro i informuar. Merr njoftime për ndërprerje shërbimesh pranë teje."))
    }
}
