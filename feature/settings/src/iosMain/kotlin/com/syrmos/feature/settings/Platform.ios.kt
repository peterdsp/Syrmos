package com.syrmos.feature.settings

import platform.UIKit.UIDevice

actual fun currentPlatformId(): String = "ios"

actual fun currentPlatformUserAgent(): String =
    "Syrmos-iOS/${UIDevice.currentDevice.systemVersion} ${UIDevice.currentDevice.model}"

actual fun currentAppVersion(): String? = null
