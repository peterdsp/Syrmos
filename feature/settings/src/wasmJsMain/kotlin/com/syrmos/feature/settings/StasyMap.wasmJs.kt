package com.syrmos.feature.settings

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun rememberStasyMapOpener(): () -> Unit = {
    // Served as a static asset from wasmJsMain/resources, so it lands
    // next to index.html in the Pages deploy.
    window.open("stasy_system_map.pdf", "_blank")
}
