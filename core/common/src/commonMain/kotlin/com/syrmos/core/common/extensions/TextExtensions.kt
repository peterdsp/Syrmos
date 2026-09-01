package com.syrmos.core.common.extensions

/**
 * Normalizes text for accent- and case-insensitive substring search across the
 * app's Greek, English and Albanian station names and codes. It lowercases,
 * then folds Greek tonos/dialytika and final sigma plus the common Latin
 * diacritics that turn up in transliterated and Albanian names, so a user
 * typing "attiki" matches "Αττική", "οδος" matches "Οδός", and "dhespoti"
 * matches "Dhëspoti".
 *
 * This is the single search normalizer shared by every predicate (see
 * `StationRepositoryImpl.matchesQuery` and `MapStationNode`). Apply it to BOTH
 * the query and the field before a `contains()` check. It deliberately does not
 * strip spaces or punctuation, keeping substring semantics intuitive; callers
 * that need a compact key (e.g. clustering) strip separately.
 *
 * A single pass over the lowercased chars keeps it allocation-light versus a
 * chain of `replace()` calls. All folded code points are in the BMP, so plain
 * Char iteration is safe.
 */
fun String.normalizeForSearch(): String {
    val lowered = lowercase()
    val sb = StringBuilder(lowered.length)
    for (ch in lowered) {
        sb.append(
            when (ch) {
                // Greek tonos + dialytika + final sigma.
                'ά' -> 'α'
                'έ' -> 'ε'
                'ή' -> 'η'
                'ί', 'ϊ', 'ΐ' -> 'ι'
                'ό' -> 'ο'
                'ύ', 'ϋ', 'ΰ' -> 'υ'
                'ώ' -> 'ω'
                'ς' -> 'σ'
                // Latin diacritics (transliterated Greek + Albanian ë/ç).
                'à', 'á', 'â', 'ã', 'ä', 'å' -> 'a'
                'è', 'é', 'ê', 'ë' -> 'e'
                'ì', 'í', 'î', 'ï' -> 'i'
                'ò', 'ó', 'ô', 'õ', 'ö' -> 'o'
                'ù', 'ú', 'û', 'ü' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                else -> ch
            },
        )
    }
    return sb.toString()
}
