package com.mattzilla30.mbooru.extension

import com.mattzilla30.mbooru.core.model.NetworkFailure
import java.io.IOException
import retrofit2.HttpException

/** Do not show exception messages: they can contain authenticated URLs or response bodies. */
fun Throwable.safeNetworkMessage(): String {
    val failure =
        when (this) {
            is com.mattzilla30.mbooru.okhttp.ApiResponseTooLargeException ->
                NetworkFailure.InvalidResponse
            is HttpException ->
                NetworkFailure.http(code(), response()?.headers()?.get("Retry-After"))
            is IOException -> NetworkFailure.Connection
            else -> NetworkFailure.InvalidResponse
        }
    return when (failure) {
        NetworkFailure.Authentication -> "Sign in again or check this site's access requirements."
        is NetworkFailure.RateLimited ->
            failure.retryAfterSeconds?.let { "Too many requests. Retry after $it seconds." }
                ?: "Too many requests. Please try again later."
        NetworkFailure.Unavailable -> "The site is temporarily unavailable. Please try again later."
        is NetworkFailure.Http -> "The site returned HTTP ${failure.code}."
        NetworkFailure.Connection -> "Connection failed. Check your connection and retry."
        NetworkFailure.InvalidResponse -> "The site returned an unsupported response."
    }
}
