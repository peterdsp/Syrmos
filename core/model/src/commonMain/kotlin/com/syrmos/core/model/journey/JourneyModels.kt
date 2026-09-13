package com.syrmos.core.model.journey

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Syrmos 3.0 "Journeys" shared contract (prompt section 7.1). Canonical typed
// models for a planned, ranked, schedule-aware itinerary and a live GO session.
// These extend the older estimate-only JourneyResult/JourneySegment; they do NOT
// replace them yet, so nothing downstream breaks while the planner is migrated.
//
// Invariants baked into the types:
//   - null means UNKNOWN, never zero. Every optional timing term is nullable so a
//     view can render "unknown" honestly instead of a fabricated 0.
//   - all instants are absolute (kotlinx.datetime.Instant); the operator service
//     date is stored separately because a service day can run past 24:00.
//   - enum wire names are lowercase and match fixtures/journeys/*.json exactly, so
//     the same JSON decodes on Kotlin and is mirror-able to Swift/JS byte-for-byte.
//   - schemaVersion travels on every persisted root for a versioned migration path
//     (see JourneyContract).

/** How the rider anchored the search in time. */
@Serializable
enum class TimeMode {
    @SerialName("now") NOW,
    @SerialName("departAt") DEPART_AT,
    @SerialName("arriveBy") ARRIVE_BY,
}

/** The requested ranking objective. Arrival/changes/walking/stable-id break ties. */
@Serializable
enum class Ranking {
    @SerialName("fastest") FASTEST,
    @SerialName("fewestChanges") FEWEST_CHANGES,
    @SerialName("leastWalking") LEAST_WALKING,
}

/** Rider accessibility preference. "Unknown information" is disclosed, never faked. */
@Serializable
enum class AccessibilityPreference {
    @SerialName("none") NONE,
    @SerialName("stepFree") STEP_FREE,
}

/** Per-leg accessibility knowledge. Absence of data is `unknown`, not `verified`. */
@Serializable
enum class LegAccessibility {
    @SerialName("verified") VERIFIED,
    @SerialName("unavailable") UNAVAILABLE,
    @SerialName("unknown") UNKNOWN,
}

/** Where a leg's clock came from. A 1-second client simulation is `estimated`. */
@Serializable
enum class TimingKind {
    @SerialName("live") LIVE,
    @SerialName("scheduled") SCHEDULED,
    @SerialName("estimated") ESTIMATED,
    @SerialName("unknown") UNKNOWN,
}

/** What the rider physically does on a leg. */
@Serializable
enum class LegKind {
    @SerialName("ride") RIDE,
    @SerialName("walk") WALK,
    @SerialName("transfer") TRANSFER,
}

/** Connection feasibility. The journey takes the worst valid transfer state. */
@Serializable
enum class FeasibilityStatus {
    @SerialName("comfortable") COMFORTABLE,
    @SerialName("tight") TIGHT,
    @SerialName("missed") MISSED,
    @SerialName("unknown") UNKNOWN,
}

/**
 * Provenance and validity window for a leg's timing/status. Timestamps are
 * nullable because "not observed" and "no expiry" are legitimate unknowns.
 * Historical cache does not extend validity: `validUntil` is honoured as-is.
 */
@Serializable
data class SourceRef(
    val sourceId: String? = null,
    val observedAt: Instant? = null,
    val fetchedAt: Instant? = null,
    val validUntil: Instant? = null,
    val snapshotVersion: String? = null,
    val statusCoverage: String? = null,
    val scheduleCoverage: String? = null,
)

/**
 * Transparent connection evidence. `minimumMarginSeconds` is the actual computed
 * margin (nullable when a required term is unknown), not a success percentage.
 */
@Serializable
data class Feasibility(
    val status: FeasibilityStatus,
    val minimumMarginSeconds: Int? = null,
    val explanationCode: String,
    val limitingLegId: String? = null,
)

/**
 * One leg of an itinerary. Traverses the REAL ordered stops so transfer cost can
 * never be collapsed away at an interchange. Every clock term is nullable to keep
 * "unknown" distinct from a real value.
 */
@Serializable
data class Leg(
    val id: String,
    val kind: LegKind,
    val fromId: String,
    val toId: String,
    val lineId: String? = null,
    val directionId: String? = null,
    val tripId: String? = null,
    val orderedStopIds: List<String> = emptyList(),
    val departureInstant: Instant? = null,
    val arrivalInstant: Instant? = null,
    val serviceDate: LocalDate,
    val timingKind: TimingKind = TimingKind.UNKNOWN,
    val uncertaintySeconds: Int? = null,
    val transferMinimumSeconds: Int? = null,
    val accessibility: LegAccessibility = LegAccessibility.UNKNOWN,
    val sourceRef: SourceRef? = null,
)

/**
 * One ranked itinerary option. IDs are stable across recomputation where the trip
 * really is the same, so a re-plan reconciles by id, never by list index or name.
 */
@Serializable
data class JourneyOption(
    val id: String,
    val requestId: String,
    val legs: List<Leg>,
    val departureInstant: Instant? = null,
    val arrivalInstant: Instant? = null,
    val durationSeconds: Int? = null,
    val transferCount: Int,
    val walkingSeconds: Int? = null,
    val sourceSummary: SourceRef? = null,
    val feasibility: Feasibility,
    /** "fastest" etc. only when that ranking is actually true; null otherwise. */
    val rankingBadge: String? = null,
)

/**
 * A planning request. All schedule evaluation happens in `serviceTimeZone`
 * (Europe/Athens); `requestedInstant` is absolute so DST and past-midnight
 * service resolve correctly.
 */
@Serializable
data class JourneyRequest(
    val fromStationId: String,
    val toStationId: String,
    val timeMode: TimeMode = TimeMode.NOW,
    val requestedInstant: Instant,
    val serviceTimeZone: String = JourneyContract.SERVICE_TIME_ZONE,
    val ranking: Ranking = Ranking.FASTEST,
    val accessibilityPreference: AccessibilityPreference = AccessibilityPreference.NONE,
    val schemaVersion: Int = JourneyContract.SCHEMA_VERSION,
)
