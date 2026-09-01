package com.syrmos.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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

fun LocalTime.secondsUntil(other: LocalTime): Int {
    val thisSecs = this.hour * 3600 + this.minute * 60 + this.second
    val otherSecs = other.hour * 3600 + other.minute * 60 + other.second
    val diff = otherSecs - thisSecs
    return if (diff >= 0) diff else diff + 24 * 3600
}

fun LocalTime.toDisplayString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
}
