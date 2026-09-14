package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors the cross-client leave-by contract in `fixtures/reminders/leave-by.json`
 * (the same cases web `web-reminders.js` and iOS `LeaveByReminder.swift` are tested
 * against) so the reminder timing and reconciliation stay identical on every
 * client. commonTest cannot read repo files, so the cases are inlined here; keep
 * them in step with leave-by.json.
 */
class LeaveByReminderTest {

    private fun rem(
        line: String = "M1", station: String = "M1_VIC", name: String = "Victoria",
        dest: String = "Kifisia", time: String = "08:00", dep: Long, lead: Long,
    ) = LeaveByReminder(line, station, name, dest, time, dep, lead)

    @Test
    fun stateCases_matchCrossClientContract() {
        val dep = 1_000_000L

        // before leave-by: scheduled, fires at leave-by
        rem(dep = dep, lead = 900).let {
            assertEquals(999_100L, it.leaveByEpochSeconds)
            assertEquals(ReminderState.SCHEDULED, it.state(998_000))
            assertEquals(999_100L, it.fireAtEpochSeconds(998_000))
            assertEquals(19, it.minutesUntilLeave(998_000))
        }
        // exactly at leave-by: leave now, nothing to schedule
        rem(dep = dep, lead = 900).let {
            assertEquals(ReminderState.LEAVE_NOW, it.state(999_100))
            assertEquals(null, it.fireAtEpochSeconds(999_100))
            assertEquals(0, it.minutesUntilLeave(999_100))
        }
        // between leave-by and departure: leave now
        rem(dep = dep, lead = 900).let {
            assertEquals(ReminderState.LEAVE_NOW, it.state(999_500))
            assertEquals(null, it.fireAtEpochSeconds(999_500))
        }
        // at and after departure: departed
        rem(dep = dep, lead = 900).let {
            assertEquals(ReminderState.DEPARTED, it.state(1_000_000))
            assertEquals(ReminderState.DEPARTED, it.state(1_000_600))
            assertEquals(null, it.fireAtEpochSeconds(1_000_600))
        }
        // rounds up a partial minute
        assertEquals(2, rem(dep = dep, lead = 900).minutesUntilLeave(999_010))
        // zero lead: leave-by is the departure itself
        rem(dep = dep, lead = 0).let {
            assertEquals(1_000_000L, it.leaveByEpochSeconds)
            assertEquals(ReminderState.SCHEDULED, it.state(999_940))
            assertEquals(1, it.minutesUntilLeave(999_940))
        }
        // negative lead is clamped to zero
        rem(dep = dep, lead = -300).let {
            assertEquals(1_000_000L, it.leaveByEpochSeconds)
            assertEquals(2, it.minutesUntilLeave(999_900))
        }
    }

    @Test
    fun id_isStableDedupKey() {
        assertEquals("M1|M1_VIC|1000000", LeaveByReminders.idFor("M1", "M1_VIC", 1_000_000))
        assertEquals(rem(dep = 1_000_000, lead = 900).id, rem(dep = 1_000_000, lead = 1200).id)
    }

    private val m1 = rem(dep = 1_000_000, lead = 900)
    private val m3 = rem(line = "M3", station = "M3_SYN", name = "Syntagma", dest = "Airport", time = "08:10", dep = 1_000_600, lead = 600)

    @Test
    fun reconcile_schedulesNewAndCollapsesDuplicates() {
        val r = LeaveByReminders.reconcile(emptyList(), listOf(m1, m3, m1), 998_000)
        assertEquals(listOf("M1|M1_VIC|1000000", "M3|M3_SYN|1000600"), r.toSchedule.map { it.id })
        assertEquals(emptyList(), r.toCancel)
        assertEquals(emptyList(), r.unchanged)
    }

    @Test
    fun reconcile_unchangedStaysUntouched() {
        val r = LeaveByReminders.reconcile(listOf(m1), listOf(m1), 998_000)
        assertEquals(emptyList(), r.toSchedule)
        assertEquals(emptyList(), r.toCancel)
        assertEquals(listOf("M1|M1_VIC|1000000"), r.unchanged.map { it.id })
    }

    @Test
    fun reconcile_removedIsCancelled() {
        val r = LeaveByReminders.reconcile(listOf(m1), emptyList(), 998_000)
        assertEquals(listOf("M1|M1_VIC|1000000"), r.toCancel)
        assertEquals(emptyList(), r.toSchedule)
    }

    @Test
    fun reconcile_movedLeaveByReschedulesSameId() {
        val moved = rem(dep = 1_000_000, lead = 1200) // same id, later leave-by
        val r = LeaveByReminders.reconcile(listOf(m1), listOf(moved), 998_000)
        assertEquals(listOf("M1|M1_VIC|1000000"), r.toSchedule.map { it.id })
        assertEquals(listOf("M1|M1_VIC|1000000"), r.toCancel)
        assertEquals(emptyList(), r.unchanged)
    }

    @Test
    fun reconcile_departedDesiredDropped_andScheduledDepartedCancelled() {
        // A desired reminder whose train already left is never scheduled.
        val dropped = LeaveByReminders.reconcile(emptyList(), listOf(m1), 1_000_600)
        assertEquals(emptyList(), dropped.toSchedule)
        assertEquals(emptyList(), dropped.toCancel)

        // A scheduled reminder whose train departed is cancelled.
        val cancelled = LeaveByReminders.reconcile(listOf(m1), listOf(m1), 1_000_600)
        assertEquals(listOf("M1|M1_VIC|1000000"), cancelled.toCancel)
        assertEquals(emptyList(), cancelled.toSchedule)
    }
}
