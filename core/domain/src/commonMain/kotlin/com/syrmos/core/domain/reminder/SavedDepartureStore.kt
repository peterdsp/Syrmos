package com.syrmos.core.domain.reminder

import com.syrmos.core.common.LeaveByReminder
import com.syrmos.core.model.reminder.SavedDeparture

/**
 * Pure list operations for the locally-owned saved-departure board (Phase N J09),
 * the Kotlin peer of the web and iOS stores. All ops take and return an ordered
 * `List<SavedDeparture>` (display order == list order) so they are trivially
 * testable and identical across clients; persistence is layered on top by each
 * platform via `ReminderContract.encode/decodeSavedDeparturesRoot`.
 *
 * The de-duplication invariant is HARD and shared: at most one saved departure
 * per [SavedDeparture.id] (line|station|departure), matching the leave-by engine's
 * reminder id so the two never disagree about identity.
 */
object SavedDepartureStore {

    /**
     * Prepend a saved departure, de-duplicating by id. If the id already exists its
     * row is updated in place (kept at its position is NOT required; it moves to the
     * top as the most-recently-touched) with the new details; a new id is prepended.
     * Never produces two rows for one id.
     */
    fun save(list: List<SavedDeparture>, entry: SavedDeparture): List<SavedDeparture> {
        val without = list.filterNot { it.id == entry.id }
        return listOf(entry) + without
    }

    fun remove(list: List<SavedDeparture>, id: String): List<SavedDeparture> =
        list.filterNot { it.id == id }

    fun contains(list: List<SavedDeparture>, id: String): Boolean =
        list.any { it.id == id }

    /**
     * Drop departures whose train has already left at [nowEpochSeconds], so a stale
     * board self-heals instead of showing yesterday's trains. Order is preserved.
     */
    fun pruneDeparted(list: List<SavedDeparture>, nowEpochSeconds: Long): List<SavedDeparture> =
        list.filter { it.departureEpochSeconds > nowEpochSeconds }

    /** Reorder to an explicit id sequence; unknown ids are ignored, missing dropped. */
    fun reorder(list: List<SavedDeparture>, order: List<String>): List<SavedDeparture> {
        val byId = list.associateBy { it.id }
        return order.mapNotNull { byId[it] }
    }

    /** Map one saved departure onto the engine's reminder type. */
    fun toReminder(entry: SavedDeparture): LeaveByReminder = LeaveByReminder(
        lineId = entry.lineId,
        stationId = entry.stationId,
        stationName = entry.stationName,
        destination = entry.destination,
        scheduledTime = entry.scheduledTime,
        departureEpochSeconds = entry.departureEpochSeconds,
        leadSeconds = entry.leadSeconds,
    )

    /** The desired reminder set for the whole board, in list order. */
    fun toReminders(list: List<SavedDeparture>): List<LeaveByReminder> = list.map(::toReminder)
}
