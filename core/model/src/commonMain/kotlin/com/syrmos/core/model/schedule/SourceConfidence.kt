package com.syrmos.core.model.schedule

/**
 * Where an on-screen answer came from, so Syrmos can say how sure it is
 * ("companion, not schedule" - it knows what it knows and says so). Lives in
 * core:model so both the domain layer that tags a departure and the design
 * system that renders the chip can share one catalog. Trilingual labels are
 * supplied by each platform; the enum itself is language-free.
 */
enum class SourceConfidence {
    /** A real-time position or arrival (e.g. a live suburban position from the operator feed). */
    LIVE,

    /** A scheduled timetable departure served from the live API, not a live position. */
    SCHEDULED,

    /** Served from the bundled offline snapshot (no network this session). */
    OFFLINE,

    /** Estimated from a frequency band rather than an exact timetabled minute. */
    ESTIMATED,

    /** The operator must be checked for live status. */
    OPERATOR_LINK,

    /** No live disruption/status data is available. */
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): SourceConfidence = when (raw?.lowercase()) {
            "live" -> LIVE
            "scheduled" -> SCHEDULED
            "offline" -> OFFLINE
            "estimated" -> ESTIMATED
            "operator_link", "operatorlink" -> OPERATOR_LINK
            else -> SCHEDULED
        }
    }
}
