package com.syrmos.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SyrmosPrimary,
    onPrimary = SyrmosSurface,
    primaryContainer = SyrmosPrimary.copy(alpha = 0.12f),
    secondary = SyrmosSecondary,
    background = SyrmosBackground,
    surface = SyrmosSurface,
    onSurface = SyrmosOnSurface,
    onSurfaceVariant = SyrmosOnSurfaceVariant,
    outline = SyrmosOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = SyrmosPrimaryDarkTheme,
    onPrimary = SyrmosPrimaryDark,
    primaryContainer = SyrmosPrimaryDarkTheme.copy(alpha = 0.12f),
    secondary = SyrmosSecondary,
    background = SyrmosBackgroundDark,
    surface = SyrmosSurfaceDark,
    onSurface = SyrmosOnSurfaceDark,
    onSurfaceVariant = SyrmosOnSurfaceVariantDark,
    outline = SyrmosOutline,
)

@Composable
fun SyrmosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
