package com.syrmos.feature.settings

import androidx.compose.runtime.Composable

// The iOS app ships a native SwiftUI Settings screen + PDFKit-backed
// StasyMapView, so the KMP version is never reached. Provide a no-op
// actual so cross-platform builds compile.
@Composable
actual fun rememberStasyMapOpener(): () -> Unit = { }
