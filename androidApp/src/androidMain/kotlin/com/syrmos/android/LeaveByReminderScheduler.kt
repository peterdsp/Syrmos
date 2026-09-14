package com.syrmos.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.syrmos.core.domain.reminder.SavedDepartureRepository
import com.syrmos.core.common.LeaveByReminder
import com.syrmos.core.common.LeaveByReminders
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.domain.reminder.SavedDepartureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Phase N J09: schedules the actual OS leave-by alarms for the saved-departure
 * board. Observes [SavedDepartureRepository] and the [NotificationSettings]
 * opt-in together; on any change it diffs the desired reminder set against what
 * is currently scheduled using the shared [LeaveByReminders.reconcile] engine and
 * only touches the alarms that changed, so a live edit never double-books or
 * leaves a stale alarm.
 *
 * When the opt-in is off the desired set is empty, so every alarm is cancelled;
 * turning it back on reschedules from the board. Exact alarms are used when the
 * user has granted them (Android 12+ `canScheduleExactAlarms`); otherwise the
 * scheduler falls back to an inexact alarm rather than failing, and the "leave
 * now" cue may arrive a little late instead of not at all.
 */
class LeaveByReminderScheduler(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** The reminders we have live alarms for, so reconcile has a real `current`. */
    private var scheduled: List<LeaveByReminder> = emptyList()

    fun start() {
        scope.launch {
            combine(
                SavedDepartureRepository.departures,
                NotificationSettings.leaveByReminders,
            ) { list, enabled -> if (enabled) SavedDepartureStore.toReminders(list) else emptyList() }
                .collect { desired -> sync(desired) }
        }
    }

    private fun sync(desired: List<LeaveByReminder>) {
        val now = Clock.System.now().epochSeconds
        val diff = LeaveByReminders.reconcile(scheduled, desired, now)
        diff.toCancel.forEach { cancel(it) }
        diff.toSchedule.forEach { schedule(it, now) }
        // The live set is everything desired that is still worth an alarm.
        scheduled = LeaveByReminders.active(desired, now)
    }

    private fun schedule(reminder: LeaveByReminder, now: Long) {
        val fireAt = reminder.fireAtEpochSeconds(now) ?: return // already past: nothing to schedule
        val pi = pendingIntent(reminder)
        val triggerMillis = fireAt * 1000
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        } else {
            // No exact-alarm grant: an inexact wake is better than nothing.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun cancel(id: String) {
        alarmManager.cancel(pendingIntentFor(id, null))
    }

    private fun pendingIntent(reminder: LeaveByReminder): PendingIntent =
        pendingIntentFor(reminder.id, reminder)

    /**
     * A PendingIntent keyed by the reminder's stable id (its hash is the request
     * code and the notification id), so cancel and schedule address the same alarm.
     * When [reminder] is null only the addressing matters (cancel path).
     */
    private fun pendingIntentFor(id: String, reminder: LeaveByReminder?): PendingIntent {
        val code = id.hashCode()
        val intent = Intent(context, LeaveByReminderReceiver::class.java).apply {
            action = "com.syrmos.android.LEAVE_BY.$id"
            putExtra(LeaveByReminderReceiver.EXTRA_NOTIF_ID, code)
            reminder?.let {
                putExtra(LeaveByReminderReceiver.EXTRA_LINE, it.lineId)
                putExtra(LeaveByReminderReceiver.EXTRA_STATION, it.stationName)
                putExtra(LeaveByReminderReceiver.EXTRA_DESTINATION, it.destination)
                putExtra(LeaveByReminderReceiver.EXTRA_SCHEDULED, it.scheduledTime)
            }
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
