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

    // MARK: - First train (new capability)

    @Test
    fun first_train_with_station() {
        val intent = parser.parse("when is the first train from Piraeus")
        val first = assertIs<AssistantIntent.FirstTrain>(intent)
        assertEquals("M1_PIR", first.stationId)
    }

    @Test
    fun first_train_greek() {
        val intent = parser.parse("πρώτο τρένο από το Σύνταγμα")
        val first = assertIs<AssistantIntent.FirstTrain>(intent)
        assertEquals("M2_SYN", first.stationId)
    }

    @Test
    fun first_train_albanian() {
        val intent = parser.parse("treni i parë nga Pireas")
        val first = assertIs<AssistantIntent.FirstTrain>(intent)
        assertEquals("M1_PIR", first.stationId)
    }

    @Test
    fun first_train_bare_line_answers_from_origin() {
        val intent = parser.parse("first M2 train")
        val first = assertIs<AssistantIntent.FirstTrain>(intent)
        assertEquals("M2", first.lineId)
    }

    @Test
    fun first_train_not_confused_with_last() {
        assertIs<AssistantIntent.FirstTrain>(parser.parse("first train Syntagma"))
        assertIs<AssistantIntent.LastTrain>(parser.parse("last train Syntagma"))
    }

    // MARK: - Accessibility (new capability)

    @Test
    fun accessibility_with_station() {
        val intent = parser.parse("is Syntagma wheelchair accessible")
        val acc = assertIs<AssistantIntent.StationAccessibility>(intent)
        assertEquals("M2_SYN", acc.stationId)
    }

    @Test
    fun accessibility_lift_phrasing() {
        val intent = parser.parse("does Piraeus have a lift")
        val acc = assertIs<AssistantIntent.StationAccessibility>(intent)
        assertEquals("M1_PIR", acc.stationId)
    }

    @Test
    fun accessibility_greek() {
        val intent = parser.parse("είναι προσβάσιμο για ΑμεΑ το Σύνταγμα")
        assertIs<AssistantIntent.StationAccessibility>(intent)
    }

    @Test
    fun accessibility_albanian() {
        val intent = parser.parse("a është Sintagma i aksesueshëm")
        val acc = assertIs<AssistantIntent.StationAccessibility>(intent)
        assertEquals("M2_SYN", acc.stationId)
    }

    @Test
    fun accessibility_without_station_asks() {
        val intent = parser.parse("is the station accessible")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.StationAccessibility>(clar.base)
    }

    // MARK: - Reverse trip follow-up (smarter conversations)

    @Test
    fun reverse_trip_and_back() {
        assertIs<AssistantIntent.ReverseTrip>(parser.parse("and back?"))
    }

    @Test
    fun reverse_trip_greek() {
        assertIs<AssistantIntent.ReverseTrip>(parser.parse("και πίσω;"))
    }

    @Test
    fun reverse_trip_albanian() {
        assertIs<AssistantIntent.ReverseTrip>(parser.parse("kthimi"))
    }

    @Test
    fun reverse_trip_dormant_when_stations_named() {
        // "X to Y and back" names two stations, so it plans X->Y instead of firing reverse.
        val intent = parser.parse("Syntagma to Piraeus and back")
        assertIs<AssistantIntent.PlanTrip>(intent)
    }

    // MARK: - Expanded phrasings

    @Test
    fun expanded_plan_phrasing_i_want_to_go() {
        val intent = parser.parse("I want to go from Syntagma to the airport")
        val plan = assertIs<AssistantIntent.PlanTrip>(intent)
        assertEquals("M2_SYN", plan.fromStationId)
        assertEquals("M3_AER", plan.toStationId)
    }

    @Test
    fun expanded_departure_phrasing_arrivals() {
        val intent = parser.parse("arrivals at Monastiraki")
        assertIs<AssistantIntent.ShowDepartures>(intent)
    }

    @Test
    fun day_probe_detects_tomorrow_trilingual() {
        assertEquals(DayContext.TOMORROW, parser.dayOf("what about tomorrow"))
        assertEquals(DayContext.TOMORROW, parser.dayOf("και αύριο;"))
        assertEquals(DayContext.WEEKEND, parser.dayOf("po fundjave"))
        assertEquals(DayContext.TODAY, parser.dayOf("Syntagma"))
    }

    // MARK: - Which lines (v2 capability)

    @Test
    fun which_lines_with_station() {
        val intent = parser.parse("which lines serve Syntagma")
        val wl = assertIs<AssistantIntent.WhichLines>(intent)
        assertEquals("M2_SYN", wl.stationId)
    }

    @Test
    fun which_lines_greek() {
        val intent = parser.parse("ποιες γραμμές περνάνε από το Σύνταγμα")
        assertIs<AssistantIntent.WhichLines>(intent)
    }

    @Test
    fun which_lines_albanian() {
        val intent = parser.parse("cilat linja shërbejnë Sintagma")
        val wl = assertIs<AssistantIntent.WhichLines>(intent)
        assertEquals("M2_SYN", wl.stationId)
    }

    @Test
    fun which_lines_without_station_asks() {
        val intent = parser.parse("which lines serve this station")
        val clar = assertIs<AssistantIntent.NeedsClarification>(intent)
        assertIs<AssistantIntent.WhichLines>(clar.base)
    }

    // MARK: - Stops between (v2 capability)

    @Test
    fun stops_between_two_stations() {
        val intent = parser.parse("how many stops from Piraeus to Syntagma")
        val sb = assertIs<AssistantIntent.StopsBetween>(intent)
        assertEquals("M1_PIR", sb.fromStationId)
        assertEquals("M2_SYN", sb.toStationId)
    }

    @Test
    fun stops_between_greek() {
        val intent = parser.parse("πόσες στάσεις από Πειραιάς μέχρι Σύνταγμα")
        val sb = assertIs<AssistantIntent.StopsBetween>(intent)
        assertEquals("M1_PIR", sb.fromStationId)
        assertEquals("M2_SYN", sb.toStationId)
    }

    @Test
    fun stops_between_not_read_as_plan() {
        // "how many stops A to B" must not fall through to a PlanTrip.
        assertIs<AssistantIntent.StopsBetween>(parser.parse("how many stops Piraeus to Syntagma"))
    }
}
