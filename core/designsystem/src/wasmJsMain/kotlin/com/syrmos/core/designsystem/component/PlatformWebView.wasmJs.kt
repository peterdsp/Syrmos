package com.syrmos.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.browser.window

@Composable
actual fun PlatformWebView(
    url: String,
    modifier: Modifier,
) {
    LaunchedEffect(url) {
        window.open(url, "_blank")
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Opening in browser...")
    }
}
