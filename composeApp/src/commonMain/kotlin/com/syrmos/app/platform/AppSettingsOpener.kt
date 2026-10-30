package com.syrmos.app.platform

/**
 * Opens this app's own OS settings page, the only way back once a permission is
 * permanently denied.
 *
 * Android's `requestPermissions` becomes a silent no-op after the rider taps
 * "Don't ask again" (and MIUI's "Deny" behaves the same way). Nearby stations,
 * the locate-me control, station geofences and the widget snapshot all then do
 * nothing, with nothing on screen explaining why and no path to fix it. iOS has
 * had `UIApplication.openSettingsURLString` behind five different affordances
 * for exactly this; Android had none.
 *
 * Platforms that have no such page (iOS reaches it through its own views, Web
 * has nothing to open) report [canOpenAppSettings] false, and the UI hides the
 * control rather than offering a dead button.
 */
expect fun canOpenAppSettings(): Boolean

/** Opens the OS settings page for this app. No-op where [canOpenAppSettings] is false. */
expect fun openAppSettings()
