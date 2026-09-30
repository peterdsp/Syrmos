package com.syrmos.core.domain.station

import com.syrmos.core.model.schedule.SourceConfidence

/**
 * The station-complex all-directions board.
 *
 * Answers one question for a whole station complex: "what leaves from here, in
 * EVERY supported direction?" Kotlin port of the web `SyrmosStationBoard` and
 * the Swift `StationComplexBoard`; all three are asserted against the same
 * language-neutral cases in `fixtures/station-board/athens-all-directions.json`,
 * so the three clients cannot drift apart on grouping, ordering, deduplication
 * or coverage.
 *
 * Why this exists. [com.syrmos.core.domain.usecase.HomeDirectionBoard] capped at
 * four rows keyed by `(lineId, Direction)`, and
 * [com.syrmos.core.domain.usecase.GetStationDeparturesUseCase] ended with
 * `.take(8)`, so a less frequent railway destination could be dropped before
 * grouping ever ran, and a two-value direction enum could not represent a
 * branch, a short turn or a trip-specific destination at all. The station was
 * not the station either: Athens is five boarding stop ids
 * (`M2_STA`, `A1_ATH`, `A3_ATH`, `A4_ATH`, `GR_ATH`) that no client joined.
 *
 * The rule here is the same as on the other two clients: enumerate every group
 * first, limit presentation last.
 */
object StationComplexBoard {

    // ---------------------------------------------------------------- identity

    /** A reviewed station complex: one name, several real boarding areas. */
    data class Complex(
        val id: String,
        val name: String,
        val nameEl: String,
        val nameSq: String,
        val nameIt: String,
        val areas: List<Area>,
        /** True when synthesised from a single station rather than the registry. */
        val synthetic: Boolean = false,
    ) {
        data class Area(
            val id: String,
            val name: String,
            val nameEl: String,
            val nameSq: String,
            val nameIt: String,
            val stopIds: List<String>,
        )

        val memberStopIds: List<String>
            get() = areas.flatMap { it.stopIds }.distinct()

        fun localizedName(language: String): String = when (language) {
            "el" -> nameEl.ifBlank { name }
            "sq" -> nameSq.ifBlank { name }
            "it" -> nameIt.ifBlank { name }
            else -> name
        }

        fun localizedAreaName(areaId: String, language: String): String {
            val area = areas.firstOrNull { it.id == areaId } ?: return ""
            return when (language) {
                "el" -> area.nameEl.ifBlank { area.name }
                "sq" -> area.nameSq.ifBlank { area.name }
                "it" -> area.nameIt.ifBlank { area.name }
                else -> area.name
            }
        }
    }

    // ---------------------------------------------------------------- coverage

    /**
     * Coverage of one (boarding stop, service) pair. Partial coverage stays
     * visible: a board that could not read one of its services must never look
     * complete.
     */
    enum class Coverage(val wire: String) {
        LOADED("loaded"),
        LOADING("loading"),
        UNAVAILABLE("unavailable"),
        NO_DEPARTURE_IN_WINDOW("no_departure_in_window"),
        NOT_OPERATING("not_operating"),
        ;

        companion object {
            fun fromWire(raw: String): Coverage =
                entries.firstOrNull { it.wire == raw } ?: LOADED
        }
    }

    data class CoverageEntry(
        val areaId: String,
        val stopId: String,
        val lineId: String,
        val operatorId: String = "",
        val patternKey: String = "",
        val destination: String = "",
        val state: Coverage,
        val reason: String? = null,
    )

    // ------------------------------------------------------------------- input

