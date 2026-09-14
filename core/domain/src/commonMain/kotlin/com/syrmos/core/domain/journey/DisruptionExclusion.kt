package com.syrmos.core.domain.journey

import com.syrmos.core.domain.assistant.AdvisorySeverity
import com.syrmos.core.domain.assistant.ServiceNotice
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.LegKind

/**
 * The result of planning with active disruptions considered (Phase R, S10
 * "Suspended segment"). A [Suspended] outcome is deliberately distinct from
 * [NoRoute]: "the line you need is closed, here is why" is honest recovery
 * information, while "no journey found" is the absence of any path. Never route a
 * rider through suspended track, and never fabricate a replacement (prompt S07).
 */
sealed interface DisruptionOutcome {
    /**
     * A usable plan. [excludedLineIds] are the suspended lines the planner routed
     * around (empty when nothing was suspended), so the UI can disclose the
     * detour honestly.
     */
    data class Routed(
        val options: List<JourneyOption>,
        val excludedLineIds: Set<String>,
    ) : DisruptionOutcome

    /**
     * The only path rides suspended track. [affectedLineIds] names the closed
     * lines and [notices] carries the operator source, so the UI can offer
     * "Alternatives / Official update" instead of a plan that cannot be travelled.
     */
    data class Suspended(
        val affectedLineIds: Set<String>,
        val notices: List<ServiceNotice>,
    ) : DisruptionOutcome

    /** No path exists even before disruption is considered. */
    data object NoRoute : DisruptionOutcome
}

/**
 * Pure, offline disruption logic shared by web/iOS/Android. The graph re-plan
 * itself lives in the platform planner (it needs the station/line repositories);
 * this object decides WHICH lines are suspended and classifies the two candidate
 * plans (with and without exclusion) into an honest [DisruptionOutcome].
 *
 * Mirrors web `web-disruption.js` and iOS `DisruptionExclusion.swift` exactly and
 * is covered case-for-case by `fixtures/journeys/disruption.json`.
 */
object DisruptionExclusion {

    /**
     * Line ids the operator has SUSPENDED, from CLOSURE-severity notices only.
     * Info and warning notices inform the rider but never remove a routable line.
     * Ids are normalized (see [normalizeLine]) so a "M3" notice matches an "m3"
     * line id.
     */
    fun suspendedLineIds(notices: List<ServiceNotice>): Set<String> =
        notices.asSequence()
            .filter { it.severity == AdvisorySeverity.CLOSURE }
            .flatMap { it.affectedLineIds.asSequence() }
            .map { normalizeLine(it) }
            .filter { it.isNotEmpty() }
            .toSet()

    /** The suspended lines an option's ride legs actually travel on. */
    fun optionUsesSuspended(option: JourneyOption, suspended: Set<String>): Set<String> {
        if (suspended.isEmpty()) return emptySet()
        return option.legs.asSequence()
            .filter { it.kind == LegKind.RIDE }
            .mapNotNull { it.lineId }
            .map { normalizeLine(it) }
            .filter { it in suspended }
            .toSet()
    }

    /**
     * Classify a disruption-aware plan.
     *
     * @param avoidingOptions options computed WITH the suspended lines banned.
     * @param naiveOptions options computed WITHOUT exclusion, used only to tell a
     *   genuine suspension (the sole path rides closed track) from an unrelated
     *   no-route.
     * @param notices the active service notices.
     */
    fun classify(
        avoidingOptions: List<JourneyOption>,
        naiveOptions: List<JourneyOption>,
        notices: List<ServiceNotice>,
    ): DisruptionOutcome {
        val suspended = suspendedLineIds(notices)
        if (avoidingOptions.isNotEmpty()) {
            // Only claim a detour for lines the naive (unrestricted) plan actually
            // rode: an unrelated closure must not show a "routing around" chip.
            val excluded = if (suspended.isEmpty()) {
                emptySet()
            } else {
                naiveOptions.firstOrNull()?.let { optionUsesSuspended(it, suspended) } ?: emptySet()
            }
            return DisruptionOutcome.Routed(options = avoidingOptions, excludedLineIds = excluded)
        }
        // No route avoids the suspension. Only call it a suspension when the sole
        // available path genuinely rides suspended track; otherwise it is a plain
        // no-route and must not blame the closure.
        val naive = naiveOptions.firstOrNull()
        if (naive != null) {
            val hit = optionUsesSuspended(naive, suspended)
            if (hit.isNotEmpty()) {
                val relevant = notices.filter { notice ->
                    notice.severity == AdvisorySeverity.CLOSURE &&
                        notice.affectedLineIds.any { normalizeLine(it) in hit }
                }
                return DisruptionOutcome.Suspended(affectedLineIds = hit, notices = relevant)
            }
        }
        return DisruptionOutcome.NoRoute
    }

    /**
     * Normalize a line id/token for comparison. Kept intentionally simple (trim +
     * lowercase, strip the "line"/"metro" wording) because notice affectedLineIds
     * and Line ids are already canonical tokens like "M3"/"T6"; this only absorbs
     * casing and the odd "Line 3" phrasing. Mirror byte-for-byte on web/iOS.
     */
    fun normalizeLine(id: String): String =
        id.trim().lowercase()
            .replace("line", "")
            .replace("metro", "")
            .replace(" ", "")
            .trim()
}
