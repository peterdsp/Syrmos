package com.syrmos.core.common.result

sealed interface SyrmosResult<out T> {
    data class Success<T>(val data: T) : SyrmosResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : SyrmosResult<Nothing>
    data object Loading : SyrmosResult<Nothing>
}

inline fun <T, R> SyrmosResult<T>.map(transform: (T) -> R): SyrmosResult<R> = when (this) {
    is SyrmosResult.Success -> SyrmosResult.Success(transform(data))
    is SyrmosResult.Error -> this
    is SyrmosResult.Loading -> this
}

inline fun <T> SyrmosResult<T>.onSuccess(action: (T) -> Unit): SyrmosResult<T> {
    if (this is SyrmosResult.Success) action(data)
    return this
}

inline fun <T> SyrmosResult<T>.onError(action: (String, Throwable?) -> Unit): SyrmosResult<T> {
    if (this is SyrmosResult.Error) action(message, cause)
    return this
}

fun <T> SyrmosResult<T>.getOrNull(): T? = when (this) {
    is SyrmosResult.Success -> data
    else -> null
}
