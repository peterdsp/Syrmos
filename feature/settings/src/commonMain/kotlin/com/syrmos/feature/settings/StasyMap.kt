package com.syrmos.feature.settings

import androidx.compose.runtime.Composable

/// Returns a lambda the SettingsScreen can call to surface the bundled
/// STASY system-map PDF. Behaviour is platform-specific:
/// - Android: opens the asset through a FileProvider in any installed
///   PDF viewer.
/// - Web: opens the served PDF in a new browser tab.
/// - iOS: the native SwiftUI Settings screen renders the map directly,
///   so the KMP version is not wired in there.
@Composable
expect fun rememberStasyMapOpener(): () -> Unit
