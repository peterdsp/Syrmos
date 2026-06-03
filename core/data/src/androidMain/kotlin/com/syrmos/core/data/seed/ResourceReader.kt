package com.syrmos.core.data.seed

import android.content.Context

actual class ResourceReader(private val context: Context) {
    actual suspend fun readText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
