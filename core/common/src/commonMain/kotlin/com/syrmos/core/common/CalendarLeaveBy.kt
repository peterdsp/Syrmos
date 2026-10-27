package com.syrmos.core.common

import kotlin.math.abs

/**
 * Syrmos 3.0.2: calendar leave-by for every event with a location.
 *
 * Classifies an event (flight, long-distance, venue, appointment, default) from
 * its title and location, picks an arrival buffer, adds a reliability margin and
 * the planned journey time, and returns when to leave plus which notifications to
 * schedule. Pure and offline; mirrored by Swift `CalendarLeaveBy.swift` and
 * validated case for case against `fixtures/calendar/leave-by.json`.
 *
 * The classifier is also the shared airport matcher: a whole-token "ath" matches
 * the airport code, while "Athens" does not.
 */
enum class CalendarEventKind(val key: String) {
    FLIGHT("flight"),
    LONG_DISTANCE("longDistance"),
    VENUE("venue"),
    APPOINTMENT("appointment"),
    DEFAULT("default"),
}

enum class CalendarPlanState(val key: String) { SCHEDULED("scheduled"), LEAVE_NOW("leaveNow"), LATE("late"), PAST("past") }

enum class CalendarNotificationKind(val key: String) {
    FLIGHT_CHECKLIST("flightChecklist"),
    HEADS_UP("headsUp"),
    LEAVE_NOW("leaveNow"),
}

enum class CalendarEligibility(val key: String) {
    ELIGIBLE("eligible"),
    ALL_DAY("allDay"),
    DECLINED("declined"),
    PAST("past"),
    BEYOND_HORIZON("beyondHorizon"),
    UNRESOLVED("unresolved"),
}

data class CalendarPlanInput(
    val title: String,
    val location: String,
    val startEpochSeconds: Long,
    val journeyMinutes: Int,
    val transfers: Int,
    val firstLegHeadwayMinutes: Int,
    val raining: Boolean = false,
    val alertOnRoute: Boolean = false,
    val flightBufferMinutes: Int? = null,
)

data class CalendarNotification(val kind: CalendarNotificationKind, val atEpochSeconds: Long)

data class CalendarPlan(
    val kind: CalendarEventKind,
    val bufferMinutes: Int,
    val marginMinutes: Int,
    val arriveByEpochSeconds: Long,
    val leaveAtEpochSeconds: Long,
    val state: CalendarPlanState,
    val notifications: List<CalendarNotification>,
)

object CalendarLeaveBy {
    const val MAX_MARGIN = 20
    const val HEADS_UP_MINUTES = 15
    const val FLIGHT_CHECKLIST_MINUTES = 180
    const val HORIZON_SECONDS: Long = 48 * 3600
    const val RESCHEDULE_THRESHOLD_SECONDS: Long = 300
    val FLIGHT_BUFFER_CHOICES = listOf(90, 120, 150)
    const val DEFAULT_FLIGHT_BUFFER = 120

    private val BUFFER_MINUTES = mapOf(
        CalendarEventKind.FLIGHT to 120,
        CalendarEventKind.LONG_DISTANCE to 25,
        CalendarEventKind.VENUE to 30,
        CalendarEventKind.APPOINTMENT to 15,
        CalendarEventKind.DEFAULT to 5,
    )

    private val FOLD = mapOf(
        'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ό' to 'ο', 'ύ' to 'υ', 'ώ' to 'ω',
        'ϊ' to 'ι', 'ΐ' to 'ι', 'ϋ' to 'υ', 'ΰ' to 'υ', 'ς' to 'σ',
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'ù' to 'u', 'ú' to 'u',
        'û' to 'u', 'ç' to 'c',
    )

    /** Substring keywords (already folded) then whole-token keywords, in class order. */
    private val CLASSES: List<Triple<CalendarEventKind, List<String>, List<String>>> = listOf(
        Triple(
            CalendarEventKind.FLIGHT,
            listOf("airport", "flight", "αεροδρομιο", "πτηση", "aeroporto", "volo", "fluturim", "aeroport", "venizelos", "βενιζελοσ", "makedonia airport"),
            listOf("ath", "skg"),
        ),
        Triple(
            CalendarEventKind.LONG_DISTANCE,
            listOf("ferry", "πλοιο", "λιμανι", "port of piraeus", "rafina", "ραφηνα", "lavrio", "λαυριο", "intercity", "hellenic train", "τρενο", "κτελ", "ktel", "σταθμοσ λαρισησ", "larissa station"),
            listOf("ose", "οσε"),
        ),
        Triple(
            CalendarEventKind.VENUE,
            listOf("οακα", "oaka", "stadium", "γηπεδο", "karaiskakis", "καραισκακη", "concert", "συναυλια", "theatre", "theater", "θεατρο", "megaron", "μεγαρο", "badminton", "cinema", "σινεμα", "ηρωδειο", "herodion", "festival", "φεστιβαλ"),
            emptyList(),
        ),
        Triple(
            CalendarEventKind.APPOINTMENT,
            listOf("doctor", "γιατρ", "hospital", "νοσοκομ", "clinic", "κλινικ", "exam", "εξετασ", "interview", "συνεντευξ", "dentist", "οδοντ", "embassy", "πρεσβεια"),
            listOf("dr", "kep", "κεπ"),
        ),
    )

