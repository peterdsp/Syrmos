package com.syrmos.app.platform

import androidx.compose.runtime.Composable
import com.syrmos.core.common.layout.ReservedRegion

// iOS does not report Android-style fold regions; native SwiftUI owns Duo
// arrangements and reserved regions in the iOS app target. The shared Compose
// layer resolves through the plain-window path here.
@Composable
actual fun rememberReservedRegions(): List<ReservedRegion> = emptyList()
