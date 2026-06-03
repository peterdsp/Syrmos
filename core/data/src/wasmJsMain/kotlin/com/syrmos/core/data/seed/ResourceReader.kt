package com.syrmos.core.data.seed

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Response

actual class ResourceReader {
    actual suspend fun readText(path: String): String {
        val response: Response = window.fetch(path).await<Response>()
        return response.text().await<JsString>().toString()
    }
}
