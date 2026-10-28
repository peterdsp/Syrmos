package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors `fixtures/calendar/leave-by.json` case for case (shared with Swift
 * `CalendarLeaveBy`). commonTest cannot read repo files, so the fixture is inlined;
 * regenerate it from the JSON whenever the generator changes.
 */
class CalendarLeaveByTest {

    @Test
    fun foldCases_matchFixture() {
        assertEquals("πτηση", CalendarLeaveBy.fold("Πτήση"))
        assertEquals("αεροδρομιο", CalendarLeaveBy.fold("ΑΕΡΟΔΡΌΜΙΟ"))
        assertEquals("καραισκακησ", CalendarLeaveBy.fold("Καραϊσκάκης"))
        assertEquals("tirane", CalendarLeaveBy.fold("Tiranë"))
        assertEquals("ηρωδειο", CalendarLeaveBy.fold("Ηρώδειο"))
    }

    @Test
    fun classifyCases_matchFixture() {
        assertEquals("flight", CalendarLeaveBy.classify("Flight to London", "").key, "Flight to London / ")
        assertEquals("flight", CalendarLeaveBy.classify("Πτήση Μιλάνο", "").key, "Πτήση Μιλάνο / ")
        assertEquals("flight", CalendarLeaveBy.classify("Volo per Roma", "").key, "Volo per Roma / ")
        assertEquals("flight", CalendarLeaveBy.classify("Fluturim Tiranë", "").key, "Fluturim Tiranë / ")
        assertEquals("flight", CalendarLeaveBy.classify("Trip", "Athens International Airport El. Venizelos").key, "Trip / Athens International Airport El. Venizelos")
        assertEquals("flight", CalendarLeaveBy.classify("A3 602", "ATH").key, "A3 602 / ATH")
        assertEquals("default", CalendarLeaveBy.classify("Meeting in Athens", "Syntagma").key, "Meeting in Athens / Syntagma")
        assertEquals("longDistance", CalendarLeaveBy.classify("Ferry to Naxos", "Piraeus").key, "Ferry to Naxos / Piraeus")
        assertEquals("longDistance", CalendarLeaveBy.classify("Πλοίο Πάρος", "Λιμάνι Ραφήνας").key, "Πλοίο Πάρος / Λιμάνι Ραφήνας")
        assertEquals("longDistance", CalendarLeaveBy.classify("Train to Thessaloniki", "OSE Larissa").key, "Train to Thessaloniki / OSE Larissa")
        assertEquals("venue", CalendarLeaveBy.classify("Συναυλία", "ΟΑΚΑ").key, "Συναυλία / ΟΑΚΑ")
        assertEquals("venue", CalendarLeaveBy.classify("Olympiacos", "Karaiskakis Stadium").key, "Olympiacos / Karaiskakis Stadium")
        assertEquals("venue", CalendarLeaveBy.classify("Θέατρο", "Ηρώδειο").key, "Θέατρο / Ηρώδειο")
        assertEquals("appointment", CalendarLeaveBy.classify("Dr. Papadopoulos", "").key, "Dr. Papadopoulos / ")
        assertEquals("appointment", CalendarLeaveBy.classify("Οδοντίατρος", "").key, "Οδοντίατρος / ")
        assertEquals("appointment", CalendarLeaveBy.classify("ΚΕΠ ραντεβού", "").key, "ΚΕΠ ραντεβού / ")
        assertEquals("appointment", CalendarLeaveBy.classify("Job interview", "Marousi").key, "Job interview / Marousi")
        assertEquals("default", CalendarLeaveBy.classify("Dinner with Maria", "Psyrri").key, "Dinner with Maria / Psyrri")
        assertEquals("default", CalendarLeaveBy.classify("Skgames meetup", "").key, "Skgames meetup / ")
        assertEquals("default", CalendarLeaveBy.classify("Drinks", "Gazi").key, "Drinks / Gazi")
    }

    private fun checkPlan(id: String, input: CalendarPlanInput, now: Long, expected: CalendarPlan) {
        assertEquals(expected, CalendarLeaveBy.plan(input, now), id)
    }

