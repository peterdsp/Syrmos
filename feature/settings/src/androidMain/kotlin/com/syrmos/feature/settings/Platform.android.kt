package com.syrmos.feature.settings

import android.os.Build

actual fun currentPlatformId(): String = "android"

actual fun currentPlatformUserAgent(): String =
    "Syrmos-Android/${Build.VERSION.RELEASE} ${Build.MANUFACTURER} ${Build.MODEL}"

actual fun currentAppVersion(): String? = null
