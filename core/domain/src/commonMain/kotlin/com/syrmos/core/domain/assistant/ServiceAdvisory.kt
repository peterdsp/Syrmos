package com.syrmos.core.domain.assistant

/**
 * A service notice relevant to Ariadne, projected from a STASY announcement by
 * the caller (feature/home on Android/Web, AriadneModel on iOS) so core/domain
 * stays free of the network layer's `STASYAnnouncement` type. [text] is the
 * already-localized title/summary the user would read on Home; station names
 * commonly appear inside it, which is how the matcher links a notice to a
 * station the user asked about.
 */
data class ServiceNotice(
    val id: String,
    /** Localized title/summary Ariadne reads back to the user. */
    val text: String,
    val affectedLineIds: List<String> = emptyList(),
    val severity: AdvisorySeverity = AdvisorySeverity.INFO,
    val validFrom: String? = null,
    val validUntil: String? = null,
    /**
     * All-language blob the matcher scans for station names, so a Greek-only
     * announcement still links to a station the user named in English. Defaults
     * to [text] when the caller has only one language available.
     */
    val searchText: String = text,
)

/** STASY severity, mapped from the feed's "info" / "warning" / "closure". */
enum class AdvisorySeverity {
    INFO,
    WARNING,
    CLOSURE,
    ;

    companion object {
        fun fromRaw(raw: String): AdvisorySeverity = when (raw.trim().lowercase()) {
            "closure", "closed" -> CLOSURE
            "warning", "warn", "alert" -> WARNING
            else -> INFO
        }
    }
}

/**
 * What Ariadne found relevant to the thing the user asked about: the matching
 * STASY notices plus whether severe weather is in play. [hasAny] is the cue for
 * the resolver to lead with the advisory before reciting the timetable.
 */
data class ServiceAdvisory(
    val notices: List<ServiceNotice> = emptyList(),
    val severeWeather: Boolean = false,
) {
    val hasAny: Boolean get() = notices.isNotEmpty() || severeWeather

    /** Closures first, then warnings, then info, so Ariadne leads with the worst news. */
    val ranked: List<ServiceNotice>
        get() = notices.sortedByDescending { it.severity.ordinal }

    /** The single most important notice, if any, for a one-line answer. */
    val top: ServiceNotice? get() = ranked.firstOrNull()

    companion object {
        val NONE = ServiceAdvisory()
    }
}

/**
 * Links the STASY notices and severe-weather signal we already show on Home to
 * whatever Ariadne is answering about: one station, one line, or a whole route.
 *
 * A notice matches a line by id, or a station by the station's name appearing in
 * the notice text. Matching runs on accent-folded, lowercased text
 * ([AthensTransitParser.fold]) so Greek, Albanian, English and Greeklish
 * converge. Pure and offline: the caller supplies the notices, the matcher never
 * fetches anything.
 *
 * Precision note: a line-wide notice (affectedLineIds contains the line) is
 * intentionally surfaced for any station on that line. For a companion, telling
 * an M3 traveller "there's an active M3 advisory, here it is" is the honest
 * move, not noise. Notices that name specific stations still match those
 * stations directly through the text.
 */
object ServiceAdvisoryMatcher {

    fun forLine(
        lineId: String,
        notices: List<ServiceNotice>,
        severeWeather: Boolean = false,
    ): ServiceAdvisory = ServiceAdvisory(
        notices = notices.filter { it.mentionsAnyLine(listOf(lineId)) },
        severeWeather = severeWeather,
    )

    fun forStation(
        stationNames: List<String>,
        stationLineIds: List<String>,
        notices: List<ServiceNotice>,
        severeWeather: Boolean = false,
    ): ServiceAdvisory = ServiceAdvisory(
        notices = notices.filter { it.mentionsAnyLine(stationLineIds) || it.mentionsAnyName(stationNames) },
        severeWeather = severeWeather,
    )

    fun forRoute(
        lineIds: List<String>,
        stationNames: List<String>,
        notices: List<ServiceNotice>,
        severeWeather: Boolean = false,
    ): ServiceAdvisory = ServiceAdvisory(
        notices = notices.filter { it.mentionsAnyLine(lineIds) || it.mentionsAnyName(stationNames) },
        severeWeather = severeWeather,
    )

    private fun ServiceNotice.mentionsAnyLine(lineIds: List<String>): Boolean {
        if (affectedLineIds.isEmpty() || lineIds.isEmpty()) return false
        val wanted = lineIds.map { normalizeLine(it) }.filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return false
        return affectedLineIds.any { normalizeLine(it) in wanted }
    }

    private fun ServiceNotice.mentionsAnyName(names: List<String>): Boolean {
        if (names.isEmpty() || searchText.isBlank()) return false
        val folded = AthensTransitParser.fold(searchText)
        return names.any { name ->
            val f = AthensTransitParser.fold(name).trim()
            // Guard against 1-2 char names matching noise; real station names are longer.
            f.length >= 3 && folded.contains(f)
        }
    }

    /** "M3" / "m3" / "line 3" / "γραμμή 3" ids reduced to a comparable token. */
    private fun normalizeLine(id: String): String =
        AthensTransitParser.fold(id)
            .replace("line", "")
            .replace("γραμμη", "")
            .replace("metro", "")
            .replace(" ", "")
            .trim()
}
