package com.syrmos.app.platform

import android.content.Intent
import android.net.Uri
import android.provider.Settings

actual fun canOpenAppSettings(): Boolean = androidPlatformContext() != null

actual fun openAppSettings() {
    val ctx = androidPlatformContext() ?: return
    // Launched from an application context, so it needs its own task.
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ctx.packageName, null),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        ctx.startActivity(intent)
    } catch (_: Throwable) {
        // A device with no settings activity for this intent is pathological,
        // but it must not take the Settings screen down with it.
    }
}
