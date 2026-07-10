package com.syrmos.core.domain.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins Ariadne's offline parser across all three supported languages. The
 * parser must only ever emit an approved [AssistantIntent], decline anything
 * outside Athens transit, and ask for a missing slot instead of guessing.
 */
class AthensTransitParserTest {

    private val vocab = AssistantVocabulary(
        stations = listOf(
            StationVocab("M2_SYN", listOf("Syntagma", "Σύνταγμα", "Sintagma"), listOf("M2", "M3")),
            StationVocab("M1_PIR", listOf("Piraeus", "Πειραιάς", "Pireas"), listOf("M1", "A1")),
            StationVocab("M3_AER", listOf("Airport", "Αεροδρόμιο", "Aeroporti"), listOf("M3", "A1")),
            StationVocab("M1_MON", listOf("Monastiraki", "Μοναστηράκι"), listOf("M1", "M3")),
        ),
        lines = listOf(
            LineVocab("M1", listOf("M1", "line 1", "γραμμή 1", "linja 1")),
            LineVocab("M2", listOf("M2", "line 2", "γραμμή 2", "metro 2", "linja 2")),
            LineVocab("M3", listOf("M3", "line 3", "γραμμή 3")),
            LineVocab("A1", listOf("A1", "airport line")),
        ),
    )
    private val parser = AthensTransitParser(vocab)

    @Test
    fun departures_from_named_station() {
        val intent = parser.parse("next trains from Syntagma")
        assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M2_SYN", intent.stationId)
    }

    @Test
    fun departures_for_bare_line_does_not_need_clarification() {
        val intent = parser.parse("next M2 train")
        assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M2", intent.lineId)
    }

    @Test
    fun departures_with_neither_station_nor_line_asks_for_station() {
        val intent = parser.parse("when is the next train")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertEquals(MissingSlot.STATION, clar.missing)
    }

    @Test
    fun weekend_day_context_is_parsed() {
        val intent = parser.parse("show me M3 trains from Syntagma this weekend")
        val dep = assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals(DayContext.WEEKEND, dep.day)
    }

    @Test
    fun last_train_with_station() {
        val intent = parser.parse("when is the last train from Piraeus")
        val last = assertIs<AssistantIntent.LastTrain>(intent)
        assertEquals("M1_PIR", last.stationId)
    }

    @Test
    fun last_train_without_station_asks_for_station() {
        val intent = parser.parse("last train home")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.LastTrain>(clar.base)
        assertEquals(MissingSlot.STATION, clar.missing)
    }

    @Test
    fun plan_trip_orders_endpoints_by_sentence_position() {
        val intent = parser.parse("how do I get from Piraeus to Syntagma")
        val plan = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M1_PIR", plan.fromStationId)
        assertEquals("M2_SYN", plan.toStationId)
    }

    @Test
    fun plan_trip_reversed_endpoints() {
        val intent = parser.parse("from Syntagma to Piraeus please")
        val plan = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M2_SYN", plan.fromStationId)
        assertEquals("M1_PIR", plan.toStationId)
    }

    @Test
    fun rain_routes_with_low_exposure_and_asks_for_origin() {
        val intent = parser.parse("it's raining, get me to the Airport")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        val plan = assertIs<AssistantIntent.PlanTrip>(clar.base)
        assertEquals("M3_AER", plan.toStationId)
        assertTrue(plan.lowExposure)
        assertEquals(MissingSlot.ORIGIN_STATION, clar.missing)
    }

    @Test
    fun alerts_query() {
        assertIs<AssistantIntent.ShowAlerts>(parser.parse("any service alerts today?"))
    }

    @Test
    fun map_query() {
        val intent = parser.parse("show Monastiraki on the map")
        val map = assertIs<AssistantIntent.OpenMap>(intent)
        assertEquals("M1_MON", map.stationId)
    }

    @Test
    fun explain_bare_line() {
        assertIs<AssistantIntent.ExplainLine>(parser.parse("tell me about line 1"))
    }

    @Test
    fun help_query() {
        assertIs<AssistantIntent.Help>(parser.parse("what can you do?"))
    }

    @Test
    fun fare_query_airport() {
        val intent = parser.parse("how much is a ticket to the Airport")
        val fare = assertIs<AssistantIntent.ExplainFare>(intent)
        assertTrue(fare.airport)
    }

    @Test
    fun fare_query_standard() {
        val intent = parser.parse("what's the ticket price")
        val fare = assertIs<AssistantIntent.ExplainFare>(intent)
        assertEquals(false, fare.airport)
    }

    @Test
    fun greek_fare_query() {
        assertIs<AssistantIntent.ExplainFare>(parser.parse("πόσο κάνει το εισιτήριο"))
    }

