package com.syrmos.core.domain.usecase

/**
 * Home "What matters now" hygiene: the operator feed sometimes carries the same
 * notice twice under different ids (a repost, a second channel), and two
 * identical cards read as a glitch. Items whose normalised text repeats are
 * dropped, keeping the first (the list is already in priority order). Shared
 * with iOS (`InsightDedupe` in HomeView.swift).
 */
object InsightDedupe {
    /** Lowercase, trimmed, whitespace collapsed, trailing punctuation dropped, first 160 chars. */
    fun normalise(text: String): String =
        text.lowercase().trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', ';', ':', ',', ' ').take(160)

    fun <T> distinctByText(items: List<T>, text: (T) -> String): List<T> {
        val seen = HashSet<String>()
        return items.filter { item ->
            val key = normalise(text(item))
            key.isEmpty() || seen.add(key)
        }
    }
}
