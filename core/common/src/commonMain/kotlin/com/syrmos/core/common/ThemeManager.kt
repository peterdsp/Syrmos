package com.syrmos.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

expect fun persistTheme(theme: AppThemeMode)
expect fun loadPersistedTheme(): AppThemeMode?

object ThemeManager {
    private val _theme = MutableStateFlow(loadPersistedTheme() ?: AppThemeMode.SYSTEM)
    val theme: StateFlow<AppThemeMode> = _theme.asStateFlow()

    fun setTheme(theme: AppThemeMode) {
        _theme.value = theme
        persistTheme(theme)
    }
}
