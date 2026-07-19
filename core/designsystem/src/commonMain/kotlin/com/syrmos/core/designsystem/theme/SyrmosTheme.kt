package com.syrmos.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app theme now draws from the canonical design tokens (task T5): the
 * light-first "Hellenic Rail Atlas" identity, warm station-white chrome with the
 * single Aegean-blue brand core. The schemes live in [SyrmosLightColorScheme] /
 * [SyrmosDarkColorScheme], derived from [com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens].
 */
@Composable
fun SyrmosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SyrmosDarkColorScheme else SyrmosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
