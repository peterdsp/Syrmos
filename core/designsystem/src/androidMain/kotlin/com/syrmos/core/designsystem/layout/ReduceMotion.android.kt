package com.syrmos.core.designsystem.layout

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no single "reduce motion" switch; the accessibility setting
 * "Remove animations" sets the animator duration scale to zero, which is
 * what apps are expected to honour.
 */
@Composable
actual fun rememberPlatformReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
