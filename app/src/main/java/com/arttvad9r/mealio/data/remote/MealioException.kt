package com.arttvad9r.mealio.data.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/** UI-facing error categories. Mapped to string resources in the presentation layer. */
enum class ErrorKind {
    URL_INVALID,
    AUTH,
    UNREACHABLE,
    NOT_MEALIE,
    HTTP,
    UNKNOWN,
}

/**
 * Application-level error. Never carries the API token or any request header —
 * only a safe category plus an optional non-sensitive hint.
 */
class MealioException(
    val kind: ErrorKind,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Maps arbitrary network/runtime failures to a [MealioException] with a safe category. */
fun Throwable.toMealioException(): MealioException {
    if (this is MealioException) return this
    return when (this) {
        is HttpException -> when (code()) {
            401, 403 -> MealioException(ErrorKind.AUTH, "auth", this)
            404 -> MealioException(ErrorKind.NOT_MEALIE, "not found", this)
            else -> MealioException(ErrorKind.HTTP, "http ${code()}", this)
        }
        is UnknownHostException -> MealioException(ErrorKind.UNREACHABLE, "dns", this)
        is SocketTimeoutException -> MealioException(ErrorKind.UNREACHABLE, "timeout", this)
        is IOException -> MealioException(ErrorKind.UNREACHABLE, "io", this)
        else -> MealioException(ErrorKind.UNKNOWN, null, this)
    }
}
