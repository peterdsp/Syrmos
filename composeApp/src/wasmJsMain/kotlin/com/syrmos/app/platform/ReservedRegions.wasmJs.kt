package com.syrmos.app.platform

import androidx.compose.runtime.Composable
import com.syrmos.core.common.layout.ReservedRegion

// The web build is desktop-only (handhelds redirect to the native apps) and has
// no fold reporting, so it always resolves through the plain-window path.
@Composable
actual fun rememberReservedRegions(): List<ReservedRegion> = emptyList()
