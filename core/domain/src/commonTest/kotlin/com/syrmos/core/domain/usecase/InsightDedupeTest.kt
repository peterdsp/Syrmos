package com.syrmos.core.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

/** Twin of the dedupe cases in iOS `HomeFeaturesTests`. */
class InsightDedupeTest {
    @Test
    fun dropsARepeatedNoticeAndKeepsTheFirst() {
        val items = listOf("a" to "Line 3 works 27/09", "b" to "Line 3 works 27/09", "c" to "Line 1 closure")
        assertEquals(listOf("a", "c"), InsightDedupe.distinctByText(items) { it.second }.map { it.first })
    }

    @Test
    fun normalisationIgnoresCaseWhitespaceAndTrailingPunctuation() {
        val items = listOf("a" to "  Line 3   works. ", "b" to "line 3 works", "c" to "Line 3 works!")
        assertEquals(listOf("a"), InsightDedupe.distinctByText(items) { it.second }.map { it.first })
    }

    @Test
    fun emptyTextsAreNeverTreatedAsDuplicatesOfEachOther() {
        val items = listOf("a" to "", "b" to "  ", "c" to "Real notice")
        assertEquals(listOf("a", "b", "c"), InsightDedupe.distinctByText(items) { it.second }.map { it.first })
    }
}
