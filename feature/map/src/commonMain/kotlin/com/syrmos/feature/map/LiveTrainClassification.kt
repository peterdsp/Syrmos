package com.syrmos.feature.map

import com.syrmos.core.model.status.LiveVehicleState
import com.syrmos.core.model.status.classifyLiveVehicleIso
import com.syrmos.core.model.transit.LiveSuburbanTrain
import kotlinx.datetime.Instant

/**
 * The stale-aware live-train fleet ready to plot, plus the set of line ids a
 * still-tracked live train covers.
 *
 * [coveredLineIds] intentionally excludes EXPIRED positions: only a LIVE or
 * STALE train suppresses its line's schedule-projected dot. Once a train
 * expires (feed offline / dropped) its line is released, so the projector fills
 * back in - the offline fallback a frozen ghost used to block.
 */
data class LiveTrainClassification(
    val markers: List<LiveTrainMarker>,
    val coveredLineIds: Set<String>,
)

/**
 * Pure, clock-injected classification of the raw live-train feed into plottable,
 * freshness-tagged markers. EXPIRED positions are dropped entirely so a
 * dead/offline feed never leaves a frozen dot pretending to be live; STALE ones
 * are kept but tagged so the renderer can de-emphasise them and show their age.
 * Extracted from the simulation loop so it is unit-testable without the whole
 * ViewModel.
 */
fun classifyLiveTrains(
    liveTrains: List<LiveSuburbanTrain>,
    now: Instant,
): LiveTrainClassification {
    val markers = liveTrains.mapNotNull { train ->
        val fr = classifyLiveVehicleIso(train.updatedAt, now)
        if (fr.state == LiveVehicleState.EXPIRED) {
            null
        } else {
            LiveTrainMarker(train, fr.state, fr.ageSeconds)
        }
    }
    return LiveTrainClassification(
        markers = markers,
        coveredLineIds = markers.map { it.train.lineId }.toSet(),
    )
}
