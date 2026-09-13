package com.syrmos.core.domain.journey

import com.syrmos.core.domain.go.GoGuidance
import com.syrmos.core.domain.go.GuidanceJourney
import com.syrmos.core.domain.go.GuidancePosition
import com.syrmos.core.domain.go.JourneyGuidance
import com.syrmos.core.model.journey.ActiveJourney
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.JourneyPhase
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.ProgressSource
import kotlinx.datetime.Instant

/**
 * Pure lifecycle for the single live GO session (the [ActiveJourney]), the Kotlin
 * spine that web `SyrmosActiveJourney` and iOS `ActiveJourneyStore` mirror. It owns
 * the transitions (start / advance / back / end) and the mapping between the
 * persisted, id-anchored session (`legId` + `confirmedStopId`) and the GO engine's
 * index-based [GuidancePosition], so a resumed trip lands on the exact same stop
 * even after the app was killed and stop names were re-resolved in another language.
 *
 * It never touches persistence, a clock or platform data: the caller supplies the
 * current instant and the [GuidanceJourney] (rebuilt from platform station data),
 * so every transition is deterministic and unit-tested on every KMP target.
 * Persistence is layered on top by each platform via
 * `JourneyContract.encodeActiveJourney` / `decodeActiveJourney`.
 *
 * The itinerary snapshot is FROZEN for the life of the session, so a [GuidanceJourney]
 * passed in must be the same trip: its ride legs line up 1:1 with the snapshot's ride
 * legs, in order, and guidance leg `i` always names snapshot ride leg `i`.
 */
object ActiveJourneyStore {

    /** RIDE legs of the snapshot, in order; guidance leg `i` corresponds to ride leg `i`. */
    private fun rideLegs(option: JourneyOption) = option.legs.filter { it.kind == LegKind.RIDE }

    /**
     * Begin a session at the origin (leg 0, board stop). Phase is derived from the
     * guidance at that position, so an already-boarded single-stop leg reads honestly.
     */
    fun start(id: String, option: JourneyOption, guidance: GuidanceJourney, startedAt: Instant): ActiveJourney {
        val pos = GuidancePosition(0, 0)
        val legId = rideLegs(option).firstOrNull()?.id
            ?: option.legs.firstOrNull()?.id
            ?: id
        return ActiveJourney(
            id = id,
            revision = 0,
            itinerarySnapshot = option,
            phase = phaseFor(guidance, pos),
            legId = legId,
            startedAt = startedAt,
            updatedAt = startedAt,
            confirmedStopId = stopIdAt(guidance, pos),
            progressSource = ProgressSource.MANUAL,
            progressObservedAt = startedAt,
        )
    }

    /**
     * The engine position for a persisted session, mapping ids back to indices. An
     * unknown legId/confirmedStopId (e.g. a re-plan dropped it) resolves to the
     * origin of its leg rather than throwing, so a resumed session is never lost.
     */
    fun positionOf(active: ActiveJourney, guidance: GuidanceJourney): GuidancePosition {
        if (guidance.legs.isEmpty()) return GuidancePosition(0, 0)
        val rides = rideLegs(active.itinerarySnapshot)
        val legIndex = rides.indexOfFirst { it.id == active.legId }
            .let { if (it in guidance.legs.indices) it else 0 }
        val stops = guidance.legs[legIndex].stops
        val stopIndex = active.confirmedStopId
            ?.let { id -> stops.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        return GuidancePosition(legIndex, stopIndex.coerceIn(0, maxOf(0, stops.lastIndex)))
    }

    /** Rewrite the session onto [pos], refreshing legId / confirmedStopId / phase / clock. */
    fun withPosition(
        active: ActiveJourney,
        guidance: GuidanceJourney,
        pos: GuidancePosition,
        now: Instant,
        source: ProgressSource = ProgressSource.MANUAL,
    ): ActiveJourney {
        val legId = rideLegs(active.itinerarySnapshot).getOrNull(pos.legIndex)?.id ?: active.legId
        return active.copy(
            phase = phaseFor(guidance, pos),
            legId = legId,
            confirmedStopId = stopIdAt(guidance, pos),
            progressSource = source,
            progressObservedAt = now,
            updatedAt = now,
        )
    }

    /** Advance one stop (rolling a leg's alight stop onto the next leg's board stop). */
    fun advance(
        active: ActiveJourney,
        guidance: GuidanceJourney,
        now: Instant,
        source: ProgressSource = ProgressSource.MANUAL,
    ): ActiveJourney = withPosition(active, guidance, GoGuidance.advance(guidance, positionOf(active, guidance)), now, source)

    /** Step back one stop, rolling onto the previous leg's alight stop at a boundary. */
    fun back(active: ActiveJourney, guidance: GuidanceJourney, now: Instant): ActiveJourney {
        val cur = positionOf(active, guidance)
        val prev = when {
            cur.stopIndex > 0 -> GuidancePosition(cur.legIndex, cur.stopIndex - 1)
            cur.legIndex > 0 -> {
                val p = cur.legIndex - 1
                GuidancePosition(p, maxOf(0, guidance.legs[p].stops.lastIndex))
            }
            else -> cur
        }
        return withPosition(active, guidance, prev, now, ProgressSource.MANUAL)
    }

    /** Mark the session ended (rider tapped End or finished). The snapshot is kept. */
    fun end(active: ActiveJourney, now: Instant): ActiveJourney =
        active.copy(phase = JourneyPhase.ENDED, updatedAt = now)

    fun isEnded(active: ActiveJourney): Boolean = active.phase == JourneyPhase.ENDED

    /** Whether a persisted session is still live, i.e. worth offering Resume. */
    fun isResumable(active: ActiveJourney): Boolean = active.phase != JourneyPhase.ENDED

    /** Map the engine instruction at [pos] to the persisted [JourneyPhase]. */
    fun phaseFor(guidance: GuidanceJourney, pos: GuidancePosition): JourneyPhase =
        when (GoGuidance.guidance(guidance, pos)) {
            is JourneyGuidance.Board -> JourneyPhase.READY_TO_BOARD
            is JourneyGuidance.Ride -> JourneyPhase.RIDING
            is JourneyGuidance.GetOffNext -> JourneyPhase.ALIGHT_SOON
            is JourneyGuidance.Transfer -> JourneyPhase.TRANSFER
            is JourneyGuidance.Arrived -> JourneyPhase.ARRIVED
        }

    private fun stopIdAt(guidance: GuidanceJourney, pos: GuidancePosition): String? =
        guidance.legs.getOrNull(pos.legIndex)?.stops?.getOrNull(pos.stopIndex)?.id
}
