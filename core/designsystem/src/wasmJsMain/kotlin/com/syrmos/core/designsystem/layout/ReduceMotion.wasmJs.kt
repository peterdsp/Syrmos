package com.syrmos.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("() => !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)")
private external fun prefersReducedMotion(): Boolean

@Composable
actual fun rememberPlatformReduceMotion(): Boolean = remember { prefersReducedMotion() }
