package com.syrmos.core.domain.station

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.transit.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationDepartureFallbackTest {

    @Test fun a_station_on_one_network_takes_that_network() {
        assertEquals(
            Region.THESSALONIKI,
            StationDepartureFallback.regionOf(listOf(Region.THESSALONIKI, Region.THESSALONIKI)),
        )
    }

    @Test fun a_station_on_mixed_networks_falls_back_to_athens() {
        assertEquals(
            Region.ATHENS,
            StationDepartureFallback.regionOf(listOf(Region.NATIONAL, Region.THESSALONIKI)),
        )
    }

    @Test fun a_station_with_no_lines_falls_back_to_athens() {
        assertEquals(Region.ATHENS, StationDepartureFallback.regionOf(emptyList()))
    }

    @Test fun athens_never_shows_the_network_fallback() {
        // Athens departures come from the bundled seed, so an empty board there
        // really does mean closed. Showing "check your connection" would lie.
        assertFalse(StationDepartureFallback.showsFallback(Region.ATHENS))
        assertNull(StationDepartureFallback.operatorName(Region.ATHENS))
        assertNull(StationDepartureFallback.operatorUrl(Region.ATHENS))
        assertNull(StationDepartureFallback.checkTimetablesOn(Region.ATHENS, AppLanguage.ENGLISH))
    }

    @Test fun every_non_athens_region_shows_a_fallback_with_an_operator_and_a_url() {
        listOf(Region.THESSALONIKI, Region.NATIONAL, Region.PATRAS).forEach { region ->
            assertTrue(StationDepartureFallback.showsFallback(region), "$region")
            assertNotNull(StationDepartureFallback.operatorName(region), "$region")
            val url = StationDepartureFallback.operatorUrl(region)
            assertNotNull(url, "$region")
            assertTrue(url.startsWith("https://"), "$region must link over https, got $url")
        }
    }

    @Test fun thessaloniki_points_at_oasth_and_the_rest_at_hellenic_train() {
        assertEquals("OASTH", StationDepartureFallback.operatorName(Region.THESSALONIKI))
        assertEquals("https://www.oasth.gr", StationDepartureFallback.operatorUrl(Region.THESSALONIKI))
        assertEquals("Hellenic Train", StationDepartureFallback.operatorName(Region.NATIONAL))
        assertEquals("Hellenic Train", StationDepartureFallback.operatorName(Region.PATRAS))
        assertEquals("https://www.hellenictrain.gr", StationDepartureFallback.operatorUrl(Region.PATRAS))
    }

    @Test fun the_failure_message_is_translated_into_all_four_languages() {
        val texts = AppLanguage.entries.map { StationDepartureFallback.couldNotLoad(it) }
        assertEquals(4, texts.toSet().size, "each language needs its own wording: $texts")
        texts.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test fun the_operator_link_label_names_the_operator_in_all_four_languages() {
        AppLanguage.entries.forEach { lang ->
            val label = StationDepartureFallback.checkTimetablesOn(Region.THESSALONIKI, lang)
            assertNotNull(label)
            assertTrue(label.contains("OASTH"), "$lang label must name the operator: $label")
        }
        val labels = AppLanguage.entries.map {
            StationDepartureFallback.checkTimetablesOn(Region.NATIONAL, it)
        }
        assertEquals(4, labels.toSet().size, "each language needs its own wording: $labels")
    }

    @Test fun the_no_timetable_line_never_claims_the_service_is_closed() {
        // Makedonia Airport: X3 and 2X hold no timetable at all, and the board
        // used to say "the line is closed or today's service has ended" while
        // the shuttles were running.
        for (lang in AppLanguage.entries) {
            val text = StationDepartureFallback.noTimetable(lang).lowercase()
            assertTrue(text.isNotBlank(), "$lang")
            for (word in listOf("closed", "κλειστ", "mbyll", "chiusa", "chiuso")) {
                assertFalse(word in text, "$lang must not claim a closure: $text")
            }
        }
    }

    @Test fun the_no_timetable_line_is_translated_into_all_four_languages() {
        val texts = AppLanguage.entries.map { StationDepartureFallback.noTimetable(it) }
        assertEquals(4, texts.toSet().size, "each language needs its own wording: $texts")
    }

    @Test fun albanian_keeps_its_diacritics() {
        assertTrue(StationDepartureFallback.couldNotLoad(AppLanguage.ALBANIAN).contains("ë"))
        assertTrue(
            StationDepartureFallback.checkTimetablesOn(Region.THESSALONIKI, AppLanguage.ALBANIAN)!!
                .contains("në"),
        )
    }
}
