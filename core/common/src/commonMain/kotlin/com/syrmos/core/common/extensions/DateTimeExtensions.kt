package com.syrmos.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Lazy because wasmJs lacks the full IANA tzdb at class-load time and would
// throw IllegalTimeZoneException eagerly, killing tests that don't touch tz.
private val athensTimeZone by lazy { TimeZone.of("Europe/Athens") }

// All three read the clock through an injectable [Clock] that defaults to
// Clock.System, so production is unchanged but tests can pin "now" to a fixed
// instant and assert time-dependent behaviour (last train, seasonal weather)
// deterministically instead of relying on the wall clock.
fun currentAthensTime(clock: Clock = Clock.System): LocalTime =
    clock.now().toLocalDateTime(athensTimeZone).time

fun currentAthensDayOfWeek(clock: Clock = Clock.System): DayOfWeek =
    clock.now().toLocalDateTime(athensTimeZone).dayOfWeek

fun currentAthensDate(clock: Clock = Clock.System): LocalDate =
    clock.now().toLocalDateTime(athensTimeZone).date

fun parseTime(timeString: String): LocalTime {
    val parts = timeString.split(":")
    val hour = parts[0].toInt()
    val minute = parts[1].toInt()
    val normalizedHour = if (hour >= 24) hour - 24 else hour
    return LocalTime(normalizedHour, minute)
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
