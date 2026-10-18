// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.core.model

sealed interface NetworkFailure {
    data object Authentication : NetworkFailure
    data class RateLimited(val retryAfterSeconds: Long?) : NetworkFailure
    data object Unavailable : NetworkFailure
    data class Http(val code: Int) : NetworkFailure
    data object Connection : NetworkFailure
    data object InvalidResponse : NetworkFailure

    companion object {
        fun http(code: Int, retryAfter: String? = null): NetworkFailure = when (code) {
            401, 403 -> Authentication
            429 -> RateLimited(retryAfter?.toLongOrNull()?.takeIf { it in 0..86400 })
            in 500..599 -> Unavailable
            else -> Http(code)
        }
    }
}
