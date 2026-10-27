package com.syrmos.core.common

import kotlin.math.ceil

/**
 * Syrmos 3.0.2: the "next train near a station" coach.
 *
 * When the rider dwells within 1 km of a station, the client computes, per
 * (line, destination) direction, the next departures from the offline projector
 * and asks this engine which friendly case applies (relax, hurry, comfortable,
 * last train...). Pure and offline; mirrored by Swift `NearbyDepartureCoach.swift`
 * and validated case for case against `fixtures/nearby/coach.json`.
 *
 * The copy for each case lives in [NearbyCoachCopy]; it never tells anyone to run.
 */
enum class CoachCase(val key: String) {
    STATION_CLOSED("stationClosed"),
    LAST_TRAIN("lastTrain"),
    MISSED_LAST("missedLast"),
    RELAX_NEXT_ONE_IS_CLOSE("relaxNextOneIsClose"),
    HURRY("hurry"),
    LONG_GAP_TAKE_IT_EASY("longGapTakeItEasy"),
    COMFORTABLE("comfortable"),
    NO_SERVICE("noService");

    companion object {
        fun fromKey(key: String): CoachCase? = entries.firstOrNull { it.key == key }
    }
}

/** One upcoming departure: whole minutes from now (0 = now) and whether it is the last today. */
data class CoachDeparture(val minutes: Int, val isLast: Boolean = false)

data class CoachResult(
    val case: CoachCase,
    val nextMinutes: Int? = null,
    val followingMinutes: Int? = null,
    val waitMinutes: Int? = null,
    val marginMinutes: Int? = null,
    val tight: Boolean? = null,
)

data class CoachGuardInput(
    val enabled: Boolean,
    val goJourneyActive: Boolean,
    val athensHour: Int,
    val speedKmh: Double?,
    val lastNotifiedForStationEpochSeconds: Long?,
    val notificationsToday: Int,
    val now: Long,
)

enum class CoachGuard(val key: String) {
    ALLOW("allow"),
    DISABLED("disabled"),
    GO_ACTIVE("goActive"),
    QUIET_HOURS("quietHours"),
    MOVING("moving"),
    COOLDOWN("cooldown"),
    DAILY_CAP("dailyCap"),
}

object NearbyDepartureCoach {
    const val RELAX_MAX_WAIT = 8
    const val HURRY_MAX_MARGIN = 2
    const val COOLDOWN_SECONDS: Long = 45 * 60
    const val MAX_PER_DAY = 4
    const val MAX_SPEED_KMH = 25.0
    const val GEOFENCE_RADIUS_METERS = 1000f
    const val GEOFENCE_DWELL_MILLIS = 60_000
    const val MONITORED_STATIONS = 20

    private val HEADLINE_PRIORITY = listOf(
        CoachCase.STATION_CLOSED, CoachCase.LAST_TRAIN, CoachCase.MISSED_LAST,
        CoachCase.RELAX_NEXT_ONE_IS_CLOSE, CoachCase.HURRY, CoachCase.LONG_GAP_TAKE_IT_EASY,
        CoachCase.COMFORTABLE, CoachCase.NO_SERVICE,
    )

    /** max(1, ceil(distance * 1.3 detour / 80 m per minute)). */
    fun walkMinutes(distanceMeters: Double): Int =
        maxOf(1, ceil(distanceMeters * 1.3 / 80.0).toInt())

    /** [departures] ascending by minutes from now. */
    fun coachCase(walk: Int, departures: List<CoachDeparture>, stationClosed: Boolean): CoachResult {
        if (stationClosed) return CoachResult(CoachCase.STATION_CLOSED)
        if (departures.isEmpty()) return CoachResult(CoachCase.NO_SERVICE)
        val first = departures.first()
        val catchable = departures.filter { it.minutes >= walk }
        if (first.minutes < walk) {
            if (catchable.isEmpty()) {
                val case = if (first.isLast || departures.last().isLast) CoachCase.MISSED_LAST else CoachCase.NO_SERVICE
                return CoachResult(case, nextMinutes = first.minutes)
            }
            val next = catchable.first()
            if (next.isLast) {
                return CoachResult(
                    CoachCase.LAST_TRAIN, nextMinutes = next.minutes,
                    tight = next.minutes - walk <= HURRY_MAX_MARGIN,
                )
            }
            val wait = next.minutes - walk
            val case = if (wait <= RELAX_MAX_WAIT) CoachCase.RELAX_NEXT_ONE_IS_CLOSE else CoachCase.LONG_GAP_TAKE_IT_EASY
            return CoachResult(case, nextMinutes = first.minutes, followingMinutes = next.minutes, waitMinutes = wait)
        }
        val margin = first.minutes - walk
        if (first.isLast) {
            return CoachResult(CoachCase.LAST_TRAIN, nextMinutes = first.minutes, tight = margin <= HURRY_MAX_MARGIN)
        }
        if (margin <= HURRY_MAX_MARGIN) {
            return CoachResult(CoachCase.HURRY, nextMinutes = first.minutes, marginMinutes = margin)
        }
        return CoachResult(CoachCase.COMFORTABLE, nextMinutes = first.minutes, marginMinutes = margin)
    }

    /** Index of the direction whose case ranks first; ties keep the lowest index; empty is null. */
    fun headlineIndex(cases: List<CoachCase>): Int? {
        var best: Int? = null
        cases.forEachIndexed { i, c ->
            val b = best
            if (b == null || HEADLINE_PRIORITY.indexOf(c) < HEADLINE_PRIORITY.indexOf(cases[b])) best = i
        }
        return best
    }

    /** 32-bit FNV-1a over the UTF-8 bytes, as an unsigned value in a Long. */
    fun fnv1a32(text: String): Long {
        var h = 0x811C9DC5L
        for (b in text.encodeToByteArray()) {
            h = h xor (b.toLong() and 0xFF)
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return h
    }

    /** Deterministic copy variant per (station, Athens day) so it does not feel robotic. */
    fun variantIndex(stationId: String, athensDate: String, variantCount: Int): Int =
        (fnv1a32("$stationId|$athensDate") % variantCount).toInt()

    /** The first blocking reason in contract order, or [CoachGuard.ALLOW]. */
    fun guard(g: CoachGuardInput): CoachGuard {
        if (!g.enabled) return CoachGuard.DISABLED
        if (g.goJourneyActive) return CoachGuard.GO_ACTIVE
        if (g.athensHour >= 23 || g.athensHour < 7) return CoachGuard.QUIET_HOURS
        val speed = g.speedKmh
        if (speed != null && speed > MAX_SPEED_KMH) return CoachGuard.MOVING
        val last = g.lastNotifiedForStationEpochSeconds
        if (last != null && g.now - last < COOLDOWN_SECONDS) return CoachGuard.COOLDOWN
        if (g.notificationsToday >= MAX_PER_DAY) return CoachGuard.DAILY_CAP
        return CoachGuard.ALLOW
    }
}
