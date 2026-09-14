package com.syrmos.core.model.reminder

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Syrmos 3.0 Phase N J09 persisted model for a saved departure the rider has
 * opted into a leave-by reminder for. This is the serializable, id-anchored
 * record the saved-departure board persists; the pure timing lives in the
 * `core/common` LeaveByReminder engine, which a [SavedDeparture] maps onto for
 * reconciliation and scheduling.
 *
 * Wire names and field order are shared across clients (Kotlin core/model, web
 * web-reminders.js store, iOS store) and validated against
 * fixtures/reminders/saved.json, so the encoded JSON round-trips byte-for-byte.
 */
@Serializable
data class SavedDeparture(
    val lineId: String,
    val stationId: String,
    val stationName: String,
    val destination: String,
    /** Scheduled clock time, HH:MM, for display. */
    val scheduledTime: String,
    /** Unix epoch second the train departs. */
    val departureEpochSeconds: Long,
    /** Seconds the rider needs before departure (walk + personal buffer). */
    val leadSeconds: Long,
    /** When the rider saved it, for stable display ordering / tie-breaks. */
    val createdAt: Instant,
    val schemaVersion: Int = ReminderContract.SCHEMA_VERSION,
) {
    /** Stable dedup key, identical to the engine's LeaveByReminder id. */
    val id: String get() = "$lineId|$stationId|$departureEpochSeconds"
}

/**
 * The persisted saved-departures list root. One versioned blob holds the whole
 * ordered list (display order == array order). `schemaVersion` is declared first
 * so the encoded JSON is byte-parity with the web and iOS stores, per
 * fixtures/reminders/saved.json.
 */
@Serializable
data class SavedDeparturesRoot(
    val schemaVersion: Int = ReminderContract.SCHEMA_VERSION,
    val savedDepartures: List<SavedDeparture> = emptyList(),
)