    @Test
    fun favorite_station() {
        val intent = parser.parse("favorite Syntagma")
        val fav = assertIs<AssistantIntent.ToggleFavorite>(intent)
        assertEquals("M2_SYN", fav.stationId)
    }

    @Test
    fun favorite_without_station_asks_for_station() {
        val intent = parser.parse("save this station")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.ToggleFavorite>(clar.base)
        assertEquals(MissingSlot.STATION, clar.missing)
    }

    @Test
    fun out_of_scope_weather_elsewhere() {
        assertIs<AssistantIntent.OutOfScope>(parser.parse("what's the weather in London"))
    }

    @Test
    fun out_of_scope_general_knowledge() {
        assertIs<AssistantIntent.OutOfScope>(parser.parse("who won the election"))
    }

    // Travel time / ETA

    @Test
    fun travel_time_to_single_station_defaults_origin_to_location() {
        val intent = parser.parse("how long to the Airport")
        val eta = assertIs<AssistantIntent.TravelTime>(intent)
        assertEquals("M3_AER", eta.toStationId)
        assertEquals(null, eta.fromStationId)
    }

    @Test
    fun travel_time_with_explicit_origin_and_destination() {
        val intent = parser.parse("how many minutes from Piraeus to Syntagma")
        val eta = assertIs<AssistantIntent.TravelTime>(intent)
        assertEquals("M1_PIR", eta.fromStationId)
        assertEquals("M2_SYN", eta.toStationId)
    }

    @Test
    fun travel_time_without_destination_asks_for_destination() {
        val intent = parser.parse("how long does it take")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.TravelTime>(clar.base)
        assertEquals(MissingSlot.DESTINATION_STATION, clar.missing)
    }

    // Fuzzy / typo tolerance

    @Test
    fun fuzzy_typo_resolves_station() {
        val intent = parser.parse("next trains from Sintagna")
        val dep = assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M2_SYN", dep.stationId)
    }

    @Test
    fun fuzzy_bare_typo_is_departures() {
        val intent = parser.parse("Monastraki")
        val dep = assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M1_MON", dep.stationId)
    }

    @Test
    fun fuzzy_does_not_match_gibberish() {
        assertIs<AssistantIntent.OutOfScope>(parser.parse("qwertyuiop"))
    }

    @Test
    fun clean_query_is_not_overridden_by_fuzzy() {
        val intent = parser.parse("Piraeus")
        val dep = assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M1_PIR", dep.stationId)
    }

    // Greek

    @Test
    fun greek_departures() {
        val intent = parser.parse("επόμενα δρομολόγια από Σύνταγμα")
        val dep = assertIs<AssistantIntent.ShowDepartures>(intent)
        assertEquals("M2_SYN", dep.stationId)
    }

    @Test
    fun greek_last_train() {
        val intent = parser.parse("τελευταίο τρένο από Πειραιάς")
        assertIs<AssistantIntent.LastTrain>(intent)
    }

    // Albanian

    @Test
    fun albanian_plan_trip() {
        val intent = parser.parse("si shkoj nga Pireas te Sintagma")
        val plan = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M1_PIR", plan.fromStationId)
        assertEquals("M2_SYN", plan.toStationId)
    }

    // --- Expanded trilingual cue coverage (cleverer rule parser) ---

    @Test
    fun final_train_english_synonym() {
        assertIs<AssistantIntent.LastTrain>(parser.parse("final train from Syntagma"))
    }

    @Test
    fun last_departure_albanian_synonym() {
        assertIs<AssistantIntent.LastTrain>(parser.parse("nisja e fundit nga Pireas"))
    }

    @Test
    fun take_me_to_is_plan_trip() {
        assertIs<AssistantIntent.PlanTrip>(parser.parse("take me to the Airport from Monastiraki"))
    }

    @Test
    fun fastest_way_greek_is_plan_trip() {
        assertIs<AssistantIntent.PlanTrip>(parser.parse("ποιος ειναι ο καλυτερος τροπος απο Μοναστηρακι στο Αεροδρομιο"))
    }

    @Test
    fun ticket_price_greek_is_fare() {
        assertIs<AssistantIntent.ExplainFare>(parser.parse("τιμη εισιτηριου για το αεροδρομιο"))
    }

    @Test
    fun what_is_ariadne_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("what is ariadne"))
    }

    @Test
    fun cfare_eshte_ariadne_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("cfare eshte ariadne"))
    }

    @Test
    fun greeting_hello_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("hello"))
    }

    @Test
    fun who_are_u_shorthand_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("who are u"))
    }

    @Test
    fun greek_greeting_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("γεια σου"))
    }

    @Test
    fun albanian_greeting_is_help() {
        assertIs<AssistantIntent.Help>(parser.parse("pershendetje"))
    }
}
