package com.syrmos.core.common.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val athensTimeZone = TimeZone.of("Europe/Athens")

fun currentAthensTime(): LocalTime {
    val now = Clock.System.now()
    return now.toLocalDateTime(athensTimeZone).time
}

fun currentAthensDayOfWeek(): kotlinx.datetime.DayOfWeek {
    val now = Clock.System.now()
    return now.toLocalDateTime(athensTimeZone).dayOfWeek
}

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

fun LocalTime.toDisplayString(): String {
    val h = hour.toString().padStart(2, '0')
    val m = minute.toString().padStart(2, '0')
    return "$h:$m"
}