    /**
     * One candidate departure from one boarding stop.
     *
     * [absoluteMinutes] is minutes from `now` on a monotonic timeline the CALLER
     * computed from absolute Europe/Athens timestamps. This type never re-derives
     * a service date and never adds 24 hours to a past departure: doing that here
     * is what produced "every yesterday train is 23 hours away".
     */
    data class Candidate(
        val stopId: String,
        val areaId: String,
        val lineId: String,
        val destination: String,
        val operatorId: String = "",
        val destinationId: String? = null,
        val patternKey: String = "",
        val tripId: String? = null,
        val serviceDate: String? = null,
        val time: String = "",
        val absoluteMinutes: Int,
        val scheduledMinutes: Int? = null,
        val source: SourceConfidence = SourceConfidence.SCHEDULED,
        val cancelled: Boolean = false,
        val status: String? = null,
        val observedAtEpochSeconds: Long? = null,
        val trainNo: String? = null,
        val serviceType: String = "",
        /**
         * False at a trip's own final stop. A terminal arrival is not a
         * departure, and offering "to this very station" is the defect this flag
         * exists to prevent.
         */
        val boardsHere: Boolean = true,
    )

    // ------------------------------------------------------------------ output

    data class Board(
        val complex: Complex,
        val windowMinutes: Int,
        val groups: List<Group>,
        /** Groups that actually have a departure inside the window. */
        val timedGroupCount: Int,
        /**
         * True when a service could not be READ. A service that simply has no
         * train tonight is complete information, not partial coverage.
         */
        val partial: Boolean,
    )

    data class Group(
        /**
         * Stable across refreshes: built from identity, never from a list index,
         * because rows reorder every time a train leaves.
         */
        val id: String,
        val areaId: String,
        val stopId: String,
        val operatorId: String,
        val lineId: String,
        val serviceType: String,
        val destination: String,
        val destinationId: String?,
        val destinationKey: String,
        val patternKey: String,
        val times: List<Time>,
        /** The soonest time inside the window that is not cancelled. */
        val next: Time?,
        val moreCount: Int,
        val total: Int,
        val coverage: Coverage,
        val coverageReason: String? = null,
        val sortMinutes: Int,
    ) {
        /**
         * Each time keeps its OWN source and cancellation state, so a group-wide
         * badge can never advertise a later live ETA over a scheduled lead time.
         */
        data class Time(
            val absoluteMinutes: Int,
            val time: String,
            val tripId: String? = null,
            val trainNo: String? = null,
            val source: SourceConfidence = SourceConfidence.SCHEDULED,
            val cancelled: Boolean = false,
            val status: String? = null,
            val observedAtEpochSeconds: Long? = null,
            val beyondWindow: Boolean = false,
            val scheduledMinutes: Int? = null,
        )

        /** The group's own source is the soonest shown time's source. */
        val source: SourceConfidence
            get() = times.firstOrNull()?.source ?: SourceConfidence.UNKNOWN
    }

    // ----------------------------------------------------------------- folding