    private val TOKEN_SPLIT = Regex("[^0-9a-zα-ω]+")

    fun fold(text: String): String =
        text.lowercase().map { FOLD[it] ?: it }.joinToString("")

    fun classify(title: String, location: String): CalendarEventKind {
        val text = fold("$title $location")
        val tokens = text.split(TOKEN_SPLIT).filter { it.isNotEmpty() }.toSet()
        for ((kind, subs, toks) in CLASSES) {
            if (subs.any { it in text } || toks.any { it in tokens }) return kind
        }
        return CalendarEventKind.DEFAULT
    }

    /** True when the shared classifier calls this an airport trip. */
    fun isAirportTrip(title: String, location: String): Boolean =
        classify(title, location) == CalendarEventKind.FLIGHT

    fun bufferMinutes(kind: CalendarEventKind, flightBufferMinutes: Int? = null): Int {
        if (kind == CalendarEventKind.FLIGHT && flightBufferMinutes in FLIGHT_BUFFER_CHOICES) return flightBufferMinutes!!
        return BUFFER_MINUTES.getValue(kind)
    }

    fun reliabilityMargin(transfers: Int, firstLegHeadway: Int, raining: Boolean, alertOnRoute: Boolean): Int {
        // ceil(headway / 2) for non-negative ints.
        val m = transfers * 2 + (firstLegHeadway + 1) / 2 + (if (raining) 5 else 0) + (if (alertOnRoute) 5 else 0)
        return minOf(MAX_MARGIN, m)
    }

    fun eligibility(
        startEpochSeconds: Long,
        now: Long,
        allDay: Boolean = false,
        declined: Boolean = false,
        destinationResolved: Boolean = true,
    ): CalendarEligibility = when {
        allDay -> CalendarEligibility.ALL_DAY
        declined -> CalendarEligibility.DECLINED
        startEpochSeconds <= now -> CalendarEligibility.PAST
        startEpochSeconds > now + HORIZON_SECONDS -> CalendarEligibility.BEYOND_HORIZON
        !destinationResolved -> CalendarEligibility.UNRESOLVED
        else -> CalendarEligibility.ELIGIBLE
    }

    fun plan(e: CalendarPlanInput, now: Long): CalendarPlan {
        val kind = classify(e.title, e.location)
        val buf = bufferMinutes(kind, e.flightBufferMinutes)
        val margin = reliabilityMargin(e.transfers, e.firstLegHeadwayMinutes, e.raining, e.alertOnRoute)
        val arriveBy = e.startEpochSeconds - buf * 60L
        val leaveAt = arriveBy - (e.journeyMinutes + margin) * 60L
        val state = when {
            now < leaveAt -> CalendarPlanState.SCHEDULED
            now < arriveBy -> CalendarPlanState.LEAVE_NOW
            now < e.startEpochSeconds -> CalendarPlanState.LATE
            else -> CalendarPlanState.PAST
        }
        val fires = buildList {
            val checklist = leaveAt - FLIGHT_CHECKLIST_MINUTES * 60L
            if (kind == CalendarEventKind.FLIGHT && checklist > now) {
                add(CalendarNotification(CalendarNotificationKind.FLIGHT_CHECKLIST, checklist))
            }
            val headsUp = leaveAt - HEADS_UP_MINUTES * 60L
            if (headsUp > now) add(CalendarNotification(CalendarNotificationKind.HEADS_UP, headsUp))
            if (leaveAt > now) add(CalendarNotification(CalendarNotificationKind.LEAVE_NOW, leaveAt))
        }
        return CalendarPlan(kind, buf, margin, arriveBy, leaveAt, state, fires)
    }

    fun needsReschedule(oldLeaveAt: Long, newLeaveAt: Long): Boolean =
        abs(newLeaveAt - oldLeaveAt) >= RESCHEDULE_THRESHOLD_SECONDS
}
