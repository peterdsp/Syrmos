package com.syrmos.feature.settings

import kotlinx.browser.window

actual fun currentPlatformId(): String = "web"

actual fun currentPlatformUserAgent(): String = try {
    window.navigator.userAgent
} catch (_: Throwable) {
    "Syrmos-Web"
}

actual fun currentAppVersion(): String? = null
