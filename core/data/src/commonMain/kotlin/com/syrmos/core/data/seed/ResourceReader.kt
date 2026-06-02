package com.syrmos.core.data.seed

expect class ResourceReader {
    suspend fun readText(path: String): String
}
