package com.syrmos.core.model.journey

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Persisted, locally-owned journey state: saved journeys and one live GO session.
// No account, no server ownership. All roots carry schemaVersion for migration.

/** The phase a live GO session is in. Mirrors the S06 state table 1:1. */
@Serializable
enum class JourneyPhase {
    @SerialName("readyToBoard") READY_TO_BOARD,
    @SerialName("riding") RIDING,
    @SerialName("alightSoon") ALIGHT_SOON,
    @SerialName("transfer") TRANSFER,
    @SerialName("waitingForNextLeg") WAITING_FOR_NEXT_LEG,
    @SerialName("locationUncertain") LOCATION_UNCERTAIN,
    @SerialName("arrived") ARRIVED,
    @SerialName("ended") ENDED,
}

/**
 * How the rider's current position was established. `manual` can move backward on
 * purpose; automatic sources can never undo a confirmed stop without evidence.
 */
@Serializable
enum class ProgressSource {
    @SerialName("manual") MANUAL,
    @SerialName("gps") GPS,
    @SerialName("matchedVehicle") MATCHED_VEHICLE,
    @SerialName("scheduleEstimate") SCHEDULE_ESTIMATE,
}

/** Planning preferences carried by a saved journey (recomputed at open time). */
@Serializable
data class JourneyPreferences(
    val ranking: Ranking = Ranking.FASTEST,
    val accessibilityPreference: AccessibilityPreference = AccessibilityPreference.NONE,
)

/**
 * Opt-in alert settings for a live session. Everything defaults off: a
 * notification is only requested at the point the rider enables trip alerts.
 */
@Serializable
data class AlertPreferences(
    val leaveByReminder: Boolean = false,
    val getOffAlert: Boolean = false,
    val disruptionAlert: Boolean = false,
)

/**
 * A locally saved regular journey. `label` is optional; a saved "Home" is just an
 * optional station selection, never an inferred residential address.
 */
@Serializable
data class SavedJourney(
    val id: String,
    val fromId: String,
    val toId: String,
    val createdAt: Instant,
    val label: String? = null,
    val preferences: JourneyPreferences = JourneyPreferences(),
    val schemaVersion: Int = JourneyContract.SCHEMA_VERSION,
)

/**
 * The persisted saved-journeys list root. One versioned blob holds the whole
 * ordered list (display order == array order). `schemaVersion` is declared first
 * so the encoded JSON is byte-parity with the web `SyrmosSavedJourneys` store and
 * iOS `SavedJourneyStore`, per fixtures/journeys/saved.json.
 */
@Serializable
data class SavedJourneysRoot(
    val schemaVersion: Int = JourneyContract.SCHEMA_VERSION,
    val savedJourneys: List<SavedJourney> = emptyList(),
)

/**
 * The single, globally-shared live GO session. The itinerary snapshot is FROZEN
 * during active guidance; new service data raises a warning/revision proposal
 * rather than mutating it silently. `revision` bumps on an accepted replacement,
 * and `alertedEventIds` de-duplicates cues so a replayed fix never alerts twice.
 */
@Serializable
data class ActiveJourney(
    val id: String,
    val revision: Int,
    val itinerarySnapshot: JourneyOption,
    val phase: JourneyPhase,
    val legId: String,
    val startedAt: Instant,
    val updatedAt: Instant,
    val confirmedStopId: String? = null,
    val progressSource: ProgressSource = ProgressSource.MANUAL,
    val progressObservedAt: Instant? = null,
    val alertedEventIds: List<String> = emptyList(),
    val alertPreferences: AlertPreferences = AlertPreferences(),
    val schemaVersion: Int = JourneyContract.SCHEMA_VERSION,
)
