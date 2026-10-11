package com.syrmos.android

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.DataFreshness
import com.syrmos.core.common.FreshnessEvaluator
import com.syrmos.core.domain.usecase.InsightDedupe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Android-side twins of the iOS XCTest contracts.
 *
 * These deliberately exercise shared production code from the Android target:
 * a passing Android build therefore cannot silently replace the iOS rules with
 * Android-specific interpretations of freshness, deduplication, or language.
 */
class AndroidIOSParityTest {
    private val now = Instant.fromEpochSeconds(1_700_000_000)

    @Test fun freshness_contract_matches_ios_for_all_boundary_cases() {
        assertEquals(DataFreshness.PREDICTED, FreshnessEvaluator.evaluate(null, now))
        assertEquals(DataFreshness.LIVE, FreshnessEvaluator.evaluate(now - 30.seconds, now, 90))
        assertEquals(DataFreshness.LIVE, FreshnessEvaluator.evaluate(now - 90.seconds, now, 90))
        assertEquals(DataFreshness.PREDICTED, FreshnessEvaluator.evaluate(now - 91.seconds, now, 90))
        assertEquals(DataFreshness.PREDICTED, FreshnessEvaluator.evaluate(now + 10.seconds, now, 90))
    }

    @Test fun insight_dedupe_preserves_first_and_normalises_text() {
        val values = listOf("a" to "  Line 3   works. ", "b" to "line 3 works", "c" to "Line 3 works!")
        assertEquals(listOf("a"), InsightDedupe.distinctByText(values) { it.second }.map { it.first })
    }

    @Test fun insight_dedupe_keeps_blank_items_distinct() {
        val values = listOf("a" to "", "b" to "  ", "c" to "Real notice")
        assertEquals(listOf("a", "b", "c"), InsightDedupe.distinctByText(values) { it.second }.map { it.first })
    }

    @Test fun supported_languages_have_stable_codes() {
        assertEquals(listOf("en", "el", "sq", "it"), AppLanguage.entries.map { it.code })
    }

    @Test fun empty_dedupe_input_has_no_result() {
        assertNull(InsightDedupe.distinctByText(emptyList<String>()) { it }.firstOrNull())
    }
}
