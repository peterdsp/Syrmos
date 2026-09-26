package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage

/**
 * "1 report" / "3 reports" in the reader's language.
 *
 * Every language we ship inflects the noun for one versus many, and a
 * counter that always uses the plural ("1 reports", "1 αναφορές") reads as
 * a bug. Each pair is (one, many). Zero takes the plural in all four
 * languages. Twin of `countLabel` in iOS Localization.swift.
 */
fun countLabel(
    count: Int,
    lang: AppLanguage,
    en: Pair<String, String>,
    el: Pair<String, String>,
    sq: Pair<String, String>,
    it: Pair<String, String>,
): String {
    val forms = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        AppLanguage.ITALIAN -> it
        else -> en
    }
    val word = if (count == 1) forms.first else forms.second
    return "$count $word"
}
