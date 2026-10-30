package com.syrmos.core.common

/**
 * "1 change" / "2 changes", in four languages.
 *
 * Ariadne rendered every interchange count through the slash form
 * ("1 change(s)", "1 αλλαγή/ές", "1 ndërrim(e)", "1 cambio/i"), which reads as
 * unfinished copy in the one place a rider is deciding whether a route is
 * convenient. The count is always known, so the right word is always known.
 */
object TransferCopy {

    /** The noun phrase for [count] interchanges, including the number. */
    fun changes(count: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> if (count == 1) "$count αλλαγή" else "$count αλλαγές"
        AppLanguage.ALBANIAN -> if (count == 1) "$count ndërrim" else "$count ndërrime"
        AppLanguage.ITALIAN -> if (count == 1) "$count cambio" else "$count cambi"
        else -> if (count == 1) "$count change" else "$count changes"
    }
}
