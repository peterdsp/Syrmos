package com.syrmos.feature.settings

import androidx.compose.runtime.Composable

/// Returns a lambda the SettingsScreen can call to surface the bundled
/// Athens transit map. Behaviour is platform-specific:
/// - Android: opens the JPEG asset through a FileProvider in any
///   installed image viewer (system gallery, etc).
/// - Web: opens the JPEG in a new browser tab.
/// - iOS: the native SwiftUI Settings screen renders the image inline
///   inside a modal sheet, so the KMP version is not wired in there.
@Composable
expect fun rememberStasyMapOpener(): () -> Unit
