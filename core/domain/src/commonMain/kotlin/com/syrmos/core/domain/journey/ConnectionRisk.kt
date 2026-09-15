package com.syrmos.core.domain.journey

import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.Leg
import com.syrmos.core.model.journey.LegKind

/**
 * Per-transfer connection risk (S07 / Phase R), the Kotlin peer of web
 * `SyrmosConnectionRisk` and iOS `ConnectionRisk`. For each transfer in a
 * [JourneyOption] it exposes the ACTUAL numbers the S07 warning shows -- the real
 * gap available and the change time to allow -- and the honest status, reusing the
 * shared [FeasibilityPolicy]/[FeasibilityCalculator] margin exactly, so the inline
 * GO warning cannot drift from the option's feasibility. Pure and side-effect free;
 * validated against fixtures/journeys/connection-risk.json.
 */
object ConnectionRisk {
    /** One transfer's risk, with the numbers the rider is shown. */
    data class TransferRisk(
        val transferIndex: Int,
        val fromLegId: String,
        val toLegId: String,
        val atStationId: String,
        val toLineId: String?,
        val status: FeasibilityStatus,
        /** Real gap = nextDeparture - previousArrival, or null when unknown. */
        val availableSeconds: Int?,
        /** Change time to allow (transfer minimum, or the policy default). */
        val recommendedSeconds: Int,
    )

    private fun severity(status: FeasibilityStatus): Int = when (status) {
        FeasibilityStatus.MISSED -> 3
        FeasibilityStatus.UNKNOWN -> 2
        FeasibilityStatus.TIGHT -> 1
        FeasibilityStatus.COMFORTABLE -> 0
    }

    private fun statusFor(marginSeconds: Int, policy: FeasibilityPolicy): FeasibilityStatus = when {
        marginSeconds < 0 -> FeasibilityStatus.MISSED
        marginSeconds <= policy.tightMaxSeconds -> FeasibilityStatus.TIGHT
        else -> FeasibilityStatus.COMFORTABLE
    }

    private fun connectorBetween(legs: List<Leg>, fromIdx: Int, toIdx: Int): Leg? {
        for (j in (fromIdx + 1) until toIdx) {
            if (legs[j].kind == LegKind.TRANSFER || legs[j].kind == LegKind.WALK) return legs[j]
        }
        return null
    }

    /** Per-transfer risk rows for an option. Empty for a direct (single-ride) trip. */
    fun risks(option: JourneyOption, policy: FeasibilityPolicy = FeasibilityPolicy.Default): List<TransferRisk> {
        val legs = option.legs
        val rideIdx = legs.indices.filter { legs[it].kind == LegKind.RIDE }
        val out = mutableListOf<TransferRisk>()
        for (k in 0 until rideIdx.size - 1) {
            val prev = legs[rideIdx[k]]
            val next = legs[rideIdx[k + 1]]
            val connector = connectorBetween(legs, rideIdx[k], rideIdx[k + 1])
            val recommended = connector?.transferMinimumSeconds ?: policy.defaultTransferBufferSeconds
            val uncertainty = prev.uncertaintySeconds ?: 0
            val a = prev.arrivalInstant
            val d = next.departureInstant
            val available: Int? = if (a != null && d != null) (d - a).inWholeSeconds.toInt() else null
            val status = if (available == null) FeasibilityStatus.UNKNOWN
                else statusFor(available - recommended - uncertainty, policy)
            out += TransferRisk(
                transferIndex = k, fromLegId = prev.id, toLegId = next.id,
                atStationId = prev.toId, toLineId = next.lineId,
                status = status, availableSeconds = available, recommendedSeconds = recommended,
            )
        }
        return out
    }

    /** The worst status across all transfers; COMFORTABLE when there are none. */
    fun worstStatus(option: JourneyOption, policy: FeasibilityPolicy = FeasibilityPolicy.Default): FeasibilityStatus {
        var worst = FeasibilityStatus.COMFORTABLE
        for (r in risks(option, policy)) if (severity(r.status) >= severity(worst)) worst = r.status
        return worst
    }

    /**
     * The nearest transfer at or after ride leg [fromRideIndex] that needs the
     * rider's attention (tight / missed / unknown), for the inline GO warning;
     * null when every upcoming connection is comfortable.
     */
    fun nextConcern(option: JourneyOption, fromRideIndex: Int = 0, policy: FeasibilityPolicy = FeasibilityPolicy.Default): TransferRisk? =
        risks(option, policy).firstOrNull { it.transferIndex >= fromRideIndex && it.status != FeasibilityStatus.COMFORTABLE }
}
