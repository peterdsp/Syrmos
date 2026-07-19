package com.syrmos.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens

/**
 * Material3 colour schemes derived from the canonical [SyrmosColorTokens]
 * (task T2). These express the 2.0 light-first "Hellenic Rail Atlas" identity:
 * warm station-white chrome with the single Aegean-blue brand core. They are the
 * module's ready-to-consume Compose surface; task T5 swaps [SyrmosTheme] onto
 * them (this file does not change the app's current look on its own).
 */
val SyrmosLightColorScheme: ColorScheme = lightColorScheme(
    primary = SyrmosColorTokens.brand,
    onPrimary = SyrmosColorTokens.surfaceCard,
    primaryContainer = SyrmosColorTokens.brand.copy(alpha = 0.12f),
    onPrimaryContainer = SyrmosColorTokens.brandStrong,
    secondary = SyrmosColorTokens.national,
    background = SyrmosColorTokens.surface,
    onBackground = SyrmosColorTokens.onSurface,
    surface = SyrmosColorTokens.surfaceCard,
    onSurface = SyrmosColorTokens.onSurface,
    surfaceVariant = SyrmosColorTokens.surfaceMuted,
    onSurfaceVariant = SyrmosColorTokens.onSurfaceMuted,
    outline = SyrmosColorTokens.outline,
    error = SyrmosColorTokens.disruption,
)

val SyrmosDarkColorScheme: ColorScheme = darkColorScheme(
    primary = SyrmosColorTokens.brandOnDark,
    onPrimary = SyrmosColorTokens.surfaceDark,
    primaryContainer = SyrmosColorTokens.brandOnDark.copy(alpha = 0.16f),
    onPrimaryContainer = SyrmosColorTokens.brandOnDark,
    secondary = SyrmosColorTokens.brandOnDark,
    background = SyrmosColorTokens.surfaceDark,
    onBackground = SyrmosColorTokens.onSurfaceDark,
    surface = SyrmosColorTokens.surfaceCardDark,
    onSurface = SyrmosColorTokens.onSurfaceDark,
    surfaceVariant = SyrmosColorTokens.surfaceMutedDark,
    onSurfaceVariant = SyrmosColorTokens.onSurfaceMutedDark,
    outline = SyrmosColorTokens.outlineDark,
    error = SyrmosColorTokens.disruption,
)
