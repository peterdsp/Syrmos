package com.syrmos.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the reader asked the system for reduced motion. Provided once at the
 * app root from [rememberPlatformReduceMotion]; screens read it to swap a glide
 * for a jump (see `SyrmosMotionTokens.reduceMotionEasing`). Defaults to false
 * so previews and tests animate as shipped.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** The platform's reduce-motion preference, read once per composition. */
@Composable
expect fun rememberPlatformReduceMotion(): Boolean