    @Test
    fun planCases_matchFixture() {
        checkPlan("default_event", CalendarPlanInput(title = "Dinner", location = "Psyrri", startEpochSeconds = 1793188800L, journeyMinutes = 30, transfers = 0, firstLegHeadwayMinutes = 6, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "default" }, 5, 3, 1793188500L, 1793186520L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793185620L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793186520L))))
        checkPlan("flight_default_120", CalendarPlanInput(title = "Flight to Paris", location = "ATH", startEpochSeconds = 1793188800L, journeyMinutes = 45, transfers = 1, firstLegHeadwayMinutes = 36, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "flight" }, 120, 20, 1793181600L, 1793177700L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793176800L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793177700L))))
        checkPlan("flight_buffer_150", CalendarPlanInput(title = "Flight to Paris", location = "ATH", startEpochSeconds = 1793188800L, journeyMinutes = 45, transfers = 0, firstLegHeadwayMinutes = 36, raining = false, alertOnRoute = false, flightBufferMinutes = 150), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "flight" }, 150, 18, 1793179800L, 1793176020L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793175120L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793176020L))))
        checkPlan("flight_buffer_invalid_falls_back", CalendarPlanInput(title = "Flight", location = "Psyrri", startEpochSeconds = 1793188800L, journeyMinutes = 45, transfers = 0, firstLegHeadwayMinutes = 10, raining = false, alertOnRoute = false, flightBufferMinutes = 100), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "flight" }, 120, 5, 1793181600L, 1793178600L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "flightChecklist" }, 1793167800L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793177700L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793178600L))))
        checkPlan("venue_rain_alert_capped", CalendarPlanInput(title = "Concert", location = "OAKA", startEpochSeconds = 1793188800L, journeyMinutes = 30, transfers = 3, firstLegHeadwayMinutes = 10, raining = true, alertOnRoute = true, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "venue" }, 30, 20, 1793187000L, 1793184000L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793183100L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793184000L))))
        checkPlan("appointment", CalendarPlanInput(title = "Doctor", location = "Psyrri", startEpochSeconds = 1793188800L, journeyMinutes = 20, transfers = 0, firstLegHeadwayMinutes = 4, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "appointment" }, 15, 2, 1793187900L, 1793186580L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "headsUp" }, 1793185680L), CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793186580L))))
        checkPlan("heads_up_already_passed", CalendarPlanInput(title = "Dinner", location = "Psyrri", startEpochSeconds = 1793169300L, journeyMinutes = 20, transfers = 0, firstLegHeadwayMinutes = 4, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "default" }, 5, 2, 1793169000L, 1793167680L, CalendarPlanState.entries.first { it.key == "scheduled" }, listOf(CalendarNotification(CalendarNotificationKind.entries.first { it.key == "leaveNow" }, 1793167680L))))
        checkPlan("leave_now_window", CalendarPlanInput(title = "Dinner", location = "Psyrri", startEpochSeconds = 1793168700L, journeyMinutes = 20, transfers = 0, firstLegHeadwayMinutes = 4, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "default" }, 5, 2, 1793168400L, 1793167080L, CalendarPlanState.entries.first { it.key == "leaveNow" }, listOf()))
        checkPlan("late", CalendarPlanInput(title = "Dinner", location = "Psyrri", startEpochSeconds = 1793167380L, journeyMinutes = 20, transfers = 0, firstLegHeadwayMinutes = 4, raining = false, alertOnRoute = false, flightBufferMinutes = null), 1793167200L,
            CalendarPlan(CalendarEventKind.entries.first { it.key == "default" }, 5, 2, 1793167080L, 1793165760L, CalendarPlanState.entries.first { it.key == "late" }, listOf()))
    }

    @Test
    fun eligibilityCases_matchFixture() {
        assertEquals("eligible", CalendarLeaveBy.eligibility(1793170800L, 1793167200L, allDay = false, declined = false, destinationResolved = true).key, "eligible")
        assertEquals("allDay", CalendarLeaveBy.eligibility(1793170800L, 1793167200L, allDay = true, declined = false, destinationResolved = true).key, "all_day")
        assertEquals("declined", CalendarLeaveBy.eligibility(1793170800L, 1793167200L, allDay = false, declined = true, destinationResolved = true).key, "declined")
        assertEquals("past", CalendarLeaveBy.eligibility(1793167200L, 1793167200L, allDay = false, declined = false, destinationResolved = true).key, "past")
        assertEquals("eligible", CalendarLeaveBy.eligibility(1793340000L, 1793167200L, allDay = false, declined = false, destinationResolved = true).key, "horizon_edge_ok")
        assertEquals("beyondHorizon", CalendarLeaveBy.eligibility(1793340001L, 1793167200L, allDay = false, declined = false, destinationResolved = true).key, "beyond_horizon")
        assertEquals("unresolved", CalendarLeaveBy.eligibility(1793170800L, 1793167200L, allDay = false, declined = false, destinationResolved = false).key, "unresolved")
    }

    @Test
    fun rescheduleCases_matchFixture() {
        assertEquals(false, CalendarLeaveBy.needsReschedule(1793167200L, 1793167499L))
        assertEquals(true, CalendarLeaveBy.needsReschedule(1793167200L, 1793167500L))
        assertEquals(true, CalendarLeaveBy.needsReschedule(1793167200L, 1793166600L))
    }

    @Test
    fun airportMatcher_doesNotTreatAthensAsTheAirportCode() {
        assertFalse(CalendarLeaveBy.isAirportTrip("Meeting in Athens", "Syntagma"))
        assertTrue(CalendarLeaveBy.isAirportTrip("A3 602", "ATH"))
        assertTrue(CalendarLeaveBy.isAirportTrip("Πτήση", ""))
    }

    @Test
    fun copy_coversEveryKindAndLanguage() {
        for (lang in AppLanguage.entries) for (n in CalendarNotificationKind.entries) for (k in CalendarEventKind.entries) {
            val t = CalendarLeaveByCopy.body(n, k, lang, mapOf("title" to "X", "line" to "M3", "station" to "Nikaia", "next" to "4", "buffer" to "120", "fare" to ""))
            assertTrue(t.isNotBlank() && !t.contains("{"), "$lang $n $k")
        }
        assertEquals(
            "Ώρα να φύγεις! Η πτήση δεν περιμένει, το M3 όμως έρχεται σε 4'.",
            CalendarLeaveByCopy.body(CalendarNotificationKind.LEAVE_NOW, CalendarEventKind.FLIGHT, AppLanguage.GREEK, mapOf("line" to "M3", "next" to "4")),
        )
    }
}
