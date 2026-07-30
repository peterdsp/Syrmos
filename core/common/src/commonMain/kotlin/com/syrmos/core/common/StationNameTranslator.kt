package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StationNameTranslator {
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
        return _greekToEnglish.value[trimmed.lowercase()] ?: trimmed
    }
}