    /**
     * Fold case and accents but keep the letters, so "Ελληνικό" and "Ελληνικο"
     * fold together while "Kifissia" and "Kifisias" never do.
     */
    fun fold(value: String?): String {
        if (value == null) return ""
        val lowered = value.trim().lowercase()
        val stripped = buildString(lowered.length) {
            for (ch in lowered) append(deaccent(ch))
        }
        return stripped.split(' ', '\t', '\n')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    /**
     * Kotlin common has no Unicode normalizer, so the accented letters the
     * Greek, Albanian and Italian station names actually use are mapped
     * explicitly. Anything else is left alone rather than mangled.
     */
    private fun deaccent(ch: Char): Char = when (ch) {
        'ά' -> 'α'; 'έ' -> 'ε'; 'ή' -> 'η'; 'ί', 'ϊ', 'ΐ' -> 'ι'
        'ό' -> 'ο'; 'ύ', 'ϋ', 'ΰ' -> 'υ'; 'ώ' -> 'ω'; 'ς' -> 'σ'
        'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ç' -> 'c'; 'ñ' -> 'n'
        else -> ch
    }

    private fun rank(source: SourceConfidence): Int = when (source) {
        SourceConfidence.LIVE -> 4
        SourceConfidence.SCHEDULED -> 3
        SourceConfidence.ESTIMATED -> 2
        SourceConfidence.OFFLINE -> 1
        else -> 0
    }

    // ----------------------------------------------------------- deduplication

    /**
     * Identity of one physical departure, used for deduplication ONLY.
     *
     * A provider-qualified trip id plus service date plus boarding stop is the
     * strong form. Without a trip id we use a documented conservative composite
     * that includes the EXACT absolute minute, so two real trains leaving in the
     * same minute stay distinct. Never a rounded countdown, never a destination
     * alone.
     */
    fun identity(d: Candidate): String =
        if (!d.tripId.isNullOrEmpty()) {
            "trip:${d.operatorId}:${d.tripId}@${d.serviceDate ?: ""}#${d.stopId}"
        } else {
            "composite:${d.stopId}|${d.lineId}|${fold(d.destination)}|${d.absoluteMinutes}"
        }

    private fun merge(a: Candidate, b: Candidate): Candidate {
        val primary = if (rank(b.source) > rank(a.source)) b else a
        val other = if (primary === a) b else a
        return primary.copy(
            cancelled = a.cancelled || b.cancelled,
            status = primary.status ?: other.status,
            scheduledMinutes = a.scheduledMinutes ?: b.scheduledMinutes,
            observedAtEpochSeconds = primary.observedAtEpochSeconds ?: other.observedAtEpochSeconds,
            tripId = a.tripId ?: b.tripId,
            trainNo = primary.trainNo ?: other.trainNo,
        )
    }

    /**
     * Collapse records that provably describe the same departure, preserving
     * input order for the survivors.
     */
    fun dedupe(list: List<Candidate>): List<Candidate> {
        val byId = LinkedHashMap<String, Candidate>()
        for (d in list) {
            val id = identity(d)
            val existing = byId[id]
            if (existing == null) {
                byId[id] = d
                continue
            }
            // Two records that BOTH carry a trip id and disagree are two trains.
            val a = existing.tripId
            val b = d.tripId
            if (!a.isNullOrEmpty() && !b.isNullOrEmpty() && a != b) {
                val alt = "$id#$b"
                if (!byId.containsKey(alt)) byId[alt] = d
                continue
            }
            byId[id] = merge(existing, d)
        }
        return byId.values.toList()
    }

    // ---------------------------------------------------------------- grouping

    fun groupId(
        areaId: String,
        operatorId: String,
        lineId: String,
        patternKey: String,
        destination: String,
    ): String = listOf(areaId, operatorId, lineId, patternKey, fold(destination)).joinToString("|")

    // --------------------------------------------------------------- the board

    /** Build the board. Pure: no clock, no network, no view state. */
    fun build(
        complex: Complex,
        candidates: List<Candidate>,
        coverage: List<CoverageEntry> = emptyList(),
        windowMinutes: Int = 12 * 60,
        maxTimesPerGroup: Int = 3,
    ): Board {
        // 1. Only real boardable departures become actionable rows.
        val raw = candidates.filter { it.boardsHere }

        // 2. Reconcile per service/trip, NOT per station. One published railway
        //    response can no longer suppress both metro directions, because
        //    nothing here picks a single winning source for a station.
        val merged = dedupe(raw)

        // 3. Enumerate groups BEFORE any limit is applied.
        val members = LinkedHashMap<String, MutableList<Candidate>>()
        val seed = HashMap<String, Candidate>()
        for (d in merged) {
            val id = groupId(d.areaId, d.operatorId, d.lineId, d.patternKey, d.destination)
            if (members[id] == null) {
                members[id] = mutableListOf()
                seed[id] = d
            }
            members.getValue(id).add(d)
        }

        val rows = members.map { (id, all) ->
            val first = seed.getValue(id)
            val sorted = all.sortedWith(
                compareBy({ it.absoluteMinutes }, { it.tripId ?: "" }),
            )
            val within = sorted.filter { it.absoluteMinutes in 0..windowMinutes }
            val beyond = sorted.filter { it.absoluteMinutes > windowMinutes }
            // A cancellation is status, never the recommended next departure.
            val eligible = within.filter { !it.cancelled }
            val pool = if (within.isEmpty()) beyond.take(1) else within
            val shown = if (maxTimesPerGroup > 0) pool.take(maxTimesPerGroup) else pool
            val next = eligible.firstOrNull()?.let {
                Group.Time(
                    absoluteMinutes = it.absoluteMinutes,
                    time = it.time,
                    tripId = it.tripId,
                    trainNo = it.trainNo,
                    source = it.source,
                )
            }
            Group(
                id = id,
                areaId = first.areaId,
                stopId = first.stopId,
                operatorId = first.operatorId,
                lineId = first.lineId,
                serviceType = sorted.firstOrNull { it.serviceType.isNotEmpty() }?.serviceType ?: "",
                destination = first.destination,
                destinationId = first.destinationId,
                destinationKey = fold(first.destination),
                patternKey = first.patternKey,
                times = shown.map {
                    Group.Time(
                        absoluteMinutes = it.absoluteMinutes,
                        time = it.time,
                        tripId = it.tripId,
                        trainNo = it.trainNo,
                        source = it.source,
                        cancelled = it.cancelled,
                        status = it.status,
                        observedAtEpochSeconds = it.observedAtEpochSeconds,
                        beyondWindow = it.absoluteMinutes > windowMinutes,
                        scheduledMinutes = it.scheduledMinutes,
                    )
                },
                next = next,
                moreCount = maxOf(0, within.size - shown.size),
                total = within.size,
                coverage = if (within.isEmpty()) Coverage.NO_DEPARTURE_IN_WINDOW else Coverage.LOADED,
                sortMinutes = next?.absoluteMinutes
                    ?: within.firstOrNull()?.absoluteMinutes
                    ?: beyond.firstOrNull()?.absoluteMinutes
                    ?: Int.MAX_VALUE,
            )
        }

        // 4. A service that produced no row still has to be visible, or a partly
        //    loaded board looks complete.
        val existingIds = rows.map { it.id }.toSet()
        val statusRows = coverage
            .filter { it.state != Coverage.LOADED }
            .mapNotNull { entry ->
                val id = groupId(
                    entry.areaId, entry.operatorId, entry.lineId, entry.patternKey, entry.destination,
                )
                if (id in existingIds) return@mapNotNull null
                Group(
                    id = id,
                    areaId = entry.areaId,
                    stopId = entry.stopId,
                    operatorId = entry.operatorId,
                    lineId = entry.lineId,
                    serviceType = "",
                    destination = entry.destination,
                    destinationId = null,
                    destinationKey = fold(entry.destination),
                    patternKey = entry.patternKey,
                    times = emptyList(),
                    next = null,
                    moreCount = 0,
                    total = 0,
                    coverage = entry.state,
                    coverageReason = entry.reason,
                    sortMinutes = Int.MAX_VALUE,
                )
            }
            .distinctBy { it.id }

        // 5. Sort by the next eligible departure with a stable tie break, so a
        //    frequent metro service cannot crowd a less frequent railway
        //    destination off the board: every group keeps exactly one row.
        val timed = rows.filter { it.total > 0 }
            .sortedWith(compareBy({ it.sortMinutes }, { it.id }))
        val quiet = (rows.filter { it.total == 0 } + statusRows).sortedBy { it.id }

        return Board(
            complex = complex,
            windowMinutes = windowMinutes,
            groups = timed + quiet,
            timedGroupCount = timed.size,
            partial = coverage.any {
                it.state == Coverage.LOADING || it.state == Coverage.UNAVAILABLE
            },
        )
    }
}
