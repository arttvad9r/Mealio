package com.arttvad9r.mealio.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.arttvad9r.mealio.R
import com.arttvad9r.mealio.data.remote.ErrorKind
import com.arttvad9r.mealio.data.remote.MealioException
import com.arttvad9r.mealio.data.remote.toMealioException

private fun errorStringRes(kind: ErrorKind?): Int = when (kind) {
    ErrorKind.URL_INVALID -> R.string.connect_error_url_invalid
    ErrorKind.AUTH -> R.string.connect_error_auth
    ErrorKind.UNREACHABLE -> R.string.connect_error_unreachable
    ErrorKind.NOT_MEALIE -> R.string.connect_error_not_mealie
    ErrorKind.HTTP -> R.string.connect_error_http
    ErrorKind.UNKNOWN, null -> R.string.connect_error_generic
}

/** Human-readable, Russian message for a network/data error (never exposes the token). */
@Composable
fun errorMessage(throwable: Throwable?): String =
    stringResource(errorStringRes(kindOf(throwable)))

/** Non-composable variant for use in coroutine callbacks. */
fun errorMessage(context: Context, throwable: Throwable?): String =
    context.getString(errorStringRes(kindOf(throwable)))

private fun kindOf(throwable: Throwable?): ErrorKind? =
    (throwable as? MealioException)?.kind ?: throwable?.toMealioException()?.kind
