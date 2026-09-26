package com.syrmos.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// The IANA tz database is the single source of DST truth, shared with the band
// projector (ComputeDeparturesFromBandsUseCase also resolves this zone). It
// replaces a hand-rolled last-Sunday-of-March/October calculation that risked
// drift around the transition hour and was a second, divergent mechanism.
// Available on every target the app builds (the web/wasmJs build already
// resolves this same zone).
private val athensZone = TimeZone.of("Europe/Athens")

private fun athensDateTime(clock: Clock): LocalDateTime =
    clock.now().toLocalDateTime(athensZone)

// All three read the clock through an injectable [Clock] that defaults to
// Clock.System, so production is unchanged but tests can pin "now" to a fixed
// instant and assert time-dependent behaviour (last train, seasonal weather)
// deterministically instead of relying on the wall clock.
fun currentAthensTime(clock: Clock = Clock.System): LocalTime =
    athensDateTime(clock).time

fun currentAthensDayOfWeek(clock: Clock = Clock.System): DayOfWeek =
    athensDateTime(clock).dayOfWeek

fun currentAthensDate(clock: Clock = Clock.System): LocalDate =
    athensDateTime(clock).date

fun parseTime(timeString: String): LocalTime {
    // Defensive: a string with no ":", a non-numeric part, an hour >= 48, or a
    // minute >= 60 previously threw (IndexOutOfBounds / NumberFormat / LocalTime
    // range). Three callers (GetNextDeparturesUseCase, HomeScreen, MapViewModel)
    // pass schedule times unguarded, so a single malformed entry would crash the
    // screen. Degrade gracefully instead — wrap past-midnight hours (25:10 ->
    // 01:10, unchanged for valid input) and clamp the rest.
    val parts = timeString.split(":")
    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    val normalizedHour = ((hour % 24) + 24) % 24
    val normalizedMinute = minute.coerceIn(0, 59)
    return LocalTime(normalizedHour, normalizedMinute)
}

fun LocalTime.minutesUntil(other: LocalTime): Int {
    val thisMinutes = this.hour * 60 + this.minute
    val otherMinutes = other.hour * 60 + other.minute
    val diff = otherMinutes - thisMinutes
    return if (diff >= 0) diff else diff + 24 * 60
}

/**
 * Seconds from this time until [other], for a live countdown. A departure that
 * has just passed (within a minute) is the train at the platform, not tomorrow's
 * service, so it reads as 0 rather than wrapping to 23h 59min; only a time more
 * than a minute in the past wraps to the next day. Mirrors iOS
 * `Departure.secondsAway(from:)`.
 */
fun LocalTime.secondsUntil(other: LocalTime): Int {
    val thisSecs = this.hour * 3600 + this.minute * 60 + this.second
    val otherSecs = other.hour * 3600 + other.minute * 60 + other.second
    var diff = otherSecs - thisSecs
    if (diff < -60) diff += 24 * 3600
    return maxOf(diff, 0)
}

fun LocalTime.toDisplayString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
}

/**
 * A feed timestamp as an Athens HH:MM clock for display. Accepts an ISO-8601
 * instant ("2026-09-26T10:14:00.000Z", with or without an offset) and a bare
 * "HH:MM" or "HH:MM:SS"; anything else is returned trimmed and unchanged, so
 * a card never shows an empty slot for a value the operator did send.
 */
fun athensClockLabel(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    runCatching { Instant.parse(text) }.getOrNull()?.let { instant ->
        val t = instant.toLocalDateTime(TimeZone.of("Europe/Athens")).time
        return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
    }
    val hm = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?$").find(text)
    if (hm != null) return hm.groupValues[1].padStart(2, '0') + ":" + hm.groupValues[2]
    return text
}
