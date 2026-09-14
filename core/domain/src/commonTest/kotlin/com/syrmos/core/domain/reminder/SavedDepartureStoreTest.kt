package com.syrmos.core.domain.reminder

import com.syrmos.core.model.reminder.SavedDeparture
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedDepartureStoreTest {

    private fun dep(
        line: String = "M1", station: String = "M1_VIC", name: String = "Victoria",
        dest: String = "Kifisia", time: String = "08:00", depSec: Long, lead: Long = 900,
        created: String = "2026-01-01T08:00:00Z",
    ) = SavedDeparture(line, station, name, dest, time, depSec, lead, Instant.parse(created))

    @Test
    fun save_prependsAndDedupesById() {
        val a = dep(depSec = 1_000_000)
        val b = dep(line = "M3", station = "M3_SYN", depSec = 1_000_600)
        var list = SavedDepartureStore.save(emptyList(), a)
        list = SavedDepartureStore.save(list, b)
        assertEquals(listOf(b.id, a.id), list.map { it.id })

        // Re-saving the same id updates in place and moves to the top, never doubles.
        val aUpdated = dep(depSec = 1_000_000, lead = 1200)
        list = SavedDepartureStore.save(list, aUpdated)
        assertEquals(listOf(a.id, b.id), list.map { it.id })
        assertEquals(1200, list.first { it.id == a.id }.leadSeconds)
        assertEquals(2, list.size)
    }

    @Test
    fun remove_andContains() {
        val a = dep(depSec = 1_000_000)
        val list = SavedDepartureStore.save(emptyList(), a)
        assertTrue(SavedDepartureStore.contains(list, a.id))
        val after = SavedDepartureStore.remove(list, a.id)
        assertFalse(SavedDepartureStore.contains(after, a.id))
        assertEquals(emptyList(), after)
    }

    @Test
    fun pruneDeparted_dropsPastTrainsKeepsOrder() {
        val past = dep(depSec = 1_000_000)
        val future = dep(line = "M3", station = "M3_SYN", depSec = 2_000_000)
        val list = listOf(past, future)
        val pruned = SavedDepartureStore.pruneDeparted(list, 1_500_000)
        assertEquals(listOf(future.id), pruned.map { it.id })
    }

    @Test
    fun reorder_followsExplicitSequence() {
        val a = dep(depSec = 1_000_000)
        val b = dep(line = "M3", station = "M3_SYN", depSec = 1_000_600)
        val list = listOf(a, b)
        assertEquals(listOf(b.id, a.id), SavedDepartureStore.reorder(list, listOf(b.id, a.id, "unknown")).map { it.id })
    }

    @Test
    fun toReminders_mapsFieldsForEngine() {
        val a = dep(depSec = 1_000_000, lead = 900)
        val reminders = SavedDepartureStore.toReminders(listOf(a))
        assertEquals(1, reminders.size)
        val r = reminders.first()
        assertEquals(a.id, r.id)
        assertEquals(999_100L, r.leaveByEpochSeconds)
        assertEquals("Victoria", r.stationName)
    }
}
