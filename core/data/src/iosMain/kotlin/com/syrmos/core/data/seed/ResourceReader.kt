package com.syrmos.core.data.seed

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

actual class ResourceReader {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun readText(path: String): String {
        val fileName = path.substringAfterLast("/").substringBeforeLast(".")
        val fileExtension = path.substringAfterLast(".")
        val directory = path.substringBeforeLast("/")
        val bundlePath = NSBundle.mainBundle.pathForResource(
            name = fileName,
            ofType = fileExtension,
            inDirectory = directory,
        ) ?: error("Resource not found: $path")
        return NSString.stringWithContentsOfFile(
            bundlePath,
            encoding = NSUTF8StringEncoding,
            error = null,
        ) ?: error("Could not read: $path")
    }
}
