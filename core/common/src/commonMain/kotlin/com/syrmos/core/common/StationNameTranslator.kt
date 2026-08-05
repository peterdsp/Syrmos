package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StationNameTranslator {
    private val englishToItalian = mapOf(
        "athens" to "Atene",
        "athina" to "Atene",
        "piraeus" to "Pireo",
        "pireas" to "Pireo",
        "thessaloniki" to "Salonicco",
        "larisa" to "Larissa",
        "larissa" to "Larissa",
        "patra" to "Patrasso",
        "patras" to "Patrasso",
    )

    private val _greekToEnglish = MutableStateFlow<Map<String, String>>(emptyMap())
    val greekToEnglish: StateFlow<Map<String, String>> = _greekToEnglish.asStateFlow()

    fun load(stationPairs: List<Pair<String, String>>) {
        val map = mutableMapOf<String, String>()
        for ((english, greek) in stationPairs) {
            val key = greek.trim().lowercase()
            if (english.trim() != greek.trim()) {
                map[key] = english.trim()
            } else if (key !in map) {
                map[key] = english.trim()
            }
        }
        _greekToEnglish.value = map
    }

    fun translate(greekName: String, language: AppLanguage): String {
        val trimmed = greekName.trim()
        if (language == AppLanguage.GREEK) return trimmed
        val english = _greekToEnglish.value[trimmed.lowercase()] ?: trimmed
        return localizeEnglish(english, language)
    }

    fun resolve(greekName: String?, englishName: String?, language: AppLanguage): String {
        val greek = greekName.orEmpty().trim()
        if (language == AppLanguage.GREEK) return greek
        val english = englishName.orEmpty().trim().ifEmpty {
            _greekToEnglish.value[greek.lowercase()] ?: greek
        }
        return localizeEnglish(english, language)
    }

    fun localizeEnglish(englishName: String, language: AppLanguage): String {
        val trimmed = englishName.trim()
        if (language != AppLanguage.ITALIAN) return trimmed
        return englishToItalian[trimmed.lowercase()] ?: trimmed
    }
}
