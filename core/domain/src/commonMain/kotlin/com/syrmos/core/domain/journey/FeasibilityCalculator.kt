package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.Feasibility
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind
import kotlinx.datetime.Instant

/**
 * Configurable, fixture-covered thresholds for connection feasibility (prompt
 * 7.3). Kept out of view code so web/iOS/Android share one policy and tests pin
 * the boundaries. `defaultTransferBufferSeconds` is an ESTIMATE used only when a
 * transfer leg carries no minimum of its own; the UI must disclose it as such.
 */
data class FeasibilityPolicy(
    /** Upper bound (inclusive) of the "tight" band; below it is comfortable. */
    val tightMaxSeconds: Int = 179,
    /** Estimate applied when a transfer leg has no `transferMinimumSeconds`. */
    val defaultTransferBufferSeconds: Int = 120,
) {
    companion object { val Default = FeasibilityPolicy() }
}

/**
 * Computes transparent connection feasibility for a planned itinerary. Never
 * emits a success percentage: the evidence is a real margin in seconds and one
 * of four honest states. The journey takes the WORST valid transfer state; a
 * required unknown term makes the whole option unknown; a closure overrides
 * everything to missed (unusable).
 *
 *   margin = nextDeparture - previousArrival - transferMinimum - uncertaintyAllowance   (seconds)
 *   margin < 0                    -> missed
 *   0 .. tightMaxSeconds          -> tight
 *   > tightMaxSeconds             -> comfortable
 *
 * Pure and side-effect free; mirrors web `web-feasibility.js` exactly.
 */
object FeasibilityCalculator {

    /**
     * Margin in seconds for one transfer, or null when a REQUIRED term is
     * unknown (previous arrival or next departure). `transferMinimumSeconds`
     * falls back to the policy default (an estimate), and unknown uncertainty is
     * treated as zero but stays disclosed by the caller.
     */
    fun transferMarginSeconds(
        previousArrival: Instant?,
        nextDeparture: Instant?,
        transferMinimumSeconds: Int?,
        uncertaintySeconds: Int?,
        policy: FeasibilityPolicy = FeasibilityPolicy.Default,
    ): Int? {
        if (previousArrival == null || nextDeparture == null) return null
        val gap = (nextDeparture - previousArrival).inWholeSeconds.toInt()
        val minimum = transferMinimumSeconds ?: policy.defaultTransferBufferSeconds
        val uncertainty = uncertaintySeconds ?: 0
        return gap - minimum - uncertainty
    }

    private fun statusFor(marginSeconds: Int, policy: FeasibilityPolicy): FeasibilityStatus = when {
        marginSeconds < 0 -> FeasibilityStatus.MISSED
        marginSeconds <= policy.tightMaxSeconds -> FeasibilityStatus.TIGHT
        else -> FeasibilityStatus.COMFORTABLE
    }

    // Worst-first severity so the option reflects its weakest link.
    private fun severity(status: FeasibilityStatus): Int = when (status) {
        FeasibilityStatus.MISSED -> 3
        FeasibilityStatus.UNKNOWN -> 2
        FeasibilityStatus.TIGHT -> 1
        FeasibilityStatus.COMFORTABLE -> 0
    }

    fun forOption(
        option: JourneyOption,
        policy: FeasibilityPolicy = FeasibilityPolicy.Default,
        closedLegIds: Set<String> = emptySet(),
    ): Feasibility {
        // A closure on any leg makes the whole plan unusable, overriding timing.
        val closed = option.legs.firstOrNull { it.id in closedLegIds }
        if (closed != null) {
            return Feasibility(FeasibilityStatus.MISSED, null, "segment_closed", closed.id)
        }

        val rideLegs = option.legs.filter { it.kind == LegKind.RIDE }
        // Direct (or nothing to connect): show schedule status, not a transfer
        // confidence calculation (prompt 7.3).
        if (rideLegs.size <= 1) {
            return Feasibility(FeasibilityStatus.COMFORTABLE, null, "direct", null)
        }

        var worst: FeasibilityStatus = FeasibilityStatus.COMFORTABLE
        var worstMargin: Int? = null
        var worstLegId: String? = null

        for (i in 0 until rideLegs.size - 1) {
            val prev = rideLegs[i]
            val next = rideLegs[i + 1]
            val connector = connectorBetween(option.legs, prev, next)
            val margin = transferMarginSeconds(
                previousArrival = prev.arrivalInstant,
                nextDeparture = next.departureInstant,
                transferMinimumSeconds = connector?.transferMinimumSeconds,
                uncertaintySeconds = prev.uncertaintySeconds,
                policy = policy,
            )
            val status = if (margin == null) FeasibilityStatus.UNKNOWN else statusFor(margin, policy)
            if (severity(status) >= severity(worst)) {
                worst = status
                worstMargin = margin
                worstLegId = connector?.id ?: next.id
            }
        }

        val code = when (worst) {
            FeasibilityStatus.MISSED -> "transfer_missed"
            FeasibilityStatus.TIGHT -> "transfer_tight"
            FeasibilityStatus.UNKNOWN -> "transfer_unknown"
            FeasibilityStatus.COMFORTABLE -> "transfer_comfortable"
        }
        // Margin is only meaningful for a known state; unknown keeps it null.
        val margin = if (worst == FeasibilityStatus.UNKNOWN) null else worstMargin
        return Feasibility(worst, margin, code, worstLegId)
    }

    // The transfer/walk leg sitting between two ride legs, if any.
    private fun connectorBetween(legs: List<Leg>, prev: Leg, next: Leg): Leg? {
        val from = legs.indexOf(prev)
        val to = legs.indexOf(next)
        if (from < 0 || to < 0) return null
        for (j in (from + 1) until to) {
            if (legs[j].kind == LegKind.TRANSFER || legs[j].kind == LegKind.WALK) return legs[j]
        }
        return null
    }
}
