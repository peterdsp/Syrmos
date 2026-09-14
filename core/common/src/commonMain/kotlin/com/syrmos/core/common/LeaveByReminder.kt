package com.syrmos.core.common

/**
 * Syrmos 3.0 Phase N J09: the pure engine for user-enabled "leave by" reminders.
 *
 * A rider opts in to be reminded when to set off to catch a saved or tracked
 * departure. The reminder fires at `departure - lead`, where `lead` is the time
 * the rider needs to reach the platform (walk plus a personal buffer). This file
 * is the single language-neutral rule the three clients share: it computes the
 * leave-by moment, classifies the reminder's state, and reconciles the desired
 * set of reminders against what is already scheduled so duplicates are collapsed
 * and changed or removed departures cancel/replace their pending notification.
 *
 * Pure and offline: everything is arithmetic against an epoch-second clock the
 * caller supplies, so it is deterministic and unit-tested on every KMP target,
 * mirrored by web `web-reminders.js` and iOS `LeaveByReminder.swift`, and
 * validated against `fixtures/reminders/leave-by.json`. Platform code layers the
 * actual scheduling (Android AlarmManager, iOS UNUserNotificationCenter, web
 * foreground timer) and the saved-departure board on top; none of the timing
 * logic lives in those layers, so it cannot drift between clients.
 */
enum class ReminderState {
    /** Leave-by is still in the future: a notification is pending. */
    SCHEDULED,

    /** It is time to go (past leave-by, before the train departs). */
    LEAVE_NOW,

    /** The train has departed: the reminder is spent and should be cleared. */
    DEPARTED,
}

/**
 * One opt-in leave-by reminder for a specific departure. [id] is the stable
 * dedup key, so the same departure chosen twice is one reminder, and a live
 * re-fetch of the same train reconciles by id rather than stacking up.
 */
data class LeaveByReminder(
    val lineId: String,
    val stationId: String,
    val stationName: String,
    val destination: String,
    /** Scheduled clock time, HH:MM, for display. */
    val scheduledTime: String,
    /** Unix epoch second the train departs. */
    val departureEpochSeconds: Long,
    /** Seconds the rider needs before departure (walk + personal buffer), floored at 0. */
    val leadSeconds: Long,
) {
    /** Stable identity: one reminder per (line, station, departure instant). */
    val id: String get() = LeaveByReminders.idFor(lineId, stationId, departureEpochSeconds)

    /** The epoch second the rider should set off. Never after the departure itself. */
    val leaveByEpochSeconds: Long
        get() = departureEpochSeconds - leadSeconds.coerceAtLeast(0)

    fun state(nowEpochSeconds: Long): ReminderState = when {
        nowEpochSeconds >= departureEpochSeconds -> ReminderState.DEPARTED
        nowEpochSeconds >= leaveByEpochSeconds -> ReminderState.LEAVE_NOW
        else -> ReminderState.SCHEDULED
    }

    /**
     * The epoch second a leave-by notification should fire, or null when that
     * moment has already passed (state is LEAVE_NOW or DEPARTED): the client
     * either prompts immediately or drops it, but never schedules into the past.
     */
    fun fireAtEpochSeconds(nowEpochSeconds: Long): Long? =
        leaveByEpochSeconds.takeIf { it > nowEpochSeconds }

    /**
     * Whole minutes until the rider must leave, rounded up so "30s to go" reads
     * as 1 min, not 0. Zero once leave-by has arrived or passed.
     */
    fun minutesUntilLeave(nowEpochSeconds: Long): Int {
        val secs = leaveByEpochSeconds - nowEpochSeconds
        if (secs <= 0) return 0
        return ((secs + 59) / 60).toInt()
    }
}

/**
 * The result of reconciling a desired reminder set against what is scheduled:
 * which pending notifications to (re)schedule, which to cancel, and which are
 * already correct and untouched. This is the whole "dedup + update + cancel"
 * contract in one pure step.
 */
data class ReminderReconciliation(
    val toSchedule: List<LeaveByReminder>,
    val toCancel: List<String>,
    val unchanged: List<LeaveByReminder>,
)

object LeaveByReminders {

    /** The dedup key for a departure. */
    fun idFor(lineId: String, stationId: String, departureEpochSeconds: Long): String =
        "$lineId|$stationId|$departureEpochSeconds"

    /** Collapse duplicates by [LeaveByReminder.id], keeping the last occurrence. */
    fun dedupe(reminders: List<LeaveByReminder>): List<LeaveByReminder> =
        reminders.associateBy { it.id }.values.toList()

    /** Reminders still worth a pending notification: not yet departed. */
    fun active(reminders: List<LeaveByReminder>, nowEpochSeconds: Long): List<LeaveByReminder> =
        dedupe(reminders).filter { it.state(nowEpochSeconds) != ReminderState.DEPARTED }

    /**
     * Diff the [desired] reminders against the [current] scheduled set at [now].
     *
     * - Duplicates in [desired] are collapsed and departed ones dropped first.
     * - An id present in both with the SAME leave-by is [ReminderReconciliation.unchanged].
     * - An id new to [desired], or whose leave-by moved (a live time change), is
     *   in [ReminderReconciliation.toSchedule]; its old pending copy, if any, is
     *   also cancelled so a moved departure never leaves a stale alarm behind.
     * - An id in [current] no longer desired (removed, or now departed) is in
     *   [ReminderReconciliation.toCancel].
     */
    fun reconcile(
        current: List<LeaveByReminder>,
        desired: List<LeaveByReminder>,
        nowEpochSeconds: Long,
    ): ReminderReconciliation {
        val desiredActive = active(desired, nowEpochSeconds)
        val currentById = dedupe(current).associateBy { it.id }
        val desiredById = desiredActive.associateBy { it.id }

        val toSchedule = mutableListOf<LeaveByReminder>()
        val unchanged = mutableListOf<LeaveByReminder>()
        for (d in desiredActive) {
            val existing = currentById[d.id]
            if (existing != null && existing.leaveByEpochSeconds == d.leaveByEpochSeconds) {
                unchanged.add(d)
            } else {
                toSchedule.add(d)
            }
        }

        val toCancel = mutableListOf<String>()
        for (id in currentById.keys) {
            val d = desiredById[id]
            // Cancel anything no longer desired, or whose leave-by moved (so the
            // new one in toSchedule replaces it rather than doubling up).
            if (d == null || d.leaveByEpochSeconds != currentById.getValue(id).leaveByEpochSeconds) {
                toCancel.add(id)
            }
        }

        return ReminderReconciliation(toSchedule, toCancel, unchanged)
    }
}
