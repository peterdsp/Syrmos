package com.syrmos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(
    url: String,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            val webView = WKWebView()
            val nsUrl = NSURL.URLWithString(url) ?: return@UIKitView webView
            webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
            webView
        },
        update = { webView ->
            val nsUrl = NSURL.URLWithString(url) ?: return@update
            webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        },
        modifier = modifier,
    )
}
