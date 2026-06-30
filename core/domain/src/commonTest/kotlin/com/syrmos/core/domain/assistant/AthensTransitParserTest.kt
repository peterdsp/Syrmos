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
}
