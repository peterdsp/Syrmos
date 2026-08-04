package com.syrmos.app

import androidx.compose.runtime.compositionLocalOf

val LocalAriadneOpener = compositionLocalOf<(() -> Unit)?> { null }
