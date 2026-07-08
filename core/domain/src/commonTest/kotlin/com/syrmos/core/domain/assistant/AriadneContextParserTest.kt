package com.syrmos.core.domain.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Phase 1 co-pilot behaviours: station status, "I'm at X" context-setting, and
 * route preference parsing, across EN / EL / SQ and messy Greeklish. Fixtures
 * follow docs/ARIADNE_VOICE.md.
 */
class AriadneContextParserTest {

    private val vocab = AssistantVocabulary(
        stations = listOf(
            StationVocab("M2_SYN", listOf("Syntagma", "Σύνταγμα", "Sintagma"), listOf("M2", "M3")),
            StationVocab("M1_PIR", listOf("Piraeus", "Πειραιάς", "Pireas"), listOf("M1", "A1")),
            StationVocab("M3_AER", listOf("Airport", "Αεροδρόμιο", "Aeroporti", "Aeroport"), listOf("M3", "A1")),
            StationVocab("M1_MON", listOf("Monastiraki", "Μοναστηράκι"), listOf("M1", "M3")),
        ),
        lines = listOf(
            LineVocab("M1", listOf("M1", "line 1")),
            LineVocab("M2", listOf("M2", "line 2")),
            LineVocab("M3", listOf("M3", "line 3")),
            LineVocab("A1", listOf("A1", "airport line")),
        ),
    )
    private val parser = AthensTransitParser(vocab)

    // MARK: Station status

    @Test
    fun is_station_open_is_status_not_departures() {
        val intent = parser.parse("is Syntagma open?")
        val status = assertIs<AssistantIntent.StationStatus>(intent)
        assertEquals("M2_SYN", status.stationId)
    }

    @Test
    fun is_station_closed_is_status_not_alerts() {
        // "closed" also lives in ALERT_WORDS; with a named station, status wins.
        val intent = parser.parse("is Monastiraki closed right now")
        val status = assertIs<AssistantIntent.StationStatus>(intent)
        assertEquals("M1_MON", status.stationId)
    }

    @Test
    fun greek_station_working_query_is_status() {
        val intent = parser.parse("λειτουργεί το Σύνταγμα;")
        val status = assertIs<AssistantIntent.StationStatus>(intent)
        assertEquals("M2_SYN", status.stationId)
    }

    @Test
    fun station_open_without_a_station_asks_which() {
        val intent = parser.parse("is the station open")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.StationStatus>(clar.base)
        assertEquals(MissingSlot.STATION, clar.missing)
    }

    // MARK: Context-set "I'm at X"

    @Test
    fun im_at_station_sets_current_location() {
        val intent = parser.parse("I'm at Syntagma")
        val loc = assertIs<AssistantIntent.SetCurrentLocation>(intent)
        assertEquals("M2_SYN", loc.stationId)
    }

    @Test
    fun messy_im_at_station_sets_current_location() {
        val intent = parser.parse("im at monastiraki")
        val loc = assertIs<AssistantIntent.SetCurrentLocation>(intent)
        assertEquals("M1_MON", loc.stationId)
    }

    @Test
    fun greek_eimai_sto_sets_current_location() {
        val intent = parser.parse("είμαι στο Σύνταγμα")
        val loc = assertIs<AssistantIntent.SetCurrentLocation>(intent)
        assertEquals("M2_SYN", loc.stationId)
    }

    @Test
    fun albanian_jam_te_sets_current_location() {
        val intent = parser.parse("jam te Syntagma")
        val loc = assertIs<AssistantIntent.SetCurrentLocation>(intent)
        assertEquals("M2_SYN", loc.stationId)
    }

    @Test
    fun im_here_with_no_station_sets_null_location() {
        val intent = parser.parse("I'm here")
        val loc = assertIs<AssistantIntent.SetCurrentLocation>(intent)
        assertEquals(null, loc.stationId)
    }

    // MARK: Route preference

    @Test
    fun im_at_x_go_y_fast_is_fastest_trip() {
        val intent = parser.parse("im at monastiraki go airport fast")
        val trip = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M1_MON", trip.fromStationId)
        assertEquals("M3_AER", trip.toStationId)
        assertEquals(RoutePreference.FASTEST, trip.preference)
    }

    @Test
    fun albanian_shpejt_is_fastest_trip() {
        val intent = parser.parse("jam te syntagma dua aeroport shpejt")
        val trip = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M2_SYN", trip.fromStationId)
        assertEquals("M3_AER", trip.toStationId)
        assertEquals(RoutePreference.FASTEST, trip.preference)
    }

    @Test
    fun easiest_route_is_fewest_changes() {
        val intent = parser.parse("give me the easiest route from Piraeus to Syntagma")
        val trip = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals(RoutePreference.FEWEST_CHANGES, trip.preference)
    }

    @Test
    fun how_do_i_go_to_dest_faster_asks_origin_and_keeps_preference() {
        // Single destination via a plan cue: origin unknown, preference kept.
        val intent = parser.parse("how do I go to the airport faster")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        val base = assertIs<AssistantIntent.PlanTrip>(clar.base)
        assertEquals("M3_AER", base.toStationId)
        assertEquals(null, base.fromStationId)
        assertEquals(RoutePreference.FASTEST, base.preference)
        assertEquals(MissingSlot.ORIGIN_STATION, clar.missing)
    }
}
