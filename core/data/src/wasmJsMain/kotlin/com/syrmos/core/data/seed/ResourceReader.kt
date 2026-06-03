package com.syrmos.core.data.seed

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

@JsFun("(url) => fetch(url).then(r => r.text())")
private external fun jsFetch(url: JsString): Promise<JsString>

actual class ResourceReader {
    actual suspend fun readText(path: String): String {
        return try {
            val promise = jsFetch(path.toJsString())
            val result: JsString = promise.awaitJs()
            result.toString()
        } catch (_: Exception) {
            "{}"
        }
    }
}

private suspend fun <T : JsAny> Promise<T>.awaitJs(): T = suspendCoroutine { cont ->
    then(
        onFulfilled = { value: T ->
            cont.resume(value)
            value
        },
        onRejected = { error: JsAny ->
            cont.resumeWithException(Exception(error.toString()))
            error
        },
    )
}
