package com.syrmos.core.domain.station

import com.syrmos.core.model.transit.Station
import kotlin.math.round

/**
 * A presentation-level station group (finding 2, Android/shared peer of the iOS
 * `StationGrouping`): one selector row per real physical station.
 *
 * Co-located stops that share a station identity, the same folded name AND the
 * same location, collapse into one group that carries every underlying stop id and
 * the union of the lines serving them. So the selector never lists the same
 * physical station twice (the tram `A1_KIF` / `A2_KIF`, both "Kifisias" at one
 * point), while genuinely distinct stations stay separate: "Kifissia" and
 * "Kifisias" differ in both name and place, so they never merge. Identity is the
 * conjunction of name AND location, never one alone.
 */
data class StationGroup(
    /** Stable across launches and input order: the smallest member id. */
    val id: String,
    val name: String,
    val nameEl: String,
    val nameSq: String?,
    /** Every routable stop id in the group, sorted, so nothing is lost on merge. */
    val memberIds: List<String>,
    /** Union of the lines serving the group, for the row's disambiguating badges. */
    val lineIds: List<String>,
) {
    /** The id carried to the planner on selection. */
    val representativeId: String get() = id
}

object StationGrouping {

    // Fold case and accents but keep the letters, so accented and unaccented
    // spellings match while genuinely different spellings never collapse together.
    private val FOLD: Map<Char, Char> = mapOf(
        'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ό' to 'ο', 'ύ' to 'υ', 'ώ' to 'ω',
        'ϊ' to 'ι', 'ϋ' to 'υ', 'ΐ' to 'ι', 'ΰ' to 'υ',
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'à' to 'a', 'è' to 'e',
        'ç' to 'c', 'ë' to 'e',
    )

    fun fold(s: String): String {
        val lower = s.trim().lowercase()
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(FOLD[c] ?: c)
        return sb.toString()
    }

    // ~11m coordinate bucket. Only near-identical points merge, so co-location is
    // required in addition to a name match: proximity is evidence, not identity.
    private fun bucket(lat: Double, lon: Double): String =
        "${round(lat * 10000.0) / 10000.0},${round(lon * 10000.0) / 10000.0}"

    /** Collapse a raw station list into presentation groups, first-seen order. */
    fun groups(stations: List<Station>): List<StationGroup> {
        val buckets = LinkedHashMap<String, MutableList<Station>>()
        for (st in stations) {
            val key = fold(st.name.ifEmpty { st.nameEl }) + "|" + bucket(st.latitude, st.longitude)
            buckets.getOrPut(key) { mutableListOf() }.add(st)
        }
        return buckets.values.map { raw ->
            val members = raw.sortedBy { it.id }
            val rep = members.first()
            val lineIds = LinkedHashSet<String>().apply { members.forEach { addAll(it.lineIds) } }.toList()
            StationGroup(rep.id, rep.name, rep.nameEl, rep.nameSq, members.map { it.id }, lineIds)
        }
    }

    /** Whether a group matches a folded query on any of its member names. */
    fun matches(group: StationGroup, foldedQuery: String): Boolean {
        if (foldedQuery.isEmpty()) return true
        return fold(group.name).contains(foldedQuery) ||
            fold(group.nameEl).contains(foldedQuery) ||
            (group.nameSq?.let { fold(it).contains(foldedQuery) } ?: false)
    }
}
