package com.syrmos.core.model.settings

data class AppSettings(
    val language: Language = Language.EN,
    val theme: AppTheme = AppTheme.SYSTEM,
    val favoriteStationIds: Set<String> = emptySet(),
    val favoriteLineIds: Set<String> = emptySet(),
)

enum class Language(val code: String, val displayName: String) {
    EN("en", "English"),
    EL("el", "Ελληνικά"),
}

enum class AppTheme {
    SYSTEM, LIGHT, DARK,
}
