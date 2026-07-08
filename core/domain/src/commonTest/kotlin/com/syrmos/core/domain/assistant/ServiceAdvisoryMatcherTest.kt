package com.syrmos.core.domain.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Home screen already shows STASY announcements and a severe-weather signal.
 * These tests pin the rule that Ariadne surfaces the same advisories when the
 * user asks about an affected station, line, or route, and stays quiet when
 * nothing is relevant. The fixture mirrors the real alert seen in the app:
 * four M3 stations closing at 21:40.
 */
class ServiceAdvisoryMatcherTest {

    private val m3Closure = ServiceNotice(
        id = "m3-eve-closure",
        text = "Traffic arrangements on Metro Line 3: Megaro Musikis, Ampelokipoi, " +
            "Panormou and Katehaki stations will close at 21:40 in the evening.",
        affectedLineIds = listOf("M3"),
        severity = AdvisorySeverity.CLOSURE,
        validFrom = "2026-07-05",
        validUntil = "2026-07-09",
    )
    private val m3ClosureGreek = ServiceNotice(
        id = "m3-eve-closure-el",
        text = "Κυκλοφοριακές ρυθμίσεις στη Γραμμή 3: οι σταθμοί Μέγαρο Μουσικής, " +
            "Αμπελόκηποι, Πανόρμου και Κατεχάκη θα κλείσουν στις 21:40.",
        affectedLineIds = listOf("M3"),
        severity = AdvisorySeverity.CLOSURE,
    )
    private val m2Info = ServiceNotice(
        id = "m2-info",
        text = "Extended hours on Line 2 this weekend.",
        affectedLineIds = listOf("M2"),
        severity = AdvisorySeverity.INFO,
    )

    @Test
    fun station_named_in_notice_text_matches() {
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = listOf("Panormou", "Πανόρμου"),
            stationLineIds = listOf("M3"),
            notices = listOf(m3Closure, m2Info),
        )
        assertTrue(advisory.hasAny)
        assertEquals("m3-eve-closure", advisory.top?.id)
    }

    @Test
    fun greek_notice_text_matches_greek_station_name() {
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = listOf("Katehaki", "Κατεχάκη"),
            stationLineIds = listOf("M3"),
            notices = listOf(m3ClosureGreek),
        )
        assertTrue(advisory.hasAny)
    }

    @Test
    fun station_on_affected_line_is_surfaced_even_if_not_named() {
        // Syntagma is an M3 station not named in the closure text, but a line-wide
        // M3 advisory should still reach an M3 traveller.
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = listOf("Syntagma", "Σύνταγμα"),
            stationLineIds = listOf("M2", "M3"),
            notices = listOf(m3Closure),
        )
        assertTrue(advisory.hasAny)
    }

    @Test
    fun station_off_the_affected_line_and_unnamed_stays_quiet() {
        // Piraeus (M1 only) is neither on M3 nor named, so no advisory.
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = listOf("Piraeus", "Πειραιάς"),
            stationLineIds = listOf("M1"),
            notices = listOf(m3Closure),
        )
        assertFalse(advisory.hasAny)
        assertEquals(0, advisory.notices.size)
    }

    @Test
    fun line_query_matches_only_its_own_line() {
        assertTrue(ServiceAdvisoryMatcher.forLine("M3", listOf(m3Closure, m2Info)).hasAny)
        assertFalse(ServiceAdvisoryMatcher.forLine("M1", listOf(m3Closure, m2Info)).hasAny)
    }

    @Test
    fun route_through_affected_line_surfaces_notice() {
        val advisory = ServiceAdvisoryMatcher.forRoute(
            lineIds = listOf("M2", "M3"),
            stationNames = listOf("Syntagma", "Panormou"),
            notices = listOf(m3Closure),
        )
        assertTrue(advisory.hasAny)
    }

    @Test
    fun closures_rank_ahead_of_info() {
        val advisory = ServiceAdvisoryMatcher.forRoute(
            lineIds = listOf("M2", "M3"),
            stationNames = emptyList(),
            notices = listOf(m2Info.copy(affectedLineIds = listOf("M2", "M3")), m3Closure),
        )
        assertEquals(AdvisorySeverity.CLOSURE, advisory.top?.severity)
    }

    @Test
    fun severe_weather_flag_passes_through_even_with_no_notices() {
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = listOf("Syntagma"),
            stationLineIds = listOf("M2", "M3"),
            notices = emptyList(),
            severeWeather = true,
        )
        assertTrue(advisory.hasAny)
        assertTrue(advisory.severeWeather)
    }

    @Test
    fun severity_maps_from_raw_feed_strings() {
        assertEquals(AdvisorySeverity.CLOSURE, AdvisorySeverity.fromRaw("closure"))
        assertEquals(AdvisorySeverity.WARNING, AdvisorySeverity.fromRaw("Warning"))
        assertEquals(AdvisorySeverity.INFO, AdvisorySeverity.fromRaw("info"))
        assertEquals(AdvisorySeverity.INFO, AdvisorySeverity.fromRaw("something-else"))
    }
}
